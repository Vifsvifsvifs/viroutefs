package dev.vifs.viroutefs.diagnostics

import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class HttpDiagnostic(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    suspend fun check(urlText: String): DiagnosticResult = withContext(Dispatchers.IO) {
        val normalizedUrl = urlText.trim()
        if (normalizedUrl.isBlank()) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Введите URL для HTTP-проверки.",
                technicalDetails = "Поле URL пустое. HTTP-запрос не выполнялся.",
                recommendedAction = "Укажите полный адрес, например https://example.com.",
            )
        }

        val initialUrl = try {
            URL(normalizedUrl)
        } catch (error: Exception) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "URL указан некорректно.",
                technicalDetails = "Не удалось разобрать URL «$normalizedUrl»: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Введите URL со схемой http:// или https://.",
            )
        }

        if (initialUrl.protocol !in setOf("http", "https")) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Поддерживаются только HTTP и HTTPS URL.",
                technicalDetails = "Схема URL: ${initialUrl.protocol}.",
                recommendedAction = "Используйте адрес, который начинается с http:// или https://.",
            )
        }

        var elapsedMs = 0L
        try {
            var response = HttpResponseSummary(
                statusCode = -1,
                finalUrl = initialUrl.toString(),
                contentType = null,
            )
            elapsedMs = measureTimeMillis {
                response = request(initialUrl)
            }
            val redirected = response.finalUrl != initialUrl.toString()
            DiagnosticResult(
                status = if (response.statusCode in 200..399) DiagnosticStatus.SUCCESS else DiagnosticStatus.WARNING,
                simpleExplanation = if (redirected) {
                    "Сервер ответил и перенаправил запрос."
                } else {
                    "Сайт доступен, сервер ответил HTTP-статусом ${response.statusCode}."
                },
                technicalDetails = "Начальный URL: $initialUrl\nФинальный URL: ${response.finalUrl}\nHTTP-статус: ${response.statusCode}\nContent-Type: ${response.contentType ?: "не указан"}\nРедирект: ${if (redirected) "да" else "нет"}",
                recommendedAction = when {
                    response.statusCode in 200..299 -> "HTTP работает. Если пользователь видит проблему в браузере, проверьте содержимое страницы или авторизацию."
                    response.statusCode in 300..399 -> "Проверьте, ожидаем ли редирект и правильно ли настроен конечный адрес."
                    response.statusCode in 400..499 -> "Сервер доступен, но отклонил запрос. Проверьте путь, права доступа или авторизацию."
                    response.statusCode >= 500 -> "Сервер доступен, но сообщает о внутренней ошибке. Проверьте серверные логи."
                    else -> "Проверьте необычный HTTP-статус и настройки сервера."
                },
                elapsedMs = elapsedMs,
            )
        } catch (error: UnknownHostException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "HTTP не начался: вероятно, DNS не нашёл хост.",
                technicalDetails = "UnknownHostException для $normalizedUrl: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Сначала выполните DNS-проверку для домена из URL.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: SSLHandshakeException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "HTTPS не открылся из-за ошибки TLS или сертификата.",
                technicalDetails = "SSLHandshakeException для $normalizedUrl: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Выполните TLS/SNI-проверку и проверьте сертификат сервера.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: SocketTimeoutException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "HTTP-запрос не дождался ответа.",
                technicalDetails = "Таймаут HTTP после $timeoutMs мс для $normalizedUrl.",
                recommendedAction = "Проверьте TCP-доступность хоста и состояние сервера.",
                elapsedMs = elapsedMs.takeIf { it > 0 } ?: timeoutMs.toLong(),
            )
        } catch (error: Exception) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "HTTP-проверка завершилась ошибкой.",
                technicalDetails = "${error::class.java.simpleName} для $normalizedUrl: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Если DNS и TCP успешны, проверьте TLS, прокси, firewall и настройки веб-сервера.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        }
    }

    private fun request(initialUrl: URL): HttpResponseSummary {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                setRequestProperty("User-Agent", "ViRouteFS/0.2-alpha")
            }
            try {
                val status = connection.responseCode
                val location = connection.getHeaderField("Location")
                val isRedirect = status in 300..399 && !location.isNullOrBlank()
                if (isRedirect && redirectCount < MAX_REDIRECTS) {
                    val nextUrl = URL(currentUrl, location)
                    if (nextUrl.protocol in setOf("http", "https")) {
                        currentUrl = nextUrl
                        return@repeat
                    }
                }
                return HttpResponseSummary(
                    statusCode = status,
                    finalUrl = currentUrl.toString(),
                    contentType = connection.contentType,
                )
            } finally {
                connection.disconnect()
            }
        }
        return HttpResponseSummary(
            statusCode = -1,
            finalUrl = currentUrl.toString(),
            contentType = null,
        )
    }

    private data class HttpResponseSummary(
        val statusCode: Int,
        val finalUrl: String,
        val contentType: String?,
    )

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 7_000
        const val MAX_REDIRECTS = 3
    }
}
