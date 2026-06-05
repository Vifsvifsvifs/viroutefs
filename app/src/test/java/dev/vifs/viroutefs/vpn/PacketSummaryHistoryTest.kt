// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals

class PacketSummaryHistoryTest {
    @Test
    fun historyIsNewestFirstAndCappedAt50() {
        val history = PacketSummaryHistory()

        repeat(55) { index ->
            history.add(
                PacketSummary(
                    timestamp = index.toLong(),
                    protocol = Ipv4Protocol.Tcp,
                    srcIp = "10.0.0.$index",
                    srcPort = 1000 + index,
                    dstIp = "203.0.113.$index",
                    dstPort = 443,
                    packetSize = 40,
                ),
            )
        }

        val summaries = history.newestFirst()
        assertEquals(50, summaries.size)
        assertEquals(54L, summaries.first().timestamp)
        assertEquals(5L, summaries.last().timestamp)
    }
}
