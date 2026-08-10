// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.activity.compose.BackHandler
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
import dev.vifs.viroutefs.root.RootAutomationConfig
import dev.vifs.viroutefs.root.RootAutomationController
import dev.vifs.viroutefs.root.RootAutomationNetwork
import dev.vifs.viroutefs.root.RootAutomationScreen
import dev.vifs.viroutefs.root.RootAutomationTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun RootAutomationScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootAutomationController(context.applicationContext) }
    val saved = remember(controller) { controller.loadConfig() }
    var target by rememberSaveable { mutableStateOf(saved.target) }
    var network by rememberSaveable { mutableStateOf(saved.network) }
    var screen by rememberSaveable { mutableStateOf(saved.screen) }
    var startHour by rememberSaveable { mutableStateOf(saved.startHour) }
    var endHour by rememberSaveable { mutableStateOf(saved.endHour) }
    var status by remember(controller) { mutableStateOf(controller.status()) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(status.message) }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Root-автоматизация",
                    "Условия по сети, экрану и локальному времени для одного выбранного root-модуля",
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
                    StatusChip(
                        when {
                            status.running && status.targetApplied -> "Условие активно"
                            status.running -> "Ожидает"
                            else -> "Выключена"
                        },
                    )
                }
                WarningText(
                    "Автоматика работает только после явного запуска и показывает постоянное уведомление с кнопкой остановки. После перезагрузки телефона она сама не запускается.",
                )
                Text(
                    "Одновременно автоматизируется один модуль. Если он уже включён вручную, ViRouteFS откажется запускать автоматику, чтобы не удалить чужую root-сессию.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            AutomationChoiceCard(
                title = "Что переключать",
                values = RootAutomationTarget.entries,
                selected = target,
                enabled = !status.running && !busy,
                label = RootAutomationTarget::displayName,
                onSelected = { target = it },
            )
        }
        item {
            AutomationChoiceCard(
                title = "Тип активной сети",
                values = RootAutomationNetwork.entries,
                selected = network,
                enabled = !status.running && !busy,
                label = RootAutomationNetwork::displayName,
                onSelected = { network = it },
            )
        }
        item {
            AutomationChoiceCard(
                title = "Состояние экрана",
                values = RootAutomationScreen.entries,
                selected = screen,
                enabled = !status.running && !busy,
                label = RootAutomationScreen::displayName,
                onSelected = { screen = it },
            )
        }
        item {
            CardBlock {
                Text("Локальное время", fontWeight = FontWeight.SemiBold)
                Text(
                    if (startHour == endHour) "Весь день" else "${hourLabel(startHour)} – ${hourLabel(endHour)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                HourEditor(
                    title = "Начало",
                    hour = startHour,
                    enabled = !status.running && !busy,
                    onChanged = { startHour = it },
                )
                HourEditor(
                    title = "Конец",
                    hour = endHour,
                    enabled = !status.running && !busy,
                    onChanged = { endHour = it },
                )
                Text(
                    "Одинаковое начало и окончание означает весь день; интервал через полночь поддерживается.",
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
                        enabled = !status.running && !busy,
                    )
                    Text(
                        "Разрешаю видимому фоновому режиму включать и выключать выбранный root-модуль при изменении условий",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = acknowledged && !status.running && !busy,
                    onClick = {
                        busy = true
                        message = null
                        val config = RootAutomationConfig(target, network, screen, startHour, endHour)
                        scope.launch {
                            val result = controller.start(config)
                            message = result.message
                            delay(500L)
                            status = controller.status()
                            busy = false
                            if (result.successful) acknowledged = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "Запускаем…" else "Запустить автоматику") }
                OutlinedButton(
                    enabled = status.running && !busy,
                    onClick = {
                        busy = true
                        val result = controller.stop()
                        message = result.message
                        scope.launch {
                            delay(1_500L)
                            status = controller.status()
                            message = controller.status().message
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Остановить автоматику") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun <T> AutomationChoiceCard(
    title: String,
    values: List<T>,
    selected: T,
    enabled: Boolean,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    CardBlock {
        Text(title, fontWeight = FontWeight.SemiBold)
        values.forEach { value ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = value == selected,
                    onClick = { onSelected(value) },
                    enabled = enabled,
                )
                Text(label(value), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HourEditor(
    title: String,
    hour: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChanged((hour + 23) % 24) }, enabled = enabled) { Text("−") }
        Text(hourLabel(hour), fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = { onChanged((hour + 1) % 24) }, enabled = enabled) { Text("+") }
    }
}

private fun hourLabel(hour: Int): String = "%02d:00".format(hour)
