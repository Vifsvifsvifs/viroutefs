// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlessTcpReachabilityTest {
    @Test
    fun resultModelDoesNotContainUuid() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val result = VlessTcpReachabilityResult(
            host = "example.com",
            port = 443,
            timestamp = 1L,
            state = VlessTcpReachabilityState.Reachable,
            message = "TCP connected; no UUID was sent.",
            elapsedMs = 10L,
        )

        assertFalse(result.toString().contains(uuid))
        assertFalse(result.displayMessage.contains(uuid))
        assertTrue(result.displayMessage.contains("reachable"))
    }

    @Test
    fun historyIsCappedAt20PerProfileAndDoesNotStoreUuid() = runTest {
        val file = File.createTempFile("vless-history", ".json").apply { delete() }
        val store = VlessTcpReachabilityHistoryStore(file)
        val uuid = "123e4567-e89b-12d3-a456-426614174000"

        (1..25).forEach { index ->
            store.add(
                VlessTcpReachabilityHistoryItem(
                    profileId = "profile-a",
                    profileNameSnapshot = "Lab VLESS",
                    host = "example.com",
                    port = 443,
                    timestamp = index.toLong(),
                    state = VlessTcpReachabilityState.Reachable,
                    message = "Connected without credentials $index",
                    elapsedMs = index.toLong(),
                ),
            )
        }
        store.add(
            VlessTcpReachabilityHistoryItem(
                profileId = "profile-b",
                profileNameSnapshot = "Other VLESS",
                host = "example.org",
                port = 8443,
                timestamp = 1L,
                state = VlessTcpReachabilityState.Timeout,
                message = "Timeout",
            ),
        )

        val profileA = store.recentForProfile("profile-a")
        val encoded = file.readText()

        assertEquals(20, profileA.size)
        assertEquals(25L, profileA.first().timestamp)
        assertEquals(6L, profileA.last().timestamp)
        assertFalse(encoded.contains(uuid))
        assertFalse(profileA.joinToString().contains(uuid))
    }

    @Test
    fun validationRejectsInvalidHostAndPort() {
        assertTrue(validateVlessTcpReachabilityTarget("", 443).any { it.contains("host") })
        assertTrue(validateVlessTcpReachabilityTarget("bad host.example", 443).any { it.contains("whitespace") })
        assertTrue(validateVlessTcpReachabilityTarget("example.com", 0).any { it.contains("1..65535") })
        assertTrue(validateVlessTcpReachabilityTarget("example.com", 65536).any { it.contains("1..65535") })
        assertTrue(validateVlessTcpReachabilityTarget("example.com", 443).isEmpty())
    }

    @Test
    fun uiTextSeparatesManualCheckFromVpnRuntime() {
        val combined = listOf(
            VLESS_RUNTIME_LIMITATION,
            VLESS_NO_HANDSHAKE_NOTICE,
            VLESS_TCP_REACHABILITY_NOTICE,
        ).joinToString(" ")

        assertTrue(combined.contains("VLESS handshake", ignoreCase = true))
        assertTrue(combined.contains("sing-box", ignoreCase = true))
        assertTrue(combined.contains("Xray-core", ignoreCase = true))
        assertTrue(combined.contains("routed separately", ignoreCase = true))
        assertTrue(combined.contains("UUID", ignoreCase = true))
    }
}
