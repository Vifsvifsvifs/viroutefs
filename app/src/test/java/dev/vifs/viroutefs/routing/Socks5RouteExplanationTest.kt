// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Socks5RouteExplanationTest {
    @Test
    fun socks5RouteDecisionReportsRuntimeAvailabilityWithoutSecrets() {
        val socks5Profile = TunnelProfile(
            id = "socks5-1",
            name = "Lab SOCKS5",
            type = TunnelType.Socks5,
            description = "Manual diagnostics only.",
            mockOnly = false,
            socks5 = Socks5ProfileConfig(name = "Lab SOCKS5", host = "127.0.0.1", port = 1080),
        )
        val config = RoutingConfigDefaults.defaultConfig().copy(
            profiles = RoutingConfigDefaults.defaultConfig().profiles + socks5Profile,
            rules = RoutingConfigDefaults.defaultConfig().rules + RouteRule(
                id = "rule-socks5",
                name = "SOCKS5 example",
                type = RouteRuleType.DOMAIN,
                targetProfileId = socks5Profile.id,
                dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                priority = 10,
                matchers = listOf("example.com"),
                reason = "Example traffic selects SOCKS5 in the policy simulator.",
                technicalDetails = "DOMAIN example.com -> SOCKS5.",
                recommendedAction = "Run manual diagnostics before relying on the profile.",
            ),
        )

        val decision = RouteEngine(config).simulate("example.com")

        assertFalse(decision.warnings.any { it.contains("not enabled", ignoreCase = true) })
        assertTrue(decision.profileMockSummary.contains("available through the local sing-box"))
        assertTrue(decision.technicalDetails.contains(SOCKS5_RUNTIME_STATUS))
    }
}
