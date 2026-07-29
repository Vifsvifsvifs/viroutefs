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
    fun legacyByeDpiDisplayNameMigratesToCompatibilityLabel() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val oldConfig = defaults.copy(
            version = 7,
            profiles = defaults.profiles.map { profile ->
                if (profile.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID) {
                    profile.copy(name = "ByeDPI")
                } else {
                    profile
                }
            },
        )

        val migrated = RoutingConfigDefaults.ensureRequiredProfiles(oldConfig)
        val compatibilityProfile = migrated.profiles.single {
            it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID
        }

        assertEquals(CURRENT_ROUTING_CONFIG_VERSION, migrated.version)
        assertEquals(
            RoutingConfigDefaults.NETWORK_COMPATIBILITY_PROFILE_NAME,
            compatibilityProfile.name,
        )
        assertTrue(compatibilityProfile.platformNotes.orEmpty().contains("ByeDPI"))
    }

    @Test
    fun migratedBuiltInRoutesCannotKeepProtocolPayloads() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val invalidPayload = SingBoxProfileConfig(
            kind = SingBoxProfileKind.Outbound,
            optionsJson = """{"type":"trojan","server":""}""",
        )
        val oldConfig = defaults.copy(
            version = 8,
            profiles = defaults.profiles.map { profile ->
                when (profile.id) {
                    RoutingConfigDefaults.SYSTEM_PROFILE_ID -> profile.copy(
                        name = "Old direct",
                        enabled = false,
                        mockOnly = true,
                        singBox = invalidPayload,
                    )
                    RoutingConfigDefaults.BLOCK_PROFILE_ID -> profile.copy(
                        name = "Old block",
                        singBox = invalidPayload,
                    )
                    else -> profile
                }
            },
        )

        val migrated = RoutingConfigDefaults.ensureRequiredProfiles(oldConfig)
        val system = migrated.profiles.single {
            it.id == RoutingConfigDefaults.SYSTEM_PROFILE_ID
        }
        val block = migrated.profiles.single {
            it.id == RoutingConfigDefaults.BLOCK_PROFILE_ID
        }

        assertEquals("System / Система", system.name)
        assertEquals(TunnelType.Direct, system.type)
        assertTrue(system.enabled)
        assertNull(system.singBox)
        assertNull(system.socks5)
        assertNull(system.vless)
        assertEquals("Block", block.name)
        assertEquals(TunnelType.Block, block.type)
        assertNull(block.singBox)
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
