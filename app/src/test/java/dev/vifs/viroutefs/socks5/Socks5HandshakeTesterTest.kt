// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import kotlinx.coroutines.test.runTest
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class Socks5HandshakeTesterTest {
    @Test
    fun noAuthSuccessUsesLocalFakeServer() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            assertEquals(5, input.read())
            assertEquals(1, input.read())
            assertEquals(0, input.read())
            output.write(byteArrayOf(5, 0))
        }

        assertEquals(Socks5TestResult.Reachable, Socks5HandshakeTester().test(profile(port = server.port)))
        server.close()
    }

    @Test
    fun usernamePasswordSuccessUsesLocalFakeServer() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            repeat(3) { input.read() }
            output.write(byteArrayOf(5, 2))
            assertEquals(1, input.read())
            val usernameSize = input.read()
            val username = ByteArray(usernameSize).also { input.read(it) }.decodeToString()
            val passwordSize = input.read()
            val password = ByteArray(passwordSize).also { input.read(it) }.decodeToString()
            assertEquals("alice", username)
            assertEquals("secret", password)
            output.write(byteArrayOf(1, 0))
        }

        assertEquals(Socks5TestResult.Reachable, Socks5HandshakeTester().test(profile(port = server.port, username = "alice", password = "secret")))
        server.close()
    }

    @Test
    fun authenticationFailureIsClear() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            repeat(3) { input.read() }
            output.write(byteArrayOf(5, 2))
            val version = input.read()
            val usernameSize = input.read()
            repeat(usernameSize) { input.read() }
            val passwordSize = input.read()
            repeat(passwordSize) { input.read() }
            assertEquals(1, version)
            output.write(byteArrayOf(1, 1))
        }

        assertEquals(Socks5TestResult.Failed.AuthenticationRejected, Socks5HandshakeTester().test(profile(port = server.port, username = "alice", password = "bad")))
        server.close()
    }

    @Test
    fun unsupportedMethodIsClear() = runTest {
        val server = FakeSocks5Server { client ->
            val input = client.getInputStream()
            val output = client.getOutputStream()
            repeat(3) { input.read() }
            output.write(byteArrayOf(5, Socks5HandshakeTester.NO_ACCEPTABLE_METHOD))
        }

        assertEquals(Socks5TestResult.Failed.UnsupportedMethod, Socks5HandshakeTester().test(profile(port = server.port)))
        server.close()
    }

    @Test
    fun invalidResponseIsClear() = runTest {
        val server = FakeSocks5Server { client ->
            client.getOutputStream().write(byteArrayOf(4, 0))
        }

        assertIs<Socks5TestResult.Failed.InvalidResponse>(Socks5HandshakeTester().test(profile(port = server.port)))
        server.close()
    }

    private fun profile(port: Int, username: String? = null, password: String? = null) = Socks5ProfileConfig(
        name = "Local test",
        host = "127.0.0.1",
        port = port,
        username = username,
        password = password,
    )
}

private class FakeSocks5Server(private val handler: (java.net.Socket) -> Unit) {
    private val serverSocket = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
    val port: Int = serverSocket.localPort
    private val worker = thread(start = true) {
        serverSocket.accept().use(handler)
    }

    fun close() {
        serverSocket.close()
        worker.join(1_000)
    }
}
