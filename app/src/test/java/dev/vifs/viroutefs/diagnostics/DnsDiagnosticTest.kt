package dev.vifs.viroutefs.diagnostics

import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DnsDiagnosticTest {
    @Test
    fun explicitlySelectedUdpServerReceivesTheRealQuery() {
        DatagramSocket(0, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 3_000
            val worker = thread(name = "fake-dns-server") {
                val requestBuffer = ByteArray(512)
                val request = DatagramPacket(requestBuffer, requestBuffer.size)
                server.receive(request)
                val query = request.data.copyOfRange(request.offset, request.offset + request.length)
                val response = answerFor(query, byteArrayOf(203.toByte(), 0, 113, 7))
                server.send(DatagramPacket(response, response.size, request.socketAddress))
            }

            val result = runBlocking {
                DnsDiagnostic(timeoutMs = 2_000).lookup(
                    domain = "example.com",
                    dnsServer = "udp://127.0.0.1:${server.localPort}",
                    recordType = "A",
                )
            }
            worker.join(3_000)

            assertEquals(DiagnosticStatus.SUCCESS, result.status)
            assertTrue(result.technicalDetails.contains("203.0.113.7"))
            assertTrue(result.technicalDetails.contains("UDP://127.0.0.1:${server.localPort}"))
            assertTrue(result.technicalDetails.contains("вне TUN"))
        }
    }

    private fun answerFor(query: ByteArray, address: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(query, 0, 2) // transaction ID
        short(0x8180)
        short(1)
        short(1)
        short(0)
        short(0)
        write(query, 12, query.size - 12)
        short(0xc00c)
        short(1)
        short(1)
        write(byteArrayOf(0, 0, 0, 60))
        short(address.size)
        write(address)
    }.toByteArray()

    private fun ByteArrayOutputStream.short(value: Int) {
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }
}
