package dev.vifs.viroutefs.routing

fun DnsPolicy.humanSummary(): String = buildString {
    append(name)
    append(" • ")
    append(type.label)
    serverText?.takeIf { it.isNotBlank() }?.let { append(" • $it") }
    resolveThroughProfileId?.let { append(" • через профиль $it") }
}
