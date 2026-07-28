// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.isVlessManualProbeTransportSupported
import dev.vifs.viroutefs.vless.validateVlessProfile
import dev.vifs.viroutefs.vless.validateVlessProtocolProbeTarget
import dev.vifs.viroutefs.vless.vlessProbeSniHost
import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SNIServerName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal const val DEV_TCP_BRIDGE_NOTICE = "Dev TCP session only. No Android traffic is forwarded."
internal const val DEV_TCP_BRIDGE_SECRET_NOTICE =
    "The VLESS UUID is used in the protocol handshake. It is never shown in session events or diagnostic logs."
internal const val DEV_TCP_BRIDGE_EVENT_OPEN = "Dev session open"
internal const val DEV_TCP_BRIDGE_NO_FORWARDING = "DevTcpBridge accepts only explicit test bytes; Android traffic, UDP, and DNS forwarding are not implemented."

internal interface DevTcpBridge {
    fun openDevSession(profileId: String, targetHost: String, targetPort: Int): TcpSessionId

    fun closeDevSession(sessionId: TcpSessionId)

    fun sendTestData(sessionId: TcpSessionId, byteArray: ByteArray): Int

    fun receiveTestData(sessionId: TcpSessionId): ByteArray

    fun snapshot(): DevTcpBridgeSnapshot
}

internal data class DevTcpBridgeSnapshot(
    val sessionId: TcpSessionId? = null,
    val profileId: String? = null,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val state: TcpSessionState = TcpSessionState.Closed,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val lastEvent: String? = null,
) {
    val hasOpenSession: Boolean = sessionId != null && state != TcpSessionState.Closed && state != TcpSessionState.Failed

    fun routePreviewEventLine(): String? = if (hasOpenSession) DEV_TCP_BRIDGE_EVENT_OPEN else null
}

internal class VlessDevTcpBridge(
    private val sessionManager: TcpSessionManager = TcpSessionManager(),
    private val profiles: () -> Map<String, VlessProfileConfig>,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    private val socketFactory: () -> Socket = { Socket() },
    private val tlsSocketFactory: (Socket, String, Int, Boolean) -> SSLSocket = { socket, host, port, autoClose ->
        (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(socket, host, port, autoClose) as SSLSocket
    },
    private val requestBuilder: (String, String, Int) -> ByteArray = ::buildVlessTcpRequest,
) : DevTcpBridge {
    private var active: ActiveDevSession? = null
    private var lastSnapshot: DevTcpBridgeSnapshot = DevTcpBridgeSnapshot(lastEvent = DEV_TCP_BRIDGE_NO_FORWARDING)

    @Synchronized
    override fun openDevSession(profileId: String, targetHost: String, targetPort: Int): TcpSessionId {
        require(active == null) { "Only one dev TCP session may be active." }
        val profile = profiles()[profileId] ?: throw IllegalArgumentException("VLESS profile not found.")
        validateProfileForDevSession(profile, targetHost, targetPort)

        val session = sessionManager.createSession(
            sourceIp = DEV_SOURCE_HOST,
            sourcePort = DEV_SOURCE_PORT,
            destinationIp = targetHost.trim(),
            destinationPort = targetPort,
        )
        lastSnapshot = session.toSnapshot(profileId, targetHost, targetPort, TcpSessionState.New)
        sessionManager.updateState(session.id, TcpSessionState.Connecting)
        lastSnapshot = lastSnapshot.copy(state = TcpSessionState.Connecting)

        val rawSocket = socketFactory()
        try {
            rawSocket.connect(InetSocketAddress(profile.host.trim(), profile.port), connectTimeoutMs)
            val streamSocket = when (profile.securityMode) {
                VlessSecurityMode.NONE -> rawSocket
                VlessSecurityMode.TLS -> rawSocket.wrapTls(profile)
                VlessSecurityMode.REALITY -> error("REALITY is not supported by DevTcpBridge yet.")
            }
            streamSocket.soTimeout = readTimeoutMs
            val request = requestBuilder(profile.uuid, targetHost.trim(), targetPort)
            streamSocket.getOutputStream().write(request)
            streamSocket.getOutputStream().flush()
            sessionManager.updateState(session.id, TcpSessionState.Connected)
            active = ActiveDevSession(session.id, profileId, targetHost.trim(), targetPort, rawSocket, streamSocket)
            lastSnapshot = snapshotFor(session.id, DEV_TCP_BRIDGE_EVENT_OPEN)
            return session.id
        } catch (error: Throwable) {
            runCatching { rawSocket.close() }
            sessionManager.updateState(session.id, TcpSessionState.Failed)
            lastSnapshot = snapshotFor(session.id, "Dev session failed: ${error.safeMessage()}")
            throw error
        }
    }

    @Synchronized
    override fun closeDevSession(sessionId: TcpSessionId) {
        val current = active ?: return
        require(current.sessionId == sessionId) { "Unknown dev TCP session." }
        sessionManager.updateState(sessionId, TcpSessionState.Closing)
        lastSnapshot = snapshotFor(sessionId, "Dev session closing")
        runCatching { if (current.streamSocket !== current.rawSocket) current.streamSocket.close() }
        runCatching { current.rawSocket.close() }
        sessionManager.closeSession(sessionId)
        active = null
        lastSnapshot = snapshotFor(sessionId, "Dev session closed")
    }

    @Synchronized
    override fun sendTestData(sessionId: TcpSessionId, byteArray: ByteArray): Int {
        val current = requireActive(sessionId)
        current.streamSocket.getOutputStream().write(byteArray)
        current.streamSocket.getOutputStream().flush()
        sessionManager.updateCounters(sessionId, bytesOutDelta = byteArray.size.toLong())
        lastSnapshot = snapshotFor(sessionId, "Dev test data sent")
        return byteArray.size
    }

    @Synchronized
    override fun receiveTestData(sessionId: TcpSessionId): ByteArray {
        val current = requireActive(sessionId)
        val buffer = ByteArray(RECEIVE_BUFFER_BYTES)
        val read = current.streamSocket.getInputStream().read(buffer)
        if (read <= 0) return ByteArray(0)
        sessionManager.updateCounters(sessionId, bytesInDelta = read.toLong())
        lastSnapshot = snapshotFor(sessionId, "Dev test data received")
        return buffer.copyOf(read)
    }

    @Synchronized
    override fun snapshot(): DevTcpBridgeSnapshot = lastSnapshot

    private fun requireActive(sessionId: TcpSessionId): ActiveDevSession {
        val current = active ?: throw IllegalStateException("No active dev TCP session.")
        require(current.sessionId == sessionId) { "Unknown dev TCP session." }
        return current
    }

    private fun Socket.wrapTls(profile: VlessProfileConfig): SSLSocket {
        val sniHost = profile.vlessProbeSniHost()
        return tlsSocketFactory(this, sniHost, profile.port, true).also { tlsSocket ->
            tlsSocket.useClientMode = true
            tlsSocket.sslParameters = tlsSocket.sslParameters.withServerName(sniHost)
            tlsSocket.startHandshake()
        }
    }

    private fun validateProfileForDevSession(profile: VlessProfileConfig, targetHost: String, targetPort: Int) {
        val errors = validateVlessProfile(profile) + validateVlessProtocolProbeTarget(targetHost.trim(), targetPort)
        require(errors.isEmpty()) { errors.joinToString(" ").sanitizeSecrets() }
        require(profile.securityMode != VlessSecurityMode.REALITY) { "REALITY is not supported by DevTcpBridge yet." }
        require(profile.transportType.isVlessManualProbeTransportSupported()) { "Only VLESS TCP transport is supported by DevTcpBridge." }
    }

    private fun TcpSessionMetadata.toSnapshot(
        profileId: String,
        targetHost: String,
        targetPort: Int,
        state: TcpSessionState,
    ): DevTcpBridgeSnapshot = DevTcpBridgeSnapshot(
        sessionId = id,
        profileId = profileId,
        targetHost = targetHost.trim(),
        targetPort = targetPort,
        state = state,
        bytesIn = bytesIn,
        bytesOut = bytesOut,
        lastEvent = DEV_TCP_BRIDGE_NO_FORWARDING,
    )

    private fun snapshotFor(sessionId: TcpSessionId, event: String): DevTcpBridgeSnapshot {
        val metadata = sessionManager.lookupSession(sessionId)
        val current = active
        return DevTcpBridgeSnapshot(
            sessionId = sessionId,
            profileId = current?.profileId ?: lastSnapshot.profileId,
            targetHost = current?.targetHost ?: lastSnapshot.targetHost,
            targetPort = current?.targetPort ?: lastSnapshot.targetPort,
            state = metadata?.state ?: TcpSessionState.Closed,
            bytesIn = metadata?.bytesIn ?: lastSnapshot.bytesIn,
            bytesOut = metadata?.bytesOut ?: lastSnapshot.bytesOut,
            lastEvent = event.sanitizeSecrets(),
        )
    }

    private fun Throwable.safeMessage(): String = (message ?: javaClass.simpleName).sanitizeSecrets()

    private fun String.sanitizeSecrets(): String = replace(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"), "***")

    private fun SSLParameters.withServerName(host: String): SSLParameters = apply {
        serverNames = listOf<SNIServerName>(SNIHostName(host))
    }

    private data class ActiveDevSession(
        val sessionId: TcpSessionId,
        val profileId: String,
        val targetHost: String,
        val targetPort: Int,
        val rawSocket: Socket,
        val streamSocket: Socket,
    )

    companion object {
        private const val DEFAULT_CONNECT_TIMEOUT_MS = 5_000
        private const val DEFAULT_READ_TIMEOUT_MS = 750
        private const val RECEIVE_BUFFER_BYTES = 4_096
        private const val DEV_SOURCE_HOST = "127.0.0.1"
        private const val DEV_SOURCE_PORT = 1
    }
}
