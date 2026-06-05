// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import dev.vifs.viroutefs.vless.protocol.buildVlessTcpRequest
import kotlinx.coroutines.test.runTest
import java.io.File
import java.net.ServerSocket
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VlessProtocolProbeTest {
    @Test
    fun tlsOrRealityProfileReturnsUnsupportedTransport() {
        val profile = plainProfile().copy(securityMode = VlessSecurityMode.TLS)

        val result = VlessProtocolProber().probeBlocking(profile, "example.com", 80)

        assertEquals(VlessProtocolProbeState.UnsupportedSecurityMode, result.state)
        assertEquals(VLESS_TLS_REALITY_UNSUPPORTED_MESSAGE, result.message)
    }

    @Test
    fun invalidTargetIsRejectedBeforeConnect() {
        val result = VlessProtocolProber().probeBlocking(plainProfile(), "bad target.example", 80)

        assertEquals(VlessProtocolProbeState.ValidationError, result.state)
        assertTrue(result.message.contains("target host", ignoreCase = true))
    }

    @Test
    fun historyIsCappedAt20AndDoesNotContainUuidOrRawFrame() = runTest {
        val file = File.createTempFile("vless-probe-history", ".json").apply { delete() }
        val store = VlessProtocolProbeHistoryStore(file)
        val uuid = plainProfile().uuid

        (1..25).forEach { index ->
            store.add(
                VlessProtocolProbeHistoryItem(
                    profileId = "profile-a",
                    profileNameSnapshot = "Lab VLESS",
                    serverHost = "vless.example",
                    serverPort = 443,
                    targetHost = "example.com",
                    targetPort = 80,
                    timestamp = index.toLong(),
                    state = VlessProtocolProbeState.ServerKeptConnectionBriefly,
                    message = "Probe completed for uuid=$uuid",
                    elapsedMs = index.toLong(),
                ),
            )
        }

        val profileHistory = store.recentForProfile("profile-a")
        val encoded = file.readText()

        assertEquals(20, profileHistory.size)
        assertEquals(25L, profileHistory.first().timestamp)
        assertEquals(6L, profileHistory.last().timestamp)
        assertFalse(encoded.contains(uuid))
        assertFalse(profileHistory.joinToString().contains(uuid))
        assertFalse(encoded.contains("raw frame", ignoreCase = true))
    }

    @Test
    fun resultMessagesDoNotContainUuid() {
        val uuid = plainProfile().uuid
        val result = VlessProtocolProbeResult(
            serverHost = "vless.example",
            serverPort = 443,
            targetHost = "example.com",
            targetPort = 80,
            timestamp = 1L,
            state = VlessProtocolProbeState.ValidationError,
            message = "uuid=$uuid failed validation",
        )

        assertFalse(result.displayMessage.contains(uuid))
        assertTrue(result.displayMessage.contains("uuid=***"))
    }

    @Test
    fun probeUsesVlessRequestBuilderForMinimalTcpRequestFrame() {
        val profile = plainProfile()
        val targetHost = "example.com"
        val targetPort = 80
        val expectedFrame = buildVlessTcpRequest(profile.uuid, targetHost, targetPort)
        lateinit var receivedFrame: ByteArray
        ServerSocket(0).use { server ->
            val serverThread = thread(start = true) {
                server.accept().use { socket ->
                    receivedFrame = socket.getInputStream().readNBytes(expectedFrame.size)
                    Thread.sleep(250)
                }
            }

            val result = VlessProtocolProber(waitTimeoutMs = 100).probeBlocking(
                profile.copy(host = "127.0.0.1", port = server.localPort),
                targetHost,
                targetPort,
            )
            serverThread.join(1_000)

            assertEquals(VlessProtocolProbeState.ServerKeptConnectionBriefly, result.state)
            assertTrue(result.steps.contains(VlessProtocolProbeState.TcpConnected))
            assertTrue(result.steps.contains(VlessProtocolProbeState.VlessRequestSent))
            assertContentEquals(expectedFrame, receivedFrame)
        }
    }

    private fun plainProfile(): VlessProfileConfig = VlessProfileConfig(
        name = "Lab VLESS",
        host = "vless.example",
        port = 443,
        uuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000").toString(),
        securityMode = VlessSecurityMode.NONE,
    )
}
