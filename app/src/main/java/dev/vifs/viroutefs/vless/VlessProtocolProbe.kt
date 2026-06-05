// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.system.measureTimeMillis

const val VLESS_PROTOCOL_PROBE_NOTICE = "This sends a minimal VLESS request frame for diagnostics only. Runtime forwarding is not enabled."
const val VLESS_TLS_REALITY_UNSUPPORTED_MESSAGE = "TLS/REALITY transport is not implemented yet."
private const val DEFAULT_PROBE_CONNECT_TIMEOUT_MS = 5_000
private const val DEFAULT_PROBE_WAIT_TIMEOUT_MS = 750

sealed interface VlessProtocolProbeState {
    val label: String

    data object TcpConnected : VlessProtocolProbeState { override val label = "TCP connected" }
    data object VlessRequestSent : VlessProtocolProbeState { override val label = "VLESS request sent" }
    data object ServerKeptConnectionBriefly : VlessProtocolProbeState { override val label = "Server kept connection briefly" }
    data object ServerClosedConnection : VlessProtocolProbeState { override val label = "Server closed connection" }
    data object Timeout : VlessProtocolProbeState { override val label = "Timeout" }
    data object Refused : VlessProtocolProbeState { override val label = "Refused" }
    data object HostDnsError : VlessProtocolProbeState { override val label = "Host/DNS error" }
    data object ValidationError : VlessProtocolProbeState { override val label = "Validation error" }
    data object UnsupportedSecurityMode : VlessProtocolProbeState { override val label = "Unsupported security mode" }
}

data class VlessProtocolProbeResult(
    val serverHost: String,
    val serverPort: Int,
    val targetHost: String,
    val targetPort: Int,
    val timestamp: Long,
    val state: VlessProtocolProbeState,
    val message: String,
    val elapsedMs: Long? = null,
    val steps: List<VlessProtocolProbeState> = emptyList(),
) {
    val displayMessage: String
        get() = buildString {
            append(state.label)
            if (message.isNotBlank()) append(": ${message.sanitizeVlessReachabilityMessage()}")
            elapsedMs?.let { append(" (${it} ms)") }
        }
}

class VlessProtocolProber(
    private val connectTimeoutMs: Int = DEFAULT_PROBE_CONNECT_TIMEOUT_MS,
    private val waitTimeoutMs: Int = DEFAULT_PROBE_WAIT_TIMEOUT_MS,
    private val socketFactory: () -> Socket = { Socket() },
    private val requestBuilder: (String, String, Int) -> ByteArray = ::buildVlessTcpRequest,
) {
    suspend fun probe(
        profile: VlessProfileConfig,
        targetHost: String,
        targetPort: Int,
    ): VlessProtocolProbeResult = withContext(Dispatchers.IO) {
        probeBlocking(profile, targetHost, targetPort)
    }

    fun probeBlocking(
        profile: VlessProfileConfig,
        targetHost: String,
        targetPort: Int,
    ): VlessProtocolProbeResult {
        val now = System.currentTimeMillis()
        val cleanServerHost = profile.host.trim()
        val cleanTargetHost = targetHost.trim()
        if (profile.securityMode != VlessSecurityMode.NONE) {
            return VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = now,
                state = VlessProtocolProbeState.UnsupportedSecurityMode,
                message = VLESS_TLS_REALITY_UNSUPPORTED_MESSAGE,
            )
        }

        val validationErrors = validateVlessProfile(profile) + validateVlessProtocolProbeTarget(cleanTargetHost, targetPort)
        if (validationErrors.isNotEmpty()) {
            return VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = now,
                state = VlessProtocolProbeState.ValidationError,
                message = validationErrors.joinToString(" ").sanitizeVlessReachabilityMessage(),
            )
        }

        val steps = mutableListOf<VlessProtocolProbeState>()
        var elapsedMs: Long? = null
        val outcome = runCatching {
            var finalState: VlessProtocolProbeState = VlessProtocolProbeState.Timeout
            var finalMessage = "Timed out while waiting for the server response."
            elapsedMs = measureTimeMillis {
                socketFactory().use { socket ->
                    socket.connect(InetSocketAddress(cleanServerHost, profile.port), connectTimeoutMs)
                    steps += VlessProtocolProbeState.TcpConnected
                    val frame = requestBuilder(profile.uuid, cleanTargetHost, targetPort)
                    socket.getOutputStream().write(frame)
                    socket.getOutputStream().flush()
                    steps += VlessProtocolProbeState.VlessRequestSent
                    socket.soTimeout = waitTimeoutMs
                    val read = socket.getInputStream().read()
                    if (read == -1) {
                        finalState = VlessProtocolProbeState.ServerClosedConnection
                        finalMessage = "Server closed the connection after receiving the minimal VLESS request frame."
                    } else {
                        finalState = VlessProtocolProbeState.ServerKeptConnectionBriefly
                        finalMessage = "Server kept the connection open briefly after receiving the minimal VLESS request frame."
                    }
                }
            }
            VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = System.currentTimeMillis(),
                state = finalState,
                message = finalMessage,
                elapsedMs = elapsedMs,
                steps = steps + finalState,
            )
        }
        return outcome.getOrElse { error ->
            val state = when (error) {
                is SocketTimeoutException -> if (steps.contains(VlessProtocolProbeState.VlessRequestSent)) {
                    VlessProtocolProbeState.ServerKeptConnectionBriefly
                } else {
                    VlessProtocolProbeState.Timeout
                }
                is ConnectException -> VlessProtocolProbeState.Refused
                is UnknownHostException -> VlessProtocolProbeState.HostDnsError
                is SecurityException, is IllegalArgumentException -> VlessProtocolProbeState.ValidationError
                is IOException -> if (steps.contains(VlessProtocolProbeState.VlessRequestSent)) {
                    VlessProtocolProbeState.ServerClosedConnection
                } else {
                    VlessProtocolProbeState.HostDnsError
                }
                else -> VlessProtocolProbeState.HostDnsError
            }
            val message = when (state) {
                VlessProtocolProbeState.ServerKeptConnectionBriefly -> "Server kept the connection open for the brief probe wait window."
                else -> error.message ?: state.label
            }
            VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = System.currentTimeMillis(),
                state = state,
                message = message.sanitizeVlessReachabilityMessage(),
                elapsedMs = elapsedMs,
                steps = if (steps.isEmpty()) listOf(state) else steps + state,
            )
        }
    }
}

fun validateVlessProtocolProbeTarget(host: String, port: Int): List<String> = buildList {
    addAll(validateVlessTcpReachabilityTarget(host, port).map { it.replace("VLESS host", "VLESS probe target host").replace("VLESS port", "VLESS probe target port") })
    if (host.trim().contains('/')) add("VLESS probe target host must be a host name or IP address, not a path or URL.")
    if (host.trim().contains(':')) add("VLESS probe target IPv6 literals are not implemented yet.")
}

fun VlessProtocolProbeResult.toProfileStatus(): VlessProfileStatus = when (state) {
    VlessProtocolProbeState.ServerKeptConnectionBriefly,
    VlessProtocolProbeState.ServerClosedConnection,
    VlessProtocolProbeState.VlessRequestSent,
    VlessProtocolProbeState.TcpConnected -> VlessProfileStatus.TcpReachable
    else -> VlessProfileStatus.LastTestFailed
}
