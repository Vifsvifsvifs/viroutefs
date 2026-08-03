// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.vifs.viroutefs.root.ConnectionAdaptationController
import dev.vifs.viroutefs.root.RootAccessController
import dev.vifs.viroutefs.root.RootCapabilitySnapshot
import dev.vifs.viroutefs.root.RootManagedModule
import dev.vifs.viroutefs.root.RootNetworkRecoveryController
import dev.vifs.viroutefs.root.RootProbeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootToolsScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootAccessController(context.applicationContext) }
    val recoveryController = remember(context) { RootNetworkRecoveryController(context.applicationContext) }
    val adaptationController = remember(context) { ConnectionAdaptationController(context.applicationContext) }
    val manager = remember(controller) { controller.detectedManager() }
    var checking by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<RootProbeOutcome?>(null) }
    var managerOpenError by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var recoveryMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var recoveryState by remember { mutableStateOf(recoveryController.currentState()) }
    var adaptationAcknowledged by rememberSaveable { mutableStateOf(false) }
    var adaptationBusy by remember { mutableStateOf(false) }
    var adaptationMessage by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Расширенные возможности устройства",
                    "Необязательный root-слой; обычные VPN, маршруты, DNS и Flow Scanner работают без него",
                )
                OutlinedButton(onClick = onBack) { Text("← Назад к настройкам") }
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Root-доступ", fontWeight = FontWeight.SemiBold)
                    StatusChip(
                        when {
                            outcome?.granted == true -> "Разрешён"
                            manager != null -> "Не проверен"
                            else -> "Не найден"
                        },
                    )
                }
                Text(
                    "Root-менеджер: ${manager?.displayName ?: "не обнаружен"}. Проверка запускается только по кнопке и может показать системный запрос KernelSU/Magisk/APatch.",
                    style = MaterialTheme.typography.bodySmall,
                )
                WarningText(
                    "Root даёт приложению возможность менять правила ядра. Каждый модуль будет выключен по умолчанию, получит отдельное подтверждение и собственный аварийный откат.",
                )
                Button(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        outcome = null
                        scope.launch {
                            outcome = withContext(Dispatchers.IO) { controller.requestAndProbe() }
                            checking = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (checking) "Ожидаем root-менеджер…" else "Запросить и проверить root")
                }
                if (manager != null && outcome?.granted != true) {
                    OutlinedButton(
                        onClick = {
                            managerOpenError = runCatching {
                                val intent = requireNotNull(controller.managerLaunchIntent())
                                context.startActivity(intent)
                            }.exceptionOrNull()?.localizedMessage
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Открыть ${manager.displayName}")
                    }
                }
                outcome?.let { result ->
                    Text(result.message, style = MaterialTheme.typography.bodySmall)
                    result.snapshot?.let { RootCapabilityDetails(it) }
                }
                managerOpenError?.let { WarningText("Не удалось открыть root-менеджер: $it") }
            }
        }
        item {
            CardBlock {
                Text("Безопасная модель", fontWeight = FontWeight.SemiBold)
                Text(
                    "Проверка выше ничего не меняет. Перед будущей операцией ViRouteFS сохранит только собственные цепочки и процессы, проверит применимость новой конфигурации и удалит их при ошибке. Чужие правила файрвола приложение очищать не будет.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Постоянный запуск при загрузке телефона появится только после физической проверки ручного восстановления. До этого root-модули будут запускаться исключительно из интерфейса.",
                    style = MaterialTheme.typography.bodySmall,
                )
                recoveryState?.let { state ->
                    WarningText(
                        "Найдена незавершённая root-сессия ${state.transactionId.take(8)}: ${state.modules.joinToString { it.name }}.",
                    )
                }
                OutlinedButton(
                    enabled = !recoveryBusy,
                    onClick = {
                        recoveryBusy = true
                        scope.launch {
                            val recovered = withContext(Dispatchers.IO) { recoveryController.recoverAll() }
                            recoveryMessage = recovered.message
                            recoveryState = recoveryController.currentState()
                            recoveryBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (recoveryBusy) "Восстанавливаем…" else "Удалить root-правила ViRouteFS")
                }
                recoveryMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            val adaptationPresent = RootManagedModule.ConnectionAdaptation in recoveryState?.modules.orEmpty()
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Адаптация соединений (root)",
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    StatusChip(
                        when {
                            adaptationBusy -> "Операция…"
                            adaptationPresent -> "Есть root-сессия"
                            else -> "Выключено"
                        },
                    )
                }
                Text(
                    "Отдельный от ByeDPI системный режим на базе zapret2/nfqws2. Он обрабатывает только веб-порты TCP 80/443 и QUIC UDP 443 через собственную NFQUEUE-цепочку ViRouteFS; это не VPN и не скрытие IP.",
                    style = MaterialTheme.typography.bodySmall,
                )
                WarningText(
                    "Экспериментальный root-модуль ещё не проверен на вашем телефоне. При несовместимом ядре запуск будет отменён, но изменение сетевых правил всё равно может кратковременно прервать соединения.",
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = adaptationAcknowledged,
                        onCheckedChange = { adaptationAcknowledged = it },
                        enabled = !adaptationBusy && !adaptationPresent,
                    )
                    Text(
                        "Понимаю риск и включаю этот root-модуль вручную",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = adaptationAcknowledged && !adaptationBusy && !adaptationPresent,
                    onClick = {
                        adaptationBusy = true
                        adaptationMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { adaptationController.start() }
                            adaptationMessage = result.message
                            recoveryState = recoveryController.currentState()
                            adaptationBusy = false
                            if (result.successful) adaptationAcknowledged = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (adaptationBusy) "Применяем с откатом…" else "Включить адаптацию соединений")
                }
                OutlinedButton(
                    enabled = adaptationPresent && !adaptationBusy,
                    onClick = {
                        adaptationBusy = true
                        adaptationMessage = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { adaptationController.stop() }
                            adaptationMessage = result.message
                            recoveryState = recoveryController.currentState()
                            adaptationBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Остановить и удалить правила модуля")
                }
                adaptationMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Техническая основа: zapret2 v1.0.4, лицензия MIT. Встроенные файлы проверяются по SHA-256 при сборке и перед запуском.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(ROOT_MODULES, key = RootModuleDescriptor::title) { module ->
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(module.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    StatusChip(module.status)
                }
                Text(module.description, style = MaterialTheme.typography.bodySmall)
                Text(
                    module.requirements,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RootCapabilityDetails(snapshot: RootCapabilitySnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("UID: ${snapshot.uid ?: "неизвестен"} • SELinux: ${snapshot.selinuxMode.ifBlank { "неизвестно" }}")
        Text("Ядро: ${snapshot.kernelRelease.ifBlank { "неизвестно" }}", style = MaterialTheme.typography.bodySmall)
        CapabilityLine("NFQUEUE", snapshot.hasNfQueue)
        CapabilityLine("iptables / IPv6", snapshot.hasIptables && snapshot.hasIp6tables)
        CapabilityLine("nftables", snapshot.hasNftables)
        CapabilityLine("tcpdump / PCAP", snapshot.hasTcpdump)
        CapabilityLine("tc / управление трафиком", snapshot.hasTrafficControl)
        CapabilityLine("ядерный WireGuard", snapshot.hasWireGuardKernelModule)
    }
}

@Composable
private fun CapabilityLine(name: String, available: Boolean) {
    Text(
        "$name: ${if (available) "доступно" else "не найдено"}",
        style = MaterialTheme.typography.bodySmall,
        color = if (available) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private data class RootModuleDescriptor(
    val title: String,
    val description: String,
    val requirements: String,
    val status: String = "Следующий этап",
)

private val ROOT_MODULES = listOf(
    RootModuleDescriptor(
        "Файрвол приложений",
        "Правила Wi‑Fi, мобильной сети, роуминга и фоновой передачи, работающие даже при выключенном Android VpnService.",
        "Нужно: root и отдельные ViRouteFS-цепочки iptables/nftables.",
    ),
    RootModuleDescriptor(
        "Усиленный запрет и защита утечек",
        "Ядерная блокировка IPv4, IPv6 и DNS, сохраняющая запрет при аварийной остановке VPN-процесса.",
        "Нужно: root, watchdog и ручная кнопка полного восстановления сети.",
    ),
    RootModuleDescriptor(
        "Flow Scanner и локальный PCAP",
        "Root-атрибуция сокетов и ограниченная запись выбранного трафика без расшифровки TLS, только с ручным экспортом.",
        "Нужно: доступ к conntrack/proc или tcpdump; лимит размера и очистка по умолчанию.",
    ),
    RootModuleDescriptor(
        "Раздача выбранного VPN",
        "Маршрутизация клиентов точки доступа через выбранный VPN-профиль с NAT, IPv6-контролем и будущим выбором маршрута по клиенту.",
        "Нужно: root, forwarding/NAT и определение интерфейсов hotspot без жёстко заданных имён.",
    ),
    RootModuleDescriptor(
        "Автоматизации",
        "Отдельные сетевые политики по Wi‑Fi/мобильной сети, экрану и расписанию.",
        "Нужно: сначала завершить транзакционный файрвол и восстановление.",
    ),
    RootModuleDescriptor(
        "Ядерный WireGuard",
        "Опциональный быстрый backend при наличии модуля ядра; userspace WireGuard останется переносимым резервом.",
        "Нужно: модуль wireguard, утилита wg и физическое сравнение маршрутов/утечек.",
    ),
)
