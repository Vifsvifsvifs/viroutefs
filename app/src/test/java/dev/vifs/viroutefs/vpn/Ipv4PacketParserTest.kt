// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun rejectsNonIpv4OrTruncatedPackets() {
        assertNull(Ipv4PacketParser.parse(byteArrayOf(0x60), 1))
        assertNull(Ipv4PacketParser.parse(ipv4Packet(protocol = 6), 19))
        assertNull(Ipv4PacketParser.parse(byteArrayOf(0x46, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), 20))
    }

    private fun ipv4Packet(protocol: Int): ByteArray = ByteArray(20).apply {
        this[0] = 0x45
        this[9] = protocol.toByte()
    }
}
