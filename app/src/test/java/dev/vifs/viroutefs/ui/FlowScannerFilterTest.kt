// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import dev.vifs.viroutefs.routing.DestinationPortRange
import dev.vifs.viroutefs.routing.ProfileGroup
import dev.vifs.viroutefs.routing.ProfileGroupMode
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RouteQuery
import dev.vifs.viroutefs.routing.RouteTransport
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.engine.runtimeProfileTag
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertEquals

class FlowScannerFilterTest {
    @Test
    fun combinesApplicationProtocolAndTextFilters() {
        val browser = event(
            app = "Browser",
            domain = "example.com",
            protocol = "443 / TCP",
            route = "Office VPN",
            packages = listOf("com.example.browser"),
        )
        val messenger = event(
            app = "Messenger",
            domain = "chat.example",
            protocol = "443 / UDP",
            route = "System",
            packages = listOf("com.example.chat"),
        )

        assertEquals(
            listOf(browser),
            filterFlowEvents(
                events = listOf(browser, messenger),
                appPackage = "com.example.browser",
                query = "office",
                protocol = FlowProtocolFilter.Tcp,
            ),
        )
    }

    @Test
    fun otherProtocolDoesNotIncludeTcpUdpOrIcmp() {
        val tcp = event(protocol = "443 / TCP")
        val unknown = event(protocol = "853 / QUIC")

        assertEquals(
            listOf(unknown),
            filterFlowEvents(
                events = listOf(tcp, unknown),
                appPackage = null,
                query = "",
                protocol = FlowProtocolFilter.Other,
            ),
        )
    }

    @Test
    fun csvExportContainsOnlyVisibleMetadataAndEscapesValues() {
        val source = event(
            app = "Browser, \"Work\"",
            domain = "example.test",
            protocol = "443 / TCP",
            packages = listOf("com.example.browser"),
        ).copy(technicalDetails = "secret technical payload")
        val csv = exportFlowEventsCsv(listOf(source))

        assertTrue(csv.startsWith("application,packages,destination"))
        assertTrue(csv.contains("\"Browser, \"\"Work\"\"\""))
        assertTrue(csv.contains("\"com.example.browser\""))
        assertTrue(csv.contains("\"example.test\""))
        assertFalse(csv.contains("secret technical payload"))
    }

    @Test
    fun routeExplanationUsesDomainPortAndTransportAcrossAvailableInputs() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val rule = RouteRule(
            id = "secure-api",
            name = "Secure API over TCP",
            type = RouteRuleType.DOMAIN,
            targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
            priority = 1,
            matchers = listOf("api.example"),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
            destinationPorts = listOf(DestinationPortRange(443, 443)),
            transport = RouteTransport.Tcp,
        )
        val config = defaults.copy(rules = listOf(rule) + defaults.rules)

        val tcp = explainFlowRoute(
            config = config,
            appPackages = listOf("com.example.browser"),
            domain = "api.example",
            destinationIp = "192.0.2.10",
            destinationPort = 443,
            network = "tcp",
        )
        val udp = explainFlowRoute(
            config = config,
            appPackages = listOf("com.example.browser"),
            domain = "api.example",
            destinationIp = "192.0.2.10",
            destinationPort = 443,
            network = "udp",
        )

        assertEquals("secure-api", tcp.matchedRule.id)
        assertEquals(RouteRuleType.DEFAULT, udp.matchedRule.type)
    }

    @Test
    fun profileGroupAcceptsGroupAndSelectedMemberAsExpectedRuntimeTags() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val group = ProfileGroup(
            id = "preferred-route",
            name = "Preferred route",
            mode = ProfileGroupMode.Manual,
            memberProfileIds = listOf("direct", "work-vpn"),
            selectedProfileId = "direct",
        )
        val config = defaults.copy(
            profileGroups = listOf(group),
            defaultProfileId = group.id,
        )
        val decision = RouteEngine(config).simulate(RouteQuery("default"))
        val tags = expectedRuntimeTags(config, decision)

        assertTrue(runtimeProfileTag(group.id) in tags)
        assertTrue(runtimeProfileTag("direct") in tags)
        assertTrue(runtimeProfileTag("work-vpn") in tags)
    }

    @Test
    fun combinesLifecycleActionIpVersionAndTimeFilters() {
        val now = 1_700_000_000_000L
        val activeBlockedIpv6 = event(
            protocol = "443 / TCP",
            lifecycle = FlowLifecycle.Active,
            blocked = true,
            ipVersion = FlowIpVersion.Ipv6,
            observedAt = now - 60_000L,
        )
        val closedAllowedIpv6 = event(
            protocol = "443 / TCP",
            lifecycle = FlowLifecycle.Closed,
            ipVersion = FlowIpVersion.Ipv6,
            observedAt = now - 60_000L,
        )
        val oldActiveBlockedIpv6 = activeBlockedIpv6.copy(
            domain = "old.example",
            observedAt = now - 10 * 60_000L,
        )

        assertEquals(
            listOf(activeBlockedIpv6),
            filterFlowEvents(
                events = listOf(activeBlockedIpv6, closedAllowedIpv6, oldActiveBlockedIpv6),
                appPackage = null,
                query = "",
                protocol = FlowProtocolFilter.All,
                lifecycle = FlowLifecycleFilter.Active,
                action = FlowActionFilter.Blocked,
                ipVersion = FlowIpVersionFilter.Ipv6,
                time = FlowTimeFilter.Last5Minutes,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun lifecycleFilterDoesNotMislabelPacketSnapshotAsLiveConnection() {
        val snapshot = event(protocol = "443 / TCP", lifecycle = FlowLifecycle.Snapshot)

        assertTrue(
            filterFlowEvents(
                events = listOf(snapshot),
                appPackage = null,
                query = "",
                protocol = FlowProtocolFilter.All,
                lifecycle = FlowLifecycleFilter.Active,
            ).isEmpty(),
        )
        assertTrue(
            filterFlowEvents(
                events = listOf(snapshot),
                appPackage = null,
                query = "",
                protocol = FlowProtocolFilter.All,
                lifecycle = FlowLifecycleFilter.Closed,
            ).isEmpty(),
        )
    }

    @Test
    fun detectsIpv4Ipv6AndUnknownDestinations() {
        assertEquals(FlowIpVersion.Ipv4, detectIpVersion("192.0.2.1"))
        assertEquals(FlowIpVersion.Ipv6, detectIpVersion("[2001:db8::1]"))
        assertEquals(FlowIpVersion.Unknown, detectIpVersion("example.com"))
        assertEquals(FlowIpVersion.Unknown, detectIpVersion("999.0.0.1"))
    }

    private fun event(
        app: String = "App",
        domain: String = "example.test",
        protocol: String,
        route: String = "System",
        packages: List<String> = emptyList(),
        lifecycle: FlowLifecycle = FlowLifecycle.Snapshot,
        blocked: Boolean = false,
        ipVersion: FlowIpVersion = FlowIpVersion.Ipv4,
        observedAt: Long = 1_700_000_000_000L,
    ) = FlowEventUi(
        appName = app,
        domain = domain,
        resolvedIp = "192.0.2.1",
        portProtocol = protocol,
        dnsPolicy = "System",
        selectedRoute = route,
        routeReason = "rule",
        riskWarning = null,
        recommendation = "check",
        status = "active",
        technicalDetails = "none",
        sourceLabel = "test",
        appPackages = packages,
        lifecycle = lifecycle,
        isBlocked = blocked,
        ipVersion = ipVersion,
        observedAt = observedAt,
    )
}
