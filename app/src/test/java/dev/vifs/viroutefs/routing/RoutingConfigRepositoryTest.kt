// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingConfigRepositoryTest {
    @Test
    fun routingConfigSerializationDoesNotContainSocks5PasswordByDefault() {
        val json = RoutingConfigJson.encode(configWithSocks5Password())

        assertFalse(json.contains(SECRET))
        assertFalse(json.contains("\"password\""))
    }

    @Test
    fun exportJsonDoesNotContainSocks5Password() {
        val repositoryJson = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = false)

        assertFalse(repositoryJson.contains(SECRET))
        assertFalse(repositoryJson.contains("\"password\""))
        assertNull(RoutingConfigJson.decode(repositoryJson).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
    }

    @Test
    fun legacyConfigWithEmbeddedSocks5PasswordCanBeMigratedAndSanitized() {
        val legacyJson = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = true)
        val decoded = RoutingConfigJson.decode(legacyJson)
        val legacyPasswords = decoded.socks5PasswordsByProfileId()
        val sanitized = decoded.withoutSocks5Passwords()
        val sanitizedJson = RoutingConfigJson.encode(sanitized)

        assertEquals(mapOf(SOCKS5_ID to SECRET), legacyPasswords)
        assertFalse(sanitizedJson.contains(SECRET))
        assertFalse(sanitizedJson.contains("\"password\""))
        assertNull(RoutingConfigJson.decode(sanitizedJson).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
    }

    @Test
    fun credentialStoreKeepsPasswordOutsideRoutingConfigJson() {
        val tempDir = createTempDir(prefix = "viroutefs-socks5-credentials")
        try {
            val routingConfigFile = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME)
            val credentialsFile = File(tempDir, Socks5CredentialStore.FILENAME)
            val credentialStore = Socks5CredentialStore(credentialsFile)
            val config = configWithSocks5Password()

            credentialStore.save(config.socks5PasswordsByProfileId())
            routingConfigFile.writeText(RoutingConfigJson.encode(config))

            val routingConfigJson = routingConfigFile.readText()
            assertFalse(routingConfigJson.contains(SECRET))
            assertFalse(routingConfigJson.contains("\"password\""))
            assertEquals(mapOf(SOCKS5_ID to SECRET), credentialStore.load())
            assertTrue(credentialsFile.readText().contains(SECRET))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun configWithSocks5Password(): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val socks5 = Socks5ProfileConfig(
            name = "Office SOCKS5",
            host = "127.0.0.1",
            port = 1080,
            username = "user",
            password = SECRET,
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

    companion object {
        private const val SOCKS5_ID = "socks5-test"
        private const val SECRET = "test-secret-value"
    }
}
