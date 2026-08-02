// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class ProfileImportTest {
    @Test
    fun vlessImportMasksUuidAndKeepsProfileDisabled() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val preview = previewProfileImport(
            "vless://$uuid@example.com:443?security=tls&type=tcp#Office",
        )
        val candidate = preview.candidates.single()

        assertEquals(TunnelType.VLESS, candidate.profile.type)
        assertFalse(candidate.profile.enabled)
        assertFalse(candidate.profile.vless!!.enabled)
        assertFalse(candidate.maskedPreview.contains(uuid))
        assertTrue(candidate.maskedPreview.contains("123e4567-…-4000"))
    }

    @Test
    fun vmessAndSingBoxJsonImportsDoNotExposeSecretsInPreview() {
        val vmessJson = """
            {"v":"2","ps":"VMess office","add":"vmess.example","port":"443",
             "id":"123e4567-e89b-12d3-a456-426614174000","scy":"auto","tls":"tls"}
        """.trimIndent()
        val vmess = Base64.getEncoder().encodeToString(
            vmessJson.toByteArray(StandardCharsets.UTF_8),
        )
        val vmessPreview = previewProfileImport("vmess://$vmess")
        val trojanPreview = previewProfileImport(
            """{"type":"trojan","tag":"Trojan office","server":"trojan.example","server_port":443,"password":"never-show-this"}""",
        )

        assertEquals(TunnelType.VMess, vmessPreview.candidates.single().profile.type)
        assertEquals(TunnelType.Trojan, trojanPreview.candidates.single().profile.type)
        assertFalse(vmessPreview.candidates.single().maskedPreview.contains("123e4567-e89b"))
        assertFalse(trojanPreview.candidates.single().maskedPreview.contains("never-show-this"))
        assertTrue(trojanPreview.candidates.single().maskedPreview.contains(REDACTED_SECRET))
    }

    @Test
    fun openVpnTextIsRecognizedAsDisabledProfile() {
        val preview = previewProfileImport(
            """
            client
            dev tun
            proto udp
            remote vpn.example 1194
            auth-user-pass
            <ca>
            -----BEGIN CERTIFICATE-----
            test
            -----END CERTIFICATE-----
            </ca>
            """.trimIndent(),
        )

        assertEquals(TunnelType.OpenVpn, preview.candidates.single().profile.type)
        assertFalse(preview.candidates.single().profile.enabled)
    }

    @Test
    fun wireGuardConfImportsAsDisabledEndpointAndMasksKeys() {
        val privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 1 })
        val publicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 2 })
        val preSharedKey = Base64.getEncoder().encodeToString(ByteArray(32) { 3 })
        val preview = previewProfileImport(
            """
            [Interface]
            PrivateKey = $privateKey
            Address = 10.20.0.2/32, fd00:20::2/128
            DNS = 9.9.9.9
            MTU = 1380
            PostUp = should-never-run

            [Peer]
            PublicKey = $publicKey
            PresharedKey = $preSharedKey
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = vpn.example:51820
            PersistentKeepalive = 25
            """.trimIndent(),
        )
        val candidate = preview.candidates.single()
        val profile = candidate.profile
        val endpoint = JSONObject(profile.singBox!!.optionsJson)
        val peer = endpoint.getJSONArray("peers").getJSONObject(0)

        assertEquals(TunnelType.WireGuard, profile.type)
        assertFalse(profile.enabled)
        assertEquals(SingBoxProfileKind.Endpoint, profile.singBox.kind)
        assertEquals(2, endpoint.getJSONArray("address").length())
        assertEquals(1380, endpoint.getInt("mtu"))
        assertEquals("vpn.example", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
        assertEquals(25, peer.getInt("persistent_keepalive_interval"))
        assertFalse(candidate.maskedPreview.contains(privateKey))
        assertFalse(candidate.maskedPreview.contains(preSharedKey))
        assertTrue(candidate.warnings.any { it.contains("DNS") })
        assertTrue(candidate.warnings.any { it.contains("postup") })
    }

    @Test
    fun wireGuardConfRejectsInvalidKeyWithoutLeakingIt() {
        val error = assertFailsWith<IllegalArgumentException> {
            importWireGuardProfile(
                """
                [Interface]
                PrivateKey = definitely-not-a-wireguard-key
                Address = 10.0.0.2/32
                [Peer]
                PublicKey = also-invalid
                AllowedIPs = 0.0.0.0/0
                Endpoint = vpn.example:51820
                """.trimIndent(),
            )
        }

        assertTrue(error.message.orEmpty().contains("PrivateKey"))
        assertFalse(error.message.orEmpty().contains("definitely-not"))
    }

    @Test
    fun wireGuardConfAcceptsBracketedIpv6PeerEndpoint() {
        val privateKey = Base64.getEncoder().encodeToString(ByteArray(32) { 4 })
        val publicKey = Base64.getEncoder().encodeToString(ByteArray(32) { 5 })
        val imported = importWireGuardProfile(
            """
            [Interface]
            PrivateKey = $privateKey
            Address = fd00:40::2/128
            [Peer]
            PublicKey = $publicKey
            AllowedIPs = ::/0
            Endpoint = [2001:db8::40]:51820
            """.trimIndent(),
        )
        val peer = JSONObject(imported.optionsJson)
            .getJSONArray("peers")
            .getJSONObject(0)

        assertEquals("2001:db8::40", peer.getString("address"))
        assertEquals(51820, peer.getInt("port"))
    }

    @Test
    fun duplicateCanBeSkippedReplacedOrCopied() {
        val preview = previewProfileImport("socks5://user:secret@127.0.0.1:1080#Local")
        val first = applyProfileImport(
            RoutingConfigDefaults.defaultConfig(),
            preview,
            ImportDuplicateResolution.Skip,
        )
        val skipped = applyProfileImport(first.config, preview, ImportDuplicateResolution.Skip)
        val replaced = applyProfileImport(first.config, preview, ImportDuplicateResolution.Replace)
        val copied = applyProfileImport(first.config, preview, ImportDuplicateResolution.Copy)

        assertEquals(1, first.added)
        assertEquals(1, skipped.skipped)
        assertEquals(1, replaced.replaced)
        assertEquals(1, copied.added)
        assertEquals(first.config.profiles.size + 1, copied.config.profiles.size)
    }

    @Test
    fun xrayXhttpConfigIsRecognizedButNotMisrepresentedAsSingBoxCompatible() {
        val preview = previewProfileImport(
            """
            {
              "outbounds": [{
                "protocol": "vless",
                "settings": {"vnext": [{"address": "example.com", "users": [{"id": "secret-id"}]}]},
                "streamSettings": {"network": "xhttp", "security": "tls"}
              }]
            }
            """.trimIndent(),
        )

        assertTrue(preview.isEmpty)
        assertTrue(preview.warnings.any { it.contains("Xray/v2rayNG") })
        assertTrue(preview.warnings.any { it.contains("XHTTP") })
        assertFalse(preview.warnings.joinToString().contains("secret-id"))
    }

    @Test
    fun v2rayNgXhttpUriImportsAsDisabledXrayProfileWithoutLeakingSecrets() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val preview = previewProfileImport(
            "vless://$uuid@edge.example:443" +
                "?encryption=none&security=tls&type=xhttp" +
                "&host=front.example&path=%2Fprivate-route&mode=packet-up" +
                "&extra=%7B%22scMaxBufferedPosts%22%3A8%7D#Phone",
        )
        val candidate = preview.candidates.single()
        val profile = candidate.profile

        assertEquals(TunnelType.XrayVlessReality, profile.type)
        assertFalse(profile.enabled)
        assertFalse(profile.vless!!.enabled)
        assertEquals("xhttp", profile.vless.transportType)
        assertEquals("packet-up", profile.vless.xhttpMode)
        assertEquals("""{"scMaxBufferedPosts":8}""", profile.vless.xhttpExtra)
        assertFalse(candidate.maskedPreview.contains(uuid))
        assertFalse(candidate.maskedPreview.contains("/private-route"))
        assertFalse(candidate.maskedPreview.contains("front.example"))
        assertTrue(candidate.maskedPreview.contains("XHTTP extra options: provided"))
    }
}
