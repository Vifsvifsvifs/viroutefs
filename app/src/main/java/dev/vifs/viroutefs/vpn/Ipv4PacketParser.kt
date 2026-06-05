// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

internal enum class Ipv4Protocol {
    Tcp,
    Udp,
    Icmp,
    Other,
}

internal data class PacketSummary(
    val timestamp: Long,
    val protocol: Ipv4Protocol,
    val srcIp: String,
    val srcPort: Int?,
    val dstIp: String,
    val dstPort: Int?,
    val packetSize: Int,
)

internal object Ipv4PacketParser {
    fun parse(packet: ByteArray, length: Int): Ipv4Protocol? = parseSummary(packet, length)?.protocol

    fun parseSummary(packet: ByteArray, length: Int, timestamp: Long = System.currentTimeMillis()): PacketSummary? {
        if (length < MIN_IPV4_HEADER_BYTES || length > packet.size || packet.isEmpty()) return null

        val firstByte = packet[0].toInt() and 0xff
        val version = firstByte shr 4
        if (version != IPV4_VERSION) return null

        val headerLength = (firstByte and IPV4_IHL_MASK) * IPV4_WORD_BYTES
        if (headerLength < MIN_IPV4_HEADER_BYTES || length < headerLength) return null

        val totalLength = readUnsignedShort(packet, IPV4_TOTAL_LENGTH_OFFSET)
        if (totalLength < headerLength || length < totalLength) return null

        val protocol = when (packet[IPV4_PROTOCOL_OFFSET].toInt() and 0xff) {
            PROTOCOL_TCP -> Ipv4Protocol.Tcp
            PROTOCOL_UDP -> Ipv4Protocol.Udp
            PROTOCOL_ICMP -> Ipv4Protocol.Icmp
            else -> Ipv4Protocol.Other
        }

        val (srcPort, dstPort) = when (protocol) {
            Ipv4Protocol.Tcp, Ipv4Protocol.Udp -> {
                if (totalLength < headerLength + MIN_TRANSPORT_PORT_BYTES || length < headerLength + MIN_TRANSPORT_PORT_BYTES) {
                    return null
                }
                readUnsignedShort(packet, headerLength) to readUnsignedShort(packet, headerLength + 2)
            }
            Ipv4Protocol.Icmp, Ipv4Protocol.Other -> null to null
        }

        return PacketSummary(
            timestamp = timestamp,
            protocol = protocol,
            srcIp = readIpv4Address(packet, IPV4_SRC_ADDRESS_OFFSET),
            srcPort = srcPort,
            dstIp = readIpv4Address(packet, IPV4_DST_ADDRESS_OFFSET),
            dstPort = dstPort,
            packetSize = length,
        )
    }

    private fun readIpv4Address(packet: ByteArray, offset: Int): String = listOf(
        packet[offset].toInt() and 0xff,
        packet[offset + 1].toInt() and 0xff,
        packet[offset + 2].toInt() and 0xff,
        packet[offset + 3].toInt() and 0xff,
    ).joinToString(".")

    private fun readUnsignedShort(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xff) shl 8) or (packet[offset + 1].toInt() and 0xff)

    private const val IPV4_VERSION = 4
    private const val IPV4_IHL_MASK = 0x0f
    private const val IPV4_WORD_BYTES = 4
    private const val MIN_IPV4_HEADER_BYTES = 20
    private const val MIN_TRANSPORT_PORT_BYTES = 4
    private const val IPV4_TOTAL_LENGTH_OFFSET = 2
    private const val IPV4_PROTOCOL_OFFSET = 9
    private const val IPV4_SRC_ADDRESS_OFFSET = 12
    private const val IPV4_DST_ADDRESS_OFFSET = 16
    private const val PROTOCOL_ICMP = 1
    private const val PROTOCOL_TCP = 6
    private const val PROTOCOL_UDP = 17
}
