// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import dev.vifs.viroutefs.routing.DestinationPortRange
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RouteTransport
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
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

    private fun event(
        app: String = "App",
        domain: String = "example.test",
        protocol: String,
        route: String = "System",
        packages: List<String> = emptyList(),
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
    )
}
