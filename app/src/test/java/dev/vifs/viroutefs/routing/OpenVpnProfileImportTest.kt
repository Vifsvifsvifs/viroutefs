// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

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

    @Test
    fun tlsCipherAndIpv4RoutesUseOpenVpnClientFields() {
        val result = importOpenVpnProfile(
            """
            client
            dev tun
            proto tcp
            remote 192.0.2.10 1194
            cipher AES-256-GCM
            route 10.0.0.0 255.255.255.0 vpn_gateway
            route 10.40.0.9 255.255.255.0 vpn_gateway
            <ca>
            -----BEGIN CERTIFICATE-----
            test
            -----END CERTIFICATE-----
            </ca>
            """.trimIndent(),
        )
        val endpoint = JSONObject(result.optionsJson)

        assertFalse(endpoint.has("cipher"))
        assertEquals("AES-256-GCM", endpoint.getJSONArray("data_ciphers").getString(0))
        assertEquals("AES-256-GCM", endpoint.getString("data_ciphers_fallback"))
        assertEquals("10.0.0.0/24", endpoint.getJSONArray("routes").getString(0))
        assertEquals("10.40.0.0/24", endpoint.getJSONArray("routes").getString(1))
        assertEquals(listOf("10.0.0.0/24", "10.40.0.0/24"), result.routes)
        assertFalse(result.warnings.any { it.contains("dev") || it.contains("route»") })
    }

    @Test
    fun migratesBeta10EndpointRoutesToSharedRouter() {
        val endpoint = JSONObject()
            .put("type", "openvpn-client")
            .put("routes", org.json.JSONArray(listOf("10.0.0.0/24", "10.40.0.0/24", "invalid")))
        val openVpn = TunnelProfile(
            id = "office-openvpn",
            name = "Office",
            type = TunnelType.OpenVpn,
            description = "Imported OpenVPN profile",
            enabled = true,
            mockOnly = false,
            singBox = SingBoxProfileConfig(SingBoxProfileKind.Endpoint, endpoint.toString()),
        )

        val migrated = RoutingConfigDefaults.defaultConfig()
            .copy(profiles = RoutingConfigDefaults.defaultConfig().profiles + openVpn)
            .withMigratedOpenVpnEndpointRoutes()
            .withSyncedProfileAppRoutingRules()

        val profile = migrated.profiles.single { it.id == openVpn.id }
        assertEquals(listOf("10.0.0.0/24", "10.40.0.0/24"), profile.appRoutingNetworks)
        assertTrue(migrated.rules.any { rule ->
            rule.targetProfileId == openVpn.id &&
                rule.type == RouteRuleType.CIDR &&
                rule.matchers == profile.appRoutingNetworks
        })
    }

    @Test
    fun authUserPassReadsExactlyTwoUtf8Lines() {
        val credentials = importOpenVpnAuthUserPass(
            "office-user\ncorrect horse battery staple\n".toByteArray(StandardCharsets.UTF_8),
        )

        assertEquals("office-user", credentials.username)
        assertEquals("correct horse battery staple", credentials.password)
    }

    @Test
    fun authUserPassRejectsMissingPasswordWithoutLeakingUsername() {
        val secretUsername = "do-not-leak-user"
        val error = assertFailsWith<IllegalArgumentException> {
            importOpenVpnAuthUserPass("$secretUsername\n".toByteArray(StandardCharsets.UTF_8))
        }

        assertTrue(error.message.orEmpty().contains("две строки"))
        assertFalse(error.message.orEmpty().contains(secretUsername))
    }
}
