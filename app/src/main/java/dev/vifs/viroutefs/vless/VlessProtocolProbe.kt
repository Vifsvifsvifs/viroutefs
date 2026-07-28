// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIServerName
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.system.measureTimeMillis

const val VLESS_PROTOCOL_PROBE_NOTICE = "This manual probe sends one minimal VLESS request over plain TCP or TLS and reads response metadata only. Saved profiles are routed separately by the VPN runtime."
const val VLESS_RESPONSE_PROBE_METADATA_NOTICE = "Response probe reads metadata only. Payload bytes are not shown or stored."
const val VLESS_REALITY_UNSUPPORTED_MESSAGE = "Security mode not supported"
private const val DEFAULT_PROBE_CONNECT_TIMEOUT_MS = 5_000
private const val DEFAULT_PROBE_WAIT_TIMEOUT_MS = 750
private const val RESPONSE_PROBE_BUFFER_BYTES = 256

sealed interface VlessProtocolProbeState {
    val label: String

    data object TcpConnected : VlessProtocolProbeState { override val label = "TCP connected" }
    data object TlsHandshakeSuccess : VlessProtocolProbeState { override val label = "TLS handshake success" }
    data object TlsHandshakeFailed : VlessProtocolProbeState { override val label = "TLS handshake failed" }
    data object VlessRequestSent : VlessProtocolProbeState { override val label = "VLESS request sent" }
    data object RequestSentNoImmediateResponse : VlessProtocolProbeState { override val label = "Request sent, no immediate response" }
    data object ResponseReceived : VlessProtocolProbeState { override val label = "Response received" }
    data object ServerKeptConnectionBriefly : VlessProtocolProbeState { override val label = "Server kept connection briefly" }
    data object ServerClosedConnection : VlessProtocolProbeState { override val label = "Server closed connection" }
    data object InvalidEmptyResponse : VlessProtocolProbeState { override val label = "Invalid/empty response" }
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
    val securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
    val responseBytes: Int = 0,
    val classification: VlessResponseClassification = state.toVlessResponseClassification(),
) {
    val responseMetadata: VlessResponseMetadata
        get() = VlessResponseMetadata(
            classification = classification,
            byteCount = responseBytes,
            elapsedMs = elapsedMs,
            securityMode = securityMode,
        )

    val displayMessage: String
        get() = buildString {
            val summary = responseMetadata.safeMessage
            val detail = message.sanitizeVlessReachabilityMessage()
            append(summary)
            if (detail.isNotBlank() && detail != summary) append(" • $detail")
            if (responseBytes > 0) append(" • response bytes: $responseBytes")
            elapsedMs?.let { append(" (${it} ms)") }
        }
}

class VlessProtocolProber(
    private val connectTimeoutMs: Int = DEFAULT_PROBE_CONNECT_TIMEOUT_MS,
    private val waitTimeoutMs: Int = DEFAULT_PROBE_WAIT_TIMEOUT_MS,
    private val socketFactory: () -> Socket = { Socket() },
    private val tlsSocketFactory: (Socket, String, Int, Boolean) -> SSLSocket = { socket, host, port, autoClose ->
        (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, host, port, autoClose) as SSLSocket
    },
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
        val securityMode = profile.securityMode
        if (securityMode == VlessSecurityMode.REALITY || !profile.transportType.isVlessManualProbeTransportSupported()) {
            val metadata = VlessResponseParser.unsupportedTransport(securityMode)
            return VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = now,
                state = metadata.classification.toProbeState(),
                message = metadata.safeMessage,
                securityMode = securityMode,
                classification = metadata.classification,
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
                securityMode = securityMode,
                classification = VlessResponseClassification.ValidationError,
            )
        }

        val steps = mutableListOf<VlessProtocolProbeState>()
        var elapsedMs: Long? = null
        val outcome = runCatching {
            var finalMetadata = VlessResponseMetadata(
                classification = VlessResponseClassification.Timeout,
                securityMode = securityMode,
            )
            elapsedMs = measureTimeMillis {
                socketFactory().use { socket ->
                    socket.connect(InetSocketAddress(cleanServerHost, profile.port), connectTimeoutMs)
                    steps += VlessProtocolProbeState.TcpConnected
                    val probeSocket = when (securityMode) {
                        VlessSecurityMode.NONE -> socket
                        VlessSecurityMode.TLS -> wrapTlsSocket(socket, profile, steps)
                        VlessSecurityMode.REALITY -> error(VLESS_REALITY_UNSUPPORTED_MESSAGE)
                    }
                    try {
                        val frame = requestBuilder(profile.uuid, cleanTargetHost, targetPort)
                        probeSocket.getOutputStream().write(frame)
                        probeSocket.getOutputStream().flush()
                        steps += VlessProtocolProbeState.VlessRequestSent
                        probeSocket.soTimeout = waitTimeoutMs
                        val responseBuffer = ByteArray(RESPONSE_PROBE_BUFFER_BYTES)
                        val responseByteCount = probeSocket.getInputStream().read(responseBuffer)
                        finalMetadata = VlessResponseParser.fromReadResult(
                            readByteCount = responseByteCount,
                            elapsedMs = null,
                            securityMode = securityMode,
                        )
                    } finally {
                        if (probeSocket !== socket) probeSocket.close()
                    }
                }
            }
            VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = System.currentTimeMillis(),
                state = finalMetadata.classification.toProbeState(),
                message = finalMetadata.safeMessage,
                elapsedMs = elapsedMs,
                steps = steps + finalMetadata.classification.toProbeState(),
                securityMode = securityMode,
                responseBytes = finalMetadata.byteCount,
                classification = finalMetadata.classification,
            )
        }
        return outcome.getOrElse { error ->
            val metadata = when (error) {
                is ConnectException, is UnknownHostException -> VlessResponseMetadata(
                    classification = VlessResponseClassification.InvalidResponse,
                    elapsedMs = elapsedMs,
                    securityMode = securityMode,
                )
                else -> VlessResponseParser.fromFailure(
                    error = error,
                    elapsedMs = elapsedMs,
                    securityMode = securityMode,
                    requestSent = steps.contains(VlessProtocolProbeState.VlessRequestSent),
                )
            }
            val state = when (error) {
                is ConnectException -> VlessProtocolProbeState.Refused
                is UnknownHostException -> VlessProtocolProbeState.HostDnsError
                is SocketTimeoutException -> if (steps.contains(VlessProtocolProbeState.VlessRequestSent)) {
                    metadata.classification.toProbeState()
                } else {
                    VlessProtocolProbeState.Timeout
                }
                else -> metadata.classification.toProbeState()
            }
            VlessProtocolProbeResult(
                serverHost = cleanServerHost,
                serverPort = profile.port,
                targetHost = cleanTargetHost,
                targetPort = targetPort,
                timestamp = System.currentTimeMillis(),
                state = state,
                message = metadata.safeMessage.sanitizeVlessReachabilityMessage(),
                elapsedMs = elapsedMs,
                steps = if (steps.isEmpty()) listOf(state) else steps + state,
                securityMode = securityMode,
                responseBytes = metadata.byteCount,
                classification = metadata.classification,
            )
        }
    }

    private fun wrapTlsSocket(socket: Socket, profile: VlessProfileConfig, steps: MutableList<VlessProtocolProbeState>): SSLSocket {
        val sniHost = profile.vlessProbeSniHost()
        return tlsSocketFactory(socket, sniHost, profile.port, true).also { tlsSocket ->
            tlsSocket.useClientMode = true
            tlsSocket.sslParameters = tlsSocket.sslParameters.withServerName(sniHost)
            tlsSocket.startHandshake()
            steps += VlessProtocolProbeState.TlsHandshakeSuccess
        }
    }
}

fun VlessProfileConfig.vlessProbeSniHost(): String = sni?.trim()?.takeIf { it.isNotBlank() } ?: host.trim()

fun String?.isVlessManualProbeTransportSupported(): Boolean = this?.trim().isNullOrBlank() || this?.trim().equals("tcp", ignoreCase = true)

private fun SSLParameters.withServerName(host: String): SSLParameters = apply {
    serverNames = listOf<SNIServerName>(SNIHostName(host))
}


fun validateVlessProtocolProbeTarget(host: String, port: Int): List<String> = buildList {
    addAll(validateVlessTcpReachabilityTarget(host, port).map { it.replace("VLESS host", "VLESS probe target host").replace("VLESS port", "VLESS probe target port") })
    if (host.trim().contains('/')) add("VLESS probe target host must be a host name or IP address, not a path or URL.")
    if (host.trim().contains(':')) add("VLESS probe target IPv6 literals are not implemented yet.")
}

fun VlessProtocolProbeResult.toProfileStatus(): VlessProfileStatus = when (state) {
    VlessProtocolProbeState.ResponseReceived,
    VlessProtocolProbeState.RequestSentNoImmediateResponse,
    VlessProtocolProbeState.ServerKeptConnectionBriefly,
    VlessProtocolProbeState.ServerClosedConnection,
    VlessProtocolProbeState.VlessRequestSent,
    VlessProtocolProbeState.TcpConnected -> VlessProfileStatus.TcpReachable
    else -> VlessProfileStatus.LastTestFailed
}
