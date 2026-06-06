// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveRouteDecisionPreviewerTest {
    @Test
    fun tcpPacketSummaryToIpMatchesCidrRule() {
        val blockRule = cidrRule(
            id = "test-net-block",
            name = "Block TEST-NET",
            matcher = "203.0.113.0/24",
            targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
        )
        val config = withExtraRule(blockRule)

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpSummary(dstIp = "203.0.113.42"))

        assertEquals("Block TEST-NET", preview.matchedRuleName)
        assertEquals("Block", preview.selectedProfileName)
        assertEquals("Block", preview.selectedProfileType)
        assertTrue(preview.displayLines.any { it == OBSERVATION_ONLY_NO_FORWARDING })
    }

    @Test
    fun defaultRuleFallbackWorks() {
        val preview = LiveRouteDecisionPreviewer(RoutingConfigDefaults.defaultConfig())
            .preview(tcpSummary(dstIp = "198.51.100.10"))

        assertEquals("Default System", preview.matchedRuleName)
        assertEquals("System / Система", preview.selectedProfileName)
        assertEquals("System", preview.selectedProfileType)
        assertTrue(preview.displayLines.contains(OBSERVATION_ONLY_NO_FORWARDING))
    }

    @Test
    fun blockProfileShownCorrectly() {
        val config = RoutingConfigDefaults.safeDefaultConfig()

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpSummary(dstIp = "198.51.100.10"))

        assertEquals("Fail-closed default", preview.matchedRuleName)
        assertEquals("Block", preview.selectedProfileName)
        assertEquals("Block", preview.selectedProfileType)
        assertFalse(preview.warnings.any { it.contains("Runtime forwarding") })
    }

    @Test
    fun socks5ProfileShowsRuntimeForwardingWarning() {
        val secret = "do-not-print-this-password"
        val socks5Profile = TunnelProfile(
            id = "socks5-lab",
            name = "Lab SOCKS5",
            type = TunnelType.Socks5,
            description = "Manual diagnostics only.",
            mockOnly = true,
            socks5 = Socks5ProfileConfig(
                name = "Lab SOCKS5",
                host = "127.0.0.1",
                port = 1080,
                username = "tester",
                password = secret,
            ),
        )
        val config = withProfileAndRule(
            profile = socks5Profile,
            rule = cidrRule(
                id = "socks5-test-net",
                name = "SOCKS5 TEST-NET",
                matcher = "203.0.113.0/24",
                targetProfileId = socks5Profile.id,
            ),
        )

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpSummary(dstIp = "203.0.113.7"))
        val decisionText = preview.displayLines.joinToString("\n")

        assertTrue(preview.warnings.contains(SOCKS5_RUNTIME_FORWARDING_NOT_ENABLED))
        assertTrue(decisionText.contains("Runtime forwarding is not enabled yet"))
        assertFalse(decisionText.contains(secret), "decision text must not include passwords/secrets")
        assertFalse(decisionText.contains("tester"), "decision text must not include SOCKS5 usernames")
    }

    @Test
    fun vlessProfileShowsRuntimeForwardingWarningWithoutUuid() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val vlessProfile = TunnelProfile(
            id = "vless-lab",
            name = "Lab VLESS",
            type = TunnelType.VLESS,
            description = "VLESS config only.",
            mockOnly = true,
            vless = VlessProfileConfig(
                name = "Lab VLESS",
                host = "example.com",
                port = 443,
                uuid = uuid,
                securityMode = VlessSecurityMode.TLS,
            ),
        )
        val config = withProfileAndRule(
            profile = vlessProfile,
            rule = cidrRule(
                id = "vless-test-net",
                name = "VLESS TEST-NET",
                matcher = "203.0.113.0/24",
                targetProfileId = vlessProfile.id,
            ),
        )

        val preview = LiveRouteDecisionPreviewer(config).preview(tcpSummary(dstIp = "203.0.113.9"))
        val decisionText = preview.displayLines.joinToString("\n")

        assertTrue(preview.warnings.contains(VLESS_RUNTIME_FORWARDING_NOT_ENABLED))
        assertTrue(decisionText.contains("Selected profile is VLESS. Runtime forwarding is not enabled yet."))
        assertTrue(decisionText.contains("Latest manual VLESS response classification:"))
        assertFalse(decisionText.contains(uuid), "decision text must not include VLESS UUID")
    }

    @Test
    fun noDuplicateParserOrSummaryModelIntroduced() {
        val sourceRoot = listOf(
            File("src/main/java/dev/vifs/viroutefs"),
            File("app/src/main/java/dev/vifs/viroutefs"),
        ).first { it.exists() }
        val sourceText = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        assertEquals(1, Regex("data\\s+class\\s+PacketSummary\\s*\\(").findAll(sourceText).count())
        assertEquals(1, Regex("class\\s+PacketSummaryHistory\\s*\\(").findAll(sourceText).count())
        assertEquals(1, Regex("fun\\s+parseSummary\\s*\\(").findAll(sourceText).count())
    }

    private fun tcpSummary(dstIp: String): PacketSummary = PacketSummary(
        timestamp = 1_700_000_000_000L,
        protocol = Ipv4Protocol.Tcp,
        srcIp = "10.250.0.2",
        srcPort = 12345,
        dstIp = dstIp,
        dstPort = 443,
        packetSize = 60,
    )

    private fun cidrRule(
        id: String,
        name: String,
        matcher: String,
        targetProfileId: String,
    ): RouteRule = RouteRule(
        id = id,
        name = name,
        type = RouteRuleType.CIDR,
        targetProfileId = targetProfileId,
        dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
        priority = 10,
        matchers = listOf(matcher),
        reason = "$name selected by packet destination IP.",
        technicalDetails = "$matcher -> $targetProfileId.",
        recommendedAction = "Review the selected route decision before enabling runtime forwarding.",
    )

    private fun withExtraRule(rule: RouteRule): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        return defaults.copy(rules = defaults.rules + rule)
    }

    private fun withProfileAndRule(profile: TunnelProfile, rule: RouteRule): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        return defaults.copy(
            profiles = defaults.profiles + profile,
            rules = defaults.rules + rule,
        )
    }
}
