// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import org.json.JSONObject
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-protected, self-describing ViRouteFS backup envelope.
 *
 * The plaintext contains the complete configuration including profile secrets,
 * but exists only in memory while encrypting/decrypting. The output never
 * stores the password or a password verifier.
 */
internal object RoutingConfigBackup {
    fun encrypt(
        config: RoutingConfig,
        password: CharArray,
        random: SecureRandom = SecureRandom(),
    ): ByteArray {
        requireStrongPassword(password)
        val plaintext = RoutingConfigJson.encode(config, includeSocks5Passwords = true)
            .toByteArray(Charsets.UTF_8)
        require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
            "Конфигурация слишком велика для резервной копии."
        }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val key = deriveKey(password, salt, KDF_ITERATIONS)
        return try {
            val cipher = Cipher.getInstance(CIPHER_NAME)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(ASSOCIATED_DATA)
            val ciphertext = cipher.doFinal(plaintext)
            JSONObject()
                .put("format", FORMAT)
                .put("version", VERSION)
                .put("createdAt", Instant.now().toString())
                .put(
                    "kdf",
                    JSONObject()
                        .put("name", KDF_NAME)
                        .put("iterations", KDF_ITERATIONS)
                        .put("salt", salt.toBase64()),
                )
                .put(
                    "cipher",
                    JSONObject()
                        .put("name", CIPHER_NAME)
                        .put("nonce", nonce.toBase64()),
                )
                .put("payload", ciphertext.toBase64())
                .toString(2)
                .toByteArray(Charsets.UTF_8)
        } finally {
            plaintext.fill(0)
            key.encoded?.fill(0)
            salt.fill(0)
            nonce.fill(0)
        }
    }

    fun decrypt(
        envelopeBytes: ByteArray,
        password: CharArray,
    ): RoutingConfig {
        require(envelopeBytes.size in 1..MAX_ENVELOPE_BYTES) {
            "Файл резервной копии пуст или слишком велик."
        }
        requireStrongPassword(password)
        val envelope = runCatching {
            JSONObject(envelopeBytes.toString(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException("Это не файл резервной копии ViRouteFS.")
        }
        require(envelope.optString("format") == FORMAT) {
            "Это не файл резервной копии ViRouteFS."
        }
        require(envelope.optInt("version", -1) == VERSION) {
            "Версия резервной копии пока не поддерживается."
        }
        val kdf = envelope.optJSONObject("kdf")
            ?: throw IllegalArgumentException("В резервной копии отсутствуют параметры защиты.")
        val cipherOptions = envelope.optJSONObject("cipher")
            ?: throw IllegalArgumentException("В резервной копии отсутствуют параметры шифрования.")
        require(kdf.optString("name") == KDF_NAME) { "Неизвестный алгоритм формирования ключа." }
        require(cipherOptions.optString("name") == CIPHER_NAME) { "Неизвестный алгоритм шифрования." }
        val iterations = kdf.optInt("iterations", -1)
        require(iterations in MIN_ACCEPTED_ITERATIONS..MAX_ACCEPTED_ITERATIONS) {
            "Некорректное число итераций защиты."
        }
        val salt = decodeBase64(kdf.optString("salt"), "salt")
        val nonce = decodeBase64(cipherOptions.optString("nonce"), "nonce")
        val ciphertext = decodeBase64(envelope.optString("payload"), "payload")
        require(salt.size == SALT_BYTES && nonce.size == NONCE_BYTES && ciphertext.isNotEmpty()) {
            "Параметры шифрования резервной копии повреждены."
        }
        val key = deriveKey(password, salt, iterations)
        val plaintext = try {
            val cipher = Cipher.getInstance(CIPHER_NAME)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, nonce))
            cipher.updateAAD(ASSOCIATED_DATA)
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Неверный пароль или файл резервной копии повреждён.")
        } catch (_: Exception) {
            throw IllegalArgumentException("Не удалось расшифровать резервную копию.")
        } finally {
            key.encoded?.fill(0)
            salt.fill(0)
            nonce.fill(0)
            ciphertext.fill(0)
        }
        return try {
            require(plaintext.size <= MAX_PLAINTEXT_BYTES) {
                "Расшифрованная конфигурация слишком велика."
            }
            val decoded = RoutingConfigDefaults.ensureRequiredProfiles(
                RoutingConfigJson.decode(plaintext.toString(Charsets.UTF_8)),
            )
            val errors = validateRoutingConfig(decoded)
            require(errors.isEmpty()) {
                "Резервная копия содержит некорректную конфигурацию: ${errors.joinToString()}"
            }
            decoded
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (_: Exception) {
            throw IllegalArgumentException("В резервной копии нет корректной конфигурации ViRouteFS.")
        } finally {
            plaintext.fill(0)
        }
    }

    fun validatePassword(password: CharArray): String? = when {
        password.size < MIN_PASSWORD_LENGTH ->
            "Пароль должен содержать не менее $MIN_PASSWORD_LENGTH символов."
        password.size > MAX_PASSWORD_LENGTH ->
            "Пароль слишком длинный. Максимум — $MAX_PASSWORD_LENGTH символа."
        password.all(Char::isWhitespace) ->
            "Пароль не может состоять только из пробелов."
        else -> null
    }

    private fun requireStrongPassword(password: CharArray) {
        validatePassword(password)?.let { message ->
            throw IllegalArgumentException(message)
        }
    }

    private fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
    ): SecretKeySpec {
        val specification = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try {
            val encoded = SecretKeyFactory.getInstance(KDF_NAME)
                .generateSecret(specification)
                .encoded
            SecretKeySpec(encoded, "AES").also { encoded.fill(0) }
        } finally {
            specification.clearPassword()
        }
    }

    private fun ByteArray.toBase64(): String =
        Base64.getEncoder().encodeToString(this)

    private fun decodeBase64(value: String, field: String): ByteArray = runCatching {
        Base64.getDecoder().decode(value)
    }.getOrElse {
        throw IllegalArgumentException("Поле $field резервной копии повреждено.")
    }

    internal const val FORMAT = "ViRouteFS-encrypted-backup"
    internal const val VERSION = 1
    internal const val MIN_PASSWORD_LENGTH = 10
    internal const val KDF_ITERATIONS = 600_000
    private const val MAX_PASSWORD_LENGTH = 1_024
    private const val MIN_ACCEPTED_ITERATIONS = 100_000
    private const val MAX_ACCEPTED_ITERATIONS = 1_000_000
    private const val KDF_NAME = "PBKDF2WithHmacSHA256"
    private const val CIPHER_NAME = "AES/GCM/NoPadding"
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val MAX_ENVELOPE_BYTES = 16 * 1024 * 1024
    private const val MAX_PLAINTEXT_BYTES = 12 * 1024 * 1024
    private val ASSOCIATED_DATA = "$FORMAT:$VERSION".toByteArray(Charsets.UTF_8)
}
