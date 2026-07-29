// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RouteConstraintsTest {
    @Test
    fun parserAcceptsExactPortsAndRangesInStableOrder() {
        assertEquals(
            listOf(
                DestinationPortRange(53),
                DestinationPortRange(443),
                DestinationPortRange(8000, 8100),
            ),
            parseDestinationPortRanges("443, 8000-8100; 53"),
        )
    }

    @Test
    fun parserRejectsInvalidAndReversedRanges() {
        assertFailsWith<IllegalArgumentException> {
            parseDestinationPortRanges("8100-8000")
        }
        assertFailsWith<IllegalArgumentException> {
            parseDestinationPortRanges("65536")
        }
    }

    @Test
    fun tcpPortRuleDoesNotCatchUdpOrAnotherPort() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val rule = RouteRule(
            id = "secure-web",
            name = "Secure web",
            type = RouteRuleType.DOMAIN,
            targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
            priority = 10,
            matchers = listOf("example.com"),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
            destinationPorts = listOf(DestinationPortRange(443)),
            transport = RouteTransport.Tcp,
        )
        val engine = RouteEngine(defaults.copy(rules = listOf(rule) + defaults.rules))

        assertEquals(
            rule.id,
            engine.simulate(RouteQuery("api.example.com", 443, RouteTransport.Tcp)).matchedRule.id,
        )
        assertEquals(
            "default_system",
            engine.simulate(RouteQuery("api.example.com", 443, RouteTransport.Udp)).matchedRule.id,
        )
        assertEquals(
            "default_system",
            engine.simulate(RouteQuery("api.example.com", 80, RouteTransport.Tcp)).matchedRule.id,
        )
    }

    @Test
    fun ipv6AddressMatchesIpv6Cidr() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val rule = RouteRule(
            id = "ipv6-office",
            name = "IPv6 office",
            type = RouteRuleType.CIDR,
            targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
            priority = 10,
            matchers = listOf("2001:db8:42::/48"),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
        )
        val config = defaults.copy(rules = listOf(rule) + defaults.rules)

        assertEquals(rule.id, RouteEngine(config).simulate("2001:db8:42::1234").matchedRule.id)
        assertTrue(validateRoutingConfig(config).isEmpty())
    }
}
