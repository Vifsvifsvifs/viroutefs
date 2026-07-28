// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.AppMatcher
import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.singBoxProfileTemplate
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingBoxRoutingConfigTest {
    @Test
    fun freshConfigurationUsesPhoneInternetAsTheFinalRoute() {
        val compiled = SingBoxRoutingConfigCompiler().compile(
            RoutingConfigDefaults.defaultConfig(),
        )
        val route = JSONObject(compiled.json).getJSONObject("route")

        assertEquals(SING_BOX_DIRECT_TAG, route.getString("final"))
        assertEquals(
            SING_BOX_DIRECT_TAG,
            compiled.profileTags.getValue(RoutingConfigDefaults.SYSTEM_PROFILE_ID),
        )
    }

    @Test
    fun emergencyBlockIsTheFirstAndFinalRoute() {
        val compiled = SingBoxRoutingConfigCompiler().compile(
            RoutingConfigDefaults.defaultConfig().copy(emergencyBlockEnabled = true),
        )
        val root = JSONObject(compiled.json)
        val route = root.getJSONObject("route")
        val first = route.getJSONArray("rules").getJSONObject(0)

        assertEquals("route", first.getString("action"))
        assertEquals(SING_BOX_BLOCK_TAG, first.getString("outbound"))
        assertEquals(SING_BOX_BLOCK_TAG, route.getString("final"))
        assertFalse(compiled.json.contains("\"hijack-dns\""))
    }

    @Test
    fun appRouteAndAppDnsUseTheSameAndroidPackage() {
        val profile = TunnelProfile(
            id = "work-socks",
            name = "Work",
            type = TunnelType.Socks5,
            description = "Work proxy",
            mockOnly = false,
            dnsPolicyId = "work-dns",
            socks5 = Socks5ProfileConfig(
                name = "Work",
                host = "192.0.2.1",
                port = 1080,
            ),
        )
        val dns = DnsPolicy(
            id = "work-dns",
            name = "Work DNS",
            type = DnsPolicyType.Custom,
            serverText = "tls://1.1.1.1",
            resolveThroughProfileId = profile.id,
            description = "DNS over the work proxy",
        )
        val rule = RouteRule(
            id = "work-app",
            name = "Work app",
            type = RouteRuleType.APP,
            targetProfileId = profile.id,
            priority = 10,
            matchers = emptyList(),
            appMatchers = listOf(
                AppMatcher(AppMatcherPlatform.Android, "com.example.work"),
            ),
            reason = "work",
            technicalDetails = "work",
            recommendedAction = "work",
        )
        val base = RoutingConfigDefaults.defaultConfig()
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(
                profiles = base.profiles + profile,
                dnsPolicies = base.dnsPolicies + dns,
                rules = listOf(rule) + base.rules,
            ),
        )
        val root = JSONObject(compiled.json)
        val routeRule = root.getJSONObject("route").getJSONArray("rules").getJSONObject(2)
        val dnsRule = root.getJSONObject("dns").getJSONArray("rules").getJSONObject(0)
        val dnsServer = root.getJSONObject("dns").getJSONArray("servers").getJSONObject(1)

        assertEquals("com.example.work", routeRule.getJSONArray("package_name").getString(0))
        assertEquals("com.example.work", dnsRule.getJSONArray("package_name").getString(0))
        assertEquals("tls", dnsServer.getString("type"))
        assertEquals(compiled.profileTags.getValue(profile.id), dnsServer.getString("detour"))
        assertTrue(root.getJSONObject("route").getBoolean("find_process"))
    }

    @Test
    fun unsupportedDefaultNeverFallsBackToDirect() {
        val base = RoutingConfigDefaults.defaultConfig()
        val unsupported = TunnelProfile(
            id = "legacy-pptp",
            name = "Old PPTP",
            type = TunnelType.Pptp,
            description = "legacy",
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(
                profiles = base.profiles + unsupported,
                defaultProfileId = unsupported.id,
                rules = base.rules.map {
                    if (it.type == RouteRuleType.DEFAULT) it.copy(targetProfileId = unsupported.id) else it
                },
            ),
        )

        assertEquals(
            SING_BOX_BLOCK_TAG,
            JSONObject(compiled.json).getJSONObject("route").getString("final"),
        )
        assertTrue(compiled.warnings.any { it.contains("unavailable") })
    }

    @Test
    fun realityProfileUsesCurrentSingBoxFieldNames() {
        val base = RoutingConfigDefaults.defaultConfig()
        val vless = TunnelProfile(
            id = "reality",
            name = "Reality",
            type = TunnelType.VLESS,
            description = "Reality",
            mockOnly = false,
            vless = VlessProfileConfig(
                name = "Reality",
                host = "example.com",
                port = 443,
                uuid = "00000000-0000-0000-0000-000000000001",
                transportType = "tcp",
                securityMode = VlessSecurityMode.REALITY,
                sni = "www.example.com",
                publicKey = "test-public-key",
                shortId = "0123456789abcdef",
                fingerprint = "chrome",
            ),
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(profiles = base.profiles + vless),
        )
        val outbound = JSONObject(compiled.json)
            .getJSONArray("outbounds")
            .let { outbounds ->
                (0 until outbounds.length())
                    .map(outbounds::getJSONObject)
                    .first { it.optString("tag") == compiled.profileTags[vless.id] }
            }
        val reality = outbound.getJSONObject("tls").getJSONObject("reality")

        assertTrue(reality.getBoolean("enabled"))
        assertEquals("test-public-key", reality.getString("public_key"))
        assertEquals("0123456789abcdef", reality.getString("short_id"))
        assertFalse(reality.has("password"))
    }

    @Test
    fun wireGuardEndpointCanBeTheTargetOfAnyCidrRule() {
        val base = RoutingConfigDefaults.defaultConfig()
        val wireGuard = TunnelProfile(
            id = "wg",
            name = "WireGuard",
            type = TunnelType.WireGuard,
            description = "WireGuard",
            mockOnly = false,
            singBox = SingBoxProfileConfig(
                SingBoxProfileKind.Endpoint,
                singBoxProfileTemplate(TunnelType.WireGuard),
            ),
        )
        val cidrRule = RouteRule(
            id = "office-network",
            name = "Office network",
            type = RouteRuleType.CIDR,
            targetProfileId = wireGuard.id,
            priority = 5,
            matchers = listOf("10.20.0.0/16"),
            reason = "office",
            technicalDetails = "office",
            recommendedAction = "office",
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(
                profiles = base.profiles + wireGuard,
                rules = listOf(cidrRule) + base.rules,
            ),
        )
        val root = JSONObject(compiled.json)
        val endpoint = root.getJSONArray("endpoints").getJSONObject(0)
        val rule = root.getJSONObject("route").getJSONArray("rules").getJSONObject(2)

        assertEquals("wireguard", endpoint.getString("type"))
        assertEquals(compiled.profileTags[wireGuard.id], endpoint.getString("tag"))
        assertEquals("10.20.0.0/16", rule.getJSONArray("ip_cidr").getString(0))
        assertEquals(compiled.profileTags[wireGuard.id], rule.getString("outbound"))
    }

    @Test
    fun enabledByeDpiUsesOnlyTheAppPrivateLoopbackProxy() {
        val base = RoutingConfigDefaults.defaultConfig()
        val config = base.copy(
            profiles = base.profiles.map {
                if (it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID) it.copy(enabled = true) else it
            },
            rules = base.rules.map {
                if (it.type == RouteRuleType.DEFAULT) {
                    it.copy(targetProfileId = RoutingConfigDefaults.BYEDPI_PROFILE_ID)
                } else {
                    it
                }
            },
            defaultProfileId = RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        )

        val compiled = SingBoxRoutingConfigCompiler(byeDpiPort = 12080).compile(config)
        val root = JSONObject(compiled.json)
        val tag = compiled.profileTags.getValue(RoutingConfigDefaults.BYEDPI_PROFILE_ID)
        val outbound = root.getJSONArray("outbounds").let { outbounds ->
            (0 until outbounds.length())
                .map(outbounds::getJSONObject)
                .first { it.optString("tag") == tag }
        }

        assertEquals("socks", outbound.getString("type"))
        assertEquals("127.0.0.1", outbound.getString("server"))
        assertEquals(12080, outbound.getInt("server_port"))
        assertEquals(tag, root.getJSONObject("route").getString("final"))
    }

    @Test
    fun missingByeDpiProcessFailsClosed() {
        val base = RoutingConfigDefaults.defaultConfig()
        val config = base.copy(
            profiles = base.profiles.map {
                if (it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID) it.copy(enabled = true) else it
            },
            rules = base.rules.map {
                if (it.type == RouteRuleType.DEFAULT) {
                    it.copy(targetProfileId = RoutingConfigDefaults.BYEDPI_PROFILE_ID)
                } else {
                    it
                }
            },
            defaultProfileId = RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        )

        val compiled = SingBoxRoutingConfigCompiler().compile(config)

        assertEquals(
            SING_BOX_BLOCK_TAG,
            JSONObject(compiled.json).getJSONObject("route").getString("final"),
        )
        assertTrue(compiled.warnings.any { it.contains("ByeDPI") && it.contains("fail closed") })
    }
}
