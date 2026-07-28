// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.singBoxProfileTemplate
import dev.vifs.viroutefs.routing.withDefaultRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReleaseReadinessTest {
    @Test
    fun defaultConfigurationUsesPhoneInternetWithoutRequiringVpn() {
        val report = evaluateReleaseReadiness(RoutingConfigDefaults.defaultConfig())
        val defaultRoute = report.items.single { it.id == "default_route" }

        assertEquals(ReadinessState.Ready, defaultRoute.state)
        assertTrue(defaultRoute.summary.contains("интернет", ignoreCase = true))
        assertTrue(report.runtimeReadyProtocols.contains(TunnelType.OpenVpn))
    }

    @Test
    fun configuredRuntimeDefaultRouteIsReportedReady() {
        val defaultRoute = TunnelProfile(
            id = "openvpn-default-route",
            name = "Work OpenVPN",
            type = TunnelType.OpenVpn,
            description = "Test default route",
            enabled = true,
            mockOnly = false,
            singBox = SingBoxProfileConfig(
                kind = SingBoxProfileKind.Endpoint,
                optionsJson = singBoxProfileTemplate(TunnelType.OpenVpn),
            ),
        )
        val config = RoutingConfigDefaults.defaultConfig()
            .copy(profiles = RoutingConfigDefaults.defaultConfig().profiles + defaultRoute)
            .withDefaultRoute(defaultRoute.id)

        val report = evaluateReleaseReadiness(config)

        assertEquals(ReadinessState.Ready, report.items.single { it.id == "default_route" }.state)
        assertEquals(ReadinessState.Ready, report.items.single { it.id == "profiles" }.state)
    }

    @Test
    fun reportKeepsLegacyProtocolsOutOfRuntimeReadyList() {
        val report = evaluateReleaseReadiness(RoutingConfigDefaults.defaultConfig())

        assertTrue(TunnelType.Pptp in report.legacyProtocols)
        assertTrue(TunnelType.Pptp !in report.runtimeReadyProtocols)
        assertTrue(report.items.single { it.id == "legacy" }.summary.contains("PPTP"))
    }
}
