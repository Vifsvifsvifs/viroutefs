// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONObject

data class OpenVpnImportResult(
    val optionsJson: String,
    val warnings: List<String>,
    val routes: List<String>,
)

data class OpenVpnAuthUserPass(
    val username: String,
    val password: String,
)

internal const val OPENVPN_ROUTE_ROUTER_MIGRATION_VERSION = 15

/** Reads the standard two-line OpenVPN auth-user-pass file without logging its contents. */
fun importOpenVpnAuthUserPass(bytes: ByteArray): OpenVpnAuthUserPass {
    require(bytes.size in 1..MAX_OPENVPN_AUTH_USER_PASS_BYTES) {
        "Файл auth-user-pass пуст или превышает безопасный размер."
    }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val source = runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }
        .getOrElse { error("Файл auth-user-pass должен быть текстом UTF-8.") }
        .replace("\r\n", "\n")
        .replace('\r', '\n')
    require('\u0000' !in source) { "Файл auth-user-pass содержит недопустимые данные." }

    val lines = source.split('\n')
    val username = lines.getOrNull(0).orEmpty().removePrefix("\uFEFF")
    val password = lines.getOrNull(1).orEmpty()
    require(username.isNotBlank() && password.isNotEmpty()) {
        "В auth-user-pass нужны две строки: логин и пароль."
    }
    require(username.length <= MAX_OPENVPN_CREDENTIAL_CHARS && password.length <= MAX_OPENVPN_CREDENTIAL_CHARS) {
        "Логин или пароль в auth-user-pass превышает безопасную длину."
    }
    require(lines.drop(2).none(String::isNotBlank)) {
        "В auth-user-pass должны быть только две строки: логин и пароль."
    }
    return OpenVpnAuthUserPass(username = username, password = password)
}

/**
 * Converts common OpenVPN client directives to a sing-box openvpn-client endpoint.
 *
 * The importer is deliberately local and conservative. Unknown directives stay
 * visible as warnings, and the resulting endpoint still has to pass libbox
 * validation before ViRouteFS saves it.
 */
fun importOpenVpnProfile(source: String): OpenVpnImportResult {
    val normalized = source.replace("\r\n", "\n").replace('\r', '\n')
    val blocks = extractInlineBlocks(normalized)
    val warnings = mutableListOf<String>()
    val remotes = mutableListOf<OpenVpnRemote>()
    var network = "udp"
    var username: String? = null
    var password: String? = null
    var legacyCipher: String? = null
    var dataCiphers: List<String> = emptyList()
    var dataCiphersFallback: String? = null
    var auth: String? = null
    var serverName: String? = null
    var remoteCertificateTls: String? = null
    var tlsVersionMin: String? = null
    var compression: String? = null
    var compressionLzo: String? = null
    var controlWrapType: String? = null
    var keyDirection: String? = null
    val routes = mutableListOf<String>()
    var routeNoPull = false
    var redirectGateway = false
    var redirectGatewayFlags: List<String> = emptyList()

    blocks.withoutBlocks.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
        val tokens = tokenizeOpenVpnLine(line)
        if (tokens.isEmpty()) return@forEachIndexed
        val directive = tokens.first().lowercase()
        val args = tokens.drop(1)
        when (directive) {
            "client", "dev", "nobind", "persist-key", "persist-tun", "remote-random",
            "remote-random-hostname", "resolv-retry", "verb", "mute",
            "auth-nocache", "pull", "route-delay", "route-method" -> Unit

            "proto" -> network = normalizeOpenVpnNetwork(args.firstOrNull(), warnings)
            "remote" -> {
                val host = args.getOrNull(0).orEmpty()
                val port = args.getOrNull(1)?.toIntOrNull() ?: 1194
                val remoteNetwork = args.getOrNull(2)?.let {
                    normalizeOpenVpnNetwork(it, warnings)
                } ?: network
                if (host.isBlank()) {
                    warnings += "Строка ${index + 1}: remote без адреса пропущен."
                } else {
                    remotes += OpenVpnRemote(host, port, remoteNetwork)
                }
            }
            "auth-user-pass" -> {
                if (args.isNotEmpty()) {
                    warnings += "Внешний файл auth-user-pass не читается автоматически. Введите username/password в редакторе."
                }
            }
            "setenv" -> if (args.firstOrNull().equals("UV_USERNAME", true)) {
                username = args.drop(1).joinToString(" ").takeIf(String::isNotBlank)
            }
            "username" -> username = args.joinToString(" ").takeIf(String::isNotBlank)
            "password" -> password = args.joinToString(" ").takeIf(String::isNotBlank)
            "cipher" -> legacyCipher = args.firstOrNull()
            "data-ciphers" -> dataCiphers = args.joinToString(" ")
                .split(':')
                .map(String::trim)
                .filter(String::isNotBlank)
            "data-ciphers-fallback" -> dataCiphersFallback = args.firstOrNull()
            "auth" -> auth = args.firstOrNull()
            "remote-cert-tls" -> remoteCertificateTls = args.firstOrNull()
            "verify-x509-name" -> serverName = args.firstOrNull()
            "tls-version-min" -> tlsVersionMin = args.firstOrNull()
            "key-direction" -> keyDirection = args.firstOrNull()
            "tls-auth" -> {
                controlWrapType = "tls-auth"
                keyDirection = args.getOrNull(1) ?: keyDirection
                if (!args.firstOrNull().equals("[inline]", true)) {
                    warnings += "Внешний tls-auth файл не импортирован. Вставьте ключ вручную."
                }
            }
            "tls-crypt" -> {
                controlWrapType = "tls-crypt"
                if (!args.firstOrNull().equals("[inline]", true)) {
                    warnings += "Внешний tls-crypt файл не импортирован. Вставьте ключ вручную."
                }
            }
            "compress" -> compression = args.firstOrNull().orEmpty()
            "comp-lzo" -> compressionLzo = args.firstOrNull() ?: "adaptive"
            "route" -> parseOpenVpnIpv4Route(args)?.let(routes::add)
                ?: run { warnings += "Строка ${index + 1}: некорректный IPv4 route пропущен." }
            "route-ipv6" -> args.firstOrNull()
                ?.takeIf(::isOpenVpnIpv6Prefix)
                ?.let(routes::add)
                ?: run { warnings += "Строка ${index + 1}: некорректный IPv6 route пропущен." }
            "route-nopull", "route-no-pull" -> routeNoPull = true
            "redirect-gateway" -> {
                redirectGateway = true
                redirectGatewayFlags = args
            }
            "ca", "cert", "key" -> if (!args.firstOrNull().equals("[inline]", true)) {
                warnings += "Внешний файл $directive не импортирован. Используйте профиль со встроенным блоком <$directive>…</$directive>."
            }
            else -> warnings += "Неизвестная директива «$directive» (строка ${index + 1}) не перенесена."
        }
    }

    require(remotes.isNotEmpty()) { "В профиле нет строки remote с адресом VPN-сервера." }
    if (blocks.ca == null) {
        warnings += "Встроенный блок <ca> не найден. Проверьте цепочку доверия перед подключением."
    }
    if (compression != null || compressionLzo != null) {
        warnings += "Профиль включает сжатие OpenVPN. Оно устарело и может ослаблять защищённость канала."
    }

    val root = JSONObject()
        .put("type", "openvpn-client")
        .put("mode", "tls")
        .put("network", network)
    if (remotes.size == 1) {
        root.put("server", remotes.single().host)
            .put("server_port", remotes.single().port)
            .put("network", remotes.single().network)
    } else {
        root.put(
            "servers",
            JSONArray(remotes.map { remote ->
                JSONObject()
                    .put("server", remote.host)
                    .put("server_port", remote.port)
                    .put("network", remote.network)
            }),
        ).put("remote_random", true)
    }
    username?.let { root.put("username", it) }
    password?.let { root.put("password", it) }
    val negotiatedCiphers = dataCiphers.ifEmpty { listOfNotNull(legacyCipher) }
    if (negotiatedCiphers.isNotEmpty()) root.put("data_ciphers", JSONArray(negotiatedCiphers))
    (dataCiphersFallback ?: legacyCipher)?.let { root.put("data_ciphers_fallback", it) }
    auth?.let { root.put("auth", it) }
    compression?.let { root.put("compression", it) }
    compressionLzo?.let { root.put("compression_lzo", it) }
    if (routes.isNotEmpty()) root.put("routes", JSONArray(routes.distinct()))
    if (routeNoPull) root.put("route_no_pull", true)
    if (redirectGateway) {
        root.put("redirect_gateway", true)
        if (redirectGatewayFlags.isNotEmpty()) {
            root.put("redirect_gateway_flags", JSONArray(redirectGatewayFlags))
        }
    }

    val tls = JSONObject()
    blocks.ca?.let { tls.put("certificate", it) }
    blocks.cert?.let { tls.put("client_certificate", it) }
    blocks.key?.let { tls.put("client_key", it) }
    serverName?.let { tls.put("server_name", it) }
    remoteCertificateTls?.let { tls.put("remote_certificate_tls", it) }
    tlsVersionMin?.let { tls.put("version_min", it) }
    val controlKey = blocks.tlsCrypt ?: blocks.tlsAuth
    if (controlKey != null) {
        tls.put(
            "control_wrap",
            JSONObject()
                .put("type", controlWrapType ?: if (blocks.tlsCrypt != null) "tls-crypt" else "tls-auth")
                .put("key", controlKey)
                .apply { keyDirection?.let { put("direction", it) } },
        )
    }
    if (tls.length() > 0) root.put("tls", tls)

    return OpenVpnImportResult(
        optionsJson = root.toString(2),
        warnings = warnings.distinct(),
        routes = routes.distinct(),
    )
}

/**
 * One-time compatibility migration for profiles imported by beta.10.
 *
 * beta.10 preserved OpenVPN `route` directives in the sing-box endpoint, but
 * did not mirror them to ViRouteFS' shared router. An endpoint that is not a
 * default/rule target is not started, so those networks could never reach it.
 */
fun RoutingConfig.withMigratedOpenVpnEndpointRoutes(): RoutingConfig {
    var changed = false
    val migratedProfiles = profiles.map { profile ->
        if (profile.type != TunnelType.OpenVpn || profile.appRoutingNetworks.isNotEmpty()) {
            return@map profile
        }
        val routes = profile.singBox
            ?.optionsJson
            ?.let(::openVpnEndpointRoutes)
            .orEmpty()
        if (routes.isEmpty()) {
            profile
        } else {
            changed = true
            profile.copy(appRoutingNetworks = routes)
        }
    }
    return if (changed) copy(profiles = migratedProfiles) else this
}

internal fun openVpnEndpointRoutes(optionsJson: String): List<String> = runCatching {
    val root = JSONObject(optionsJson)
    if (root.optString("type") != "openvpn-client") return@runCatching emptyList()
    val routes = root.optJSONArray("routes") ?: return@runCatching emptyList()
    buildList {
        repeat(routes.length()) { index ->
            routes.optString(index)
                .trim()
                .takeIf(String::isNotBlank)
                ?.takeIf(::isValidIpOrCidr)
                ?.let(::add)
        }
    }.distinct()
}.getOrDefault(emptyList())

private data class OpenVpnRemote(
    val host: String,
    val port: Int,
    val network: String,
)

private data class OpenVpnInlineBlocks(
    val withoutBlocks: String,
    val ca: String?,
    val cert: String?,
    val key: String?,
    val tlsAuth: String?,
    val tlsCrypt: String?,
)

private fun extractInlineBlocks(source: String): OpenVpnInlineBlocks {
    val values = mutableMapOf<String, String>()
    val without = INLINE_BLOCK_PATTERN.replace(source) { match ->
        val name = match.groupValues[1].lowercase()
        values[name] = match.groupValues[2].trim()
        ""
    }
    return OpenVpnInlineBlocks(
        withoutBlocks = without,
        ca = values["ca"],
        cert = values["cert"],
        key = values["key"],
        tlsAuth = values["tls-auth"],
        tlsCrypt = values["tls-crypt"],
    )
}

private fun normalizeOpenVpnNetwork(value: String?, warnings: MutableList<String>): String {
    val normalized = value.orEmpty().lowercase()
    return when {
        normalized.startsWith("tcp") -> "tcp"
        normalized.startsWith("udp") || normalized.isBlank() -> "udp"
        else -> {
            warnings += "Неизвестный транспорт «$value» заменён на UDP."
            "udp"
        }
    }
}

private fun parseOpenVpnIpv4Route(args: List<String>): String? {
    val addressParts = args.getOrNull(0)?.split('.')?.map(String::toIntOrNull) ?: return null
    if (addressParts.size != 4 || addressParts.any { it == null || it !in 0..255 }) return null
    val maskParts = args.getOrNull(1)
        ?.takeUnless { it.equals("vpn_gateway", true) || it.equals("net_gateway", true) }
        ?.split('.')
        ?.map(String::toIntOrNull)
        ?: listOf(255, 255, 255, 255)
    if (maskParts.size != 4 || maskParts.any { it == null || it !in 0..255 }) return null
    val maskBits = maskParts.fold(0L) { value, part -> (value shl 8) or requireNotNull(part).toLong() }
    val inverted = maskBits.inv() and 0xffffffffL
    if ((inverted and (inverted + 1L)) != 0L) return null
    val prefix = java.lang.Long.bitCount(maskBits)
    val addressBits = addressParts.fold(0L) { value, part -> (value shl 8) or requireNotNull(part).toLong() }
    val networkBits = addressBits and maskBits
    val normalized = listOf(24, 16, 8, 0).joinToString(".") { shift ->
        ((networkBits shr shift) and 0xff).toString()
    }
    return "$normalized/$prefix"
}

private fun isOpenVpnIpv6Prefix(value: String): Boolean {
    val parts = value.split('/', limit = 2)
    if (parts.size != 2 || parts[1].toIntOrNull() !in 0..128) return false
    return runCatching { java.net.InetAddress.getByName(parts[0]) }
        .getOrNull() is java.net.Inet6Address
}

private fun tokenizeOpenVpnLine(line: String): List<String> =
    OPENVPN_TOKEN_PATTERN.findAll(line).map { match ->
        match.groups[1]?.value
            ?: match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: ""
    }.filter(String::isNotBlank).toList()

private val INLINE_BLOCK_PATTERN = Regex(
    """(?is)<(ca|cert|key|tls-auth|tls-crypt)>\s*(.*?)\s*</\1>""",
)

private val OPENVPN_TOKEN_PATTERN = Regex(
    """"([^"\\]*(?:\\.[^"\\]*)*)"|'([^'\\]*(?:\\.[^'\\]*)*)'|(\S+)""",
)

private const val MAX_OPENVPN_AUTH_USER_PASS_BYTES = 16 * 1024
private const val MAX_OPENVPN_CREDENTIAL_CHARS = 1024
