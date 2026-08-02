package dev.vifs.viroutefs.diagnostics

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsWireCodecTest {
    @Test
    fun buildsAQueryWithExpectedHeaderAndQuestion() {
        val query = DnsWireCodec.buildQuery("example.com", "A", 0x1234)

        assertContentEquals(
            byteArrayOf(0x12, 0x34, 0x01, 0x00, 0x00, 0x01),
            query.copyOfRange(0, 6),
        )
        assertContentEquals(byteArrayOf(0x00, 0x01, 0x00, 0x01), query.takeLast(4).toByteArray())
    }

    @Test
    fun parsesCompressedAAnswer() {
        val response = response(
            transactionId = 0x1234,
            flags = 0x8180,
            type = 1,
            address = byteArrayOf(93, 184.toByte(), 216.toByte(), 34),
        )

        val parsed = DnsWireCodec.parseResponse(response, 0x1234, "A")

        assertEquals(listOf("93.184.216.34"), parsed.addresses)
        assertEquals(0, parsed.responseCode)
        assertEquals(1, parsed.answerCount)
    }

    @Test
    fun parsesAaaaAnswerAndTruncationFlag() {
        val address = byteArrayOf(
            0x20, 0x01, 0x0d, 0xb8.toByte(), 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 1,
        )
        val parsed = DnsWireCodec.parseResponse(
            response(0xbeef, 0x8380, 28, address),
            0xbeef,
            "AAAA",
        )

        assertTrue(parsed.truncated)
        assertEquals(1, parsed.addresses.size)
        assertTrue(parsed.addresses.single().startsWith("2001:db8:"))
    }

    @Test
    fun rejectsMismatchedAndTruncatedResponses() {
        val response = response(7, 0x8183, 1, byteArrayOf(127, 0, 0, 1))

        assertFailsWith<DnsProtocolException> { DnsWireCodec.parseResponse(response, 8, "A") }
        assertFailsWith<DnsProtocolException> { DnsWireCodec.parseResponse(response.copyOf(8), 7, "A") }
    }

    private fun response(
        transactionId: Int,
        flags: Int,
        type: Int,
        address: ByteArray,
    ): ByteArray = ByteArrayOutputStream().apply {
        short(transactionId)
        short(flags)
        short(1)
        short(1)
        short(0)
        short(0)
        write(byteArrayOf(7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte()))
        write(byteArrayOf(3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0))
        short(type)
        short(1)
        short(0xc00c)
        short(type)
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
