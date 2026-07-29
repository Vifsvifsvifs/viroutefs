// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.AppMatcher
import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsServerConfig
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.DestinationPortRange
import dev.vifs.viroutefs.routing.DomainMatcherMode
import dev.vifs.viroutefs.routing.ProfileGroup
import dev.vifs.viroutefs.routing.ProfileGroupMode
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RouteTransport
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.encodeDomainMatcher
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
    fun manualGroupCompilesToExplicitSelectorAndRoutesThroughGroupTag() {
        val base = RoutingConfigDefaults.defaultConfig()
        val first = socksProfile("group-first", 1081)
        val second = socksProfile("group-second", 1082)
        val group = ProfileGroup(
            id = "manual-group",
            name = "Manual group",
            mode = ProfileGroupMode.Manual,
            memberProfileIds = listOf(first.id, second.id),
            selectedProfileId = second.id,
        )
        val rule = base.rules.first().copy(
            id = "manual-group-default",
            targetProfileId = group.id,
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(
                profiles = base.profiles + first + second,
                profileGroups = listOf(group),
                rules = listOf(rule),
                defaultProfileId = group.id,
            ),
        )
        val root = JSONObject(compiled.json)
        val outbounds = root.getJSONArray("outbounds")
        val selector = (0 until outbounds.length())
            .map(outbounds::getJSONObject)
            .first { it.optString("type") == "selector" }

        assertEquals(runtimeProfileTag(group.id), selector.getString("tag"))
        assertEquals(runtimeProfileTag(second.id), selector.getString("default"))
        assertEquals(runtimeProfileTag(group.id), root.getJSONObject("route").getString("final"))
        assertTrue(group.id in compiled.runtimeProfileIds)
    }

    @Test
    fun latencyGroupCompilesToExplicitHttpsUrlTestWithoutSystemFallback() {
        val base = RoutingConfigDefaults.defaultConfig()
        val first = socksProfile("latency-first", 1083)
        val second = socksProfile("latency-second", 1084)
        val group = ProfileGroup(
            id = "latency-group",
            name = "Fastest office",
            mode = ProfileGroupMode.Latency,
            memberProfileIds = listOf(first.id, second.id),
            testUrl = "https://example.com/health",
            testIntervalSeconds = 120,
            toleranceMs = 75,
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(
                profiles = base.profiles + first + second,
                profileGroups = listOf(group),
            ),
        )
        val outbounds = JSONObject(compiled.json).getJSONArray("outbounds")
        val urlTest = (0 until outbounds.length())
            .map(outbounds::getJSONObject)
            .first { it.optString("type") == "urltest" }
        val members = urlTest.getJSONArray("outbounds")
            .let { array -> (0 until array.length()).map(array::getString) }

        assertEquals(listOf(runtimeProfileTag(first.id), runtimeProfileTag(second.id)), members)
        assertEquals("https://example.com/health", urlTest.getString("url"))
        assertEquals("120s", urlTest.getString("interval"))
        assertEquals(75, urlTest.getInt("tolerance"))
        assertFalse(members.contains(SING_BOX_DIRECT_TAG))
        assertTrue(compiled.warnings.any { it.contains("explicit HTTPS availability check") })
    }

    @Test
    fun routeConstraintsCompileToSingBoxNetworkPortsAndRanges() {
        val base = RoutingConfigDefaults.defaultConfig()
        val rule = RouteRule(
            id = "api",
            name = "API",
            type = RouteRuleType.DOMAIN,
            targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
            priority = 5,
            matchers = listOf("api.example"),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
            destinationPorts = listOf(
                DestinationPortRange(443),
                DestinationPortRange(8000, 8100),
            ),
            transport = RouteTransport.Tcp,
        )
        val root = JSONObject(
            SingBoxRoutingConfigCompiler().compile(
                base.copy(rules = listOf(rule) + base.rules),
            ).json,
        )
        val compiledRule = root.getJSONObject("route").getJSONArray("rules").let { rules ->
            (0 until rules.length())
                .map(rules::getJSONObject)
                .first { it.optJSONArray("domain_suffix")?.optString(0) == "api.example" }
        }

        assertEquals("tcp", compiledRule.getString("network"))
        assertEquals(443, compiledRule.getJSONArray("port").getInt(0))
        assertEquals("8000:8100", compiledRule.getJSONArray("port_range").getString(0))
    }

    @Test
    fun domainModesCompileToDistinctNativeSingBoxFields() {
        val base = RoutingConfigDefaults.defaultConfig()
        val cases = listOf(
            Triple(DomainMatcherMode.Exact, "exact.example", "domain"),
            Triple(DomainMatcherMode.Suffix, "suffix.example", "domain_suffix"),
            Triple(DomainMatcherMode.Keyword, "keyword", "domain_keyword"),
            Triple(DomainMatcherMode.Regex, "(^|\\.)regex\\.example$", "domain_regex"),
        )
        val rules = cases.mapIndexed { index, (mode, value, _) ->
            RouteRule(
                id = "domain-$index",
                name = "Domain $index",
                type = RouteRuleType.DOMAIN,
                targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
                priority = index + 1,
                matchers = listOf(encodeDomainMatcher(mode, value)),
                reason = "test",
                technicalDetails = "test",
                recommendedAction = "test",
            )
        }
        val compiledRules = JSONObject(
            SingBoxRoutingConfigCompiler().compile(
                base.copy(rules = rules + base.rules),
            ).json,
        ).getJSONObject("route").getJSONArray("rules").let { array ->
            (0 until array.length()).map(array::getJSONObject)
        }

        cases.forEach { (mode, value, field) ->
            assertTrue(
                compiledRules.any { it.optJSONArray(field)?.optString(0) == value },
                "$mode should compile to $field",
            )
        }
    }

    private fun socksProfile(id: String, port: Int): TunnelProfile = TunnelProfile(
        id = id,
        name = id,
        type = TunnelType.Socks5,
        description = "group fixture",
        enabled = true,
        mockOnly = false,
        socks5 = Socks5ProfileConfig(
            name = id,
            host = "192.0.2.10",
            port = port,
        ),
    )

    @Test
    fun customDnsServersCompileInPriorityOrderWithoutDeprecatedCacheFlag() {
        val base = RoutingConfigDefaults.defaultConfig()
        val policy = DnsPolicy(
            id = "multi-dns",
            name = "Multi DNS",
            type = DnsPolicyType.Custom,
            description = "test",
            servers = listOf(
                DnsServerConfig("second", "https://dns.google/dns-query", priority = 20),
                DnsServerConfig("first", "tls://1.1.1.1", priority = 10),
            ),
        )
        val compiled = SingBoxRoutingConfigCompiler().compile(
            base.copy(dnsPolicies = base.dnsPolicies + policy),
        )
        val dns = JSONObject(compiled.json).getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        val custom = (0 until servers.length())
            .map(servers::getJSONObject)
            .filter { it.optString("type") in setOf("tls", "https") }

        assertEquals(listOf("tls", "https"), custom.map { it.getString("type") })
        assertEquals(4096, dns.getInt("cache_capacity"))
        assertEquals("10s", dns.getString("timeout"))
        assertFalse(dns.has("independent_cache"))
        assertTrue(compiled.warnings.any { it.contains("priority order") })
    }

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
        assertTrue(compiled.warnings.any { it.contains("TCP/TLS compatibility") && it.contains("fail closed") })
    }

    @Test
    fun xrayProfileUsesOnlyItsReadyAppPrivateEndpoint() {
        val base = RoutingConfigDefaults.defaultConfig()
        val xray = TunnelProfile(
            id = "xhttp",
            name = "XHTTP",
            type = TunnelType.XrayVlessReality,
            description = "Xray route",
            enabled = true,
            mockOnly = false,
            vless = VlessProfileConfig(
                name = "XHTTP",
                host = "edge.example",
                port = 443,
                uuid = "123e4567-e89b-12d3-a456-426614174000",
                transportType = "xhttp",
                securityMode = VlessSecurityMode.TLS,
                sni = "front.example",
            ),
        )
        val config = base.copy(
            profiles = base.profiles + xray,
            defaultProfileId = xray.id,
            rules = base.rules.map {
                if (it.type == RouteRuleType.DEFAULT) it.copy(targetProfileId = xray.id) else it
            },
        )
        val compiled = SingBoxRoutingConfigCompiler(
            xrayEndpoints = mapOf(xray.id to LocalEngineEndpoint("127.0.0.1", 23080)),
        ).compile(config)
        val root = JSONObject(compiled.json)
        val tag = compiled.profileTags.getValue(xray.id)
        val outbound = root.getJSONArray("outbounds").let { outbounds ->
            (0 until outbounds.length())
                .map(outbounds::getJSONObject)
                .first { it.optString("tag") == tag }
        }

        assertEquals("socks", outbound.getString("type"))
        assertEquals("127.0.0.1", outbound.getString("server"))
        assertEquals(23080, outbound.getInt("server_port"))
        assertEquals(tag, root.getJSONObject("route").getString("final"))
        assertFalse(compiled.json.contains("123e4567-e89b"))
        assertFalse(compiled.json.contains("edge.example"))
    }

    @Test
    fun missingXrayProcessFailsClosed() {
        val base = RoutingConfigDefaults.defaultConfig()
        val xray = TunnelProfile(
            id = "xhttp-missing",
            name = "XHTTP",
            type = TunnelType.XrayVlessReality,
            description = "Xray route",
            enabled = true,
            mockOnly = false,
            vless = VlessProfileConfig(
                name = "XHTTP",
                host = "edge.example",
                port = 443,
                uuid = "123e4567-e89b-12d3-a456-426614174000",
                transportType = "xhttp",
                securityMode = VlessSecurityMode.TLS,
            ),
        )
        val config = base.copy(
            profiles = base.profiles + xray,
            defaultProfileId = xray.id,
            rules = base.rules.map {
                if (it.type == RouteRuleType.DEFAULT) it.copy(targetProfileId = xray.id) else it
            },
        )

        val compiled = SingBoxRoutingConfigCompiler().compile(config)

        assertEquals(
            SING_BOX_BLOCK_TAG,
            JSONObject(compiled.json).getJSONObject("route").getString("final"),
        )
        assertTrue(compiled.warnings.any { it.contains("Xray") && it.contains("fail closed") })
    }
}
