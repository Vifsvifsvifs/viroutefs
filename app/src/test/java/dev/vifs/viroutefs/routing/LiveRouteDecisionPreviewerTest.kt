// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveRouteDecisionPreviewerTest {
    @Test
    fun tcpPacketToIpMatchesIpRule() {
        val config = baseConfigWith(
            RouteRule(
                id = "rule-ip",
                name = "TEST-NET lab",
                type = RouteRuleType.CIDR,
                targetProfileId = RoutingConfigDefaults.SYSTEM_PROFILE_ID,
                dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                priority = 10,
                matchers = listOf("203.0.113.0/24"),
                reason = "Test IP range uses System.",
                technicalDetails = "CIDR match only.",
                recommendedAction = "No action.",
            ),
        )

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpPacket(destinationIp = "203.0.113.7"), observedAt = 1L)

        assertEquals("TEST-NET lab", preview.matchedRuleName)
        assertEquals("System", preview.selectedProfileType)
        assertTrue(preview.decisionText.contains("TEST-NET lab"))
    }

    @Test
    fun defaultRuleFallbackWorks() {
        val preview = LiveRouteDecisionPreviewer(RoutingConfigDefaults.defaultConfig())
            .preview(tcpPacket(destinationIp = "198.51.100.9"), observedAt = 2L)

        assertNull(preview.matchedRuleName)
        assertEquals("System / Система", preview.selectedProfileName)
        assertTrue(preview.decisionText.contains("default rule fallback"))
    }

    @Test
    fun blockProfileIsShownCorrectly() {
        val config = baseConfigWith(
            RouteRule(
                id = "rule-block",
                name = "Block TEST-NET",
                type = RouteRuleType.CIDR,
                targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
                dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                priority = 10,
                matchers = listOf("203.0.113.0/24"),
                reason = "Blocked by policy.",
                technicalDetails = "CIDR block.",
                recommendedAction = "Keep blocked unless expected.",
            ),
        )

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpPacket(destinationIp = "203.0.113.44"), observedAt = 3L)

        assertEquals("Block", preview.selectedProfileName)
        assertEquals("Block", preview.selectedProfileType)
        assertNull(preview.warning)
    }

    @Test
    fun socks5ProfileShowsRuntimeForwardingWarningWithoutSecretText() {
        val socks5Profile = TunnelProfile(
            id = "socks5-lab",
            name = "Lab SOCKS5",
            type = TunnelType.Socks5,
            description = "Manual diagnostics only.",
            socks5 = Socks5ProfileConfig(
                name = "Lab SOCKS5",
                host = "127.0.0.1",
                port = 1080,
                username = "tester",
                password = "super-secret-password",
            ),
        )
        val config = RoutingConfigDefaults.defaultConfig().copy(
            profiles = RoutingConfigDefaults.defaultConfig().profiles + socks5Profile,
            rules = RoutingConfigDefaults.defaultConfig().rules + RouteRule(
                id = "rule-socks5-ip",
                name = "SOCKS5 TEST-NET",
                type = RouteRuleType.CIDR,
                targetProfileId = socks5Profile.id,
                dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                priority = 10,
                matchers = listOf("203.0.113.0/24"),
                reason = "Select SOCKS5 in preview only.",
                technicalDetails = "CIDR to SOCKS5.",
                recommendedAction = "Do not expect forwarding.",
            ),
        )

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpPacket(destinationIp = "203.0.113.55"), observedAt = 4L)

        assertEquals(SOCKS5_RUNTIME_LIMITATION, preview.warning)
        assertTrue(preview.decisionText.contains("Runtime forwarding is not enabled yet"))
        assertFalse(preview.decisionText.contains("super-secret-password"))
        assertFalse(preview.decisionText.contains("tester"))
        assertFalse(preview.decisionText.contains("127.0.0.1"))
    }

    private fun baseConfigWith(rule: RouteRule): RoutingConfig = RoutingConfigDefaults.defaultConfig().copy(
        rules = RoutingConfigDefaults.defaultConfig().rules + rule,
    )

    private fun tcpPacket(destinationIp: String): LivePacketMetadata = LivePacketMetadata(
        protocol = "TCP",
        sourceIp = "10.250.0.2",
        destinationIp = destinationIp,
        sourcePort = 42000,
        destinationPort = 443,
    )
}
