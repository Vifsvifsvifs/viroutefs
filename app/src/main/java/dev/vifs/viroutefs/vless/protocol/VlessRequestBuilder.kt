// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless.protocol

import java.io.ByteArrayOutputStream
import java.util.UUID

private const val MAX_PORT = 65535

data class VlessTcpRequest(
    val uuid: UUID,
    val destination: VlessAddress,
    val port: Int,
)

data class VlessFrameDebugInfo(
    val lengthBytes: Int,
    val command: VlessCommand,
    val addressType: Byte,
    val uuidBytes: String = "16 bytes redacted",
) {
    fun safeSummary(): String = "VLESS TCP request frame: length=$lengthBytes bytes, command=${command.name}, addressType=0x${(addressType.toInt() and 0xff).toString(16).padStart(2, '0')}, uuid=$uuidBytes"
}

fun buildVlessTcpRequest(
    uuid: String,
    targetHost: String,
    targetPort: Int,
): ByteArray {
    val parsedUuid = parseUuid(uuid)
    val destination = VlessAddress.parse(targetHost)
    return buildVlessTcpRequest(VlessTcpRequest(parsedUuid, destination, targetPort))
}

fun buildVlessTcpRequest(request: VlessTcpRequest): ByteArray {
    require(request.port in 1..MAX_PORT) { "invalid port" }

    val addressBytes = request.destination.encodedBytes()
    val output = ByteArrayOutputStream(
        1 + VlessProtocol.UUID_LENGTH_BYTES + 1 + 1 + 2 + 1 + addressBytes.size,
    )
    output.write(VlessProtocol.VERSION.toInt())
    output.write(request.uuid.toRawBytes())
    output.write(VlessProtocol.ADDONS_LENGTH_NONE.toInt())
    output.write(VlessCommand.Tcp.wireValue.toInt())
    output.write((request.port ushr 8) and 0xff)
    output.write(request.port and 0xff)
    output.write(request.destination.type.toInt())
    output.write(addressBytes)
    return output.toByteArray()
}

fun describeVlessTcpRequestFrame(frame: ByteArray): VlessFrameDebugInfo {
    require(frame.size >= 22) { "invalid VLESS frame" }
    val command = VlessCommand.entries.firstOrNull { it.wireValue == frame[18] }
        ?: throw IllegalArgumentException("unsupported command")
    return VlessFrameDebugInfo(
        lengthBytes = frame.size,
        command = command,
        addressType = frame[21],
    )
}

fun parseVlessUuidBytes(uuid: String): ByteArray = parseUuid(uuid).toRawBytes()

private fun parseUuid(uuid: String): UUID = try {
    UUID.fromString(uuid.trim())
} catch (_: IllegalArgumentException) {
    throw IllegalArgumentException("invalid UUID")
}

private fun UUID.toRawBytes(): ByteArray {
    val output = ByteArray(VlessProtocol.UUID_LENGTH_BYTES)
    var most = mostSignificantBits
    var least = leastSignificantBits
    for (index in 7 downTo 0) {
        output[index] = (most and 0xff).toByte()
        most = most ushr 8
    }
    for (index in 15 downTo 8) {
        output[index] = (least and 0xff).toByte()
        least = least ushr 8
    }
    return output
}
