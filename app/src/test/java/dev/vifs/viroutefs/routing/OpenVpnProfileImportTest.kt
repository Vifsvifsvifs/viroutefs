// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenVpnProfileImportTest {
    @Test
    fun importsCommonInlineClientProfile() {
        val result = importOpenVpnProfile(
            """
            client
            proto udp
            remote vpn.example.com 1194
            data-ciphers AES-256-GCM:AES-128-GCM
            auth SHA256
            remote-cert-tls server
            <ca>
            -----BEGIN CERTIFICATE-----
            test-ca
            -----END CERTIFICATE-----
            </ca>
            """.trimIndent(),
        )

        val root = JSONObject(result.optionsJson)
        assertEquals("openvpn-client", root.getString("type"))
        assertEquals("vpn.example.com", root.getString("server"))
        assertEquals(1194, root.getInt("server_port"))
        assertEquals("udp", root.getString("network"))
        assertEquals("server", root.getJSONObject("tls").getString("remote_certificate_tls"))
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun preservesMultipleRemotesAndWarnsAboutUnknownDirectives() {
        val result = importOpenVpnProfile(
            """
            client
            remote one.example.com 443 tcp-client
            remote two.example.com 1194 udp
            vendor-custom-option yes
            """.trimIndent(),
        )

        val root = JSONObject(result.optionsJson)
        assertEquals(2, root.getJSONArray("servers").length())
        assertTrue(root.getBoolean("remote_random"))
        assertTrue(result.warnings.any { it.contains("vendor-custom-option") })
        assertTrue(result.warnings.any { it.contains("<ca>") })
    }
}
