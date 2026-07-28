package dev.vifs.viroutefs.route

import dev.vifs.viroutefs.diagnostics.DiagnosticStatus
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.routing.RouteDecision
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRuleType
import java.net.URL
import java.util.Locale
import kotlin.system.measureTimeMillis

class RouteDiagnosticRunner(
    private val routeEngine: RouteEngine,
    private val dnsDiagnostic: DnsDiagnostic = DnsDiagnostic(),
    private val tcpDiagnostic: TcpDiagnostic = TcpDiagnostic(),
    private val tlsDiagnostic: TlsDiagnostic = TlsDiagnostic(),
    private val httpDiagnostic: HttpDiagnostic = HttpDiagnostic(),
) {
    suspend fun run(
        target: String,
        portText: String,
        sni: String,
        appVersion: String,
    ): RouteDiagnosticReport {
        val startedAt = System.currentTimeMillis()
        val normalizedTarget = target.trim().ifBlank { "example.com" }
        val parsedTarget = ParsedTarget.from(normalizedTarget)
        val host = parsedTarget.host.ifBlank { normalizedTarget }.trimHostForNetworkChecks()
        val port = portText.trim().toIntOrNull()?.takeIf { it in 1..65_535 } ?: 443
        val normalizedSni = sni.trim().ifBlank { null }
        val routeDecision = routeEngine.simulate(normalizedTarget)

        var dnsStep: RouteDiagnosticStep? = null
        lateinit var tcpStep: RouteDiagnosticStep
        var tlsStep: RouteDiagnosticStep? = null
        var httpStep: RouteDiagnosticStep? = null

        val elapsedMs = measureTimeMillis {
            dnsStep = if (host.looksLikeDomain()) {
                RouteDiagnosticStep(
                    title = "DNS",
                    result = dnsDiagnostic.lookup(host, "системный DNS Android", "A"),
                )
            } else {
                RouteDiagnosticStep(
                    title = "DNS",
                    result = null,
                    skippedReason = "DNS не запускался: цель не похожа на доменное имя.",
                )
            }

            tcpStep = RouteDiagnosticStep(
                title = "TCP",
                result = tcpDiagnostic.check(host, port.toString(), "5"),
            )

            val shouldRunTls = port == 443 || normalizedSni != null
            tlsStep = if (shouldRunTls) {
                RouteDiagnosticStep(
                    title = "TLS/SNI",
                    result = tlsDiagnostic.check(host, port.toString(), normalizedSni ?: host),
                )
            } else {
                RouteDiagnosticStep(
                    title = "TLS/SNI",
                    result = null,
                    skippedReason = "TLS не запускался: порт не 443 и SNI не указан.",
                )
            }

            val httpUrl = parsedTarget.url ?: if (port == 443 || normalizedSni != null || host.looksLikeDomain()) {
                val scheme = if (port == 443 || normalizedSni != null) "https" else "http"
                "$scheme://$host${if (port in listOf(80, 443)) "" else ":$port"}"
            } else {
                null
            }
            httpStep = if (httpUrl != null) {
                RouteDiagnosticStep(
                    title = "HTTP",
                    result = httpDiagnostic.check(httpUrl),
                )
            } else {
                RouteDiagnosticStep(
                    title = "HTTP",
                    result = null,
                    skippedReason = "HTTP не запускался: цель не похожа на URL и HTTPS нельзя уверенно предположить.",
                )
            }
        }

        val summary = buildSummary(routeDecision, listOfNotNull(dnsStep, tcpStep, tlsStep, httpStep))
        val recommendation = buildRecommendation(routeDecision, listOfNotNull(dnsStep, tcpStep, tlsStep, httpStep))

        return RouteDiagnosticReport(
            appVersion = appVersion,
            inputTarget = normalizedTarget,
            hostForDiagnostics = host,
            port = port,
            sni = normalizedSni,
            routeDecision = routeDecision,
            dnsStep = dnsStep,
            tcpStep = tcpStep,
            tlsStep = tlsStep,
            httpStep = httpStep,
            finalSummary = summary,
            recommendedNextAction = recommendation,
            timestampMs = startedAt,
            elapsedMs = elapsedMs,
        )
    }

    private fun buildSummary(
        routeDecision: RouteDecision,
        steps: List<RouteDiagnosticStep>,
    ): String {
        val routeText = if (routeDecision.matchedRule.type == RouteRuleType.DEFAULT) {
            "Отдельное правило не найдено. Используется маршрут по умолчанию: ${routeDecision.tunnelProfile.name}."
        } else {
            "Маршрут выбран: ${routeDecision.tunnelProfile.name}.\nПричина: ${routeDecision.plainReason}"
        }
        val runResults = steps.filter { it.wasRun }.mapNotNull { step -> step.result?.let { step.title to it } }
        val hasError = runResults.any { it.second.status == DiagnosticStatus.ERROR }
        val statusText = if (runResults.isEmpty()) {
            "Сетевые проверки не выполнялись."
        } else if (hasError) {
            "Проверка через текущее подключение Android не прошла: ${runResults.first { it.second.status == DiagnosticStatus.ERROR }.second.simpleExplanation}"
        } else {
            "Проверка через текущее подключение Android успешна: ${runResults.joinToString { it.first }} отвечают без критических ошибок."
        }
        val dnsText = "DNS-политика: ${routeDecision.dnsPolicySummary}. ${routeDecision.dnsLeakSummary}"
        val warningsText = routeDecision.warnings.joinToString("\n") { "Предупреждение: $it" }
        return "$routeText\n$dnsText\n$statusText\n$warningsText\nЭти DNS/TCP/TLS/HTTP-проверки выполняются процессом ViRouteFS вне собственного TUN. Они проверяют доступность узла через текущую сеть Android, но не доказывают работу выбранного VPN-профиля."
    }

    private fun buildRecommendation(
        routeDecision: RouteDecision,
        steps: List<RouteDiagnosticStep>,
    ): String {
        val firstError = steps.firstOrNull { it.result?.status == DiagnosticStatus.ERROR }?.result
        return firstError?.recommendedAction
            ?: routeDecision.recommendedAction + " Для проверки самого маршрута включите VPN и проверьте внешний IP/DNS из приложения, которому назначено правило."
    }

    private data class ParsedTarget(
        val host: String,
        val url: String?,
    ) {
        companion object {
            fun from(target: String): ParsedTarget {
                val hasScheme = target.startsWith("http://", ignoreCase = true) || target.startsWith("https://", ignoreCase = true)
                if (hasScheme) {
                    val parsed = runCatching { URL(target) }.getOrNull()
                    if (parsed != null) {
                        return ParsedTarget(host = parsed.host.orEmpty(), url = parsed.toString())
                    }
                }
                return ParsedTarget(host = target.substringBefore('/').substringBefore(':'), url = null)
            }
        }
    }

    private fun String.trimHostForNetworkChecks(): String =
        removePrefix("[").removeSuffix("]")

    private fun String.looksLikeDomain(): Boolean {
        val value = trim().lowercase(Locale.ROOT)
        if (value.isBlank() || value.contains(' ') || value.toIpv4OrNull()) return false
        return value.contains('.') && value.split('.').all { label ->
            label.isNotBlank() && label.length <= 63 && label.all { it.isLetterOrDigit() || it == '-' }
        }
    }

    private fun String.toIpv4OrNull(): Boolean {
        val parts = split('.')
        return parts.size == 4 && parts.all { part -> part.toIntOrNull()?.let { it in 0..255 } == true }
    }
}
