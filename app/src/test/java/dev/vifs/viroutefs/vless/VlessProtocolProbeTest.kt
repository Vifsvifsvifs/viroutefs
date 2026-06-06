// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketAddress
import java.util.UUID
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SNIHostName
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlessProtocolProbeTest {
    @Test
    fun tlsProfileSelectsTlsProbePath() {
        val profile = plainProfile().copy(securityMode = VlessSecurityMode.TLS, sni = "tls.example")
        val fakeTlsSocket = FakeSslSocket()

        val result = VlessProtocolProber(
            socketFactory = { FakeTcpSocket() },
            tlsSocketFactory = { _, host, port, autoClose ->
                fakeTlsSocket.wrapHost = host
                fakeTlsSocket.wrapPort = port
                fakeTlsSocket.wrapAutoClose = autoClose
                fakeTlsSocket
            },
        ).probeBlocking(profile, "example.com", 80)

        assertEquals(VlessProtocolProbeState.ServerClosedConnection, result.state)
        assertEquals(VlessSecurityMode.TLS, result.securityMode)
        assertTrue(fakeTlsSocket.handshakeStarted)
        assertTrue(result.steps.contains(VlessProtocolProbeState.TcpConnected))
        assertTrue(result.steps.contains(VlessProtocolProbeState.TlsHandshakeSuccess))
        assertTrue(result.steps.contains(VlessProtocolProbeState.VlessRequestSent))
        assertContentEquals(buildVlessTcpRequest(profile.uuid, "example.com", 80), fakeTlsSocket.sentBytes())
        assertEquals("tls.example", fakeTlsSocket.wrapHost)
        assertEquals(443, fakeTlsSocket.wrapPort)
        assertTrue(fakeTlsSocket.wrapAutoClose)
    }

    @Test
    fun sniDefaultsToProfileHostWhenBlank() {
        val profile = plainProfile().copy(securityMode = VlessSecurityMode.TLS, sni = " ")
        val fakeTlsSocket = FakeSslSocket()

        VlessProtocolProber(
            socketFactory = { FakeTcpSocket() },
            tlsSocketFactory = { _, _, _, _ -> fakeTlsSocket },
        ).probeBlocking(profile, "example.com", 80)

        val serverName = fakeTlsSocket.appliedSslParameters.serverNames.single() as SNIHostName
        assertEquals(profile.host, serverName.asciiName)
    }

    @Test
    fun realityProfileReturnsUnsupportedTransport() {
        val profile = plainProfile().copy(securityMode = VlessSecurityMode.REALITY)

        val result = VlessProtocolProber(socketFactory = { error("REALITY must not connect") }).probeBlocking(profile, "example.com", 80)

        assertEquals(VlessProtocolProbeState.UnsupportedSecurityMode, result.state)
        assertEquals(VlessResponseClassification.UnsupportedTransport, result.classification)
        assertEquals("Security mode not supported", result.message)
        assertEquals(VlessSecurityMode.REALITY, result.securityMode)
    }

    @Test
    fun invalidTargetIsRejectedBeforeConnect() {
        val result = VlessProtocolProber().probeBlocking(plainProfile(), "bad target.example", 80)

        assertEquals(VlessProtocolProbeState.ValidationError, result.state)
        assertTrue(result.message.contains("target host", ignoreCase = true))
    }

    @Test
    fun historyIsCappedAt20AndDoesNotContainUuidPayloadOrRawFrame() = runTest {
        val file = File.createTempFile("vless-probe-history", ".json").apply { delete() }
        val store = VlessProtocolProbeHistoryStore(file)
        val uuid = plainProfile().uuid
        val payload = "payload-secret-bytes"

        (1..25).forEach { index ->
            store.add(
                VlessProtocolProbeHistoryItem(
                    profileId = "profile-a",
                    profileNameSnapshot = "Lab VLESS",
                    serverHost = "vless.example",
                    serverPort = 443,
                    targetHost = "example.com",
                    targetPort = 80,
                    timestamp = index.toLong(),
                    state = VlessProtocolProbeState.ResponseReceived,
                    message = "Probe completed for uuid=$uuid with metadata only",
                    elapsedMs = index.toLong(),
                    securityMode = VlessSecurityMode.TLS,
                    responseBytes = 3,
                ),
            )
        }

        val profileHistory = store.recentForProfile("profile-a")
        val encoded = file.readText()

        assertEquals(20, profileHistory.size)
        assertEquals(25L, profileHistory.first().timestamp)
        assertEquals(6L, profileHistory.last().timestamp)
        assertEquals(VlessSecurityMode.TLS, profileHistory.first().securityMode)
        assertEquals(3, profileHistory.first().responseBytes)
        assertFalse(encoded.contains(uuid))
        assertFalse(profileHistory.joinToString().contains(uuid))
        assertFalse(encoded.contains(payload))
        assertFalse(profileHistory.joinToString().contains(payload))
        assertFalse(encoded.contains("raw frame", ignoreCase = true))
    }

    @Test
    fun resultMessagesDoNotContainUuid() {
        val uuid = plainProfile().uuid
        val result = VlessProtocolProbeResult(
            serverHost = "vless.example",
            serverPort = 443,
            targetHost = "example.com",
            targetPort = 80,
            timestamp = 1L,
            state = VlessProtocolProbeState.ValidationError,
            message = "uuid=$uuid failed validation",
        )

        assertFalse(result.displayMessage.contains(uuid))
        assertTrue(result.displayMessage.contains("uuid=***"))
    }


    @Test
    fun responseReceivedMapsToResponseReceivedWithByteCount() {
        val response = byteArrayOf(0x00, 0x01, 0x02)
        val result = VlessProtocolProber(
            socketFactory = { FakeProbeSocket(ByteArrayInputStream(response)) },
        ).probeBlocking(plainProfile(), "example.com", 80)

        assertEquals(VlessProtocolProbeState.ResponseReceived, result.state)
        assertEquals(VlessResponseClassification.ResponseReceived, result.classification)
        assertEquals(response.size, result.responseBytes)
        assertTrue(result.displayMessage.contains("Response received: ${response.size} bytes"))
    }

    @Test
    fun emptyResponseMapsSafelyWithoutPayloadStorage() {
        val result = VlessProtocolProber(
            socketFactory = { FakeProbeSocket(ZeroReadInputStream()) },
        ).probeBlocking(plainProfile(), "example.com", 80)

        assertEquals(VlessProtocolProbeState.InvalidEmptyResponse, result.state)
        assertEquals(VlessResponseClassification.EmptyResponse, result.classification)
        assertEquals(0, result.responseBytes)
        assertFalse(result.displayMessage.contains("0x"))
    }

    @Test
    fun timeoutAfterRequestMapsToNoImmediateResponseSafely() {
        val result = VlessProtocolProber(
            socketFactory = { FakeProbeSocket(TimeoutInputStream()) },
        ).probeBlocking(plainProfile(), "example.com", 80)

        assertEquals(VlessProtocolProbeState.RequestSentNoImmediateResponse, result.state)
        assertEquals(VlessResponseClassification.Timeout, result.classification)
        assertEquals(0, result.responseBytes)
        assertTrue(result.steps.contains(VlessProtocolProbeState.VlessRequestSent))
    }

    @Test
    fun serverClosedMapsSafely() {
        val result = VlessProtocolProber(
            socketFactory = { FakeProbeSocket(ByteArrayInputStream(ByteArray(0))) },
        ).probeBlocking(plainProfile(), "example.com", 80)

        assertEquals(VlessProtocolProbeState.ServerClosedConnection, result.state)
        assertEquals(VlessResponseClassification.ServerClosed, result.classification)
        assertEquals(0, result.responseBytes)
    }

    @Test
    fun tlsResponseClassificationUsesParser() {
        val profile = plainProfile().copy(securityMode = VlessSecurityMode.TLS)
        val fakeTlsSocket = FakeSslSocket(response = byteArrayOf(0x01, 0x02, 0x03, 0x04))

        val result = VlessProtocolProber(
            socketFactory = { FakeTcpSocket() },
            tlsSocketFactory = { _, _, _, _ -> fakeTlsSocket },
        ).probeBlocking(profile, "example.com", 80)

        assertEquals(VlessProtocolProbeState.ResponseReceived, result.state)
        assertEquals(VlessResponseClassification.ResponseReceived, result.classification)
        assertEquals(4, result.responseBytes)
        assertEquals(VlessSecurityMode.TLS, result.responseMetadata.securityMode)
        assertTrue(result.displayMessage.contains("Response received: 4 bytes"))
    }

    @Test
    fun invalidTargetPortIsRejectedBeforeConnect() {
        val result = VlessProtocolProber(socketFactory = { error("invalid target must not connect") })
            .probeBlocking(plainProfile(), "example.com", 70000)

        assertEquals(VlessProtocolProbeState.ValidationError, result.state)
        assertEquals(VlessResponseClassification.ValidationError, result.classification)
        assertTrue(result.message.contains("target port", ignoreCase = true))
    }

    @Test
    fun unsupportedTransportWarnsBeforeConnect() {
        val result = VlessProtocolProber(socketFactory = { error("unsupported transport must not connect") })
            .probeBlocking(plainProfile().copy(transportType = "ws"), "example.com", 80)

        assertEquals(VlessProtocolProbeState.UnsupportedSecurityMode, result.state)
        assertEquals(VlessResponseClassification.UnsupportedTransport, result.classification)
        assertEquals("Security mode not supported", result.displayMessage)
    }

    @Test
    fun plainTcpPathStillWorksForSecurityModeNone() {
        val profile = plainProfile()
        val targetHost = "example.com"
        val targetPort = 80
        val expectedFrame = buildVlessTcpRequest(profile.uuid, targetHost, targetPort)
        lateinit var receivedFrame: ByteArray
        ServerSocket(0).use { server ->
            val serverThread = thread(start = true) {
                server.accept().use { socket ->
                    receivedFrame = socket.getInputStream().readNBytes(expectedFrame.size)
                    Thread.sleep(250)
                }
            }

            val result = VlessProtocolProber(waitTimeoutMs = 100).probeBlocking(
                profile.copy(host = "127.0.0.1", port = server.localPort),
                targetHost,
                targetPort,
            )
            serverThread.join(1_000)

            assertEquals(VlessProtocolProbeState.RequestSentNoImmediateResponse, result.state)
            assertEquals(VlessSecurityMode.NONE, result.securityMode)
            assertTrue(result.steps.contains(VlessProtocolProbeState.TcpConnected))
            assertFalse(result.steps.contains(VlessProtocolProbeState.TlsHandshakeSuccess))
            assertTrue(result.steps.contains(VlessProtocolProbeState.VlessRequestSent))
            assertContentEquals(expectedFrame, receivedFrame)
        }
    }

    private fun plainProfile(): VlessProfileConfig = VlessProfileConfig(
        name = "Lab VLESS",
        host = "vless.example",
        port = 443,
        uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
        securityMode = VlessSecurityMode.NONE,
    )
}

private class FakeTcpSocket : Socket() {
    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
}

private class FakeProbeSocket(
    private val input: InputStream,
) : Socket() {
    private val output = ByteArrayOutputStream()

    override fun connect(endpoint: SocketAddress?, timeout: Int) = Unit
    override fun getOutputStream(): OutputStream = output
    override fun getInputStream(): InputStream = input
    override fun setSoTimeout(timeout: Int) = Unit
    override fun close() = Unit
}

private class ZeroReadInputStream : InputStream() {
    override fun read(): Int = 0
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
}

private class TimeoutInputStream : InputStream() {
    override fun read(): Int = throw SocketTimeoutException("metadata read timed out")
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = throw SocketTimeoutException("metadata read timed out")
}

private class FakeSslSocket(
    private val response: ByteArray = ByteArray(0),
) : SSLSocket() {
    private val output = ByteArrayOutputStream()
    var wrapHost: String? = null
    var wrapPort: Int? = null
    var wrapAutoClose: Boolean = false
    var handshakeStarted: Boolean = false
    var appliedSslParameters: SSLParameters = SSLParameters()

    fun sentBytes(): ByteArray = output.toByteArray()

    override fun startHandshake() {
        handshakeStarted = true
    }

    override fun getOutputStream(): OutputStream = output
    override fun getInputStream(): InputStream = ByteArrayInputStream(response)
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
