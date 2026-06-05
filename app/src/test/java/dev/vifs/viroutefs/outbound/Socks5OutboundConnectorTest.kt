// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.outbound

import dev.vifs.viroutefs.socks5.Socks5HandshakeTester
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.encodeSocks5ConnectRequest
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class Socks5OutboundConnectorTest {
    @Test
    fun outboundTargetValidationAcceptsDomainAndPort() {
        assertTrue(OutboundTarget("example.com", 443).validationErrors().isEmpty())
    }

    @Test
    fun outboundTargetValidationRejectsInvalidHost() {
        val errors = OutboundTarget("bad host", 443).validationErrors().joinToString(" ")

        assertTrue(errors.contains("whitespace"))
    }

    @Test
    fun outboundTargetValidationRejectsInvalidPort() {
        val errors = OutboundTarget("example.com", 0).validationErrors().joinToString(" ")

        assertTrue(errors.contains("1..65535"))
    }

    @Test
    fun socks5DomainConnectRequestIsEncodedThroughSharedHelper() {
        assertContentEquals(
            byteArrayOf(0x05, 0x01, 0x00, 0x03, 11) + "example.com".encodeToByteArray() + byteArrayOf(0x01, 0xBB.toByte()),
            encodeSocks5ConnectRequest("example.com", 443),
        )
    }

    @Test
    fun successfulConnectMapsToOutboundSuccess() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            repeat(3) { input.read() }
            output.write(byteArrayOf(5, 0))
            val connectRequest = ByteArray(18)
            input.readFully(connectRequest)
            assertContentEquals(encodeSocks5ConnectRequest("example.com", 443), connectRequest)
            output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0x01, 0xBB.toByte()))
        }
        try {
            val result = connector(server.port).connect(request())

            assertIs<OutboundConnectResult.Success>(result)
        } finally {
            server.close()
        }
    }

    @Test
    fun proxyRejectionMapsToConnectRejectedByProxy() = runTest {
        val server = FakeSocks5Server { client ->
            noAuthGreeting(client)
            client.getInputStream().skipConnectRequest()
            client.getOutputStream().write(byteArrayOf(5, 2, 0, 1, 127, 0, 0, 1, 0, 0))
        }
        try {
            assertIs<OutboundConnectResult.ConnectRejectedByProxy>(connector(server.port).connect(request()))
        } finally {
            server.close()
        }
    }

    @Test
    fun targetFailureMapsToTargetUnreachable() = runTest {
        val server = FakeSocks5Server { client ->
            noAuthGreeting(client)
            client.getInputStream().skipConnectRequest()
            client.getOutputStream().write(byteArrayOf(5, 5, 0, 1, 127, 0, 0, 1, 0, 0))
        }
        try {
            assertIs<OutboundConnectResult.TargetUnreachable>(connector(server.port).connect(request()))
        } finally {
            server.close()
        }
    }

    @Test
    fun invalidResponseMapsToInvalidResponse() = runTest {
        val server = FakeSocks5Server { client ->
            noAuthGreeting(client)
            client.getInputStream().skipConnectRequest()
            client.getOutputStream().write(byteArrayOf(4, 0, 0, 1, 127, 0, 0, 1, 0, 0))
        }
        try {
            assertIs<OutboundConnectResult.InvalidResponse>(connector(server.port).connect(request()))
        } finally {
            server.close()
        }
    }

    @Test
    fun timeoutMapsToTimeout() = runTest {
        val server = FakeSocks5Server { client ->
            client.getInputStream().read()
            Thread.sleep(500)
        }
        try {
            assertIs<OutboundConnectResult.Timeout>(connector(server.port).connect(request(timeoutMs = 50)))
        } finally {
            server.close()
        }
    }

    @Test
    fun connectorResultMessageDoesNotExposePassword() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            repeat(3) { input.read() }
            output.write(byteArrayOf(5, Socks5HandshakeTester.NO_ACCEPTABLE_METHOD))
        }
        try {
            val result = connector(server.port, password = "super-secret-password").connect(request())

            assertFalse(result.message.contains("super-secret-password"))
        } finally {
            server.close()
        }
    }

    private fun connector(port: Int, password: String? = null) = Socks5OutboundConnector(
        profile = Socks5ProfileConfig(
            name = "Local test",
            host = "127.0.0.1",
            port = port,
            username = password?.let { "user" },
            password = password,
        ),
    )

    private fun request(timeoutMs: Int = 1_000) = OutboundConnectRequest(
        profileId = "profile-a",
        target = OutboundTarget("example.com", 443),
        timeoutMs = timeoutMs,
    )

    private fun noAuthGreeting(client: Socket) {
        val input = client.getInputStream()
        val output = client.getOutputStream()
        repeat(3) { input.read() }
        output.write(byteArrayOf(5, 0))
    }

    private fun java.io.InputStream.skipConnectRequest() {
        val expected = ByteArray(18)
        readFully(expected)
    }

    private fun java.io.InputStream.readFully(buffer: ByteArray) {
        var offset = 0
        while (offset < buffer.size) {
            val count = read(buffer, offset, buffer.size - offset)
            if (count < 0) error("Stream ended early")
            offset += count
        }
    }
}

private class FakeSocks5Server(private val handler: (Socket) -> Unit) {
    private val serverSocket = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort
    private val worker = thread(start = true) {
        runCatching { serverSocket.accept().use(handler) }
    }

    fun close() {
        serverSocket.close()
        worker.join(1_000)
    }
}
