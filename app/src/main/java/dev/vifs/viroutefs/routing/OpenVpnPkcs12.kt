// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Base64

internal data class OpenVpnPkcs12Material(
    val clientCertificatePem: String,
    val clientKeyPem: String,
    val certificateAuthorityPem: String?,
    val alias: String,
    val certificateCount: Int,
    val leafSubject: String,
    val warnings: List<String>,
)

/**
 * Converts one local PKCS#12 identity to the PEM fields accepted by sing-box.
 * The container password is used only while opening the selected bytes and is
 * never returned or persisted. The private key is exported as unencrypted
 * PKCS#8 because the resulting profile is encrypted by ProfileSecretStore.
 */
internal fun importOpenVpnPkcs12(
    bytes: ByteArray,
    password: CharArray,
): OpenVpnPkcs12Material {
    require(bytes.isNotEmpty()) { "Выбранный файл PKCS#12 пуст." }
    val passwordCopy = password.copyOf()
    try {
        val keyStore = KeyStore.getInstance("PKCS12")
        try {
            ByteArrayInputStream(bytes).use { input -> keyStore.load(input, passwordCopy) }
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Не удалось открыть PKCS#12. Проверьте пароль и файл .p12/.pfx.",
                error,
            )
        }

        val keyAliases = buildList {
            val aliases = keyStore.aliases()
            while (aliases.hasMoreElements()) {
                aliases.nextElement().takeIf(keyStore::isKeyEntry)?.let(::add)
            }
        }
        require(keyAliases.isNotEmpty()) {
            "В файле PKCS#12 нет закрытого ключа клиента."
        }
        require(keyAliases.size == 1) {
            "В файле PKCS#12 несколько закрытых ключей. Экспортируйте нужный ключ в отдельный файл .p12/.pfx."
        }

        val alias = keyAliases.single()
        val privateKey = try {
            keyStore.getKey(alias, passwordCopy) as? PrivateKey
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Не удалось открыть закрытый ключ клиента. Проверьте пароль .p12/.pfx.",
                error,
            )
        } ?: error("Выбранная запись PKCS#12 не является закрытым ключом.")
        val chain = keyStore.getCertificateChain(alias)
            .orEmpty()
            .map { certificate ->
                certificate as? X509Certificate
                    ?: error("Цепочка сертификатов PKCS#12 имеет неподдерживаемый формат (не X.509).")
            }
        require(chain.isNotEmpty()) {
            "В PKCS#12 для закрытого ключа нет клиентского сертификата."
        }

        val warnings = buildList {
            chain.first().let { leaf ->
                try {
                    leaf.checkValidity()
                } catch (_: CertificateExpiredException) {
                    add("Срок действия клиентского сертификата истёк.")
                } catch (_: CertificateNotYetValidException) {
                    add("Срок действия клиентского сертификата ещё не начался.")
                }
            }
        }
        val clientCertificates = chain.joinToString(separator = "\n") { certificate ->
            certificate.encoded.toPem("CERTIFICATE")
        }
        val certificateAuthorities = chain
            .drop(1)
            .filter { certificate -> certificate.basicConstraints >= 0 }
            .takeIf(List<X509Certificate>::isNotEmpty)
            ?.joinToString(separator = "\n") { certificate ->
                certificate.encoded.toPem("CERTIFICATE")
            }
        val encodedKey = privateKey.encoded
            ?: error("Закрытый ключ PKCS#12 нельзя экспортировать в формате PKCS#8.")
        val clientKey = try {
            encodedKey.toPem("PRIVATE KEY")
        } finally {
            encodedKey.fill(0)
        }

        return OpenVpnPkcs12Material(
            clientCertificatePem = clientCertificates,
            clientKeyPem = clientKey,
            certificateAuthorityPem = certificateAuthorities,
            alias = alias,
            certificateCount = chain.size,
            leafSubject = chain.first().subjectX500Principal.name,
            warnings = warnings,
        )
    } finally {
        passwordCopy.fill('\u0000')
    }
}

private fun ByteArray.toPem(label: String): String = buildString {
    append("-----BEGIN ")
    append(label)
    appendLine("-----")
    appendLine(PEM_ENCODER.encodeToString(this@toPem))
    append("-----END ")
    append(label)
    appendLine("-----")
}

private val PEM_ENCODER: Base64.Encoder = Base64.getMimeEncoder(
    64,
    "\n".toByteArray(Charsets.US_ASCII),
)
