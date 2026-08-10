// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SingBoxOpenVpnStatusTest {
    @Test
    fun nativeOpenVpnErrorReplacesGenericTransportFailure() {
        val result = openVpnProfileTestFailure(
            transportFailure = "Connection reset",
            status = OpenVpnRuntimeStatusSnapshot(
                state = "error",
                stateText = "Authentication failed",
                error = "AUTH_FAILED password=do-not-show token=also-secret",
                connected = false,
            ),
            statusStreamError = null,
        )

        assertContains(result, "Authentication failed")
        assertContains(result, "AUTH_FAILED")
        assertContains(result, "password=<redacted>")
        assertContains(result, "token=<redacted>")
        assertFalse(result.contains("do-not-show"))
        assertFalse(result.contains("also-secret"))
    }

    @Test
    fun authenticationChallengeExplainsWhatOpenVpnIsWaitingFor() {
        val result = openVpnProfileTestFailure(
            transportFailure = "Connection closed",
            status = OpenVpnRuntimeStatusSnapshot(
                state = "auth_pending",
                stateText = "",
                error = "",
                challengeMessage = "Enter the one-time code",
                connected = false,
            ),
            statusStreamError = null,
        )

        assertEquals(
            "OpenVPN ожидает данные авторизации: Enter the one-time code",
            result,
        )
    }

    @Test
    fun nonOpenVpnFailureIsKeptWhenThereIsNoOpenVpnStatus() {
        val result = openVpnProfileTestFailure(
            transportFailure = "Connection reset",
            status = null,
            statusStreamError = null,
        )

        assertEquals("Connection reset", result)
    }
}
