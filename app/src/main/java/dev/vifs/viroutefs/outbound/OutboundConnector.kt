// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.outbound

internal data class OutboundTarget(
    val host: String,
    val port: Int,
) {
    fun validationErrors(): List<String> = validate(host, port)

    companion object {
        fun validate(host: String, port: Int): List<String> = buildList {
            val trimmedHost = host.trim()
            if (trimmedHost.isBlank()) add("Target host must not be blank.")
            if (trimmedHost.any { it.isISOControl() || it.isWhitespace() }) add("Target host must not contain whitespace or control characters.")
            if (trimmedHost.encodeToByteArray().size > 255) add("Target host must be 255 bytes or shorter for SOCKS5 domain-name CONNECT.")
            if (port !in 1..65535) add("Target port must be in range 1..65535.")
        }
    }
}

internal data class OutboundConnectRequest(
    val profileId: String,
    val target: OutboundTarget,
    val timeoutMs: Int,
) {
    fun validationErrors(): List<String> = buildList {
        if (profileId.isBlank()) add("Profile id must not be blank.")
        addAll(target.validationErrors())
        if (timeoutMs <= 0) add("Timeout must be greater than zero milliseconds.")
    }
}

internal sealed interface OutboundConnectResult {
    val message: String

    data class Success(override val message: String = "Proxy opened the TCP CONNECT target; no application payload was sent.") : OutboundConnectResult
    data class AuthenticationRejected(override val message: String = "SOCKS5 username/password authentication was rejected.") : OutboundConnectResult
    data class UnsupportedAuthMethod(override val message: String = "The SOCKS5 proxy did not accept the configured authentication method.") : OutboundConnectResult
    data class ConnectRejectedByProxy(override val message: String = "The SOCKS5 proxy rejected the CONNECT request.") : OutboundConnectResult
    data class TargetUnreachable(override val message: String = "The SOCKS5 proxy could not reach the requested target.") : OutboundConnectResult
    data class Timeout(override val message: String = "SOCKS5 CONNECT timed out.") : OutboundConnectResult
    data class InvalidResponse(override val message: String = "The SOCKS5 proxy returned an invalid response.") : OutboundConnectResult
    data class ValidationError(override val message: String) : OutboundConnectResult
}

internal interface OutboundConnector {
    suspend fun connect(request: OutboundConnectRequest): OutboundConnectResult
}
