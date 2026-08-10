// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.importOpenVpnProfile
import kotlin.test.Test
import kotlin.test.assertTrue

class SingBoxEngineProfileValidationTest {
    @Test
    fun routedOpenVpnProfileDoesNotInheritMissingDefaultRuleError() {
        val imported = importOpenVpnProfile(
            """
            client
            dev tun
            proto tcp
            remote 192.0.2.10 1194
            cipher AES-256-GCM
            route 10.0.0.0 255.255.255.0
            <ca>
            -----BEGIN CERTIFICATE-----
            test-ca
            -----END CERTIFICATE-----
            </ca>
            """.trimIndent(),
        )
        val profile = TunnelProfile(
            id = "office-openvpn",
            name = "Office OpenVPN",
            type = TunnelType.OpenVpn,
            description = "Test OpenVPN profile",
            enabled = true,
            mockOnly = false,
            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
            singBox = SingBoxProfileConfig(SingBoxProfileKind.Endpoint, imported.optionsJson),
            appRoutingNetworks = imported.routes,
        )

        val errors = validateSingBoxEngineProfile(profile)

        assertTrue(errors.isEmpty(), errors.joinToString("\n"))
    }
}
