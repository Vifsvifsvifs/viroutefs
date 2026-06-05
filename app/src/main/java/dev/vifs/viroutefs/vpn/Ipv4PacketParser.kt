// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

internal enum class Ipv4Protocol {
    Tcp,
    Udp,
    Icmp,
    Other,
}

internal object Ipv4PacketParser {
    fun parse(packet: ByteArray, length: Int): Ipv4Protocol? {
        if (length < MIN_IPV4_HEADER_BYTES || length > packet.size || packet.isEmpty()) return null

        val firstByte = packet[0].toInt() and 0xff
        val version = firstByte shr 4
        if (version != IPV4_VERSION) return null

        val headerLength = (firstByte and IPV4_IHL_MASK) * IPV4_WORD_BYTES
        if (headerLength < MIN_IPV4_HEADER_BYTES || length < headerLength) return null

        return when (packet[IPV4_PROTOCOL_OFFSET].toInt() and 0xff) {
            PROTOCOL_TCP -> Ipv4Protocol.Tcp
            PROTOCOL_UDP -> Ipv4Protocol.Udp
            PROTOCOL_ICMP -> Ipv4Protocol.Icmp
            else -> Ipv4Protocol.Other
        }
    }

    private const val IPV4_VERSION = 4
    private const val IPV4_IHL_MASK = 0x0f
    private const val IPV4_WORD_BYTES = 4
    private const val MIN_IPV4_HEADER_BYTES = 20
    private const val IPV4_PROTOCOL_OFFSET = 9
    private const val PROTOCOL_ICMP = 1
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
}
