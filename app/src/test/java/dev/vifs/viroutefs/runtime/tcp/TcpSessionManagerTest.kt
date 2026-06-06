// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.runtime.tcp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TcpSessionManagerTest {
    @Test
    fun createSessionStoresNewMetadata() {
        val manager = TcpSessionManager()
        val session = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_000,
            destinationIp = "203.0.113.10",
            destinationPort = 443,
            now = 1_000L,
            id = TcpSessionId("tcp-test-1"),
        )

        assertEquals(TcpSessionId("tcp-test-1"), session.id)
        assertEquals("10.250.0.2", session.sourceIp)
        assertEquals(41_000, session.sourcePort)
        assertEquals("203.0.113.10", session.destinationIp)
        assertEquals(443, session.destinationPort)
        assertEquals(1_000L, session.createdAt)
        assertEquals(1_000L, session.lastActivityAt)
        assertEquals(0L, session.bytesIn)
        assertEquals(0L, session.bytesOut)
        assertEquals(TcpSessionState.New, session.state)
        assertEquals(1, manager.stateStats().count(TcpSessionState.New))
    }

    @Test
    fun lookupSessionReturnsStoredMetadata() {
        val manager = TcpSessionManager()
        val created = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_001,
            destinationIp = "198.51.100.5",
            destinationPort = 80,
            now = 2_000L,
            id = TcpSessionId("tcp-test-lookup"),
        )

        assertEquals(created, manager.lookupSession(TcpSessionId("tcp-test-lookup")))
        assertNull(manager.lookupSession(TcpSessionId("missing")))
    }

    @Test
    fun closeSessionMarksSessionClosedWithoutRemovingIt() {
        val manager = TcpSessionManager()
        val session = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_002,
            destinationIp = "203.0.113.20",
            destinationPort = 443,
            now = 3_000L,
            id = TcpSessionId("tcp-test-close"),
        )

        val closed = manager.closeSession(session.id, now = 3_500L)

        requireNotNull(closed)
        assertEquals(TcpSessionState.Closed, closed.state)
        assertEquals(3_500L, closed.lastActivityAt)
        assertEquals(closed, manager.lookupSession(session.id))
        assertEquals(0, manager.stateStats().activeSessions)
    }

    @Test
    fun cleanupIdleSessionRemovesExpiredSessions() {
        val manager = TcpSessionManager()
        val idle = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_003,
            destinationIp = "203.0.113.30",
            destinationPort = 443,
            now = 1_000L,
            id = TcpSessionId("tcp-idle"),
        )
        val fresh = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_004,
            destinationIp = "203.0.113.31",
            destinationPort = 443,
            now = 2_900L,
            id = TcpSessionId("tcp-fresh"),
        )

        val removed = manager.cleanupIdleSessions(idleTimeoutMillis = 1_000L, now = 3_000L)

        assertEquals(listOf(idle), removed)
        assertNull(manager.lookupSession(idle.id))
        assertEquals(fresh, manager.lookupSession(fresh.id))
    }

    @Test
    fun countersUpdateBytesAndActivityOnly() {
        val manager = TcpSessionManager()
        val session = manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_005,
            destinationIp = "203.0.113.40",
            destinationPort = 443,
            now = 4_000L,
            id = TcpSessionId("tcp-test-counters"),
        )

        val updated = manager.updateCounters(session.id, bytesInDelta = 128L, bytesOutDelta = 256L, now = 4_100L)

        requireNotNull(updated)
        assertEquals(128L, updated.bytesIn)
        assertEquals(256L, updated.bytesOut)
        assertEquals(4_100L, updated.lastActivityAt)
        assertEquals(TcpSessionState.New, updated.state)
    }

    @Test
    fun sessionManagerPerformsNoNetworking() {
        val manager = TcpSessionManager()
        val sourceRoot = listOf(
            java.io.File("src/main/java/dev/vifs/viroutefs/runtime/tcp"),
            java.io.File("app/src/main/java/dev/vifs/viroutefs/runtime/tcp"),
        ).first { it.exists() }
        val sourceFiles = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.name to it.readText() }

        manager.createSession(
            sourceIp = "10.250.0.2",
            sourcePort = 41_006,
            destinationIp = "203.0.113.50",
            destinationPort = 443,
            now = 5_000L,
            id = TcpSessionId("tcp-no-network"),
        )

        assertTrue(manager.snapshot().isNotEmpty())
        assertFalse(sourceFiles.getValue("TcpSessionManager.kt").contains("Socket"))
        assertFalse(sourceFiles.getValue("TcpSessionManager.kt").contains("Datagram"))
        assertFalse(sourceFiles.getValue("TcpBridge.kt").contains("java.net"))
    }
}
