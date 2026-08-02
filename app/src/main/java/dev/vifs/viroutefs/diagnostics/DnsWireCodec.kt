package dev.vifs.viroutefs.diagnostics

import java.io.ByteArrayOutputStream
import java.net.InetAddress

internal object DnsWireCodec {
    private const val HEADER_SIZE = 12
    private const val CLASS_IN = 1

    data class Response(
        val addresses: List<String>,
        val responseCode: Int,
        val truncated: Boolean,
        val answerCount: Int,
        val sizeBytes: Int,
    )

    fun buildQuery(
        domain: String,
        recordType: String,
        transactionId: Int,
    ): ByteArray {
        val typeCode = recordType.toTypeCode()
        require(transactionId in 0..0xffff) { "DNS transaction ID must fit into 16 bits." }

        return ByteArrayOutputStream().apply {
            writeUnsignedShort(transactionId)
            writeUnsignedShort(0x0100) // recursion desired
            writeUnsignedShort(1) // one question
            writeUnsignedShort(0)
            writeUnsignedShort(0)
            writeUnsignedShort(0)
            domain.split('.').forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                require(bytes.size in 1..63) { "Invalid DNS label length." }
                write(bytes.size)
                write(bytes)
            }
            write(0)
            writeUnsignedShort(typeCode)
            writeUnsignedShort(CLASS_IN)
        }.toByteArray()
    }

    fun parseResponse(
        message: ByteArray,
        expectedTransactionId: Int,
        recordType: String,
    ): Response {
        if (message.size < HEADER_SIZE) throw DnsProtocolException("DNS response is shorter than its header.")
        val responseId = message.unsignedShort(0)
        if (responseId != expectedTransactionId) {
            throw DnsProtocolException("DNS response transaction ID does not match the request.")
        }

        val flags = message.unsignedShort(2)
        if (flags and 0x8000 == 0) throw DnsProtocolException("Received a DNS query instead of a response.")
        val questionCount = message.unsignedShort(4)
        val answerCount = message.unsignedShort(6)
        val expectedType = recordType.toTypeCode()
        var offset = HEADER_SIZE

        repeat(questionCount) {
            offset = message.skipName(offset)
            message.requireAvailable(offset, 4)
            offset += 4
        }

        val addresses = mutableListOf<String>()
        repeat(answerCount) {
            offset = message.skipName(offset)
            message.requireAvailable(offset, 10)
            val type = message.unsignedShort(offset)
            val dnsClass = message.unsignedShort(offset + 2)
            val dataLength = message.unsignedShort(offset + 8)
            offset += 10
            message.requireAvailable(offset, dataLength)

            val expectedLength = if (expectedType == 1) 4 else 16
            if (dnsClass == CLASS_IN && type == expectedType && dataLength == expectedLength) {
                val addressBytes = message.copyOfRange(offset, offset + dataLength)
                addresses += InetAddress.getByAddress(addressBytes).hostAddress.orEmpty()
            }
            offset += dataLength
        }

        return Response(
            addresses = addresses.distinct(),
            responseCode = flags and 0x000f,
            truncated = flags and 0x0200 != 0,
            answerCount = answerCount,
            sizeBytes = message.size,
        )
    }

    private fun String.toTypeCode(): Int = when (uppercase()) {
        "A" -> 1
        "AAAA" -> 28
        else -> throw IllegalArgumentException("Unsupported DNS record type: $this")
    }

    private fun ByteArray.skipName(start: Int): Int {
        var offset = start
        var labels = 0
        while (true) {
            requireAvailable(offset, 1)
            val length = this[offset].toInt() and 0xff
            when {
                length == 0 -> return offset + 1
                length and 0xc0 == 0xc0 -> {
                    requireAvailable(offset, 2)
                    return offset + 2
                }
                length > 63 -> throw DnsProtocolException("DNS name contains an invalid label length.")
                else -> {
                    requireAvailable(offset + 1, length)
                    offset += length + 1
                    labels += 1
                    if (labels > 127) throw DnsProtocolException("DNS name contains too many labels.")
                }
            }
        }
    }

    private fun ByteArray.unsignedShort(offset: Int): Int {
        requireAvailable(offset, 2)
        return ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)
    }

    private fun ByteArray.requireAvailable(offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset > size - length) {
            throw DnsProtocolException("DNS response ended unexpectedly.")
        }
    }

    private fun ByteArrayOutputStream.writeUnsignedShort(value: Int) {
        write((value ushr 8) and 0xff)
        write(value and 0xff)
    }
}

internal class DnsProtocolException(message: String) : Exception(message)
