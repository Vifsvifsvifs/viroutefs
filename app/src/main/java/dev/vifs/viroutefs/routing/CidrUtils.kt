package dev.vifs.viroutefs.routing

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

fun isValidCidr(text: String): Boolean {
    val parts = text.trim().split('/')
    if (parts.size != 2) return false
    val address = parseNumericIpAddress(parts[0]) ?: return false
    val prefix = parts[1].toIntOrNull() ?: return false
    return when (address) {
        is Inet4Address -> prefix in 0..32
        is Inet6Address -> prefix in 0..128
        else -> false
    }
}

fun isValidIpAddress(text: String): Boolean = parseNumericIpAddress(text.trim()) != null

fun ipAddressInCidr(addressText: String, cidrText: String): Boolean {
    val address = parseNumericIpAddress(addressText.trim()) ?: return false
    val parts = cidrText.trim().split('/')
    if (parts.size != 2) return false
    val network = parseNumericIpAddress(parts[0]) ?: return false
    if (address.address.size != network.address.size) return false
    val prefix = parts[1].toIntOrNull() ?: return false
    val maxBits = address.address.size * 8
    if (prefix !in 0..maxBits) return false

    val fullBytes = prefix / 8
    val remainingBits = prefix % 8
    for (index in 0 until fullBytes) {
        if (address.address[index] != network.address[index]) return false
    }
    if (remainingBits == 0) return true
    val mask = (0xFF shl (8 - remainingBits)) and 0xFF
    return (address.address[fullBytes].toInt() and mask) ==
        (network.address[fullBytes].toInt() and mask)
}

private fun parseNumericIpAddress(value: String): InetAddress? {
    val candidate = value
        .trim()
        .removePrefix("[")
        .removeSuffix("]")
        .substringBefore('%')
    if (candidate.isBlank() || ('.' !in candidate && ':' !in candidate)) return null
    if ('.' in candidate && ':' !in candidate && !candidate.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) {
        return null
    }
    return runCatching { InetAddress.getByName(candidate) }.getOrNull()
}
