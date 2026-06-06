// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import dev.vifs.viroutefs.vpn.Ipv4Protocol
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreviewer
import dev.vifs.viroutefs.vpn.PacketSummary
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevTcpBridgeTest {
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun sessionOpenCloseLifecycleUsesTcpSessionStates() {
        val socket = FakeDevSocket(response = byteArrayOf(1, 2, 3))
        val bridge = bridge(socket = socket)

        val sessionId = bridge.openDevSession("profile-a", "example.com", 80)

        assertEquals(TcpSessionState.Connected, bridge.snapshot().state)
        assertTrue(bridge.snapshot().hasOpenSession)
        assertEquals(DEV_TCP_BRIDGE_EVENT_OPEN, bridge.snapshot().lastEvent)

        bridge.closeDevSession(sessionId)

        assertEquals(TcpSessionState.Closed, bridge.snapshot().state)
        assertFalse(bridge.snapshot().hasOpenSession)
        assertTrue(socket.closed)
    }

    @Test
    fun countersIncrementOnlyForExplicitSendAndReceiveTestData() {
        val socket = FakeDevSocket(response = byteArrayOf(9, 8, 7, 6))
        val bridge = bridge(socket = socket)
        val sessionId = bridge.openDevSession("profile-a", "example.com", 80)

        val sent = bridge.sendTestData(sessionId, byteArrayOf(4, 5))
        val received = bridge.receiveTestData(sessionId)

        assertEquals(2, sent)
        assertContentEquals(byteArrayOf(9, 8, 7, 6), received)
        assertEquals(2, bridge.snapshot().bytesOut)
        assertEquals(4, bridge.snapshot().bytesIn)
    }

    @Test
    fun bridgeDoesNotExposeGeneralTrafficForwardingApi() {
        val methodNames = DevTcpBridge::class.java.methods.map { it.name }.toSet()

        assertTrue(methodNames.contains("sendTestData"))
        assertTrue(methodNames.contains("receiveTestData"))
        assertFalse(methodNames.contains("forward"))
        assertFalse(methodNames.contains("forwardUdp"))
        assertFalse(methodNames.contains("forwardDns"))
        assertEquals(DEV_TCP_BRIDGE_NO_FORWARDING, DevTcpBridgeSnapshot().copy(lastEvent = DEV_TCP_BRIDGE_NO_FORWARDING).lastEvent)
    }

    @Test
    fun singleSessionLimitRejectsSecondOpenSession() {
        val bridge = bridge(socket = FakeDevSocket())

        bridge.openDevSession("profile-a", "example.com", 80)

        assertFailsWith<IllegalArgumentException> {
            bridge.openDevSession("profile-a", "example.org", 80)
        }
    }

    @Test
    fun plainTcpPathUsesProfileSecurityNone() {
        val socket = FakeDevSocket()
        val bridge = bridge(socket = socket, profile = profile(securityMode = VlessSecurityMode.NONE))

        bridge.openDevSession("profile-a", "example.com", 80)

        assertTrue(socket.connected)
        assertContentEquals(buildVlessTcpRequest(uuid, "example.com", 80), socket.sentBytes())
    }

    @Test
    fun tlsPathWrapsSocketForTlsProfiles() {
        val rawSocket = FakeDevSocket()
        val tlsSocket = FakeDevSslSocket(response = byteArrayOf(1))
        val bridge = bridge(
            socket = rawSocket,
            profile = profile(securityMode = VlessSecurityMode.TLS, sni = "tls.example"),
            tlsSocket = tlsSocket,
        )

        bridge.openDevSession("profile-a", "example.com", 80)

        assertTrue(rawSocket.connected)
        assertTrue(tlsSocket.handshakeStarted)
        assertEquals("tls.example", tlsSocket.wrapHost)
        assertContentEquals(buildVlessTcpRequest(uuid, "example.com", 80), tlsSocket.sentBytes())
        assertContentEquals(ByteArray(0), rawSocket.sentBytes())
    }

    @Test
    fun routePreviewLogsDevSessionOpenEvent() {
        val summary = PacketSummary(
            timestamp = 1L,
            protocol = Ipv4Protocol.Tcp,
            srcIp = "10.0.0.2",
            srcPort = 12345,
            dstIp = "203.0.113.10",
            dstPort = 443,
            packetSize = 40,
        )

        val preview = LiveRouteDecisionPreviewer(RoutingConfigDefaults.defaultConfig()).preview(summary, devSessionOpen = true)

        assertTrue(preview.displayLines.contains(DEV_TCP_BRIDGE_EVENT_OPEN))
    }

    @Test
    fun uuidIsNotStoredInSnapshotsOrEvents() {
        val bridge = bridge(socket = FakeDevSocket())
        val sessionId = bridge.openDevSession("profile-a", "example.com", 80)
        bridge.sendTestData(sessionId, "hello".encodeToByteArray())

        val snapshotText = bridge.snapshot().toString()

        assertFalse(snapshotText.contains(uuid))
        assertFalse(snapshotText.contains("123e4567"))
    }

    private fun bridge(
        socket: FakeDevSocket,
        profile: VlessProfileConfig = profile(),
        tlsSocket: FakeDevSslSocket = FakeDevSslSocket(),
    ): VlessDevTcpBridge = VlessDevTcpBridge(
        profiles = { mapOf("profile-a" to profile) },
        socketFactory = { socket },
        tlsSocketFactory = { _, host, port, autoClose ->
            tlsSocket.wrapHost = host
            tlsSocket.wrapPort = port
            tlsSocket.wrapAutoClose = autoClose
            tlsSocket
        },
    )

    private fun profile(
        securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
        sni: String? = null,
    ): VlessProfileConfig = VlessProfileConfig(
        name = "Lab VLESS",
        host = "vless.example",
        port = 443,
        uuid = uuid,
        transportType = "tcp",
        securityMode = securityMode,
        sni = sni,
    )
}

private class FakeDevSocket(
    response: ByteArray = ByteArray(0),
) : Socket() {
    private val output = ByteArrayOutputStream()
    private val input = ByteArrayInputStream(response)
    var connected: Boolean = false
    var closed: Boolean = false

    fun sentBytes(): ByteArray = output.toByteArray()

    override fun connect(endpoint: SocketAddress?, timeout: Int) {
        connected = true
    }

    override fun getOutputStream(): OutputStream = output
    override fun getInputStream(): InputStream = input
    override fun setSoTimeout(timeout: Int) = Unit
    override fun close() {
        closed = true
    }
}

private class FakeDevSslSocket(
    private val response: ByteArray = ByteArray(0),
) : SSLSocket() {
    private val output = ByteArrayOutputStream()
    var wrapHost: String? = null
    var wrapPort: Int? = null
    var wrapAutoClose: Boolean = false
    var handshakeStarted: Boolean = false
    private var appliedSslParameters: SSLParameters = SSLParameters()

    fun sentBytes(): ByteArray = output.toByteArray()

    override fun startHandshake() {
        handshakeStarted = true
    }

    override fun getOutputStream(): OutputStream = output
    override fun getInputStream(): InputStream = ByteArrayInputStream(response)
    override fun setSoTimeout(timeout: Int) = Unit
    override fun setSSLParameters(params: SSLParameters) {
        appliedSslParameters = params
    }
    override fun getSSLParameters(): SSLParameters = appliedSslParameters
    override fun getSupportedCipherSuites(): Array<String> = emptyArray()
    override fun getEnabledCipherSuites(): Array<String> = emptyArray()
    override fun setEnabledCipherSuites(suites: Array<out String>?) = Unit
    override fun getSupportedProtocols(): Array<String> = emptyArray()
    override fun getEnabledProtocols(): Array<String> = emptyArray()
    override fun setEnabledProtocols(protocols: Array<out String>?) = Unit
    override fun getSession(): SSLSession = throw UnsupportedOperationException("No fake SSL session")
    override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
    override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener?) = Unit
    override fun setUseClientMode(mode: Boolean) = Unit
    override fun getUseClientMode(): Boolean = true
    override fun setNeedClientAuth(need: Boolean) = Unit
    override fun getNeedClientAuth(): Boolean = false
    override fun setWantClientAuth(want: Boolean) = Unit
    override fun getWantClientAuth(): Boolean = false
    override fun setEnableSessionCreation(flag: Boolean) = Unit
    override fun getEnableSessionCreation(): Boolean = true
    override fun bind(bindpoint: SocketAddress?) = Unit
    override fun close() = Unit
    override fun connect(endpoint: SocketAddress?) = Unit
    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
    override fun getInetAddress(): InetAddress? = null
    override fun getLocalAddress(): InetAddress? = null
    override fun getLocalPort(): Int = 0
    override fun getPort(): Int = 0
}
