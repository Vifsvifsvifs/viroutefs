// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class VlessProfileTest {
    @Test
    fun validVlessConfigAccepted() {
        assertTrue(validateVlessProfile(validProfile()).isEmpty())
    }

    @Test
    fun parseBasicVlessUri() {
        val result = parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443#Lab%20VLESS")

        val profile = assertIs<VlessUriParseResult.Success>(result).profile
        assertEquals("123e4567-e89b-12d3-a456-426614174000", profile.uuid)
        assertEquals("example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("Lab VLESS", profile.name)
    }

    @Test
    fun parseTlsParams() {
        val tls = assertIs<VlessUriParseResult.Success>(
            parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?security=tls&sni=example.com&alpn=h2%2Chttp%2F1.1#TLS"),
        ).profile

        assertEquals(VlessSecurityMode.TLS, tls.securityMode)
        assertEquals("example.com", tls.sni)
        assertEquals("h2,http/1.1", tls.alpn)
    }

    @Test
    fun parseWsParams() {
        val ws = assertIs<VlessUriParseResult.Success>(
            parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?type=ws&path=%2Fchat&host=edge.example#WS"),
        ).profile

        assertEquals("ws", ws.transportType)
        assertEquals("/chat", ws.path)
        assertEquals("edge.example", ws.hostHeader)
    }

    @Test
    fun parseGrpcParams() {
        val grpc = assertIs<VlessUriParseResult.Success>(
            parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?type=grpc&serviceName=my-service#Grpc"),
        ).profile

        assertEquals("grpc", grpc.transportType)
        assertEquals("my-service", grpc.serviceName)
    }

    @Test
    fun parseTlsRealityParams() {
        val tls = assertIs<VlessUriParseResult.Success>(
            parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?type=ws&security=tls&encryption=none&path=%2Fchat&host=edge.example#TLS"),
        ).profile
        assertEquals("ws", tls.transportType)
        assertEquals(VlessSecurityMode.TLS, tls.securityMode)
        assertEquals("none", tls.encryption)
        assertEquals("/chat", tls.path)
        assertEquals("edge.example", tls.hostHeader)

        val reality = assertIs<VlessUriParseResult.Success>(
            parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?type=tcp&security=reality&sni=www.example.com&fp=chrome&pbk=PUBLIC&sid=abcd#Reality"),
        ).profile
        assertEquals("tcp", reality.transportType)
        assertEquals(VlessSecurityMode.REALITY, reality.securityMode)
        assertEquals("www.example.com", reality.sni)
        assertEquals("chrome", reality.fingerprint)
        assertEquals("PUBLIC", reality.publicKey)
        assertEquals("abcd", reality.shortId)
    }

    @Test
    fun parseFlowSniFingerprintPublicKeyAndShortId() {
        val result = parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:443?flow=xtls-rprx-vision&sni=example.org&fp=firefox&pbk=abc123&sid=01")

        val profile = assertIs<VlessUriParseResult.Success>(result).profile
        assertEquals("xtls-rprx-vision", profile.flow)
        assertEquals("example.org", profile.sni)
        assertEquals("firefox", profile.fingerprint)
        assertEquals("abc123", profile.publicKey)
        assertEquals("01", profile.shortId)
    }

    @Test
    fun invalidUuidRejected() {
        val errors = validateVlessProfile(validProfile(uuid = "not-a-uuid"))
        val parseResult = parseVlessUri("vless://not-a-uuid@example.com:443#Bad")

        assertTrue(errors.any { it.contains("UUID") })
        assertIs<VlessUriParseResult.Error>(parseResult)
    }

    @Test
    fun invalidPortRejected() {
        val errors = validateVlessProfile(validProfile(port = 0))
        val parseResult = parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@example.com:70000#Bad")

        assertTrue(errors.any { it.contains("1..65535") })
        assertIs<VlessUriParseResult.Error>(parseResult)
    }

    @Test
    fun missingHostRejected() {
        val parseResult = parseVlessUri("vless://123e4567-e89b-12d3-a456-426614174000@:443#Bad")

        assertIs<VlessUriParseResult.Error>(parseResult)
    }

    @Test
    fun missingUuidRejected() {
        val parseResult = parseVlessUri("vless://example.com:443#Bad")

        assertIs<VlessUriParseResult.Error>(parseResult)
    }

    @Test
    fun exportedUriRoundTrips() {
        val original = validProfile(
            transportType = "grpc",
            encryption = "none",
            flow = "xtls-rprx-vision",
            sni = "sni.example",
            publicKey = "pub",
            shortId = "sid",
            fingerprint = "chrome",
            path = "/service",
            hostHeader = "front.example",
            alpn = "h2,http/1.1",
            serviceName = "my-service",
            pinnedPeerCertSha256 = "AA:BB:CC",
            verifyPeerCertByName = false,
        )

        val exported = exportVlessUri(original)
        val reparsed = assertIs<VlessUriParseResult.Success>(parseVlessUri(exported)).profile

        assertEquals(original.name, reparsed.name)
        assertEquals(original.host, reparsed.host)
        assertEquals(original.port, reparsed.port)
        assertEquals(original.uuid, reparsed.uuid)
        assertEquals(original.transportType, reparsed.transportType)
        assertEquals(original.securityMode, reparsed.securityMode)
        assertEquals(original.encryption, reparsed.encryption)
        assertEquals(original.flow, reparsed.flow)
        assertEquals(original.sni, reparsed.sni)
        assertEquals(original.publicKey, reparsed.publicKey)
        assertEquals(original.shortId, reparsed.shortId)
        assertEquals(original.fingerprint, reparsed.fingerprint)
        assertEquals(original.path, reparsed.path)
        assertEquals(original.hostHeader, reparsed.hostHeader)
        assertEquals(original.alpn, reparsed.alpn)
        assertEquals(original.serviceName, reparsed.serviceName)
        assertEquals(original.pinnedPeerCertSha256, reparsed.pinnedPeerCertSha256)
        assertEquals(original.verifyPeerCertByName, reparsed.verifyPeerCertByName)
    }

    @Test
    fun safeSummaryDoesNotExposeUuid() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val summary = validProfile(uuid = uuid).safeSummary()

        assertFalse(summary.contains(uuid))
        assertFalse(summary.contains("123e4567"))
        assertTrue(summary.contains("Lab VLESS @ example.com:443"))
    }

    @Test
    fun maskedPreviewDoesNotExposeFullUuid() {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val preview = validProfile(uuid = uuid).maskedPreview()

        assertFalse(preview.contains(uuid))
        assertTrue(preview.contains("123e4567-…-4000"))
    }

    private fun validProfile(
        port: Int = 443,
        uuid: String = "123e4567-e89b-12d3-a456-426614174000",
        transportType: String? = null,
        encryption: String? = null,
        flow: String? = null,
        sni: String? = null,
        publicKey: String? = null,
        shortId: String? = null,
        fingerprint: String? = null,
        path: String? = null,
        hostHeader: String? = null,
        alpn: String? = null,
        serviceName: String? = null,
        pinnedPeerCertSha256: String? = null,
        verifyPeerCertByName: Boolean? = null,
    ): VlessProfileConfig = VlessProfileConfig(
        name = "Lab VLESS",
        host = "example.com",
        port = port,
        uuid = uuid,
        transportType = transportType,
        securityMode = VlessSecurityMode.TLS,
        encryption = encryption,
        flow = flow,
        sni = sni,
        publicKey = publicKey,
        shortId = shortId,
        fingerprint = fingerprint,
        path = path,
        hostHeader = hostHeader,
        alpn = alpn,
        serviceName = serviceName,
        pinnedPeerCertSha256 = pinnedPeerCertSha256,
        verifyPeerCertByName = verifyPeerCertByName,
    )
}
