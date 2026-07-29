package dev.vifs.viroutefs.routing

fun DnsPolicy.humanSummary(): String = buildString {
    append(name)
    append(" • ")
    append(type.label)
    orderedServers().takeIf { it.isNotEmpty() }?.let { servers ->
        append(" • ")
        append(servers.joinToString(" → ") { it.address })
    }
    resolveThroughProfileId?.let { append(" • через профиль $it") }
}
