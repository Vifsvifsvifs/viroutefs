// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

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
import androidx.compose.runtime.LaunchedEffect
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
import dev.vifs.viroutefs.root.RootKernelWireGuardController
import dev.vifs.viroutefs.root.RootKernelWireGuardDetails
import dev.vifs.viroutefs.root.RootKernelWireGuardProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootKernelWireGuardScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootKernelWireGuardController(context.applicationContext) }
    var profiles by remember { mutableStateOf<List<RootKernelWireGuardProfile>>(emptyList()) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var running by remember(controller) { mutableStateOf(controller.isRunning()) }
    var activeProfileId by remember(controller) { mutableStateOf(controller.activeProfileId()) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var loadingProfiles by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<RootKernelWireGuardDetails?>(null) }

    suspend fun reloadProfiles() {
        profiles = withContext(Dispatchers.IO) { controller.listProfiles() }
        val preferred = activeProfileId?.let { active -> profiles.firstOrNull { it.id == active } }
            ?: profiles.firstOrNull { it.id == selectedProfileId && it.compatible }
            ?: profiles.firstOrNull { it.compatible }
        selectedProfileId = preferred?.id
        loadingProfiles = false
    }

    LaunchedEffect(controller) { reloadProfiles() }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Системный WireGuard (root)",
                    "Отдельный быстрый режим через модуль ядра; обычный WireGuard ViRouteFS без root остаётся основным совместимым вариантом",
                )
                OutlinedButton(onClick = onBack) { Text("← Назад в root-центр") }
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Состояние", fontWeight = FontWeight.SemiBold)
                    StatusChip(if (running) "Включён" else "Выключен")
                }
                activeProfileId?.let { active ->
                    val name = profiles.firstOrNull { it.id == active }?.name ?: active
                    Text("Активный профиль: $name", style = MaterialTheme.typography.bodySmall)
                }
                WarningText(
                    "Перед запуском остановите обычный сетевой контроль ViRouteFS. Системный режим не заменяет его навсегда и не включается после перезагрузки телефона.",
                )
                Text(
                    "Для полного туннеля требуется Custom DNS с обычным IP-адресом. DoH/DoT, host-переопределения и ручные маршруты других профилей здесь не смешиваются: они продолжают работать в обычном режиме ViRouteFS.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            details = withContext(Dispatchers.IO) { controller.details() }
                            details?.let {
                                running = it.running || controller.isRunning()
                                message = it.message
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Проверяем модуль…" else "Проверить модуль и состояние")
                }
                details?.let { value ->
                    value.moduleVersion?.let { Text("Версия модуля: $it", style = MaterialTheme.typography.bodySmall) }
                    if (value.running) {
                        Text(
                            "Получено ${formatKernelWireGuardBytes(value.receivedBytes)} • отправлено ${formatKernelWireGuardBytes(value.transmittedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            CardBlock {
                Text("WireGuard-профиль", fontWeight = FontWeight.SemiBold)
                when {
                    loadingProfiles -> Text("Читаем сохранённые профили…", style = MaterialTheme.typography.bodySmall)
                    profiles.isEmpty() -> WarningText("Сначала добавьте или импортируйте WireGuard-профиль на странице VPN.")
                    else -> profiles.forEach { profile ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(
                                selected = selectedProfileId == profile.id,
                                onClick = { selectedProfileId = profile.id },
                                enabled = profile.compatible && !running && !busy,
                            )
                            Column(Modifier.weight(1f)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(profile.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                                    StatusChip(if (profile.compatible) "Готов" else "Нужно исправить")
                                }
                                Text(profile.summary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
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
                        enabled = !running && !busy,
                    )
                    Text(
                        "Понимаю, что это экспериментальный root-режим и соединение кратковременно прервётся при запуске или остановке",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = acknowledged && selectedProfileId != null && !running && !busy,
                    onClick = {
                        val profileId = selectedProfileId ?: return@Button
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.start(profileId) }
                            message = result.message
                            running = controller.isRunning()
                            activeProfileId = controller.activeProfileId()
                            busy = false
                            if (result.successful) acknowledged = false
                            reloadProfiles()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Запускаем с безопасным откатом…" else "Включить системный WireGuard")
                }
                OutlinedButton(
                    enabled = running && !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.stop() }
                            message = result.message
                            running = controller.isRunning()
                            activeProfileId = controller.activeProfileId()
                            details = null
                            busy = false
                            reloadProfiles()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Остановить системный WireGuard и восстановить сеть") }
                Text(
                    "Конфигурация отката хранится только в зашифрованном виде через Android Keystore. ViRouteFS запускает только встроенные wg/wg-quick-команды и не изменяет настройки уведомлений или журналирования Magisk.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatKernelWireGuardBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> "%.2f ГБ".format(value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> "%.1f МБ".format(value / (1024.0 * 1024.0))
    value >= 1024L -> "%.1f КБ".format(value / 1024.0)
    else -> "$value Б"
}
