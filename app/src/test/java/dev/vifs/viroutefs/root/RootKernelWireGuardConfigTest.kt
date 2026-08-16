// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.ProfileAppRoutingMode
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import org.json.JSONArray
import org.json.JSONObject

class RootKernelWireGuardConfigTest {
    @Test
    fun fullTunnelRequiresPlainCustomDns() {
        val profile = wireGuardProfile(allowedIps = listOf("0.0.0.0/0", "::/0"))
        val routing = RoutingConfigDefaults.defaultConfig().copy(
            profiles = RoutingConfigDefaults.defaultConfig().profiles + profile,
        )

        val error = assertFailsWith<IllegalArgumentException> {
            prepareKernelConfig(profile, routing)
        }

        assertContains(error.message.orEmpty(), "Custom DNS")
    }

    @Test
    fun fullTunnelCarriesDnsAndSelectedApplicationsIntoOfficialConfig() {
        val profile = wireGuardProfile(
            allowedIps = listOf("0.0.0.0/0", "::/0"),
            dnsPolicyId = "dns-kernel",
            packages = listOf("org.example.browser", "com.example.mail"),
        )
        val routing = RoutingConfigDefaults.defaultConfig().copy(
            profiles = RoutingConfigDefaults.defaultConfig().profiles + profile,
            dnsPolicies = RoutingConfigDefaults.defaultConfig().dnsPolicies + DnsPolicy(
                id = "dns-kernel",
                name = "Kernel DNS",
                type = DnsPolicyType.Custom,
                serverText = "9.9.9.9, 2620:fe::fe",
                description = "test",
            ),
        )

        val prepared = prepareKernelConfig(profile, routing)

        assertContains(prepared.wgQuickText, "DNS = 9.9.9.9, 2620:fe::fe")
        assertContains(prepared.wgQuickText, "IncludedApplications = org.example.browser, com.example.mail")
        assertContains(prepared.summary, "Полный туннель")
    }

    @Test
    fun bypassModeIsInvertedForKernelApplicationSelection() {
        val profile = wireGuardProfile(
            allowedIps = listOf("10.0.0.0/8"),
            mode = ProfileAppRoutingMode.BypassSelected,
            packages = listOf("org.example.browser"),
        )
        val routing = RoutingConfigDefaults.defaultConfig().copy(
            profiles = RoutingConfigDefaults.defaultConfig().profiles + profile,
        )

        val prepared = prepareKernelConfig(profile, routing)

        assertContains(prepared.wgQuickText, "ExcludedApplications = org.example.browser")
        assertFalse(prepared.wgQuickText.contains("IncludedApplications"))
        assertFalse(prepared.wgQuickText.contains("DNS ="))
    }

    private fun wireGuardProfile(
        allowedIps: List<String>,
        dnsPolicyId: String? = null,
        mode: ProfileAppRoutingMode = ProfileAppRoutingMode.SelectedApps,
        packages: List<String> = emptyList(),
    ): TunnelProfile {
        val privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        val publicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
        val options = JSONObject()
            .put("type", "wireguard")
            .put("address", JSONArray(listOf("10.20.0.2/32")))
            .put("private_key", privateKey)
            .put(
                "peers",
                JSONArray().put(
                    JSONObject()
                        .put("address", "vpn.example")
                        .put("port", 51820)
                        .put("public_key", publicKey)
                        .put("allowed_ips", JSONArray(allowedIps)),
                ),
            )
        return TunnelProfile(
            id = "wg-test",
            name = "WireGuard test",
            type = TunnelType.WireGuard,
            description = "test",
            enabled = true,
            dnsPolicyId = dnsPolicyId,
            singBox = SingBoxProfileConfig(SingBoxProfileKind.Endpoint, options.toString()),
            appRoutingMode = mode,
            appRoutingPackages = packages,
        )
    }
}
