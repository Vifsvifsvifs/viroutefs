package dev.vifs.viroutefs.diagnostics

import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class TlsDiagnostic(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    suspend fun check(
        host: String,
        portText: String,
        sni: String,
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val normalizedHost = host.trim()
        val normalizedSni = sni.trim().ifBlank { normalizedHost }
        val port = portText.trim().toIntOrNull()

        if (normalizedHost.isBlank()) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Введите хост для TLS-проверки.",
                technicalDetails = "Поле хоста пустое. TLS-подключение не выполнялось.",
                recommendedAction = "Укажите домен или IP-адрес сервера.",
            )
        }
        if (port == null || port !in 1..65_535) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Порт TLS указан некорректно.",
                technicalDetails = "Значение порта: «$portText». Допустимый диапазон: 1–65535.",
                recommendedAction = "Обычно HTTPS использует порт 443.",
            )
        }

        var elapsedMs = 0L
        try {
            var protocol = "не определён"
            var cipherSuite = "не определён"
            var certificateDetails = "Сертификат сервера не получен."
            elapsedMs = measureTimeMillis {
                Socket().use { rawSocket ->
                    rawSocket.connect(InetSocketAddress(normalizedHost, port), timeoutMs)
                    rawSocket.soTimeout = timeoutMs
                    val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
                    (factory.createSocket(rawSocket, normalizedHost, port, true) as SSLSocket).use { sslSocket ->
                        sslSocket.soTimeout = timeoutMs
                        sslSocket.sslParameters = sslSocket.sslParameters.withSni(normalizedSni)
                        sslSocket.startHandshake()
                        val session = sslSocket.session
                        protocol = session.protocol
                        cipherSuite = session.cipherSuite
                        val certificate = session.peerCertificates.firstOrNull() as? X509Certificate
                        certificateDetails = certificate?.toDetails() ?: certificateDetails
                    }
                }
            }
            DiagnosticResult(
                status = DiagnosticStatus.SUCCESS,
                simpleExplanation = "TLS-рукопожатие успешно завершилось.",
                technicalDetails = "Хост: $normalizedHost\nПорт: $port\nSNI: $normalizedSni\nПротокол: $protocol\nШифр: $cipherSuite\n$certificateDetails",
                recommendedAction = "TLS на сервере отвечает. Если сайт не открывается, проверьте HTTP-статус и возможные редиректы.",
                elapsedMs = elapsedMs,
            )
        } catch (error: CertificateExpiredException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Сертификат сервера истёк.",
                technicalDetails = "CertificateExpiredException для $normalizedHost:$port: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Обновите сертификат на сервере или обратитесь к администратору ресурса.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: CertificateNotYetValidException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Сертификат сервера ещё не начал действовать.",
                technicalDetails = "CertificateNotYetValidException для $normalizedHost:$port: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Проверьте дату на устройстве и период действия сертификата сервера.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: SSLHandshakeException) {
            val mismatch = error.message?.contains("hostname", ignoreCase = true) == true ||
                error.message?.contains("No subject alternative", ignoreCase = true) == true
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = if (mismatch) "Сертификат не подходит к указанному имени сервера." else "TLS-рукопожатие завершилось ошибкой.",
                technicalDetails = "SSLHandshakeException для $normalizedHost:$port с SNI $normalizedSni: ${error.message ?: "без сообщения"}.",
                recommendedAction = if (mismatch) "Проверьте SNI и имя хоста: они должны совпадать с сертификатом сервера." else "Проверьте сертификат, поддерживаемые версии TLS и настройки сервера.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: SocketTimeoutException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "TLS-подключение не дождалось ответа.",
                technicalDetails = "Таймаут TLS для $normalizedHost:$port после $timeoutMs мс.",
                recommendedAction = "Проверьте доступность TCP-порта, firewall и стабильность сети.",
                elapsedMs = elapsedMs.takeIf { it > 0 } ?: timeoutMs.toLong(),
            )
        } catch (error: Exception) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "TLS-проверка не удалась.",
                technicalDetails = "${error::class.java.simpleName} для $normalizedHost:$port: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Сначала проверьте DNS и TCP, затем повторите TLS-проверку с правильным SNI.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        }
    }

    private fun SSLParameters.withSni(serverName: String): SSLParameters = apply {
        if (serverName.isNotBlank()) {
            serverNames = listOf(SNIHostName(serverName))
        }
    }

    private fun X509Certificate.toDetails(): String = buildString {
        appendLine("Сертификат subject: ${subjectX500Principal.name}")
        appendLine("Сертификат issuer: ${issuerX500Principal.name}")
        append("Действует: ${notBefore} — ${notAfter}")
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 7_000
    }
}
