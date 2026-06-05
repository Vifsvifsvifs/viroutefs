// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Socks5ConnectProtocolTest {
    @Test
    fun connectRequestUsesDomainAddressTypeForDomainTarget() {
        val request = encodeSocks5ConnectRequest("example.com", 443)

        assertContentEquals(
            byteArrayOf(0x05, 0x01, 0x00, 0x03, 11) + "example.com".encodeToByteArray() + byteArrayOf(0x01, 0xBB.toByte()),
            request,
        )
    }

    @Test
    fun connectRequestSupportsIpv4TargetWithoutDnsLookup() {
        val request = encodeSocks5ConnectRequest("192.0.2.10", 1080)

        assertContentEquals(byteArrayOf(0x05, 0x01, 0x00, 0x01, 192.toByte(), 0, 2, 10, 0x04, 0x38), request)
    }

    @Test
    fun successfulConnectResponseParsesAsSuccess() {
        val result = parseSocks5ConnectResponse(byteArrayOf(0x05, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0x01, 0xBB.toByte()))

        assertEquals(Socks5DiagnosticState.ConnectSuccess, result.state)
        assertEquals(Socks5DiagnosticTestType.Connect, result.testType)
    }

    @Test
    fun rejectedConnectResponseParsesAsProxyRejection() {
        val result = parseSocks5ConnectResponse(byteArrayOf(0x05, 0x02, 0x00, 0x01, 127, 0, 0, 1, 0, 0))

        assertEquals(Socks5DiagnosticState.ConnectRejectedByProxy, result.state)
    }

    @Test
    fun targetFailureConnectResponseParsesAsTargetUnreachable() {
        val result = parseSocks5ConnectResponse(byteArrayOf(0x05, 0x05, 0x00, 0x01, 127, 0, 0, 1, 0, 0))

        assertEquals(Socks5DiagnosticState.TargetUnreachable, result.state)
    }

    @Test
    fun invalidConnectResponseIsReported() {
        val result = parseSocks5ConnectResponse(byteArrayOf(0x04, 0x00, 0x00, 0x01, 127, 0, 0, 1, 0, 0))

        assertEquals(Socks5DiagnosticState.InvalidSocks5Response, result.state)
    }

    @Test
    fun historySerializationDoesNotIncludePasswordsAndLimitsTwentyPerProfile() = runTest {
        val tempDir = createTempDir(prefix = "socks5-history-test")
        try {
            val store = Socks5TestHistoryStore(tempDir.resolve(Socks5TestHistoryStore.FILENAME))
            (0 until 25).forEach { index ->
                store.add(
                    Socks5TestHistoryItem(
                        profileId = if (index < 22) "profile-a" else "profile-b",
                        profileNameSnapshot = "Office",
                        testType = Socks5DiagnosticTestType.Connect,
                        targetHost = "example.com",
                        targetPort = 443,
                        timestamp = index.toLong(),
                        state = Socks5DiagnosticState.ConnectSuccess,
                        message = "ok secret=hidden password=hidden",
                        elapsedMs = 10,
                    ),
                )
            }
            val profileA = store.recentForProfile("profile-a")
            val profileB = store.recentForProfile("profile-b")
            val json = tempDir.resolve(Socks5TestHistoryStore.FILENAME).readText()

            assertEquals(20, profileA.size)
            assertEquals(3, profileB.size)
            assertFalse(json.contains("hidden"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
