// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal const val REDACTED_SECRET = "<redacted>"

/**
 * Secrets belonging to one stable profile id.
 *
 * The complete advanced sing-box object is encrypted because protocol-specific
 * credentials can be nested and new upstream fields must not accidentally fall
 * back to plaintext when sing-box adds a feature.
 */
internal data class ProfileSecrets(
    val socks5Password: String? = null,
    val vlessUuid: String? = null,
    val vlessXhttpExtra: String? = null,
    val singBoxOptionsJson: String? = null,
) {
    val isEmpty: Boolean
        get() = socks5Password.isNullOrEmpty() &&
            vlessUuid.isNullOrEmpty() &&
            vlessXhttpExtra.isNullOrEmpty() &&
            singBoxOptionsJson.isNullOrEmpty()

    fun merge(newer: ProfileSecrets): ProfileSecrets = ProfileSecrets(
        socks5Password = newer.socks5Password ?: socks5Password,
        vlessUuid = newer.vlessUuid ?: vlessUuid,
        vlessXhttpExtra = newer.vlessXhttpExtra ?: vlessXhttpExtra,
        singBoxOptionsJson = newer.singBoxOptionsJson ?: singBoxOptionsJson,
    )
}

internal interface ProfileSecretStore {
    fun load(): Map<String, ProfileSecrets>
    fun save(secretsByProfileId: Map<String, ProfileSecrets>)
}

/**
 * AES-256-GCM storage backed by an app-private key in Android Keystore.
 *
 * The encrypted file is kept in noBackupFilesDir. Android removes both the file
 * and the non-exportable key when the application is fully cleared.
 */
internal class AndroidKeystoreProfileSecretStore(context: Context) : ProfileSecretStore {
    private val encryptedFile = File(context.noBackupFilesDir, FILENAME)
    private val keyProvider = AndroidSecretKeyProvider()

    override fun load(): Map<String, ProfileSecrets> {
        if (!encryptedFile.exists()) return emptyMap()
        val envelope = encryptedFile.readText(Charsets.UTF_8)
        val plaintext = AesGcmSecretCodec.decrypt(envelope, keyProvider.getOrCreate())
        return ProfileSecretsJson.decode(plaintext)
    }

    override fun save(secretsByProfileId: Map<String, ProfileSecrets>) {
        encryptedFile.parentFile?.mkdirs()
        val plaintext = ProfileSecretsJson.encode(secretsByProfileId)
        val envelope = AesGcmSecretCodec.encrypt(plaintext, keyProvider.getOrCreate())
        encryptedFile.writeTextAtomically(envelope)
        encryptedFile.setReadable(false, false)
        encryptedFile.setWritable(false, false)
        encryptedFile.setReadable(true, true)
        encryptedFile.setWritable(true, true)
    }

    companion object {
        const val FILENAME = "profile_secrets.v1.json.aesgcm"
    }
}

internal class InMemoryProfileSecretStore(
    initial: Map<String, ProfileSecrets> = emptyMap(),
) : ProfileSecretStore {
    private var values = initial.toMap()

    override fun load(): Map<String, ProfileSecrets> = values

    override fun save(secretsByProfileId: Map<String, ProfileSecrets>) {
        values = secretsByProfileId.toMap()
    }
}

internal object AesGcmSecretCodec {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val ENVELOPE_VERSION = 1
    private const val TAG_LENGTH_BITS = 128

    fun encrypt(plaintext: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return JSONObject()
            .put("version", ENVELOPE_VERSION)
            .put("algorithm", TRANSFORMATION)
            .put("iv", cipher.iv.toBase64())
            .put("ciphertext", ciphertext.toBase64())
            .toString()
    }

    fun decrypt(envelope: String, key: SecretKey): String {
        val root = JSONObject(envelope)
        require(root.getInt("version") == ENVELOPE_VERSION) {
            "Unsupported encrypted secret-store version."
        }
        require(root.getString("algorithm") == TRANSFORMATION) {
            "Unsupported encrypted secret-store algorithm."
        }
        val iv = root.getString("iv").fromBase64()
        val ciphertext = root.getString("ciphertext").fromBase64()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    }
}

internal class AndroidSecretKeyProvider(
    private val keyAlias: String = PROFILE_SECRET_KEY_ALIAS,
) {
    fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val PROFILE_SECRET_KEY_ALIAS = "dev.vifs.viroutefs.profile-secrets.v1"
    }
}

private object ProfileSecretsJson {
    fun encode(secretsByProfileId: Map<String, ProfileSecrets>): String {
        val profiles = JSONObject()
        secretsByProfileId
            .filterKeys(String::isNotBlank)
            .filterValues { !it.isEmpty }
            .toSortedMap()
            .forEach { (profileId, secrets) ->
                profiles.put(
                    profileId,
                    JSONObject().apply {
                        secrets.socks5Password?.takeIf(String::isNotEmpty)?.let {
                            put("socks5Password", it)
                        }
                        secrets.vlessUuid?.takeIf(String::isNotEmpty)?.let {
                            put("vlessUuid", it)
                        }
                        secrets.vlessXhttpExtra?.takeIf(String::isNotEmpty)?.let {
                            put("vlessXhttpExtra", it)
                        }
                        secrets.singBoxOptionsJson?.takeIf(String::isNotEmpty)?.let {
                            put("singBoxOptionsJson", it)
                        }
                    },
                )
            }
        return JSONObject()
            .put("version", 1)
            .put("profiles", profiles)
            .toString()
    }

    fun decode(json: String): Map<String, ProfileSecrets> {
        val root = JSONObject(json)
        require(root.getInt("version") == 1) { "Unsupported profile secret schema." }
        val profiles = root.getJSONObject("profiles")
        return profiles.keys().asSequence().associateWith { profileId ->
            val value = profiles.getJSONObject(profileId)
            ProfileSecrets(
                socks5Password = value.optSecret("socks5Password"),
                vlessUuid = value.optSecret("vlessUuid"),
                vlessXhttpExtra = value.optSecret("vlessXhttpExtra"),
                singBoxOptionsJson = value.optSecret("singBoxOptionsJson"),
            )
        }
    }
}

private fun JSONObject.optSecret(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf(String::isNotEmpty)

private fun ByteArray.toBase64(): String = Base64.getEncoder().encodeToString(this)

private fun String.fromBase64(): ByteArray = Base64.getDecoder().decode(this)
