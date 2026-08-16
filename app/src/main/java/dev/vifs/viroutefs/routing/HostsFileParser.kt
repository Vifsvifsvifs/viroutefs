// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

data class ParsedHostsEntry(
    val hostname: String,
    val ipAddress: String,
)

data class HostsFileParseResult(
    val entries: List<ParsedHostsEntry>,
    val errors: List<String>,
)

/** Accepts both standard `IP hostname` and convenient `hostname IP` lines. */
fun parseHostsFileText(source: String): HostsFileParseResult {
    val entriesByHostname = linkedMapOf<String, ParsedHostsEntry>()
    val errors = mutableListOf<String>()
    source.lineSequence().forEachIndexed { index, rawLine ->
        val line = rawLine.substringBefore('#').trim()
        if (line.isBlank()) return@forEachIndexed
        val tokens = line
            .replace("->", " ")
            .replace('=', ' ')
            .split(Regex("\\s+"))
            .map(String::trim)
            .filter { it.isNotBlank() && it != "-" }
        val ipTokens = tokens.filter(::isHostsIpAddress)
        val hostTokens = tokens.filterNot(::isHostsIpAddress)
        when {
            ipTokens.size != 1 -> errors += "Строка ${index + 1}: нужен ровно один IP-адрес."
            hostTokens.isEmpty() -> errors += "Строка ${index + 1}: не указано имя хоста."
            hostTokens.any { !HOSTNAME_PATTERN.matches(it) } ->
                errors += "Строка ${index + 1}: некорректное имя хоста."
            else -> hostTokens.forEach { hostname ->
                val normalizedHostname = hostname.lowercase()
                entriesByHostname[normalizedHostname] = ParsedHostsEntry(
                    hostname = normalizedHostname,
                    ipAddress = ipTokens.single(),
                )
            }
        }
    }
    return HostsFileParseResult(entriesByHostname.values.toList(), errors)
}

private fun isHostsIpAddress(value: String): Boolean = '/' !in value && isValidIpOrCidr(value)

private val HOSTNAME_PATTERN = Regex(
    "(?i)^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)*[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.?$",
)
