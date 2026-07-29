// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import kotlin.test.Test
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
