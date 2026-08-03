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
            server("fr-slow", "FR", 90),
        )

        val result = createAutomaticVpnGateRoute(
            config = RoutingConfigDefaults.defaultConfig(),
            servers = servers,
            excludedCountryCode = "ru",
        )
        val group = result.config.profileGroups.single { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
        val selectedProfiles = result.config.profiles.filter { it.id in group.memberProfileIds }

        assertEquals(listOf("JP", "DE", "US", "NL"), result.selectedServers.map { it.countryCode })
        assertEquals(ProfileGroupMode.Latency, group.mode)
        assertEquals(group.id, result.config.defaultProfileId)
        assertEquals(4, selectedProfiles.size)
        assertTrue(selectedProfiles.all(TunnelProfile::enabled))
        assertTrue(selectedProfiles.none { it.name.contains("RU") })
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
