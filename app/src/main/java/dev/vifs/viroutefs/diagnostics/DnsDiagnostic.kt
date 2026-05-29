package dev.vifs.viroutefs.diagnostics

import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.system.measureTimeMillis

class DnsDiagnostic(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun lookup(
        domain: String,
        dnsServer: String,
        recordType: String,
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val normalizedDomain = domain.trim().removeSuffix(".")
        val normalizedType = recordType.trim().uppercase()
        val serverText = dnsServer.trim().ifBlank { "не указан" }

        if (normalizedDomain.isBlank()) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Введите домен для проверки DNS.",
                technicalDetails = "Поле домена пустое. Запрос DNS не выполнялся.",
                recommendedAction = "Укажите домен, например example.com, и нажмите «Проверить».",
            )
        }

        val asciiDomain = normalizedDomain.toAsciiOrNull()
        if (asciiDomain == null || !asciiDomain.isValidDomainName()) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Домен выглядит некорректно.",
                technicalDetails = "Значение «$normalizedDomain» не похоже на допустимое доменное имя.",
                recommendedAction = "Проверьте опечатки: домен должен состоять из меток через точку, без схемы https:// и пути.",
            )
        }

        if (normalizedType !in SUPPORTED_RECORD_TYPES) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Тип DNS-записи пока не поддерживается.",
                technicalDetails = "Запрошен тип «$normalizedType». В версии 0.3-alpha поддерживаются только A и AAAA.",
                recommendedAction = "Выберите A для IPv4-адресов или AAAA для IPv6-адресов.",
            )
        }

        var elapsedMs = 0L
        try {
            var addresses: List<InetAddress> = emptyList()
            elapsedMs = measureTimeMillis {
                addresses = withTimeout(timeoutMs) {
                    InetAddress.getAllByName(asciiDomain).toList().filter { address ->
                        when (normalizedType) {
                            "A" -> address is Inet4Address
                            "AAAA" -> address is Inet6Address
                            else -> false
                        }
                    }
                }
            }

            if (addresses.isEmpty()) {
                DiagnosticResult(
                    status = DiagnosticStatus.WARNING,
                    simpleExplanation = "DNS ответил, но записей типа $normalizedType не найдено.",
                    technicalDetails = "Домен: $asciiDomain\nТип: $normalizedType\nDNS-сервер в поле: $serverText\nИспользован системный DNS Android: да\nАдреса: нет записей выбранного типа.",
                    recommendedAction = "Попробуйте другой тип записи или проверьте, настроены ли такие записи у домена.",
                    elapsedMs = elapsedMs,
                )
            } else {
                DiagnosticResult(
                    status = DiagnosticStatus.SUCCESS,
                    simpleExplanation = "DNS успешно нашёл адреса для домена.",
                    technicalDetails = "Домен: $asciiDomain\nТип: $normalizedType\nDNS-сервер в поле: $serverText\nИспользован системный DNS Android: да\nАдреса: ${addresses.joinToString { it.hostAddress ?: it.hostName }}",
                    recommendedAction = "Если приложение позже пойдёт не туда, сравните эти адреса с ожидаемыми адресами вашего сервиса.",
                    elapsedMs = elapsedMs,
                )
            }
        } catch (error: TimeoutCancellationException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "DNS-запрос не успел завершиться.",
                technicalDetails = "Таймаут DNS после ${timeoutMs} мс. Домен: $asciiDomain. Использовался системный DNS Android.",
                recommendedAction = "Проверьте подключение к сети или попробуйте другой домен позже.",
                elapsedMs = timeoutMs,
            )
        } catch (error: UnknownHostException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "DNS не нашёл такой домен.",
                technicalDetails = "UnknownHostException для $asciiDomain: ${error.message ?: "без сообщения"}. Использовался системный DNS Android.",
                recommendedAction = "Проверьте написание домена и доступность DNS в текущей сети.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: Exception) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "DNS-проверка завершилась ошибкой.",
                technicalDetails = "${error::class.java.simpleName}: ${error.message ?: "без сообщения"}",
                recommendedAction = "Проверьте сеть и повторите запрос. Если ошибка повторяется, попробуйте другой домен.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        }
    }

    private fun String.toAsciiOrNull(): String? = try {
        IDN.toASCII(this, IDN.USE_STD3_ASCII_RULES).lowercase()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun String.isValidDomainName(): Boolean {
        if (length !in 1..253 || startsWith(".") || endsWith(".") || contains("/")) return false
        val labels = split('.')
        return labels.all { label ->
            label.length in 1..63 &&
                !label.startsWith("-") &&
                !label.endsWith("-") &&
                label.all { it.isLetterOrDigit() || it == '-' }
        } && labels.any { label -> label.any { it.isLetter() } }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        val SUPPORTED_RECORD_TYPES = setOf("A", "AAAA")
    }
}
