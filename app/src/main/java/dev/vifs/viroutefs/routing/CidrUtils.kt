package dev.vifs.viroutefs.routing

fun isValidCidr(text: String): Boolean {
    val parts = text.split('/')
    if (parts.size != 2) return false

    val octets = parts[0].split('.')
    if (octets.size != 4) return false

    val prefix = parts[1].toIntOrNull() ?: return false
    if (prefix !in 0..32) return false

    return octets.all { octet ->
        octet.toIntOrNull()?.let { value -> value in 0..255 } == true
    }
}
