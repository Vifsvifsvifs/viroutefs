// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import java.net.InetAddress
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class XrayRoutingConfigTest {
    @Test
    fun xhttpProfileCompilesToAnIsolatedLoopbackSocksRoute() {
        val profile = xhttpProfile().copy(
            pinnedPeerCertSha256 = "AA:BB:CC",
            verifyPeerCertByName = "front.example",
        )
        val compiled = compileXrayRuntime(
            listOf(
                XrayLocalProfile(
                    profileId = "xhttp-office",
                    localSocksPort = 22080,
                    profile = profile,
                    resolvedServerAddress = "203.0.113.17",
                ),
            ),
        )
        val root = JSONObject(compiled.json)
        val inbound = root.getJSONArray("inbounds").getJSONObject(0)
        val outbound = root.getJSONArray("outbounds").getJSONObject(0)
        val stream = outbound.getJSONObject("streamSettings")
        val xhttp = stream.getJSONObject("xhttpSettings")
        val tls = stream.getJSONObject("tlsSettings")
        val endpoint = outbound.getJSONObject("settings")
            .getJSONArray("vnext")
            .getJSONObject(0)

        assertEquals("127.0.0.1", inbound.getString("listen"))
        assertEquals(22080, inbound.getInt("port"))
        assertEquals("socks", inbound.getString("protocol"))
        assertTrue(inbound.getJSONObject("settings").getBoolean("udp"))
        assertEquals("vless", outbound.getString("protocol"))
        assertEquals("203.0.113.17", endpoint.getString("address"))
        assertEquals("xhttp", stream.getString("network"))
        assertEquals("packet-up", xhttp.getString("mode"))
        assertEquals("/route", xhttp.getString("path"))
        assertEquals("front.example", xhttp.getString("host"))
        assertEquals(8, xhttp.getJSONObject("extra").getInt("scMaxBufferedPosts"))
        assertEquals("tls", stream.getString("security"))
        assertEquals("front.example", tls.getString("serverName"))
        assertEquals("AA:BB:CC", tls.getString("pinnedPeerCertSha256"))
        assertEquals("front.example", tls.getString("verifyPeerCertByName"))
        assertFalse(tls.has("allowInsecure"))
        assertEquals(mapOf("xhttp-office" to 22080), compiled.profilePorts)
        assertFalse(compiled.json.contains("vless://"))
    }

    @Test
    fun androidDnsResolutionPrefersIpv4AndDoesNotChangeTlsHostname() {
        val resolved = resolveXrayServerAddress("edge.example") {
            arrayOf(
                InetAddress.getByName("2001:db8::17"),
                InetAddress.getByName("203.0.113.17"),
            )
        }

        assertEquals("203.0.113.17", resolved)
    }

    @Test
    fun invalidXhttpExtraIsRejectedBeforeTheNativeProcessStarts() {
        val error = assertFailsWith<IllegalArgumentException> {
            compileXrayRuntime(
                listOf(
                    XrayLocalProfile(
                        profileId = "broken",
                        localSocksPort = 22081,
                        profile = xhttpProfile().copy(xhttpExtra = "{broken"),
                    ),
                ),
            )
        }

        assertTrue(error.message.orEmpty().contains("valid JSON object"))
    }

    @Test
    fun xrayValidatorDoesNotAcceptARegularSingBoxVlessProfile() {
        val tunnel = TunnelProfile(
            id = "regular-vless",
            name = "Regular",
            type = TunnelType.VLESS,
            description = "test",
            vless = xhttpProfile(),
        )

        assertTrue(validateXrayProfile(tunnel).any { it.contains("Expected an Xray") })
    }

    private fun xhttpProfile() = VlessProfileConfig(
        name = "Office XHTTP",
        host = "edge.example",
        port = 443,
        uuid = "123e4567-e89b-12d3-a456-426614174000",
        transportType = "xhttp",
        securityMode = VlessSecurityMode.TLS,
        sni = "front.example",
        fingerprint = "chrome",
        path = "/route",
        hostHeader = "front.example",
        xhttpMode = "packet-up",
        xhttpExtra = """{"scMaxBufferedPosts":8}""",
    )
}
