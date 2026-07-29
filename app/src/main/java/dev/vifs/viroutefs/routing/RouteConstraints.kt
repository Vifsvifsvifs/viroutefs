// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

data class DestinationPortRange(
    val first: Int,
    val last: Int = first,
) {
    init {
        require(first in 1..65535) { "Начальный порт должен быть от 1 до 65535." }
        require(last in first..65535) { "Конечный порт должен быть не меньше начального и не больше 65535." }
    }

    fun contains(port: Int): Boolean = port in first..last

    fun toDisplayText(): String = if (first == last) "$first" else "$first-$last"

    fun toSingBoxRange(): String = "$first:$last"
}

enum class RouteTransport {
    Any,
    Tcp,
    Udp,
}

fun parseDestinationPortRanges(value: String): List<DestinationPortRange> {
    if (value.isBlank()) return emptyList()
    return value
        .split(Regex("[,;\\s]+"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .map { token ->
            val parts = token.split('-', limit = 2)
            val first = parts.first().toIntOrNull()
                ?: throw IllegalArgumentException("Порт «$token» не является числом.")
            val last = parts.getOrNull(1)?.toIntOrNull()
                ?: if (parts.size == 1) first else throw IllegalArgumentException("Диапазон «$token» заполнен не полностью.")
            DestinationPortRange(first, last)
        }
        .distinct()
        .sortedWith(compareBy(DestinationPortRange::first, DestinationPortRange::last))
}

fun List<DestinationPortRange>.toDisplayText(): String =
    joinToString(", ") { it.toDisplayText() }
