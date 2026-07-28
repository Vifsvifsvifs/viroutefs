// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import org.json.JSONArray
import org.json.JSONObject

data class OpenVpnImportResult(
    val optionsJson: String,
    val warnings: List<String>,
)

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
    var cipher: String? = null
    var dataCiphers: List<String> = emptyList()
    var auth: String? = null
    var serverName: String? = null
    var remoteCertificateTls: String? = null
    var tlsVersionMin: String? = null
    var compression: String? = null
    var compressionLzo: String? = null
    var controlWrapType: String? = null
    var keyDirection: String? = null

    blocks.withoutBlocks.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank() || line.startsWith("#") || line.startsWith(";")) return@forEachIndexed
        val tokens = tokenizeOpenVpnLine(line)
        if (tokens.isEmpty()) return@forEachIndexed
        val directive = tokens.first().lowercase()
        val args = tokens.drop(1)
        when (directive) {
            "client", "nobind", "persist-key", "persist-tun", "remote-random",
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
            "cipher" -> cipher = args.firstOrNull()
            "data-ciphers" -> dataCiphers = args.joinToString(" ")
                .split(':')
                .map(String::trim)
                .filter(String::isNotBlank)
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
    cipher?.let { root.put("cipher", it) }
    if (dataCiphers.isNotEmpty()) root.put("data_ciphers", JSONArray(dataCiphers))
    auth?.let { root.put("auth", it) }
    compression?.let { root.put("compression", it) }
    compressionLzo?.let { root.put("compression_lzo", it) }

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
    )
}

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
