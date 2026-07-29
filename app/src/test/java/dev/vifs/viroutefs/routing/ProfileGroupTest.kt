// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.engine.routedProfileIds
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileGroupTest {
    @Test
    fun manualGroupCanBeTheExplicitDefaultAndExpandsToItsMembers() {
        val first = socksProfile("first")
        val second = socksProfile("second")
        val group = ProfileGroup(
            id = "office",
            name = "Office",
            mode = ProfileGroupMode.Manual,
            memberProfileIds = listOf(first.id, second.id),
            selectedProfileId = second.id,
        )
        val defaults = RoutingConfigDefaults.defaultConfig()
        val config = defaults.copy(
            profiles = defaults.profiles + first + second,
            profileGroups = listOf(group),
            defaultProfileId = group.id,
            rules = defaults.rules.map { it.copy(targetProfileId = group.id) },
        )

        assertTrue(validateRoutingConfig(config).isEmpty())
        assertNull(defaultRouteActivationError(config))
        assertEquals(setOf(first.id, second.id), config.routedProfileIds())
    }

    @Test
    fun latencyGroupRejectsImplicitBlockAndNonHttpsHealthCheck() {
        val first = socksProfile("first")
        val defaults = RoutingConfigDefaults.defaultConfig()
        val group = ProfileGroup(
            id = "unsafe",
            name = "Unsafe",
            mode = ProfileGroupMode.Latency,
            memberProfileIds = listOf(first.id, RoutingConfigDefaults.BLOCK_PROFILE_ID),
            testUrl = "http://example.com/health",
            testIntervalSeconds = 5,
        )
        val errors = validateRoutingConfig(
            defaults.copy(
                profiles = defaults.profiles + first,
                profileGroups = listOf(group),
            ),
        )

        assertTrue(errors.any { it.contains("Block") })
        assertTrue(errors.any { it.contains("HTTPS") })
        assertTrue(errors.any { it.contains("интервал") })
    }

    @Test
    fun removingAProfileRemovesBrokenGroupAndFailsItsRulesClosed() {
        val first = socksProfile("first")
        val second = socksProfile("second")
        val group = ProfileGroup(
            id = "pair",
            name = "Pair",
            mode = ProfileGroupMode.Manual,
            memberProfileIds = listOf(first.id, second.id),
            selectedProfileId = first.id,
        )
        val defaults = RoutingConfigDefaults.defaultConfig()
        val explicit = defaults.rules.first().copy(
            id = "through-pair",
            type = RouteRuleType.DOMAIN,
            matchers = listOf("example.com"),
            targetProfileId = group.id,
        )
        val config = defaults.copy(
            profiles = defaults.profiles + first + second,
            profileGroups = listOf(group),
            rules = defaults.rules + explicit,
            defaultProfileId = group.id,
        )

        val removed = config.withoutProfile(first.id)

        assertTrue(removed.profileGroups.isEmpty())
        assertEquals(
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
            removed.rules.first { it.id == explicit.id }.targetProfileId,
        )
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, removed.defaultProfileId)
        assertTrue(validateRoutingConfig(removed).isEmpty())
    }

    private fun socksProfile(id: String): TunnelProfile = TunnelProfile(
        id = id,
        name = id,
        type = TunnelType.Socks5,
        description = "test",
        enabled = true,
        mockOnly = false,
        socks5 = Socks5ProfileConfig(
            name = id,
            host = "192.0.2.1",
            port = 1080,
        ),
    )
}
