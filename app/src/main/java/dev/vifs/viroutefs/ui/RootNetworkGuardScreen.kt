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
import dev.vifs.viroutefs.root.RootManagedModule
import dev.vifs.viroutefs.root.RootNetworkGuardConfig
import dev.vifs.viroutefs.root.RootNetworkGuardController
import dev.vifs.viroutefs.root.RootNetworkRecoveryController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootNetworkGuardScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootNetworkGuardController(context.applicationContext) }
    val recovery = remember(context) { RootNetworkRecoveryController(context.applicationContext) }
    var config by remember(controller) { mutableStateOf(controller.loadConfig()) }
    var present by remember {
        mutableStateOf(recovery.currentState()?.modules.orEmpty().any(NETWORK_GUARD_MODULES::contains))
    }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Ядерная защита сети",
                    "Аварийный запрет прямых утечек IPv4, IPv6 и DNS после остановки VPN",
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
                    StatusChip(if (present) "Есть root-сессия" else "Выключена")
                }
                WarningText(
                    "VPN lock намеренно продолжает блокировать прямой интернет пользовательских приложений, если ViRouteFS или VPN-процесс упал. Перед удалением приложения выключите защиту; если root станет недоступен, правила гарантированно исчезнут после перезагрузки телефона.",
                )
                Text(
                    "Системные UID Android ниже 10000 не блокируются, чтобы не ломать телефонию и базовые службы. Для них строгая защита появится только после физической проверки отдельного allowlist.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            CardBlock {
                GuardOption(
                    checked = config.vpnLock,
                    onChecked = { config = config.copy(vpnLock = it) },
                    enabled = !busy,
                    title = "Аварийный VPN lock",
                    description = "Разрешает пользовательским UID выход только в tun/wg; собственный UID ViRouteFS может поднимать внешний туннель.",
                )
                GuardOption(
                    checked = config.blockDirectDns,
                    onChecked = { config = config.copy(blockDirectDns = it) },
                    enabled = !busy,
                    title = "Запрет прямого DNS",
                    description = "Закрывает TCP/UDP 53 и 853 вне VPN для пользовательских UID. DNS-over-HTTPS на 443 этим правилом не определяется.",
                )
                GuardOption(
                    checked = config.blockDirectIpv6,
                    onChecked = { config = config.copy(blockDirectIpv6 = it) },
                    enabled = !busy,
                    title = "Запрет прямого IPv6",
                    description = "Не даёт пользовательским UID уйти в IPv6 мимо tun/wg, даже если основной прямой IPv4 разрешён.",
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
                        enabled = !busy,
                    )
                    Text(
                        "Понимаю, что VPN lock может оставить приложения без сети до ручной остановки или перезагрузки",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = acknowledged && !busy && !config.isEmpty,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.apply(config) }
                            message = result.message
                            present = recovery.currentState()?.modules.orEmpty().any(NETWORK_GUARD_MODULES::contains)
                            busy = false
                            if (result.successful) acknowledged = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Применяем с откатом…" else "Включить выбранную защиту")
                }
                OutlinedButton(
                    enabled = present && !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.stop() }
                            message = result.message
                            present = recovery.currentState()?.modules.orEmpty().any(NETWORK_GUARD_MODULES::contains)
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Остановить и удалить правила защиты")
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun GuardOption(
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean,
    title: String,
    description: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked, enabled = enabled)
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

private val NETWORK_GUARD_MODULES = setOf(
    RootManagedModule.EmergencyNetworkLock,
    RootManagedModule.LeakProtection,
)
