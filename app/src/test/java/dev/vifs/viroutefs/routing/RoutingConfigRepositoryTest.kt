// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingConfigRepositoryTest {
    @Test
    fun emergencyBlockRoundTripsAndOldConfigsDefaultToOff() {
        val enabledJson = RoutingConfigJson.encode(
            RoutingConfigDefaults.defaultConfig().copy(emergencyBlockEnabled = true),
        )
        val oldJson = RoutingConfigJson.encode(RoutingConfigDefaults.defaultConfig())
            .replace(Regex(",?\\s*\"emergencyBlockEnabled\"\\s*:\\s*false"), "")

        assertTrue(RoutingConfigJson.decode(enabledJson).emergencyBlockEnabled)
        assertFalse(RoutingConfigJson.decode(oldJson).emergencyBlockEnabled)
    }

    @Test
    fun routingConfigSerializationDoesNotContainSocks5PasswordKeyByDefault() {
        val json = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = false)

        assertFalse(json.contains(SECRET))
        assertFalse(json.contains(PASSWORD_KEY_JSON))
        assertNull(RoutingConfigJson.decode(json).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
    }

    @Test
    fun exportJsonDoesNotContainSocks5PasswordKey() {
        val tempDir = createTempDirectory("viroutefs-routing-export").toFile()
        try {
            val repository = RoutingConfigRepository(
                tempDir,
                InMemoryProfileSecretStore(),
            )
            val repositoryJson = repository.exportJson(configWithSocks5Password())

            assertFalse(repositoryJson.contains(SECRET))
            assertFalse(repositoryJson.contains(PASSWORD_KEY_JSON))
            assertNull(RoutingConfigJson.decode(repositoryJson).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun normalSaveDoesNotWriteSocks5PasswordKeyToRoutingConfigJson() = runTest {
        val tempDir = createTempDirectory("viroutefs-routing-save").toFile()
        try {
            val secretStore = InMemoryProfileSecretStore()
            val repository = RoutingConfigRepository(tempDir, secretStore)
            val config = configWithSocks5Password()

            repository.save(config)

            val routingConfigFile = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME)
            val savedJson = routingConfigFile.readText()
            assertFalse(savedJson.contains(SECRET))
            assertFalse(savedJson.contains(PASSWORD_KEY_JSON))
            assertTrue(savedJson.contains("profile-secrets:$SOCKS5_ID"))
            assertEquals(
                mapOf(SOCKS5_ID to ProfileSecrets(socks5Password = SECRET)),
                secretStore.load(),
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun includeSocks5PasswordsFalseOmitsPasswordKeyEntirely() {
        val json = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = false)

        assertFalse(json.contains(PASSWORD_KEY_JSON))
    }

    @Test
    fun includeSocks5PasswordsTrueOnlyWritesNonBlankPassword() {
        val withPasswordJson = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = true)
        val blankPasswordJson = RoutingConfigJson.encode(
            configWithSocks5Password(password = " "),
            includeSocks5Passwords = true,
        )
        val nullPasswordJson = RoutingConfigJson.encode(
            configWithSocks5Password(password = null),
            includeSocks5Passwords = true,
        )

        assertTrue(withPasswordJson.contains(PASSWORD_KEY_JSON))
        assertTrue(withPasswordJson.contains(SECRET))
        assertFalse(blankPasswordJson.contains(PASSWORD_KEY_JSON))
        assertFalse(nullPasswordJson.contains(PASSWORD_KEY_JSON))
    }

    @Test
    fun legacyConfigWithEmbeddedSocks5PasswordCanBeMigratedAndSanitized() {
        val legacyJson = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = true)
        val decoded = RoutingConfigJson.decode(legacyJson)
        val legacySecrets = decoded.profileSecretsByProfileId()
        val sanitized = decoded.withoutProfileSecrets()
        val sanitizedJson = RoutingConfigJson.encode(sanitized)

        assertEquals(
            mapOf(SOCKS5_ID to ProfileSecrets(socks5Password = SECRET)),
            legacySecrets,
        )
        assertFalse(sanitizedJson.contains(SECRET))
        assertFalse(sanitizedJson.contains(PASSWORD_KEY_JSON))
        assertNull(RoutingConfigJson.decode(sanitizedJson).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
    }

    @Test
    fun plaintextBetaCredentialFileIsMigratedThenDeleted() = runTest {
        val tempDir = createTempDirectory("viroutefs-socks5-credentials").toFile()
        try {
            val routingConfigFile = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME)
            val credentialsFile = File(tempDir, Socks5CredentialStore.FILENAME)
            val legacyStore = Socks5CredentialStore(credentialsFile)
            val secretStore = InMemoryProfileSecretStore()
            val config = configWithSocks5Password(password = null)

            legacyStore.save(mapOf(SOCKS5_ID to SECRET))
            routingConfigFile.writeText(RoutingConfigJson.encode(config))
            val repository = RoutingConfigRepository(tempDir, secretStore, legacyStore)
            val loaded = repository.load()

            val routingJson = routingConfigFile.readText()
            assertFalse(routingJson.contains(SECRET))
            assertFalse(routingJson.contains(PASSWORD_KEY_JSON))
            assertEquals(
                SECRET,
                loaded.config.profiles.first { it.id == SOCKS5_ID }.socks5?.password,
            )
            assertEquals(
                mapOf(SOCKS5_ID to ProfileSecrets(socks5Password = SECRET)),
                secretStore.load(),
            )
            assertFalse(credentialsFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun routingConfigJsonRedactsVlessUuidAndRepositoryRestoresIt() = runTest {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val tempDir = createTempDirectory("viroutefs-vless-secrets").toFile()
        try {
            val secretStore = InMemoryProfileSecretStore()
            val repository = RoutingConfigRepository(tempDir, secretStore)
            repository.save(configWithVless(uuid))

            val json = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME).readText()
            val loadedVless = repository.load().config.profiles.first { it.id == VLESS_ID }.vless

            assertFalse(json.contains(uuid))
            assertTrue(json.contains(REDACTED_SECRET))
            assertEquals(uuid, loadedVless?.uuid)
            assertFalse(loadedVless?.safeSummary().orEmpty().contains(uuid))
            assertEquals(uuid, secretStore.load()[VLESS_ID]?.vlessUuid)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun configWithSocks5Password(password: String? = SECRET): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val socks5 = Socks5ProfileConfig(
            name = "Office SOCKS5",
            host = "127.0.0.1",
            port = 1080,
            username = "user",
            password = password,
        )
        val profile = TunnelProfile(
            id = SOCKS5_ID,
            name = socks5.name,
            type = TunnelType.Socks5,
            description = "SOCKS5 127.0.0.1:1080. Manual connectivity testing only.",
            mockOnly = true,
            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
            socks5 = socks5,
        )
        return defaults.copy(profiles = defaults.profiles + profile)
    }


    private fun configWithVless(uuid: String): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val vless = VlessProfileConfig(
            name = "Office VLESS",
            host = "vless.example",
            port = 443,
            uuid = uuid,
            securityMode = VlessSecurityMode.TLS,
        )
        val profile = TunnelProfile(
            id = VLESS_ID,
            name = vless.name,
            type = TunnelType.VLESS,
            description = "VLESS config-only profile.",
            mockOnly = true,
            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
            vless = vless,
        )
        return defaults.copy(profiles = defaults.profiles + profile)
    }

    companion object {
        private const val SOCKS5_ID = "socks5-test"
        private const val VLESS_ID = "vless-test"
        private const val SECRET = "test-secret-value"
        private const val PASSWORD_KEY_JSON = "\"password\""
    }
}
