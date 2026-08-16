// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertEquals

class RootSocketSnapshotScannerTest {
    @Test
    fun parsesBoundedIpv4AndIpv6ProcSocketRows() {
        val sockets = parseRootSocketTables(
            """
                @@VIROUTEFS_SOCKET:tcp4
                  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                   0: 0100007F:C001 08080808:01BB 01 00000000:00000000 00:00000000 00000000 10123 0 456
                @@VIROUTEFS_SOCKET:udp6
                  sl  local_address                         remote_address                        st tx_queue rx_queue tr tm->when retrnsmt   uid  timeout inode
                   1: 00000000000000000000000001000000:14E9 00000000000000000000000000000000:0000 07 00000000:00000000 00:00000000 00000000 10456 0 789
            """.trimIndent(),
        )

        assertEquals(2, sockets.size)
        assertEquals("127.0.0.1", sockets[0].localAddress)
        assertEquals("8.8.8.8", sockets[0].remoteAddress)
        assertEquals(443, sockets[0].remotePort)
        assertEquals("ESTABLISHED", sockets[0].state)
        assertEquals(10_123, sockets[0].uid)
        assertEquals("0:0:0:0:0:0:0:1", sockets[1].localAddress)
        assertEquals(5_353, sockets[1].localPort)
        assertEquals("UNCONNECTED", sockets[1].state)
        assertEquals(10_456, sockets[1].uid)
    }

    @Test
    fun ignoresMalformedRowsAndUnknownMarkers() {
        val sockets = parseRootSocketTables(
            """
                @@VIROUTEFS_SOCKET:unknown
                0: broken row
                @@VIROUTEFS_SOCKET:tcp4
                0: 0100007F:ZZZZ 08080808:01BB 01 0:0 0:0 0 10123 0 456
            """.trimIndent(),
        )

        assertEquals(emptyList(), sockets)
    }
}
