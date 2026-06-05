// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketSummaryHistoryTest {
    @Test
    fun historyIsNewestFirstAndCappedAt50() {
        val history = PacketSummaryHistory()

        repeat(55) { index ->
            history.add(summary(index))
        }

        val summaries = history.newestFirst()
        assertEquals(50, summaries.size)
        assertEquals(54L, summaries.first().timestamp)
        assertEquals(5L, summaries.last().timestamp)
    }

    @Test
    fun clearPacketListEmptiesSummaries() {
        val history = PacketSummaryHistory()
        history.add(summary(1))
        history.add(summary(2))

        history.clear(timestamp = 3)

        assertEquals(emptyList(), history.newestFirst())
        assertEquals(3L, history.lastUpdatedAt())
    }

    @Test
    fun pausedInspectorDoesNotAppendAndResumeAppendsAgain() {
        val history = PacketSummaryHistory()
        assertTrue(history.add(summary(1)))

        history.setPaused(true, timestamp = 2)
        assertTrue(history.isPaused())
        assertFalse(history.add(summary(3)))
        assertEquals(listOf(1L), history.newestFirst().map { it.timestamp })

        history.setPaused(false, timestamp = 4)
        assertFalse(history.isPaused())
        assertTrue(history.add(summary(5)))
        assertEquals(listOf(5L, 1L), history.newestFirst().map { it.timestamp })
    }

    @Test
    fun packetSummaryStoresMetadataOnly() {
        val summary = summary(1)
        val encoded = VpnServiceController.encodePacketSummary(summary)

        assertEquals(7, encoded.split("|").size)
        assertFalse(encoded.contains("secret", ignoreCase = true))
        assertFalse(encoded.contains("payload", ignoreCase = true))
        assertEquals(summary, VpnServiceController.decodePacketSummaries(arrayListOf(encoded)).single())
    }

    private fun summary(index: Int): PacketSummary = PacketSummary(
        timestamp = index.toLong(),
        protocol = Ipv4Protocol.Tcp,
        srcIp = "10.0.0.$index",
        srcPort = 1000 + index,
        dstIp = "203.0.113.$index",
        dstPort = 443,
        packetSize = 40,
    )
}
