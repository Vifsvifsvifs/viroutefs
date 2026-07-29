// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import javax.crypto.KeyGenerator
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileSecretStoreTest {
    @Test
    fun aesGcmRoundTripDoesNotExposePlaintext() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val plaintext = """{"password":"correct horse battery staple","uuid":"secret-id"}"""

        val envelope = AesGcmSecretCodec.encrypt(plaintext, key)
        val restored = AesGcmSecretCodec.decrypt(envelope, key)

        assertEquals(plaintext, restored)
        assertFalse(envelope.contains("correct horse battery staple"))
        assertFalse(envelope.contains("secret-id"))
        assertTrue(envelope.contains("\"algorithm\":\"AES/GCM/NoPadding\""))
    }

    @Test
    fun aesGcmRejectsTamperedCiphertext() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val envelope = AesGcmSecretCodec.encrypt("secret", key)
        val root = JSONObject(envelope)
        val ciphertext = root.getString("ciphertext")
        val replacement = if (ciphertext.first() == 'A') 'B' else 'A'
        val tampered = root.put("ciphertext", replacement + ciphertext.drop(1)).toString()

        assertFails { AesGcmSecretCodec.decrypt(tampered, key) }
    }

    @Test
    fun nestedAdvancedProfileSecretsAreRedacted() {
        val source = """
            {
              "type":"wireguard",
              "private_key":"private-value",
              "peers":[{"public_key":"public-value","preshared_key":"psk-value"}],
              "transport":{"password":"nested-password"}
            }
        """.trimIndent()

        val redacted = redactSensitiveJson(source)

        assertFalse(redacted.contains("private-value"))
        assertFalse(redacted.contains("psk-value"))
        assertFalse(redacted.contains("nested-password"))
        assertTrue(redacted.contains("public-value"))
        assertTrue(redacted.contains(REDACTED_SECRET))
    }
}
