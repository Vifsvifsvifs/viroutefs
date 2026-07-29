// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.json.JSONObject

internal interface SubscriptionSecretStore {
    fun load(): Map<String, String>
    fun save(urlsBySubscriptionId: Map<String, String>)
}

/**
 * Full subscription URLs often contain access tokens. They therefore use a
 * separate AES-256-GCM file under noBackupFilesDir and never enter the regular
 * routing_config.json or diagnostic export.
 */
internal class AndroidKeystoreSubscriptionSecretStore(
    context: Context,
) : SubscriptionSecretStore {
    private val encryptedFile = File(context.noBackupFilesDir, FILENAME)
    private val keyProvider = SubscriptionSecretKeyProvider()

    override fun load(): Map<String, String> {
        if (!encryptedFile.exists()) return emptyMap()
        val envelope = encryptedFile.readText(Charsets.UTF_8)
        return decodeSubscriptionUrls(
            AesGcmSecretCodec.decrypt(envelope, keyProvider.getOrCreate()),
        )
    }

    override fun save(urlsBySubscriptionId: Map<String, String>) {
        encryptedFile.parentFile?.mkdirs()
        val plaintext = encodeSubscriptionUrls(urlsBySubscriptionId)
        val envelope = AesGcmSecretCodec.encrypt(plaintext, keyProvider.getOrCreate())
        encryptedFile.writeTextAtomically(envelope)
        encryptedFile.setReadable(false, false)
        encryptedFile.setWritable(false, false)
        encryptedFile.setReadable(true, true)
        encryptedFile.setWritable(true, true)
    }

    companion object {
        const val FILENAME = "subscription_secrets.v1.json.aesgcm"
    }
}

internal class InMemorySubscriptionSecretStore(
    initial: Map<String, String> = emptyMap(),
) : SubscriptionSecretStore {
    private var values = initial.toMap()

    override fun load(): Map<String, String> = values

    override fun save(urlsBySubscriptionId: Map<String, String>) {
        values = urlsBySubscriptionId.toMap()
    }
}

private fun encodeSubscriptionUrls(urlsBySubscriptionId: Map<String, String>): String {
    val values = JSONObject()
    urlsBySubscriptionId
        .filterKeys(String::isNotBlank)
        .filterValues(String::isNotBlank)
        .toSortedMap()
        .forEach(values::put)
    return JSONObject()
        .put("version", 1)
        .put("urls", values)
        .toString()
}

private fun decodeSubscriptionUrls(json: String): Map<String, String> {
    val root = JSONObject(json)
    require(root.optInt("version", -1) == 1) {
        "Unsupported subscription secret schema."
    }
    val values = root.getJSONObject("urls")
    return values.keys().asSequence()
        .mapNotNull { id ->
            values.optString(id).takeIf(String::isNotBlank)?.let { id to it }
        }
        .toMap()
}

private class SubscriptionSecretKeyProvider {
    fun getOrCreate(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
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
        private const val KEY_ALIAS = "dev.vifs.viroutefs.subscription-secrets.v1"
    }
}
