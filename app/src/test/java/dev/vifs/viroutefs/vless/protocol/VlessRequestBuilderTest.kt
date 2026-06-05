// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VlessRequestBuilderTest {
    private val uuid = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun uuidStringConvertsTo16Bytes() {
        val bytes = parseVlessUuidBytes(uuid)

        assertEquals(16, bytes.size)
        assertContentEquals(
            byteArrayOf(
                0x12, 0x3e, 0x45, 0x67,
                0xe8.toByte(), 0x9b.toByte(), 0x12, 0xd3.toByte(),
                0xa4.toByte(), 0x56, 0x42, 0x66,
                0x14, 0x17, 0x40, 0x00,
            ),
            bytes,
        )
    }

    @Test
    fun tcpCommandFrameContainsCommand01() {
        val frame = buildVlessTcpRequest(uuid, "example.com", 443)

        assertEquals(0x01, frame[18].toInt() and 0xff)
    }

    @Test
    fun portIsBigEndian() {
        val frame = buildVlessTcpRequest(uuid, "example.com", 443)

        assertEquals(0x01, frame[19].toInt() and 0xff)
        assertEquals(0xbb, frame[20].toInt() and 0xff)
    }

    @Test
    fun ipv4AddressEncoding() {
        val frame = buildVlessTcpRequest(uuid, "192.0.2.10", 80)

        assertEquals(VlessProtocol.ADDRESS_TYPE_IPV4, frame[21])
        assertContentEquals(byteArrayOf(192.toByte(), 0, 2, 10), frame.copyOfRange(22, 26))
    }

    @Test
    fun domainAddressEncoding() {
        val frame = buildVlessTcpRequest(uuid, "example.com", 443)

        assertEquals(VlessProtocol.ADDRESS_TYPE_DOMAIN, frame[21])
        assertEquals(11, frame[22].toInt() and 0xff)
        assertEquals("example.com", frame.copyOfRange(23, frame.size).decodeToString())
    }

    @Test
    fun domainLongerThan255BytesRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            buildVlessTcpRequest(uuid, "a".repeat(256), 443)
        }

        assertEquals("invalid host", error.message)
    }

    @Test
    fun invalidUuidRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            buildVlessTcpRequest("not-a-uuid", "example.com", 443)
        }

        assertEquals("invalid UUID", error.message)
    }

    @Test
    fun invalidPortRejected() {
        val low = assertFailsWith<IllegalArgumentException> {
            buildVlessTcpRequest(uuid, "example.com", 0)
        }
        val high = assertFailsWith<IllegalArgumentException> {
            buildVlessTcpRequest(uuid, "example.com", 65536)
        }

        assertEquals("invalid port", low.message)
        assertEquals("invalid port", high.message)
    }

    @Test
    fun blankHostRejected() {
        val error = assertFailsWith<IllegalArgumentException> {
            buildVlessTcpRequest(uuid, "   ", 443)
        }

        assertEquals("invalid host", error.message)
    }

    @Test
    fun noUuidAppearsInErrorMessages() {
        val errors = listOf(
            assertFailsWith<IllegalArgumentException> { buildVlessTcpRequest(uuid, "", 443) },
            assertFailsWith<IllegalArgumentException> { buildVlessTcpRequest(uuid, "example.com", -1) },
            assertFailsWith<IllegalArgumentException> { buildVlessTcpRequest("not-a-uuid", "example.com", 443) },
        )

        errors.forEach { error ->
            assertFalse(error.message.orEmpty().contains(uuid))
            assertFalse(error.message.orEmpty().contains("123e4567"))
        }
    }

    @Test
    fun frameLengthExpectedForSimpleDomainTarget() {
        val frame = buildVlessTcpRequest(uuid, "example.com", 443)

        assertEquals(34, frame.size)
        assertEquals(34, describeVlessTcpRequestFrame(frame).lengthBytes)
    }

    @Test
    fun frameLengthExpectedForIpv4Target() {
        val frame = buildVlessTcpRequest(uuid, "192.0.2.10", 80)

        assertEquals(26, frame.size)
    }

    @Test
    fun debugHelperDoesNotExposeUuid() {
        val frame = buildVlessTcpRequest(uuid, "example.com", 443)
        val summary = describeVlessTcpRequestFrame(frame).safeSummary()

        assertTrue(summary.contains("length=34"))
        assertFalse(summary.contains(uuid))
        assertFalse(summary.contains("123e4567"))
    }
}
