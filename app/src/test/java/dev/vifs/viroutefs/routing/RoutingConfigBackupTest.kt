// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RoutingConfigBackupTest {
    @Test
    fun encryptedBackupRoundTripsCompleteConfigWithoutPlaintextSecrets() {
        val password = "correct horse battery staple".toCharArray()
        val config = secretConfig()

        val encrypted = RoutingConfigBackup.encrypt(config, password)
        val envelopeText = encrypted.toString(Charsets.UTF_8)
        val restored = RoutingConfigBackup.decrypt(encrypted, password)

        assertTrue(JSONObject(envelopeText).getString("format") == RoutingConfigBackup.FORMAT)
        assertFalse(envelopeText.contains(SECRET))
        assertFalse(envelopeText.contains(password.concatToString()))
        assertEquals(
            SECRET,
            restored.profiles.first { it.id == PROFILE_ID }.socks5?.password,
        )
        assertEquals(config.rules.map { it.id }, restored.rules.map { it.id })
        assertEquals(config.rules.map { it.targetProfileId }, restored.rules.map { it.targetProfileId })
        assertEquals(config.dnsPolicies, restored.dnsPolicies)
    }

    @Test
    fun wrongPasswordAndTamperingAreRejectedWithSameSafeMessage() {
        val encrypted = RoutingConfigBackup.encrypt(secretConfig(), PASSWORD)
        val wrongPassword = assertFailsWith<IllegalArgumentException> {
            RoutingConfigBackup.decrypt(encrypted, "completely-wrong".toCharArray())
        }
        val envelope = JSONObject(encrypted.toString(Charsets.UTF_8))
        val payload = envelope.getString("payload")
        envelope.put(
            "payload",
            payload.replaceRange(
                0,
                1,
                if (payload.first() == 'A') "B" else "A",
            ),
        )
        val tampered = assertFailsWith<IllegalArgumentException> {
            RoutingConfigBackup.decrypt(envelope.toString().toByteArray(), PASSWORD)
        }

        assertEquals("Неверный пароль или файл резервной копии повреждён.", wrongPassword.message)
        assertEquals(wrongPassword.message, tampered.message)
    }

    @Test
    fun weakPasswordAndHostileKdfCostAreRejectedBeforeWork() {
        assertEquals(
            "Пароль должен содержать не менее 10 символов.",
            RoutingConfigBackup.validatePassword("short".toCharArray()),
        )
        val envelope = JSONObject(
            RoutingConfigBackup.encrypt(secretConfig(), PASSWORD).toString(Charsets.UTF_8),
        )
        envelope.getJSONObject("kdf").put("iterations", Int.MAX_VALUE)

        val failure = assertFailsWith<IllegalArgumentException> {
            RoutingConfigBackup.decrypt(envelope.toString().toByteArray(), PASSWORD)
        }

        assertEquals("Некорректное число итераций защиты.", failure.message)
    }

    @Test
    fun unsupportedBackupVersionDoesNotAttemptDecryption() {
        val envelope = JSONObject(
            RoutingConfigBackup.encrypt(secretConfig(), PASSWORD).toString(Charsets.UTF_8),
        ).put("version", RoutingConfigBackup.VERSION + 1)

        val failure = assertFailsWith<IllegalArgumentException> {
            RoutingConfigBackup.decrypt(envelope.toString().toByteArray(), PASSWORD)
        }

        assertEquals("Версия резервной копии пока не поддерживается.", failure.message)
    }

    private fun secretConfig(): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val socks5 = Socks5ProfileConfig(
            name = "Backup test",
            host = "127.0.0.1",
            port = 1080,
            username = "tester",
            password = SECRET,
        )
        return defaults.copy(
            profiles = defaults.profiles + TunnelProfile(
                id = PROFILE_ID,
                name = socks5.name,
                type = TunnelType.Socks5,
                description = "Encrypted backup fixture.",
                enabled = false,
                mockOnly = false,
                dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                socks5 = socks5,
            ),
        )
    }

    companion object {
        private const val PROFILE_ID = "backup-socks5"
        private const val SECRET = "never-appear-in-envelope"
        private val PASSWORD = "strong backup password".toCharArray()
    }
}
