// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

internal class PacketSummaryHistory(private val limit: Int = DEFAULT_LIMIT) {
    private val summaries = ArrayDeque<PacketSummary>()
    private var paused: Boolean = false
    private var updatedAt: Long? = null

    @Synchronized
    fun add(summary: PacketSummary): Boolean {
        if (paused) return false
        summaries.addFirst(summary)
        while (summaries.size > limit) {
            summaries.removeLast()
        }
        updatedAt = summary.timestamp
        return true
    }

    @Synchronized
    fun clear(timestamp: Long = System.currentTimeMillis()) {
        summaries.clear()
        updatedAt = timestamp
    }

    @Synchronized
    fun setPaused(paused: Boolean, timestamp: Long = System.currentTimeMillis()) {
        if (this.paused == paused) return
        this.paused = paused
        updatedAt = timestamp
    }

    @Synchronized
    fun isPaused(): Boolean = paused

    @Synchronized
    fun lastUpdatedAt(): Long? = updatedAt

    @Synchronized
    fun newestFirst(): List<PacketSummary> = summaries.toList()

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}
