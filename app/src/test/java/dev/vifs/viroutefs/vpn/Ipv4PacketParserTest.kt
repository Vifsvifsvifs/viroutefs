// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class Ipv4PacketParserTest {
    @Test
    fun parsesIpv4TcpSummary() {
        val summary = Ipv4PacketParser.parseSummary(
            ipv4Packet(protocol = 6, srcPort = 12_345, dstPort = 443),
            40,
            timestamp = 1_000L,
        )

        requireNotNull(summary)
        assertEquals(1_000L, summary.timestamp)
        assertEquals(Ipv4Protocol.Tcp, summary.protocol)
        assertEquals("10.0.0.2", summary.srcIp)
        assertEquals(12_345, summary.srcPort)
        assertEquals("203.0.113.10", summary.dstIp)
        assertEquals(443, summary.dstPort)
        assertEquals(40, summary.packetSize)
    }

    @Test
    fun parsesIpv4UdpSummary() {
        val summary = Ipv4PacketParser.parseSummary(
            ipv4Packet(protocol = 17, srcPort = 53, dstPort = 54_321),
            28,
            timestamp = 2_000L,
        )

        requireNotNull(summary)
        assertEquals(Ipv4Protocol.Udp, summary.protocol)
        assertEquals("10.0.0.2", summary.srcIp)
        assertEquals(53, summary.srcPort)
        assertEquals("203.0.113.10", summary.dstIp)
        assertEquals(54_321, summary.dstPort)
        assertEquals(28, summary.packetSize)
    }

    @Test
    fun parsesIcmpSummaryWithoutPorts() {
        val summary = Ipv4PacketParser.parseSummary(
            ipv4Packet(protocol = 1, totalLength = 28),
            28,
            timestamp = 3_000L,
        )

        requireNotNull(summary)
        assertEquals(Ipv4Protocol.Icmp, summary.protocol)
        assertEquals("10.0.0.2", summary.srcIp)
        assertNull(summary.srcPort)
        assertEquals("203.0.113.10", summary.dstIp)
        assertNull(summary.dstPort)
        assertEquals(28, summary.packetSize)
    }

    @Test
    fun classifiesOtherIpv4ProtocolsWithoutLoggingPayload() {
        assertEquals(Ipv4Protocol.Other, Ipv4PacketParser.parse(ipv4Packet(protocol = 47, totalLength = 20), 20))
    }


    @Test
    fun parserDoesNotStorePayloadOrSecrets() {
        val packet = ipv4Packet(protocol = 6, totalLength = 48, srcPort = 12_345, dstPort = 443).apply {
            "secret!".encodeToByteArray().copyInto(this, destinationOffset = 40)
        }

        val summary = Ipv4PacketParser.parseSummary(packet, 48, timestamp = 4_000L)

        requireNotNull(summary)
        assertEquals(48, summary.packetSize)
        assertEquals("10.0.0.2", summary.srcIp)
        assertEquals("203.0.113.10", summary.dstIp)
        assertEquals(12_345, summary.srcPort)
        assertEquals(443, summary.dstPort)
    }
    @Test
    fun rejectsNonIpv4OrTruncatedPackets() {
        assertNull(Ipv4PacketParser.parseSummary(byteArrayOf(0x60), 1))
        assertNull(Ipv4PacketParser.parseSummary(ipv4Packet(protocol = 6), 19))
        assertNull(Ipv4PacketParser.parseSummary(byteArrayOf(0x46, 0, 0, 20, 0, 0, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 20))
        assertNull(Ipv4PacketParser.parseSummary(ipv4Packet(protocol = 6, totalLength = 40), 23))
        assertNull(Ipv4PacketParser.parseSummary(ipv4Packet(protocol = 17, totalLength = 28), 23))
    }

    private fun ipv4Packet(
        protocol: Int,
        totalLength: Int = when (protocol) {
            6 -> 40
            17 -> 28
            else -> 20
        },
        srcPort: Int = 1_234,
        dstPort: Int = 4_321,
    ): ByteArray = ByteArray(totalLength.coerceAtLeast(20)).apply {
        this[0] = 0x45
        this[2] = ((totalLength ushr 8) and 0xff).toByte()
        this[3] = (totalLength and 0xff).toByte()
        this[9] = protocol.toByte()
        this[12] = 10
        this[13] = 0
        this[14] = 0
        this[15] = 2
        this[16] = 203.toByte()
        this[17] = 0
        this[18] = 113
        this[19] = 10
        if (protocol == 6 || protocol == 17) {
            this[20] = ((srcPort ushr 8) and 0xff).toByte()
            this[21] = (srcPort and 0xff).toByte()
            this[22] = ((dstPort ushr 8) and 0xff).toByte()
            this[23] = (dstPort and 0xff).toByte()
        }
    }
}
