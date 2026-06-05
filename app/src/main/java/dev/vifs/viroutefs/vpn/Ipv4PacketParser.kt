// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

internal enum class Ipv4Protocol {
    Tcp,
    Udp,
    Icmp,
    Other,
}

internal data class Ipv4PacketSummary(
    val protocol: Ipv4Protocol,
    val sourceAddress: String,
    val destinationAddress: String,
    val sourcePort: Int? = null,
    val destinationPort: Int? = null,
)

internal object Ipv4PacketParser {
    fun parse(packet: ByteArray, length: Int): Ipv4Protocol? = parseSummary(packet, length)?.protocol

    fun parseSummary(packet: ByteArray, length: Int): Ipv4PacketSummary? {
        if (length < MIN_IPV4_HEADER_BYTES || length > packet.size || packet.isEmpty()) return null

        val firstByte = packet[0].toInt() and 0xff
        val version = firstByte shr 4
        if (version != IPV4_VERSION) return null

        val headerLength = (firstByte and IPV4_IHL_MASK) * IPV4_WORD_BYTES
        if (headerLength < MIN_IPV4_HEADER_BYTES || length < headerLength) return null

        val protocol = when (packet[IPV4_PROTOCOL_OFFSET].toInt() and 0xff) {
            PROTOCOL_TCP -> Ipv4Protocol.Tcp
            PROTOCOL_UDP -> Ipv4Protocol.Udp
            PROTOCOL_ICMP -> Ipv4Protocol.Icmp
            else -> Ipv4Protocol.Other
        }
        val sourcePort = if (protocol.hasPorts && length >= headerLength + TRANSPORT_PORT_HEADER_BYTES) {
            packet.readUInt16(headerLength)
        } else {
            null
        }
        val destinationPort = if (protocol.hasPorts && length >= headerLength + TRANSPORT_PORT_HEADER_BYTES) {
            packet.readUInt16(headerLength + 2)
        } else {
            null
        }
        return Ipv4PacketSummary(
            protocol = protocol,
            sourceAddress = packet.readIpv4Address(IPV4_SOURCE_OFFSET),
            destinationAddress = packet.readIpv4Address(IPV4_DESTINATION_OFFSET),
            sourcePort = sourcePort,
            destinationPort = destinationPort,
        )
    }

    private val Ipv4Protocol.hasPorts: Boolean
        get() = this == Ipv4Protocol.Tcp || this == Ipv4Protocol.Udp

    private fun ByteArray.readUInt16(offset: Int): Int =
        ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)

    private fun ByteArray.readIpv4Address(offset: Int): String =
        (0 until IPV4_ADDRESS_BYTES).joinToString(".") { index -> (this[offset + index].toInt() and 0xff).toString() }

    private const val IPV4_VERSION = 4
    private const val IPV4_IHL_MASK = 0x0f
    private const val IPV4_WORD_BYTES = 4
    private const val MIN_IPV4_HEADER_BYTES = 20
    private const val IPV4_PROTOCOL_OFFSET = 9
    private const val IPV4_SOURCE_OFFSET = 12
    private const val IPV4_DESTINATION_OFFSET = 16
    private const val IPV4_ADDRESS_BYTES = 4
    private const val TRANSPORT_PORT_HEADER_BYTES = 4
    private const val PROTOCOL_ICMP = 1
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
}
