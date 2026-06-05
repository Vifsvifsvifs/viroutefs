// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import android.test.mock.MockContext
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoutingConfigRepositoryTest {
    @Test
    fun routingConfigSerializationDoesNotContainSocks5PasswordKeyByDefault() {
        val json = RoutingConfigJson.encode(configWithSocks5Password(), includeSocks5Passwords = false)

        assertFalse(json.contains(SECRET))
        assertFalse(json.contains(PASSWORD_KEY_JSON))
        assertNull(RoutingConfigJson.decode(json).profiles.first { it.id == SOCKS5_ID }.socks5?.password)
    }

    @Test
    fun exportJsonDoesNotContainSocks5PasswordKey() {
        val tempDir = createTempDir(prefix = "viroutefs-routing-export")
        try {
            val repository = RoutingConfigRepository(
                tempDir.routingTestContext(),
                Socks5CredentialStore(File(tempDir, Socks5CredentialStore.FILENAME)),
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
        val tempDir = createTempDir(prefix = "viroutefs-routing-save")
        try {
            val context = tempDir.routingTestContext()
            val credentialsFile = File(tempDir, "no_backup/${Socks5CredentialStore.FILENAME}")
            val repository = RoutingConfigRepository(context, Socks5CredentialStore(credentialsFile))
            val config = configWithSocks5Password()

            repository.save(config)

            val routingConfigFile = File(tempDir, RoutingConfigRepository.ROUTING_CONFIG_FILENAME)
            val savedJson = routingConfigFile.readText()
            assertFalse(savedJson.contains(SECRET))
            assertFalse(savedJson.contains(PASSWORD_KEY_JSON))
            assertEquals(mapOf(SOCKS5_ID to SECRET), Socks5CredentialStore(credentialsFile).load())
            assertTrue(credentialsFile.readText().contains(SECRET))
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
        val legacyPasswords = decoded.socks5PasswordsByProfileId()
        val sanitized = decoded.withoutSocks5Passwords()
        val sanitizedJson = RoutingConfigJson.encode(sanitized)

        assertEquals(mapOf(SOCKS5_ID to SECRET), legacyPasswords)
        assertFalse(sanitizedJson.contains(SECRET))
        assertFalse(sanitizedJson.contains(PASSWORD_KEY_JSON))
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

            val routingJson = routingConfigFile.readText()
            assertFalse(routingJson.contains(SECRET))
            assertFalse(routingJson.contains(PASSWORD_KEY_JSON))
            assertEquals(mapOf(SOCKS5_ID to SECRET), credentialStore.load())
            assertTrue(credentialsFile.readText().contains(SECRET))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun File.routingTestContext(): MockContext = object : MockContext() {
        override fun getFilesDir(): File = this@routingTestContext
        override fun getNoBackupFilesDir(): File = File(this@routingTestContext, "no_backup").also { it.mkdirs() }
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

    companion object {
        private const val SOCKS5_ID = "socks5-test"
        private const val SECRET = "test-secret-value"
        private const val PASSWORD_KEY_JSON = "\"password\""
    }
}
