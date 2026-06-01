package dev.vifs.viroutefs.route

import dev.vifs.viroutefs.diagnostics.DiagnosticStatus
import dev.vifs.viroutefs.routing.RouteDecision
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val VERSION_LIMITATION_NOTE =
    "В этой версии диагностика выполняется через текущее подключение Android. Выбранный маршрут пока симулируется."
private const val SAFETY_NOTE = "Проверяйте только свои ресурсы или сети, где у вас есть разрешение."

data class RouteDiagnosticReport(
    val appVersion: String,
    val inputTarget: String,
    val hostForDiagnostics: String,
    val port: Int,
    val sni: String?,
    val routeDecision: RouteDecision,
    val dnsStep: RouteDiagnosticStep?,
    val tcpStep: RouteDiagnosticStep,
    val tlsStep: RouteDiagnosticStep?,
    val httpStep: RouteDiagnosticStep?,
    val finalSummary: String,
    val recommendedNextAction: String,
    val timestampMs: Long,
    val elapsedMs: Long,
) {
    val selectedTunnel: String = routeDecision.tunnelProfile.name
    val matchedRule: String = routeDecision.matchedRule.name
    val routeExplanation: String = routeDecision.plainReason
    val dnsPolicy: String = routeDecision.dnsPolicySummary
    val limitationNote: String = VERSION_LIMITATION_NOTE
    val safetyNote: String = SAFETY_NOTE

    fun checks(): List<RouteDiagnosticStep> = listOfNotNull(dnsStep, tcpStep, tlsStep, httpStep)

    fun toPlainText(): String = buildString {
        appendLine("ViRouteFS $appVersion — отчёт диагностики маршрута")
        appendLine("Время: ${timestampMs.toUtcReportTime()}")
        appendLine("Цель: $inputTarget")
        appendLine("Хост проверки: $hostForDiagnostics")
        appendLine("Порт: $port")
        appendLine("SNI: ${sni ?: "не указан"}")
        appendLine()
        appendLine("Выбранный маршрут: $selectedTunnel")
        appendLine("Сработавшее правило: $matchedRule")
        appendLine("DNS-политика: $dnsPolicy")
        appendLine("Почему выбран: $routeExplanation")
        routeDecision.warnings.forEach { appendLine("Предупреждение: $it") }
        appendLine()
        appendLine("Проверки:")
        checks().forEach { step ->
            appendLine("- ${step.title}: ${step.result?.status?.toReportLabel() ?: "не выполнялась"}")
            appendLine("  ${step.result?.simpleExplanation ?: step.skippedReason.orEmpty()}")
            step.result?.technicalDetailsWithElapsed()?.lineSequence()?.forEach { detailLine ->
                appendLine("  $detailLine")
            }
        }
        appendLine()
        appendLine("Итог: $finalSummary")
        appendLine("Рекомендация: $recommendedNextAction")
        appendLine("Ограничение версии: $limitationNote")
        appendLine("Безопасность: $safetyNote")
        appendLine("Время выполнения отчёта: $elapsedMs мс")
    }
}

private fun DiagnosticStatus.toReportLabel(): String = when (this) {
    DiagnosticStatus.SUCCESS -> "успех"
    DiagnosticStatus.WARNING -> "предупреждение"
    DiagnosticStatus.ERROR -> "ошибка"
}

private fun Long.toUtcReportTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date(this))
