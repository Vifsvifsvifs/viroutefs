// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.util.Locale

internal const val MAX_SUBSCRIPTION_BYTES: Int = 2 * 1024 * 1024
internal const val MAX_SUBSCRIPTION_PROFILES: Int = 512

internal fun interface SubscriptionHostResolver {
    fun resolve(host: String): Array<InetAddress>
}

internal data class SubscriptionFetchResult(
    val body: String,
    val fetchedAtEpochMs: Long,
)

internal class ProfileSubscriptionClient(
    private val resolver: SubscriptionHostResolver = SubscriptionHostResolver(InetAddress::getAllByName),
    private val connectionFactory: (URL) -> HttpURLConnection = { url ->
        url.openConnection() as HttpURLConnection
    },
) {
    fun fetch(rawUrl: String): SubscriptionFetchResult {
        var currentUrl = validateSubscriptionUrl(rawUrl, resolver)
        repeat(MAX_REDIRECTS + 1) { redirectIndex ->
            validateSubscriptionUrl(currentUrl.toString(), resolver)
            val connection = connectionFactory(currentUrl).apply {
                instanceFollowRedirects = false
                useCaches = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty(
                    "Accept",
                    "text/plain, application/json, application/yaml, text/yaml;q=0.9, */*;q=0.1",
                )
            }
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    require(redirectIndex < MAX_REDIRECTS) {
                        "Слишком много перенаправлений при загрузке подписки."
                    }
                    val location = connection.getHeaderField("Location")
                        ?.takeIf(String::isNotBlank)
                        ?: error("Сервер подписки вернул перенаправление без адреса.")
                    currentUrl = validateSubscriptionUrl(URL(currentUrl, location).toString(), resolver)
                    return@repeat
                }
                require(status == HttpURLConnection.HTTP_OK) {
                    "Сервер подписки вернул HTTP $status."
                }
                val declaredSize = connection.contentLengthLong
                require(declaredSize < 0L || declaredSize <= MAX_SUBSCRIPTION_BYTES) {
                    "Подписка слишком большая. Максимум — 2 МБ."
                }
                val bytes = connection.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_SUBSCRIPTION_BYTES) {
                            "Подписка слишком большая. Максимум — 2 МБ."
                        }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
                require(bytes.isNotEmpty()) { "Сервер вернул пустую подписку." }
                val body = try {
                    decodeStrictUtf8(bytes)
                } finally {
                    bytes.fill(0)
                }
                return SubscriptionFetchResult(
                    body = body,
                    fetchedAtEpochMs = Instant.now().toEpochMilli(),
                )
            } finally {
                connection.disconnect()
            }
        }
        error("Не удалось загрузить подписку.")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 3
        val REDIRECT_CODES = setOf(
            HttpURLConnection.HTTP_MOVED_PERM,
            HttpURLConnection.HTTP_MOVED_TEMP,
            HttpURLConnection.HTTP_SEE_OTHER,
            307,
            308,
        )
    }
}

internal fun validateSubscriptionUrlSyntax(rawUrl: String): String? {
    if (rawUrl.length > MAX_SUBSCRIPTION_URL_LENGTH) {
        return "URL слишком длинный. Максимум — $MAX_SUBSCRIPTION_URL_LENGTH символов."
    }
    if (rawUrl == REDACTED_SECRET) {
        return "защищённый URL недоступен; добавьте подписку заново."
    }
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull()
        ?: return "введите корректный HTTPS URL."
    if (!uri.scheme.equals("https", ignoreCase = true)) {
        return "разрешены только HTTPS-адреса."
    }
    if (uri.rawUserInfo != null) {
        return "логин и пароль в адресе запрещены; используйте токен провайдера в пути или query."
    }
    if (uri.rawFragment != null) return "фрагмент # в URL подписки не поддерживается."
    val host = uri.host?.trimEnd('.')?.takeIf(String::isNotBlank)
        ?: return "в URL отсутствует корректное имя сервера."
    if (host.equals("localhost", true) || host.endsWith(".localhost", true)) {
        return "локальные адреса запрещены."
    }
    val asciiHost = runCatching { IDN.toASCII(host) }.getOrNull()
        ?: return "имя сервера некорректно."
    if ('.' !in asciiHost && ':' !in asciiHost) {
        return "локальные одноуровневые имена запрещены."
    }
    if (uri.port == 0 || uri.port > 65535) return "порт URL некорректен."
    return null
}

private const val MAX_SUBSCRIPTION_URL_LENGTH = 8_192

internal fun validateSubscriptionUrl(
    rawUrl: String,
    resolver: SubscriptionHostResolver = SubscriptionHostResolver(InetAddress::getAllByName),
): URL {
    validateSubscriptionUrlSyntax(rawUrl)?.let { error(it) }
    val uri = URI(rawUrl.trim())
    val host = uri.host.trimEnd('.').lowercase(Locale.ROOT)
    val addresses = runCatching { resolver.resolve(host) }
        .getOrElse { error("Не удалось определить адрес сервера подписки.") }
    require(addresses.isNotEmpty()) { "Сервер подписки не имеет IP-адреса." }
    require(addresses.all(::isPublicSubscriptionAddress)) {
        "URL подписки указывает на локальную, служебную или непубличную сеть."
    }
    return uri.toURL()
}

internal fun maskSubscriptionUrl(rawUrl: String): String {
    val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return "HTTPS URL скрыт"
    val host = uri.host ?: return "HTTPS URL скрыт"
    val port = uri.port.takeIf { it > 0 && it != 443 }?.let { ":$it" }.orEmpty()
    return "https://$host$port/…"
}

internal fun isPublicSubscriptionAddress(address: InetAddress): Boolean {
    if (
        address.isAnyLocalAddress ||
        address.isLoopbackAddress ||
        address.isLinkLocalAddress ||
        address.isSiteLocalAddress ||
        address.isMulticastAddress
    ) {
        return false
    }
    val bytes = address.address
    return when (address) {
        is Inet4Address -> isPublicIpv4(bytes)
        is Inet6Address -> isPublicIpv6(bytes)
        else -> false
    }
}

private fun isPublicIpv4(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    return when {
        first == 0 -> false
        first == 10 -> false
        first == 100 && second in 64..127 -> false
        first == 127 -> false
        first == 169 && second == 254 -> false
        first == 172 && second in 16..31 -> false
        first == 192 && second == 0 -> false
        first == 192 && second == 168 -> false
        first == 198 && second in 18..19 -> false
        first == 198 && second == 51 -> false
        first == 203 && second == 0 -> false
        first >= 224 -> false
        else -> true
    }
}

private fun isPublicIpv6(bytes: ByteArray): Boolean {
    val first = bytes[0].toInt() and 0xff
    val second = bytes[1].toInt() and 0xff
    if (first and 0xfe == 0xfc) return false
    if (first == 0x20 && second == 0x01 && (bytes[2].toInt() and 0xff) == 0x0d &&
        (bytes[3].toInt() and 0xff) == 0xb8
    ) {
        return false
    }
    return true
}

private fun decodeStrictUtf8(bytes: ByteArray): String {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        .getOrElse { error("Подписка содержит некорректный UTF-8 текст.") }
}
