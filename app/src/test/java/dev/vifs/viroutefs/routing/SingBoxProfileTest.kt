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
    fun openConnectTemplateIsAValidNonSystemEndpoint() {
        val config = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Endpoint,
            optionsJson = singBoxProfileTemplate(TunnelType.OpenConnectAnyConnect),
        )

        assertTrue(validateSingBoxProfile(TunnelType.OpenConnectAnyConnect, config).isEmpty())
        val normalized = normalizedSingBoxProfileObject(
            TunnelType.OpenConnectAnyConnect,
            config,
            "profile_openconnect",
        )
        assertEquals("profile_openconnect", normalized.getString("tag"))
        assertFalse(normalized.getBoolean("system"))
    }

    @Test
    fun openVpnRequiresTrustAndClientCertificateKeyPair() {
        val incomplete = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Endpoint,
            optionsJson = singBoxProfileTemplate(TunnelType.OpenVpn),
        )
        val withTrust = JSONObject(incomplete.optionsJson).apply {
            getJSONObject("tls").put("certificate", TEST_CERTIFICATE)
        }
        val certificateOnly = JSONObject(withTrust.toString()).apply {
            getJSONObject("tls").put("client_certificate", TEST_CERTIFICATE)
        }
        val complete = JSONObject(certificateOnly.toString()).apply {
            getJSONObject("tls").put("client_key", TEST_PRIVATE_KEY)
        }

        assertTrue(
            validateSingBoxProfile(TunnelType.OpenVpn, incomplete).any { it.contains("CA-сертификат") },
        )
        assertTrue(
            validateSingBoxProfile(
                TunnelType.OpenVpn,
                SingBoxProfileConfig(SingBoxProfileKind.Endpoint, certificateOnly.toString()),
            ).any { it.contains("вместе") },
        )
        assertTrue(
            validateSingBoxProfile(
                TunnelType.OpenVpn,
                SingBoxProfileConfig(SingBoxProfileKind.Endpoint, complete.toString()),
            ).isEmpty(),
        )
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

    companion object {
        private const val TEST_CERTIFICATE =
            "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----"
        private const val TEST_PRIVATE_KEY =
            "-----BEGIN PRIVATE KEY-----\ntest\n-----END PRIVATE KEY-----"
    }
}
