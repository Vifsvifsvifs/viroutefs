// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnGateCatalogTest {
    @Test
    fun quotedCatalogRowParsesAndCreatesDisabledOpenVpnProfile() {
        val openVpn = """
            client
            dev tun
            proto tcp
            remote vpn.example 443
            auth-user-pass
            <ca>
            -----BEGIN CERTIFICATE-----
            test
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        val encoded = Base64.getEncoder().encodeToString(openVpn.toByteArray())
        val catalog = """
            *vpn_servers
            #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
            vpn-1,203.0.113.10,500,27,125000000,"Test, Country",TC,4,86400000,100,999,2weeks,"Operator, Inc.","Hello, world",$encoded
            *
        """.trimIndent()

        val server = parseVpnGateCatalog(catalog).single()
        val preview = previewVpnGateProfile(server)
        val profile = preview.candidates.single().profile
        val endpoint = JSONObject(requireNotNull(profile.singBox).optionsJson)

        assertEquals("Test, Country", server.countryName)
        assertEquals("Operator, Inc.", server.operator)
        assertEquals(27, server.pingMillis)
        assertEquals(TunnelType.OpenVpn, profile.type)
        assertFalse(profile.enabled)
        assertTrue(profile.name.startsWith("VPNGate • Test, Country"))
        assertEquals("vpn", endpoint.getString("username"))
        assertEquals("vpn", endpoint.getString("password"))
        assertEquals(openVpn, decodeVpnGateOpenVpnConfig(server))
    }

    @Test
    fun duplicateServersAreCollapsedAndMissingPingIsAllowed() {
        val encoded = Base64.getEncoder().encodeToString("client\nremote example.test 1194".toByteArray())
        val header = "#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64"
        val row = "vpn-1,203.0.113.10,1,,2,Test,TC,0,0,0,0,none,,,$encoded"

        val servers = parseVpnGateCatalog("$header\n$row\n$row\n*")

        assertEquals(1, servers.size)
        assertEquals(null, servers.single().pingMillis)
    }

    @Test(expected = IllegalStateException::class)
    fun invalidBase64IsRejectedBeforeImport() {
        decodeVpnGateOpenVpnConfig(
            VpnGateServer(
                hostName = "broken",
                ipAddress = "203.0.113.1",
                score = 0,
                pingMillis = null,
                speedBitsPerSecond = 0,
                countryName = "Test",
                countryCode = "TC",
                activeSessions = 0,
                uptimeMillis = 0,
                totalUsers = 0,
                logType = "",
                operator = "",
                message = "",
                openVpnConfigBase64 = "not-base64!",
            ),
        )
    }

    @Test
    fun automaticRouteExcludesHomeCountryAndBuildsLatencyFailoverGroup() {
        val servers = listOf(
            server("ru-fast", "RU", 4),
            server("jp-fast", "JP", 12),
            server("de-fast", "DE", 18),
            server("us-fast", "US", 25),
            server("nl-fast", "NL", 30),
            server("es-fast", "ES", 40),
            server("fr-slow", "FR", 90),
        )

        val result = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = servers,
            excludedCountryCode = "ru",
        )
        val group = result.config.profileGroups.single { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
        val selectedProfiles = result.config.profiles.filter { it.id in group.memberProfileIds }

        assertEquals(listOf("JP", "DE", "US", "NL", "ES", "FR"), result.selectedServers.map { it.countryCode })
        assertEquals(ProfileGroupMode.Latency, group.mode)
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, result.config.defaultProfileId)
        assertEquals(6, selectedProfiles.size)
        assertTrue(selectedProfiles.none(TunnelProfile::enabled))
        assertTrue(selectedProfiles.none { it.name.contains("RU") })
        assertTrue(selectedProfiles.all { it.sourceSubscriptionId == null && it.sourceEntryKey == null })
        assertTrue(validateRoutingConfig(result.config).isEmpty())
    }

    @Test
    fun automaticVpnGateNeverBecomesDefaultWithoutSelectedApps() {
        val prepared = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = listOf(
                server("jp", "JP", 12),
                server("de", "DE", 18),
            ),
            excludedCountryCode = "RU",
        ).config.withAutomaticVpnGateEnabled(true)

        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, prepared.defaultProfileId)
        assertTrue(prepared.rules.none { it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID })
    }

    @Test
    fun preferredCountryKeepsAutomaticFailoverInsideThatCountry() {
        val result = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = listOf(
                server("jp-fast", "JP", 8),
                server("us-one", "US", 25),
                server("us-two", "US", 35),
                server("de-fast", "DE", 10),
            ),
            excludedCountryCode = "RU",
            preferredCountryCode = "us",
        )

        assertEquals(listOf("US", "US"), result.selectedServers.map(VpnGateServer::countryCode))
        assertEquals(
            "US",
            result.config.profileGroups.single { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }.preferredCountryCode,
        )
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, result.config.defaultProfileId)
    }

    @Test
    fun automaticVpnGateIsOneManagedRouteForSelectedApps() {
        val automatic = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = listOf(
                server("jp", "JP", 12),
                server("de", "DE", 18),
            ),
            excludedCountryCode = "RU",
        ).config
            .withAutomaticVpnGateApps(listOf("com.google.android.youtube"))
            .withAutomaticVpnGateEnabled(true)

        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, automatic.defaultProfileId)
        assertTrue(automatic.isAutomaticVpnGateEnabled())
        assertEquals(
            listOf("com.google.android.youtube"),
            automatic.rules.single { it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID }
                .appMatchers.map { it.value },
        )
        assertTrue(validateRoutingConfig(automatic).isEmpty())

        val disabled = automatic.withAutomaticVpnGateEnabled(false)
        assertFalse(disabled.isAutomaticVpnGateEnabled())
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, disabled.defaultProfileId)

        val removed = disabled.withoutAutomaticVpnGate()
        assertFalse(removed.hasAutomaticVpnGate())
        assertTrue(removed.profiles.none(TunnelProfile::isAutomaticVpnGateProfile))
        assertTrue(removed.rules.none { it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID })
        assertTrue(validateRoutingConfig(removed).isEmpty())
    }

    @Test
    fun betaTwelveLegacyVpnGateProfilesAreRepairedBeforeValidation() {
        val legacy = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = listOf(
                server("jp", "JP", 12),
                server("de", "DE", 18),
            ),
            excludedCountryCode = "RU",
        ).config.copy(
            version = 15,
            profiles = createAutomaticVpnGateRoute(
                config = RoutingConfigDefaults.defaultConfig(),
                servers = listOf(
                    server("jp", "JP", 12),
                    server("de", "DE", 18),
                ),
                excludedCountryCode = "RU",
            ).config.profiles.map { profile ->
                if (profile.isAutomaticVpnGateProfile()) {
                    profile.copy(
                        sourceSubscriptionId = "vpngate:auto",
                        sourceEntryKey = profile.id,
                    )
                } else {
                    profile
                }
            },
        )

        assertTrue(validateRoutingConfig(legacy).any { it.contains("подписка vpngate:auto не найдена") })
        val migrated = legacy.withMigratedVpnGateManagement()
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, migrated.defaultProfileId)
        assertFalse(migrated.isAutomaticVpnGateEnabled())
        assertTrue(migrated.profiles.filter(TunnelProfile::isAutomaticVpnGateProfile).all {
            it.sourceSubscriptionId == null && it.sourceEntryKey == null && !it.enabled
        })
        assertTrue(validateRoutingConfig(migrated).isEmpty())
    }

    @Test
    fun migrationAndUnifiedDeleteNeverRemovePersonalProfileAddedToGroup() {
        val servers = listOf(
            server("jp", "JP", 12),
            server("de", "DE", 18),
        )
        val automatic = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = servers,
            excludedCountryCode = "RU",
        ).config
        val personal = previewVpnGateProfile(server("personal", "DE", 25))
            .candidates.single().profile.copy(id = "profile_personal_openvpn", name = "Личный OpenVPN", enabled = true)
        val mixed = automatic.copy(
            version = 15,
            profiles = automatic.profiles + personal,
            profileGroups = automatic.profileGroups.map { group ->
                if (group.id == VPN_GATE_AUTOMATIC_GROUP_ID) {
                    group.copy(memberProfileIds = group.memberProfileIds + personal.id)
                } else {
                    group
                }
            },
        )

        val migrated = mixed.withMigratedVpnGateManagement()
        assertTrue(migrated.profiles.single { it.id == personal.id }.enabled)

        val removed = migrated.withoutAutomaticVpnGate()
        assertTrue(removed.profiles.any { it.id == personal.id && it.enabled })
        assertFalse(removed.hasAutomaticVpnGate())
        assertTrue(validateRoutingConfig(removed).isEmpty())
    }

    private fun server(host: String, country: String, ping: Int): VpnGateServer {
        val config = """
            client
            dev tun
            proto tcp
            remote $host.example 443
            <ca>
            -----BEGIN CERTIFICATE-----
            test-$host
            -----END CERTIFICATE-----
            </ca>
        """.trimIndent()
        return VpnGateServer(
            hostName = host,
            ipAddress = "203.0.113.$ping",
            score = 1_000L - ping,
            pingMillis = ping,
            speedBitsPerSecond = 100_000_000L,
            countryName = country,
            countryCode = country,
            activeSessions = 1,
            uptimeMillis = 86_400_000L,
            totalUsers = 10,
            logType = "2weeks",
            operator = "test",
            message = "",
            openVpnConfigBase64 = Base64.getEncoder().encodeToString(config.toByteArray()),
        )
    }
}
