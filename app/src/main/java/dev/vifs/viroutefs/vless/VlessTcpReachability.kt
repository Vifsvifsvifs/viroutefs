// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.system.measureTimeMillis

const val VLESS_TCP_REACHABILITY_NOTICE = "Manual TCP reachability only: no VLESS handshake, no UUID sent, no TLS/REALITY, and no runtime forwarding."
private const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000

sealed interface VlessTcpReachabilityState {
    val label: String

    data object Reachable : VlessTcpReachabilityState {
        override val label: String = "reachable"
    }

    data object Timeout : VlessTcpReachabilityState {
        override val label: String = "timeout"
    }

    data object Refused : VlessTcpReachabilityState {
        override val label: String = "refused"
    }

    data object DnsOrHostError : VlessTcpReachabilityState {
        override val label: String = "DNS/host error"
    }

    data object ValidationError : VlessTcpReachabilityState {
        override val label: String = "validation error"
    }
}

data class VlessTcpReachabilityResult(
    val host: String,
    val port: Int,
    val timestamp: Long,
    val state: VlessTcpReachabilityState,
    val message: String,
    val elapsedMs: Long? = null,
) {
    val displayMessage: String
        get() = buildString {
            append(state.label)
            if (message.isNotBlank()) append(": ${message.sanitizeVlessReachabilityMessage()}")
            elapsedMs?.let { append(" (${it} ms)") }
        }
}

class VlessTcpReachabilityTester(
    private val timeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
) {
    suspend fun test(host: String, port: Int): VlessTcpReachabilityResult = withContext(Dispatchers.IO) {
        val validationErrors = validateVlessTcpReachabilityTarget(host, port)
        if (validationErrors.isNotEmpty()) {
            return@withContext VlessTcpReachabilityResult(
                host = host.trim(),
                port = port,
                timestamp = System.currentTimeMillis(),
                state = VlessTcpReachabilityState.ValidationError,
                message = validationErrors.joinToString(" "),
            )
        }

        var elapsedMs: Long? = null
        val result = runCatching {
            elapsedMs = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host.trim(), port), timeoutMs)
                    // TCP reachability only. Intentionally close immediately without writing bytes.
                }
            }
        }
        val now = System.currentTimeMillis()
        result.fold(
            onSuccess = {
                VlessTcpReachabilityResult(
                    host = host.trim(),
                    port = port,
                    timestamp = now,
                    state = VlessTcpReachabilityState.Reachable,
                    message = "TCP socket connected and closed immediately; no bytes, UUID, TLS, REALITY, or VLESS handshake were sent.",
                    elapsedMs = elapsedMs,
                )
            },
            onFailure = { error ->
                val state = when (error) {
                    is SocketTimeoutException -> VlessTcpReachabilityState.Timeout
                    is ConnectException -> VlessTcpReachabilityState.Refused
                    is UnknownHostException -> VlessTcpReachabilityState.DnsOrHostError
                    is SecurityException, is IllegalArgumentException -> VlessTcpReachabilityState.ValidationError
                    else -> VlessTcpReachabilityState.DnsOrHostError
                }
                VlessTcpReachabilityResult(
                    host = host.trim(),
                    port = port,
                    timestamp = now,
                    state = state,
                    message = error.message ?: state.label,
                    elapsedMs = elapsedMs,
                )
            },
        )
    }
}

fun validateVlessTcpReachabilityTarget(host: String, port: Int): List<String> = buildList {
    val trimmedHost = host.trim()
    if (trimmedHost.isBlank()) add("VLESS host must not be blank.")
    if (trimmedHost.any { it.isISOControl() || it.isWhitespace() }) add("VLESS host must not contain whitespace or control characters.")
    if (trimmedHost.length > 253) add("VLESS host must be 253 characters or shorter.")
    if (trimmedHost.startsWith('.') || trimmedHost.endsWith('.')) add("VLESS host must not start or end with a dot.")
    if (port !in 1..65535) add("VLESS port must be in range 1..65535.")
}

fun VlessTcpReachabilityResult.toProfileStatus(): VlessProfileStatus = when (state) {
    VlessTcpReachabilityState.Reachable -> VlessProfileStatus.TcpReachable
    else -> VlessProfileStatus.LastTestFailed
}

fun String.sanitizeVlessReachabilityMessage(): String =
    replace(Regex("(?i)(uuid|password|pass|pwd|secret|token)=\\S+"), "$1=***")
        .replace(Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"), "[uuid redacted]")
