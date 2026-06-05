// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless.protocol

import java.nio.charset.StandardCharsets

sealed interface VlessAddress {
    val type: Byte
    fun encodedBytes(): ByteArray

    class Ipv4(private val octets: ByteArray) : VlessAddress {
        init {
            require(octets.size == 4) { "invalid host" }
        }

        override val type: Byte = VlessProtocol.ADDRESS_TYPE_IPV4

        override fun encodedBytes(): ByteArray = octets.copyOf()

        override fun equals(other: Any?): Boolean = other is Ipv4 && octets.contentEquals(other.octets)

        override fun hashCode(): Int = octets.contentHashCode()
    }

    data class Domain(val value: String) : VlessAddress {
        private val domainBytes = value.toByteArray(StandardCharsets.UTF_8)

        init {
            require(value.isNotBlank()) { "invalid host" }
            require(domainBytes.size <= VlessProtocol.MAX_DOMAIN_LENGTH_BYTES) { "invalid host" }
        }

        override val type: Byte = VlessProtocol.ADDRESS_TYPE_DOMAIN

        override fun encodedBytes(): ByteArray = byteArrayOf(domainBytes.size.toByte()) + domainBytes
    }

    companion object {
        fun parse(host: String): VlessAddress {
            val trimmed = host.trim()
            require(trimmed.isNotBlank()) { "invalid host" }
            require(!trimmed.any { it.isISOControl() || it.isWhitespace() }) { "invalid host" }
            require(!trimmed.contains('/')) { "invalid host" }
            if (trimmed.contains(':')) {
                // TODO: Add local IPv6 literal encoding before enabling IPv6 VLESS request construction.
                throw IllegalArgumentException("unsupported address type")
            }

            parseIpv4(trimmed)?.let { return Ipv4(it) }
            require(!trimmed.all { it.isDigit() || it == '.' }) { "invalid host" }

            val domainBytes = trimmed.toByteArray(StandardCharsets.UTF_8)
            require(domainBytes.size <= VlessProtocol.MAX_DOMAIN_LENGTH_BYTES) { "invalid host" }
            return Domain(trimmed)
        }

        private fun parseIpv4(host: String): ByteArray? {
            val parts = host.split('.')
            if (parts.size != 4) return null
            val octets = ByteArray(4)
            for ((index, part) in parts.withIndex()) {
                if (part.isEmpty() || !part.all { it in '0'..'9' }) return null
                val value = part.toIntOrNull() ?: return null
                if (value !in 0..255) return null
                octets[index] = value.toByte()
            }
            return octets
        }
    }
}
