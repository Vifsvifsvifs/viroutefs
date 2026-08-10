// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.root.RootPacketCaptureController
import dev.vifs.viroutefs.root.RootPacketCaptureMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootPacketCaptureScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootPacketCaptureController(context.applicationContext) }
    var snapshot by remember(controller) { mutableStateOf(controller.snapshot()) }
    var durationSeconds by rememberSaveable { mutableStateOf(30) }
    var mode by rememberSaveable { mutableStateOf(RootPacketCaptureMode.WebAndDns) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val exporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { controller.exportCapture(uri) }
            message = result.message
            snapshot = withContext(Dispatchers.IO) { controller.snapshot() }
            busy = false
        }
    }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Локальная запись PCAP",
                    "Ограниченная диагностика сетевых пакетов на root-устройстве",
                )
                OutlinedButton(onClick = onBack) { Text("← Назад в root-центр") }
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Состояние", fontWeight = FontWeight.SemiBold)
                    StatusChip(if (snapshot.running) "Идёт запись" else "Остановлена")
                }
                Text(
                    if (snapshot.captureAvailable) {
                        "Локальный файл: ${formatCaptureSize(snapshot.captureBytes)}"
                    } else {
                        "Готового локального файла нет"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                WarningText(
                    "PCAP содержит сетевые адреса, время, порты и первые 128 байт каждого пакета. В незашифрованном трафике туда может попасть часть содержимого. ViRouteFS никуда его не отправляет.",
                )
                Text(
                    "Запись хранится в приватном каталоге приложения, не переживает переустановку и выходит наружу только через ручной экспорт. TLS не расшифровывается.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            CardBlock {
                Text("Что записывать", fontWeight = FontWeight.SemiBold)
                RootPacketCaptureMode.entries.forEach { option ->
                    CaptureChoice(
                        selected = mode == option,
                        enabled = !snapshot.running && !busy,
                        title = option.displayName,
                        description = when (option) {
                            RootPacketCaptureMode.AllTraffic -> "Все протоколы на всех интерфейсах; возможны дубликаты до и после VPN."
                            RootPacketCaptureMode.WebAndDns -> "Фиксированный фильтр TCP 80/443, UDP 443 и DNS 53/853."
                        },
                        onClick = { mode = option },
                    )
                }
                Text("Длительность", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(15, 30, 60).forEach { seconds ->
                        val selected = durationSeconds == seconds
                        if (selected) {
                            Button(
                                onClick = { durationSeconds = seconds },
                                enabled = !snapshot.running && !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("$seconds с") }
                        } else {
                            OutlinedButton(
                                onClick = { durationSeconds = seconds },
                                enabled = !snapshot.running && !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text("$seconds с") }
                        }
                    }
                }
                Text(
                    "Жёсткий предел: 25 000 пакетов и менее 4 МБ, даже если экран закроется раньше таймера.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !snapshot.running && !busy,
                    )
                    Text(
                        "Понимаю, что PCAP может содержать чувствительные сетевые метаданные и фрагменты открытого трафика",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = acknowledged && !snapshot.running && !busy,
                    onClick = {
                        busy = true
                        message = null
                        val selectedDuration = durationSeconds
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                controller.start(selectedDuration, mode)
                            }
                            message = result.message
                            snapshot = withContext(Dispatchers.IO) { controller.snapshot() }
                            busy = false
                            if (result.successful) {
                                acknowledged = false
                                delay((selectedDuration + 2) * 1_000L)
                                snapshot = withContext(Dispatchers.IO) { controller.snapshot() }
                                if (!snapshot.running && snapshot.captureAvailable) {
                                    message = "Запись завершена по таймеру. Файл готов к ручному экспорту."
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Запускаем с root…" else "Начать ограниченную запись")
                }
                OutlinedButton(
                    enabled = snapshot.running && !busy,
                    onClick = {
                        busy = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.stop() }
                            message = result.message
                            snapshot = withContext(Dispatchers.IO) { controller.snapshot() }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Остановить сейчас") }
                OutlinedButton(
                    enabled = snapshot.captureAvailable && !snapshot.running && !busy,
                    onClick = { exporter.launch("viroutefs-${System.currentTimeMillis()}.pcap") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Экспортировать PCAP…") }
                OutlinedButton(
                    enabled = snapshot.captureAvailable && !snapshot.running && !busy,
                    onClick = {
                        val result = controller.deleteCapture()
                        message = result.message
                        snapshot = controller.snapshot()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Удалить локальный PCAP") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun CaptureChoice(
    selected: Boolean,
    enabled: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCaptureSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.2f МБ".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f КБ".format(bytes / 1024.0)
    else -> "$bytes Б"
}
