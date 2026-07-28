// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultRouteTest {
    @Test
    fun freshConfigurationUsesNormalPhoneInternet() {
        val config = RoutingConfigDefaults.defaultConfig()
        val defaultRule = config.rules.single { it.type == RouteRuleType.DEFAULT }

        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, config.defaultProfileId)
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, defaultRule.targetProfileId)
        assertNull(defaultRouteActivationError(config))
    }

    @Test
    fun oldConfigurationWithoutProviderMigratesToSystem() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val oldConfig = defaults.copy(
            version = 6,
            defaultProfileId = null,
            rules = defaults.rules.map { rule ->
                if (rule.type == RouteRuleType.DEFAULT) {
                    rule.copy(
                        name = "Provider tunnel not selected",
                        targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
                    )
                } else {
                    rule
                }
            },
        )

        val migrated = RoutingConfigDefaults.ensureRequiredProfiles(oldConfig)
        val defaultRule = migrated.rules.single { it.type == RouteRuleType.DEFAULT }

        assertEquals(CURRENT_ROUTING_CONFIG_VERSION, migrated.version)
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, migrated.defaultProfileId)
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, defaultRule.targetProfileId)
        assertNull(defaultRouteActivationError(migrated))
    }

    @Test
    fun unavailableCustomDefaultStillFailsClosed() {
        val config = RoutingConfigDefaults.defaultConfig().copy(
            defaultProfileId = "missing-profile",
        )

        val error = defaultRouteActivationError(config)

        requireNotNull(error)
        assertTrue(error.contains("не найден", ignoreCase = true))
    }
}
