package dev.vifs.viroutefs.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DnsEndpointTest {
    @Test
    fun blankUsesAndroidSystemResolver() {
        assertEquals(DnsTransport.SYSTEM, DnsEndpoint.parse("").transport)
        assertEquals(DnsTransport.SYSTEM, DnsEndpoint.parse("system").transport)
    }

    @Test
    fun plainAddressMeansUdpPort53() {
        val endpoint = DnsEndpoint.parse("1.1.1.1")

        assertEquals(DnsTransport.UDP, endpoint.transport)
        assertEquals("1.1.1.1", endpoint.host)
        assertEquals(53, endpoint.port)
    }

    @Test
    fun parsesTcpTlsAndIpv6Endpoints() {
        assertEquals(5353, DnsEndpoint.parse("tcp://8.8.8.8:5353").port)
        assertEquals(DnsTransport.TLS, DnsEndpoint.parse("tls://dns.google").transport)
        val ipv6 = DnsEndpoint.parse("udp://[2606:4700:4700::1111]:53")
        assertEquals("2606:4700:4700::1111", ipv6.host)
        assertEquals("UDP://[2606:4700:4700::1111]:53", ipv6.displayName)
    }

    @Test
    fun dohGetsStandardPathWhenItIsMissing() {
        val endpoint = DnsEndpoint.parse("https://dns.google")

        assertEquals(DnsTransport.HTTPS, endpoint.transport)
        assertEquals("https://dns.google/dns-query", endpoint.httpsUrl)
        assertTrue(endpoint.bootstrapNote.orEmpty().contains("bootstrap"))
    }

    @Test
    fun unsupportedAndUnsafeEndpointsAreRejected() {
        assertFailsWith<IllegalArgumentException> { DnsEndpoint.parse("quic://dns.example") }
        assertFailsWith<IllegalArgumentException> { DnsEndpoint.parse("ftp://dns.example") }
        assertFailsWith<IllegalArgumentException> { DnsEndpoint.parse("udp://user@dns.example") }
        assertFailsWith<IllegalArgumentException> { DnsEndpoint.parse("tcp://dns.example:not-a-port") }
        assertFailsWith<IllegalArgumentException> { DnsEndpoint.parse("tcp://1.1.1.1:70000") }
    }
}
