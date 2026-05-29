package dev.vifs.viroutefs.diagnostics

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class TcpDiagnostic(
    private val defaultTimeoutMs: Int = DEFAULT_TIMEOUT_MS,
) {
    suspend fun check(
        host: String,
        portText: String,
        timeoutSecondsText: String,
    ): DiagnosticResult = withContext(Dispatchers.IO) {
        val normalizedHost = host.trim()
        val port = portText.trim().toIntOrNull()
        val timeoutMs = timeoutSecondsText.trim().toIntOrNull()
            ?.coerceIn(1, 30)
            ?.times(1_000)
            ?: defaultTimeoutMs

        if (normalizedHost.isBlank()) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Введите хост для TCP-проверки.",
                technicalDetails = "Поле хоста пустое. TCP-подключение не выполнялось.",
                recommendedAction = "Укажите домен или IP-адрес, например example.com.",
            )
        }
        if (port == null || port !in 1..65_535) {
            return@withContext DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Порт указан некорректно.",
                technicalDetails = "Значение порта: «$portText». Допустимый диапазон: 1–65535.",
                recommendedAction = "Введите порт сервиса, например 443 для HTTPS или 80 для HTTP.",
            )
        }

        var elapsedMs = 0L
        try {
            elapsedMs = measureTimeMillis {
                val address = InetAddress.getByName(normalizedHost)
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                }
            }
            DiagnosticResult(
                status = DiagnosticStatus.SUCCESS,
                simpleExplanation = "TCP-подключение установлено.",
                technicalDetails = "Хост: $normalizedHost\nПорт: $port\nТаймаут: $timeoutMs мс\nСокет успешно открылся и был закрыт.",
                recommendedAction = "Базовая доступность порта есть. Если сервис всё равно не работает, проверьте TLS или HTTP на следующем шаге.",
                elapsedMs = elapsedMs,
            )
        } catch (error: UnknownHostException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "DNS не смог найти IP-адрес хоста.",
                technicalDetails = "UnknownHostException для $normalizedHost: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Проверьте имя хоста или выполните DNS-проверку на экране DNS.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: SocketTimeoutException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "TCP-подключение не дождалось ответа.",
                technicalDetails = "Таймаут подключения к $normalizedHost:$port после $timeoutMs мс.",
                recommendedAction = "Проверьте, открыт ли порт, не блокирует ли его firewall, VPN, мобильный оператор или корпоративная сеть.",
                elapsedMs = elapsedMs.takeIf { it > 0 } ?: timeoutMs.toLong(),
            )
        } catch (error: java.net.ConnectException) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "Удалённая сторона отказала в TCP-подключении.",
                technicalDetails = "ConnectException для $normalizedHost:$port: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Проверьте, запущен ли сервис на этом порту и правильно ли указан порт.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        } catch (error: Exception) {
            DiagnosticResult(
                status = DiagnosticStatus.ERROR,
                simpleExplanation = "TCP-проверка завершилась неизвестной ошибкой.",
                technicalDetails = "${error::class.java.simpleName} для $normalizedHost:$port: ${error.message ?: "без сообщения"}.",
                recommendedAction = "Проверьте сеть, имя хоста и порт, затем повторите проверку.",
                elapsedMs = elapsedMs.takeIf { it > 0 },
            )
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000
    }
}
