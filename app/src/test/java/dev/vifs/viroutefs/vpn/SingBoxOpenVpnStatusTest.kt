// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingBoxOpenVpnStatusTest {
    @Test
    fun connectionProbeNoiseDoesNotHideNativeSessionFailure() {
        assertFalse(
            isOpenVpnSessionDiagnostic(
                "router: pre-match: forward icmp connection via outbound/openvpn-client[office]",
            ),
        )
        assertTrue(
            isOpenVpnSessionDiagnostic(
                "endpoint/openvpn-client[office]: session with 10.0.0.1 over tcp failed: openvpn tls handshake: EOF",
            ),
        )
    }

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

    @Test
    fun runtimeLogExplainsFailureWhileEndpointIsStillConnecting() {
        val result = openVpnProfileTestFailure(
            transportFailure = "Connection reset",
            status = OpenVpnRuntimeStatusSnapshot(
                state = "connecting",
                stateText = "Connecting",
                error = "",
                connected = false,
            ),
            statusStreamError = null,
            runtimeDiagnostic = "\u001B[31mopenvpn-client[office]: TLS handshake failed\u001B[0m",
        )

        assertContains(result, "Connecting")
        assertContains(result, "TLS handshake failed")
        assertContains(result, "Connection reset")
        assertFalse(result.contains("\u001B["))
    }
}
