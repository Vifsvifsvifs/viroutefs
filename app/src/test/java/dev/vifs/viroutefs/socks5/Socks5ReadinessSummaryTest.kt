// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Socks5ReadinessSummaryTest {
    @Test
    fun summaryChoosesLatestConnectSuccessForTargetLine() {
        val summary = deriveSocks5ReadinessSummary(
            listOf(
                item(
                    timestamp = 100L,
                    testType = Socks5DiagnosticTestType.Connect,
                    state = Socks5DiagnosticState.ConnectSuccess,
                    targetHost = "old.example",
                    targetPort = 443,
                ),
                item(
                    timestamp = 300L,
                    testType = Socks5DiagnosticTestType.Connect,
                    state = Socks5DiagnosticState.ConnectSuccess,
                    targetHost = "new.example",
                    targetPort = 8443,
                ),
                item(
                    timestamp = 200L,
                    testType = Socks5DiagnosticTestType.Handshake,
                    state = Socks5DiagnosticState.HandshakeReachable,
                ),
            ),
        )

        assertEquals(Socks5ReadinessState.ConnectSuccess, summary.state)
        assertEquals("CONNECT OK", summary.badgeLabel)
        assertEquals("Last CONNECT OK: new.example:8443", summary.lastConnectSuccessLine)
        assertEquals("new.example", summary.lastTargetHost)
        assertEquals(8443, summary.lastTargetPort)
    }

    @Test
    fun summaryHandlesLastFailure() {
        val summary = deriveSocks5ReadinessSummary(
            listOf(
                item(
                    timestamp = 100L,
                    testType = Socks5DiagnosticTestType.Connect,
                    state = Socks5DiagnosticState.ConnectSuccess,
                    targetHost = "example.com",
                    targetPort = 443,
                ),
                item(
                    timestamp = 400L,
                    testType = Socks5DiagnosticTestType.Connect,
                    state = Socks5DiagnosticState.Timeout,
                    targetHost = "example.com",
                    targetPort = 443,
                    message = "password=hunter2 timed out",
                ),
            ),
        )

        assertEquals(Socks5ReadinessState.LastTestFailed, summary.state)
        assertEquals("Last test failed", summary.badgeLabel)
        assertTrue(summary.routeExplanationLine.startsWith("Last manual SOCKS5 test failed:"))
        assertFalse(summary.userSafeMessage.contains("hunter2"))
        assertTrue(summary.userSafeMessage.contains("password=***"))
    }

    @Test
    fun summaryHandlesNoHistoryAsNotTested() {
        val summary = deriveSocks5ReadinessSummary(emptyList())

        assertEquals(Socks5ReadinessState.NotTested, summary.state)
        assertEquals("Not tested", summary.badgeLabel)
        assertEquals("SOCKS5 profile has not been tested yet.", summary.routeExplanationLine)
    }

    @Test
    fun summaryDoesNotExposePasswordLikeValues() {
        val summary = deriveSocks5ReadinessSummary(
            listOf(
                item(
                    timestamp = 100L,
                    testType = Socks5DiagnosticTestType.Handshake,
                    state = Socks5DiagnosticState.AuthenticationRejected,
                    message = "auth failed pass=supersecret secret=token",
                ),
            ),
        )

        assertFalse(summary.userSafeMessage.contains("supersecret"))
        assertFalse(summary.userSafeMessage.contains("token"))
        assertTrue(summary.userSafeMessage.contains("pass=***"))
        assertTrue(summary.userSafeMessage.contains("secret=***"))
    }

    @Test
    fun summaryKeepsLastHandshakeAndConnectResults() {
        val summary = deriveSocks5ReadinessSummary(
            listOf(
                item(timestamp = 100L, testType = Socks5DiagnosticTestType.Handshake, state = Socks5DiagnosticState.HandshakeReachable),
                item(timestamp = 200L, testType = Socks5DiagnosticTestType.Connect, state = Socks5DiagnosticState.ConnectSuccess, targetHost = "example.org", targetPort = 443),
            ),
        )

        assertNotNull(summary.lastHandshake)
        assertNotNull(summary.lastConnect)
        assertEquals("example.org", summary.lastConnect?.targetHost)
    }

    private fun item(
        timestamp: Long,
        testType: Socks5DiagnosticTestType,
        state: Socks5DiagnosticState,
        targetHost: String? = null,
        targetPort: Int? = null,
        message: String = state.label,
    ) = Socks5TestHistoryItem(
        profileId = "socks5-1",
        profileNameSnapshot = "SOCKS5",
        testType = testType,
        targetHost = targetHost,
        targetPort = targetPort,
        timestamp = timestamp,
        state = state,
        message = message,
        elapsedMs = 42L,
    )
}
