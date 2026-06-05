// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

internal class PacketSummaryHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val summaries = ArrayDeque<PacketSummary>()

    @Synchronized
    fun add(summary: PacketSummary) {
        summaries.addFirst(summary)
        while (summaries.size > limit) {
            summaries.removeLast()
        }
    }

    @Synchronized
    fun clear() {
        summaries.clear()
    }

    @Synchronized
    fun newestFirst(): List<PacketSummary> = summaries.toList()

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
