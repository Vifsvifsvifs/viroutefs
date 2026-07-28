// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SingBoxProfileTest {
    @Test
    fun wireGuardTemplateIsAValidEndpointAndCannotRequestSystemInterface() {
        val config = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Endpoint,
            optionsJson = singBoxProfileTemplate(TunnelType.WireGuard),
        )

        assertTrue(validateSingBoxProfile(TunnelType.WireGuard, config).isEmpty())
        val normalized = normalizedSingBoxProfileObject(TunnelType.WireGuard, config, "profile_wg")
        assertEquals("profile_wg", normalized.getString("tag"))
        assertFalse(normalized.getBoolean("system"))
    }

    @Test
    fun openVpnAndOpenConnectTemplatesAreValidNonSystemEndpoints() {
        listOf(TunnelType.OpenVpn, TunnelType.OpenConnectAnyConnect).forEach { type ->
            val config = SingBoxProfileConfig(
                kind = SingBoxProfileKind.Endpoint,
                optionsJson = singBoxProfileTemplate(type),
            )

            assertTrue(validateSingBoxProfile(type, config).isEmpty(), type.name)
            val normalized = normalizedSingBoxProfileObject(type, config, "profile_${type.name}")
            assertEquals("profile_${type.name}", normalized.getString("tag"))
            assertFalse(normalized.getBoolean("system"))
        }
    }

    @Test
    fun hysteriaAndSnellTemplatesAreValidOutbounds() {
        listOf(TunnelType.Hysteria, TunnelType.Snell).forEach { type ->
            val config = SingBoxProfileConfig(
                kind = SingBoxProfileKind.Outbound,
                optionsJson = singBoxProfileTemplate(type),
            )

            assertTrue(validateSingBoxProfile(type, config).isEmpty(), type.name)
        }
    }

    @Test
    fun aFullSingBoxConfigurationIsRejected() {
        val config = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Outbound,
            optionsJson = """{"type":"trojan","server":"example.com","server_port":443,"password":"x","route":{}}""",
        )

        assertTrue(validateSingBoxProfile(TunnelType.Trojan, config).any { it.contains("один объект") })
    }

    @Test
    fun wrongEngineTypeIsExplained() {
        val config = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Outbound,
            optionsJson = """{"type":"vmess","server":"example.com","server_port":443,"uuid":"x"}""",
        )

        assertTrue(validateSingBoxProfile(TunnelType.Trojan, config).any { it.contains("'trojan'") })
    }

    @Test
    fun repositoryRoundTripsAdvancedProfile() {
        val base = RoutingConfigDefaults.defaultConfig()
        val advanced = TunnelProfile(
            id = "hy2",
            name = "Hysteria 2",
            type = TunnelType.Hysteria2,
            description = "test",
            mockOnly = false,
            singBox = SingBoxProfileConfig(
                SingBoxProfileKind.Outbound,
                singBoxProfileTemplate(TunnelType.Hysteria2),
            ),
        )

        val decoded = RoutingConfigJson.decode(
            RoutingConfigJson.encode(base.copy(profiles = base.profiles + advanced)),
        )
        val actual = decoded.profiles.first { it.id == advanced.id }

        assertEquals(SingBoxProfileKind.Outbound, actual.singBox?.kind)
        assertEquals(
            "hysteria2",
            JSONObject(actual.singBox?.optionsJson.orEmpty()).getString("type"),
        )
        assertTrue(validateRoutingConfig(decoded).isEmpty())
    }
}
