package dev.vifs.viroutefs.diagnostics

import java.io.IOException
import java.net.IDN
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class DnsDiagnostic(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun lookup(
        domain: String,
        dnsServer: String,
        recordType: String,
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val normalizedDomain = domain.trim().removeSuffix(".")
        val normalizedType = recordType.trim().uppercase()

        if (normalizedDomain.isBlank()) {
            return@withContext validationError(
                "Введите домен для проверки DNS.",
                "Поле домена пустое. Запрос DNS не выполнялся.",
                "Укажите домен, например example.com, и нажмите «Проверить».",
            )
        }

        val asciiDomain = normalizedDomain.toAsciiOrNull()
        if (asciiDomain == null || !asciiDomain.isValidDomainName()) {
            return@withContext validationError(
                "Домен выглядит некорректно.",
                "Значение «$normalizedDomain» не похоже на допустимое доменное имя.",
                "Проверьте опечатки: укажите домен без https:// и пути.",
            )
        }

        if (normalizedType !in SUPPORTED_RECORD_TYPES) {
            return@withContext validationError(
                "Тип DNS-записи пока не поддерживается.",
                "Запрошен тип «$normalizedType». Ручная проверка поддерживает A и AAAA.",
                "Выберите A для IPv4-адресов или AAAA для IPv6-адресов.",
            )
        }

        val endpoint = try {
            DnsEndpoint.parse(dnsServer)
        } catch (error: IllegalArgumentException) {
            return@withContext validationError(
                "Адрес DNS-сервера не удалось разобрать.",
                error.message ?: "Некорректный адрес DNS-сервера.",
                "Примеры: 1.1.1.1, tcp://8.8.8.8, tls://dns.google или https://dns.google/dns-query.",
            )
        }

        try {
            if (endpoint.transport == DnsTransport.SYSTEM) {
                return@withContext lookupWithSystemResolver(asciiDomain, normalizedType, startedAt)
            }

            val transactionId = SECURE_RANDOM.nextInt(0x1_0000)
            val query = DnsWireCodec.buildQuery(asciiDomain, normalizedType, transactionId)
            val client = DnsProbeClient(timeoutMs)
            var actualEndpoint = endpoint
            var response = DnsWireCodec.parseResponse(
                client.query(endpoint, query),
                transactionId,
                normalizedType,
            )
            var transportNote: String? = null
            if (response.truncated && endpoint.transport == DnsTransport.UDP) {
                actualEndpoint = endpoint.asTcpFallback()
                response = DnsWireCodec.parseResponse(
                    client.query(actualEndpoint, query),
                    transactionId,
                    normalizedType,
                )
                transportNote = "UDP-ответ был усечён, поэтому проверка автоматически повторена по TCP."
            }

            val elapsedMs = elapsedSince(startedAt)
            val details = buildTechnicalDetails(
                domain = asciiDomain,
                recordType = normalizedType,
                endpoint = actualEndpoint,
                response = response,
                transportNote = transportNote,
            )
            when {
                response.responseCode != 0 -> dnsErrorResponse(response.responseCode, details, elapsedMs)
                response.addresses.isEmpty() -> DiagnosticResult(
                    status = DiagnosticStatus.WARNING,
                    simpleExplanation = "DNS-сервер ответил, но записей типа $normalizedType не найдено.",
                    technicalDetails = details,
                    recommendedAction = "Попробуйте другой тип записи или проверьте, настроены ли такие записи у домена.",
                    elapsedMs = elapsedMs,
                )
                else -> DiagnosticResult(
                    status = DiagnosticStatus.SUCCESS,
                    simpleExplanation = "Выбранный DNS-сервер ответил и нашёл адреса домена.",
                    technicalDetails = details,
                    recommendedAction = "Если приложение направляется не туда, сравните эти адреса с результатом через другой DNS и с журналом маршрута.",
                    elapsedMs = elapsedMs,
                )
            }
        } catch (error: TimeoutCancellationException) {
            timeoutResult(asciiDomain, endpoint, startedAt)
        } catch (error: SocketTimeoutException) {
            timeoutResult(asciiDomain, endpoint, startedAt)
        } catch (error: SSLHandshakeException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Защищённое соединение с DNS-сервером не прошло проверку.",
                technicalDetails = baseRequestDetails(asciiDomain, normalizedType, endpoint) +
                    "\nОшибка TLS: ${error.message ?: "без сообщения"}",
                recommendedAction = "Проверьте имя сервера, дату и время телефона, затем попробуйте официальный адрес DoT/DoH провайдера.",
                elapsedMs = elapsedSince(startedAt),
            )
        } catch (error: UnknownHostException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = if (endpoint.transport == DnsTransport.SYSTEM) {
                    "Системный DNS Android не нашёл такой домен."
                } else {
                    "Не удалось найти или открыть указанный DNS-сервер."
                },
                technicalDetails = baseRequestDetails(asciiDomain, normalizedType, endpoint) +
                    "\n${error::class.java.simpleName}: ${error.message ?: "без сообщения"}",
                recommendedAction = "Проверьте адрес DNS-сервера и подключение телефона к интернету.",
                elapsedMs = elapsedSince(startedAt),
            )
        } catch (error: DnsProtocolException) {
            protocolError(asciiDomain, normalizedType, endpoint, error, startedAt)
        } catch (error: IOException) {
            protocolError(asciiDomain, normalizedType, endpoint, error, startedAt)
        } catch (error: Exception) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "DNS-проверка завершилась ошибкой.",
                technicalDetails = baseRequestDetails(asciiDomain, normalizedType, endpoint) +
                    "\n${error::class.java.simpleName}: ${error.message ?: "без сообщения"}",
                recommendedAction = "Проверьте сеть и адрес DNS-сервера. Если ошибка повторяется, приложите технические детали к отчёту.",
                elapsedMs = elapsedSince(startedAt),
            )
        }
    }

    private suspend fun lookupWithSystemResolver(
        domain: String,
        recordType: String,
        startedAt: Long,
    ): DiagnosticResult {
        val addresses = withTimeout(timeoutMs) {
            InetAddress.getAllByName(domain).toList().filter { address ->
                when (recordType) {
                    "A" -> address is Inet4Address
                    "AAAA" -> address is Inet6Address
                    else -> false
                }
            }
        }
        val elapsedMs = elapsedSince(startedAt)
        val details = buildString {
            append(baseRequestDetails(domain, recordType, DnsEndpoint(DnsTransport.SYSTEM)))
            append("\nАдреса: ")
            append(addresses.joinToString { it.hostAddress ?: it.hostName }.ifBlank { "нет записей выбранного типа" })
        }
        return if (addresses.isEmpty()) {
            DiagnosticResult(
                status = DiagnosticStatus.WARNING,
                simpleExplanation = "Системный DNS ответил, но записей типа $recordType не найдено.",
                technicalDetails = details,
                recommendedAction = "Попробуйте другой тип записи или укажите конкретный DNS-сервер для сравнения.",
                elapsedMs = elapsedMs,
            )
        } else {
            DiagnosticResult(
                status = DiagnosticStatus.SUCCESS,
                simpleExplanation = "Системный DNS Android успешно нашёл адреса домена.",
                technicalDetails = details,
                recommendedAction = "Для сравнения можно указать конкретный UDP, TCP, DoT или DoH сервер.",
                elapsedMs = elapsedMs,
            )
        }
    }

    private fun buildTechnicalDetails(
        domain: String,
        recordType: String,
        endpoint: DnsEndpoint,
        response: DnsWireCodec.Response,
        transportNote: String?,
    ): String = buildString {
        append(baseRequestDetails(domain, recordType, endpoint))
        append("\nКод ответа: ").append(response.responseCode).append(" (").append(responseCodeName(response.responseCode)).append(')')
        append("\nОтветов в пакете: ").append(response.answerCount)
        append("\nРазмер ответа: ").append(response.sizeBytes).append(" байт")
        append("\nАдреса: ").append(response.addresses.joinToString().ifBlank { "нет записей выбранного типа" })
        transportNote?.let { append("\n").append(it) }
        endpoint.bootstrapNote?.let { append("\n").append(it) }
    }

    private fun baseRequestDetails(domain: String, recordType: String, endpoint: DnsEndpoint): String =
        "Домен: $domain\nТип: $recordType\nDNS: ${endpoint.displayName}\nКонтур: физическое подключение Android, вне TUN ViRouteFS."

    private fun dnsErrorResponse(responseCode: Int, details: String, elapsedMs: Long): DiagnosticResult {
        val name = responseCodeName(responseCode)
        val explanation = when (responseCode) {
            2 -> "DNS-сервер временно не смог обработать запрос (SERVFAIL)."
            3 -> "DNS-сервер сообщает, что такого домена не существует (NXDOMAIN)."
            5 -> "DNS-сервер отказался выполнять запрос (REFUSED)."
            else -> "DNS-сервер вернул ошибку $name."
        }
        return DiagnosticResult(
            status = DiagnosticStatus.ERROR,
            simpleExplanation = explanation,
            technicalDetails = details,
            recommendedAction = "Проверьте домен и повторите запрос через другой DNS-сервер, чтобы понять, локальна ли проблема.",
            elapsedMs = elapsedMs,
        )
    }

    private fun timeoutResult(
        domain: String,
        endpoint: DnsEndpoint,
        startedAt: Long,
    ): DiagnosticResult = DiagnosticResult(
        status = DiagnosticStatus.ERROR,
        simpleExplanation = "DNS-сервер не ответил вовремя.",
        technicalDetails = "Домен: $domain\nDNS: ${endpoint.displayName}\nТаймаут: $timeoutMs мс\nКонтур: физическое подключение Android, вне TUN ViRouteFS.",
        recommendedAction = "Проверьте интернет, адрес и порт сервера. Затем сравните с системным DNS, оставив поле сервера пустым.",
        elapsedMs = elapsedSince(startedAt),
    )

    private fun protocolError(
        domain: String,
        recordType: String,
        endpoint: DnsEndpoint,
        error: Exception,
        startedAt: Long,
    ): DiagnosticResult = DiagnosticResult(
        status = DiagnosticStatus.ERROR,
        simpleExplanation = "DNS-сервер доступен, но корректный ответ получить не удалось.",
        technicalDetails = baseRequestDetails(domain, recordType, endpoint) +
            "\n${error::class.java.simpleName}: ${error.message ?: "без сообщения"}",
        recommendedAction = "Проверьте схему и порт сервера либо повторите тест через другой DNS.",
        elapsedMs = elapsedSince(startedAt),
    )

    private fun validationError(simple: String, technical: String, action: String): DiagnosticResult = DiagnosticResult(
        status = DiagnosticStatus.ERROR,
        simpleExplanation = simple,
        technicalDetails = technical,
        recommendedAction = action,
    )

    private fun responseCodeName(code: Int): String = when (code) {
        0 -> "NOERROR"
        1 -> "FORMERR"
        2 -> "SERVFAIL"
        3 -> "NXDOMAIN"
        4 -> "NOTIMP"
        5 -> "REFUSED"
        else -> "RCODE $code"
    }

    private fun elapsedSince(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

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
        } && labels.any { label -> label.any(Char::isLetter) }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        val SUPPORTED_RECORD_TYPES = setOf("A", "AAAA")
        val SECURE_RANDOM = SecureRandom()
    }
}
