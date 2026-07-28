package dev.vifs.viroutefs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.diagnostics.DiagnosticStatus
import dev.vifs.viroutefs.route.RouteDiagnosticReport
import dev.vifs.viroutefs.route.RouteDiagnosticStep
import dev.vifs.viroutefs.routing.RouteDecision

@Composable
fun RouteDecisionCard(routeDecision: RouteDecision) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteInfoCard(
            title = "Решение маршрута",
            simple = "${routeDecision.input} → ${routeDecision.tunnelProfile.name}",
            details = routeDecision.technicalDetails,
            action = routeDecision.recommendedAction,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RouteLabeledText("Профиль", "${routeDecision.tunnelProfile.name} (${routeDecision.tunnelProfile.type.label})")
                RouteLabeledText("Правило", "${routeDecision.matchedRule.name}, приоритет ${routeDecision.matchedRule.priority}")
                RouteLabeledText("DNS-политика", routeDecision.dnsPolicySummary)
                RouteLabeledText("Почему выбран", routeDecision.plainReason)
                RouteLabeledText("DNS и утечки", routeDecision.dnsLeakSummary)
                if (routeDecision.tunnelProfile.mockOnly) {
                    RouteLabeledText("Ограничение профиля", routeDecision.profileMockSummary)
                }
                routeDecision.warnings.forEach { warning ->
                    RouteLabeledText("Предупреждение", warning)
                }
            }
        }
    }
}

@Composable
fun RouteDiagnosticsInputCard(
    target: String,
    port: String,
    sni: String,
    isRunning: Boolean,
    onTargetChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSniChange: (String) -> Unit,
    onRun: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Диагностика маршрута",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Проверяйте только свои ресурсы или сети, где у вас есть разрешение.")
            Text("В этой версии диагностика выполняется через текущее подключение Android. Выбранный маршрут пока симулируется.")
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChange,
                label = { Text("Домен, IP или приложение") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Порт") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = sni,
                    onValueChange = onSniChange,
                    label = { Text("SNI") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Button(
                onClick = onRun,
                enabled = !isRunning,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(if (isRunning) "Проверка..." else "Проверить маршрут")
            }
        }
    }
}

@Composable
fun RouteDiagnosticReportCard(
    report: RouteDiagnosticReport,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        RouteInfoCard(
            title = "Итог",
            simple = report.finalSummary,
            details = "Цель: ${report.inputTarget}\nХост проверки: ${report.hostForDiagnostics}\nПорт: ${report.port}\nВремя выполнения: ${report.elapsedMs} мс",
            action = report.recommendedNextAction,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RouteLabeledText("Выбранный маршрут", report.selectedTunnel)
                RouteLabeledText("Сработавшее правило", report.matchedRule)
                RouteLabeledText("DNS-политика", report.dnsPolicy)
                RouteLabeledText("Почему выбран", report.routeExplanation)
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Проверки",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                report.checks().forEach { step ->
                    RouteDiagnosticStepRow(step)
                }
            }
        }
        RouteInfoCard(
            title = "Граница этой проверки",
            simple = report.limitationNote,
            details = "Сетевые проверки отчёта выполняются самим ViRouteFS вне собственного TUN. Они не подтверждают, что выбранное приложение действительно прошло через указанный VPN.",
            action = report.recommendedNextAction,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onCopy) {
                Text("Скопировать отчёт")
            }
            Button(onClick = onShare) {
                Text("Поделиться отчётом")
            }
        }
    }
}

@Composable
fun RouteDiagnosticHistoryCard(report: RouteDiagnosticReport) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = report.inputTarget,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Маршрут: ${report.selectedTunnel}")
            Text("Итог: ${report.finalSummary.lineSequence().firstOrNull().orEmpty()}")
            Text("Время выполнения: ${report.elapsedMs} мс • timestamp: ${report.timestampMs}")
        }
    }
}

@Composable
fun RouteDiagnosticStepRow(step: RouteDiagnosticStep) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                AssistChip(
                    onClick = {},
                    label = { Text(step.result?.status?.toRouteStatusLabel() ?: "Не запускалась") },
                )
            }
            Text(step.result?.simpleExplanation ?: step.skippedReason.orEmpty())
            step.result?.let { result ->
                RouteLabeledText("Технические детали", result.technicalDetailsWithElapsed())
            }
        }
    }
}

fun Context.copyRouteReport(report: RouteDiagnosticReport) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("ViRouteFS route report", report.toPlainText()))
    Toast.makeText(this, "Отчёт скопирован", Toast.LENGTH_SHORT).show()
}

fun Context.shareRouteReport(report: RouteDiagnosticReport) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, report.toPlainText())
    }
    startActivity(Intent.createChooser(shareIntent, "Поделиться отчётом"))
}

@Composable
private fun RouteInfoCard(
    title: String,
    simple: String,
    details: String,
    action: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            RouteLabeledText("Простое объяснение", simple)
            RouteLabeledText("Технические детали", details)
            RouteLabeledText("Рекомендация", action)
        }
    }
}

@Composable
private fun RouteLabeledText(
    label: String,
    body: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun DiagnosticStatus.toRouteStatusLabel(): String = when (this) {
    DiagnosticStatus.SUCCESS -> "Успех"
    DiagnosticStatus.WARNING -> "Предупреждение"
    DiagnosticStatus.ERROR -> "Ошибка"
}
