package dev.vifs.viroutefs.diagnostics

import java.net.URI

internal enum class DnsTransport {
    SYSTEM,
    UDP,
    TCP,
    TLS,
    HTTPS,
}

internal data class DnsEndpoint(
    val transport: DnsTransport,
    val host: String? = null,
    val port: Int? = null,
    val httpsUrl: String? = null,
) {
    val displayName: String
        get() = when (transport) {
            DnsTransport.SYSTEM -> "системный DNS Android"
            DnsTransport.HTTPS -> httpsUrl.orEmpty()
            else -> "${transport.name}://${hostForDisplay()}:$port"
        }

    val bootstrapNote: String?
        get() = host?.takeUnless(String::isNumericAddress)?.let {
            "Имя DNS-сервера $it было найдено через системный DNS Android (bootstrap)."
        }

    fun asTcpFallback(): DnsEndpoint = copy(transport = DnsTransport.TCP)

    private fun hostForDisplay(): String = host.orEmpty().let { value ->
        if (value.contains(':') && !value.startsWith('[')) "[$value]" else value
    }

    companion object {
        fun parse(rawValue: String): DnsEndpoint {
            val value = rawValue.trim()
            if (value.isBlank() || value.lowercase() in SYSTEM_ALIASES) {
                return DnsEndpoint(DnsTransport.SYSTEM)
            }

            val scheme = value.substringBefore("://", missingDelimiterValue = "").lowercase()
            if (scheme in setOf("quic", "h3")) {
                throw IllegalArgumentException(
                    "DNS-over-QUIC/H3 поддерживается маршрутизатором, но пока не поддерживается этой ручной проверкой.",
                )
            }
            if (scheme.isNotBlank() && scheme !in SUPPORTED_SCHEMES) {
                throw IllegalArgumentException("Неизвестная схема DNS-сервера: $scheme://")
            }

            return if (scheme == "https") parseHttps(value) else parseSocketEndpoint(value, scheme)
        }

        private fun parseHttps(value: String): DnsEndpoint {
            val uri = runCatching { URI(value) }.getOrElse {
                throw IllegalArgumentException("Некорректный адрес DNS-over-HTTPS.")
            }
            if (uri.host.isNullOrBlank() || uri.userInfo != null || uri.fragment != null) {
                throw IllegalArgumentException("Для DoH нужен адрес вида https://dns.example/dns-query без логина и фрагмента.")
            }
            val port = uri.port.takeIf { it >= 0 } ?: 443
            requireValidPort(port)
            val normalized = if (uri.rawPath.isNullOrBlank() || uri.rawPath == "/") {
                URI("https", null, uri.host, uri.port, "/dns-query", uri.rawQuery, null).toString()
            } else {
                uri.toString()
            }
            return DnsEndpoint(DnsTransport.HTTPS, uri.host, port, normalized)
        }

        private fun parseSocketEndpoint(value: String, explicitScheme: String): DnsEndpoint {
            val transport = when (explicitScheme) {
                "tcp" -> DnsTransport.TCP
                "tls" -> DnsTransport.TLS
                else -> DnsTransport.UDP
            }
            val defaultPort = if (transport == DnsTransport.TLS) 853 else 53
            val authority = if (explicitScheme.isBlank()) value else value.substringAfter("://")
            if (authority.contains('/') || authority.contains('?') || authority.contains('#') || authority.contains('@')) {
                throw IllegalArgumentException("Для UDP/TCP/TLS укажите только имя или IP DNS-сервера и необязательный порт.")
            }

            val (host, port) = parseAuthority(authority, defaultPort)
            if (host.isBlank() || host.any(Char::isWhitespace)) {
                throw IllegalArgumentException("Не указан адрес DNS-сервера.")
            }
            requireValidPort(port)
            return DnsEndpoint(transport, host, port)
        }

        private fun parseAuthority(authority: String, defaultPort: Int): Pair<String, Int> {
            if (authority.startsWith('[')) {
                val closing = authority.indexOf(']')
                if (closing <= 1) throw IllegalArgumentException("Некорректный IPv6-адрес DNS-сервера.")
                val host = authority.substring(1, closing)
                val suffix = authority.substring(closing + 1)
                val port = when {
                    suffix.isBlank() -> defaultPort
                    suffix.startsWith(':') -> suffix.substring(1).toIntOrNull()
                    else -> null
                } ?: throw IllegalArgumentException("Некорректный порт DNS-сервера.")
                return host to port
            }

            val colonCount = authority.count { it == ':' }
            if (colonCount == 1) {
                val possiblePort = authority.substringAfterLast(':')
                if (possiblePort.isBlank() || !possiblePort.all(Char::isDigit)) {
                    throw IllegalArgumentException("Некорректный порт DNS-сервера.")
                }
                return authority.substringBeforeLast(':') to
                    (possiblePort.toIntOrNull() ?: throw IllegalArgumentException("Некорректный порт DNS-сервера."))
            }
            return authority to defaultPort
        }

        private fun requireValidPort(port: Int) {
            if (port !in 1..65_535) throw IllegalArgumentException("Порт DNS-сервера должен быть от 1 до 65535.")
        }

        private val SYSTEM_ALIASES = setOf(
            "system",
            "android",
            "system://",
            "системный dns android",
        )
        private val SUPPORTED_SCHEMES = setOf("udp", "tcp", "tls", "https")
    }
}

private fun String.isNumericAddress(): Boolean {
    if (contains(':')) return all { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' || it == '%' }
    val parts = split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull()?.let { it in 0..255 } == true
    }
}
