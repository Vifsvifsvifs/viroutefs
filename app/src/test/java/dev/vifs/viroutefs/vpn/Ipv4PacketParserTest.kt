// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class Ipv4PacketParserTest {
    @Test
    fun parsesTcpUdpAndIcmpIpv4Packets() {
        assertEquals(Ipv4Protocol.Tcp, Ipv4PacketParser.parse(ipv4Packet(protocol = 6), 20))
        assertEquals(Ipv4Protocol.Udp, Ipv4PacketParser.parse(ipv4Packet(protocol = 17), 20))
        assertEquals(Ipv4Protocol.Icmp, Ipv4PacketParser.parse(ipv4Packet(protocol = 1), 20))
    }

    @Test
    fun classifiesOtherIpv4ProtocolsWithoutLoggingPayload() {
        assertEquals(Ipv4Protocol.Other, Ipv4PacketParser.parse(ipv4Packet(protocol = 47), 20))
    }

    @Test
    fun parsesIpv4HeaderAndTransportMetadataWithoutPayload() {
        val summary = assertNotNull(
            Ipv4PacketParser.parseSummary(
                ipv4Packet(
                    protocol = 6,
                    source = byteArrayOf(10, 0, 0, 2),
                    destination = byteArrayOf(203.toByte(), 0, 113, 9),
                    sourcePort = 42_000,
                    destinationPort = 443,
                ),
                24,
            ),
        )

        assertEquals(Ipv4Protocol.Tcp, summary.protocol)
        assertEquals("10.0.0.2", summary.sourceAddress)
        assertEquals("203.0.113.9", summary.destinationAddress)
        assertEquals(42_000, summary.sourcePort)
        assertEquals(443, summary.destinationPort)
    }

    @Test
    fun rejectsNonIpv4OrTruncatedPackets() {
        assertNull(Ipv4PacketParser.parse(byteArrayOf(0x60), 1))
        assertNull(Ipv4PacketParser.parse(ipv4Packet(protocol = 6), 19))
        assertNull(Ipv4PacketParser.parse(byteArrayOf(0x46, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 20))
    }

    private fun ipv4Packet(
        protocol: Int,
        source: ByteArray = byteArrayOf(0, 0, 0, 0),
        destination: ByteArray = byteArrayOf(0, 0, 0, 0),
        sourcePort: Int? = null,
        destinationPort: Int? = null,
    ): ByteArray = ByteArray(if (sourcePort != null && destinationPort != null) 24 else 20).apply {
        this[0] = 0x45
        this[9] = protocol.toByte()
        source.copyInto(this, destinationOffset = 12)
        destination.copyInto(this, destinationOffset = 16)
        if (sourcePort != null && destinationPort != null) {
            this[20] = (sourcePort shr 8).toByte()
            this[21] = sourcePort.toByte()
            this[22] = (destinationPort shr 8).toByte()
            this[23] = destinationPort.toByte()
        }
    }
}
