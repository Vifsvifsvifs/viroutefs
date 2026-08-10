// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import dev.vifs.viroutefs.InstalledAppUi
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.loadInstalledAppsForRouting
import dev.vifs.viroutefs.root.RootAppFirewallConfig
import dev.vifs.viroutefs.root.RootAppFirewallController
import dev.vifs.viroutefs.root.RootManagedModule
import dev.vifs.viroutefs.root.RootNetworkRecoveryController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootAppFirewallScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootAppFirewallController(context.applicationContext) }
    val recovery = remember(context) { RootNetworkRecoveryController(context.applicationContext) }
    var config by remember(controller) { mutableStateOf(controller.loadConfig()) }
    var installedApps by remember(context) { mutableStateOf<List<InstalledAppUi>>(emptyList()) }
    var appsLoading by remember(context) { mutableStateOf(true) }
    var mode by rememberSaveable { mutableStateOf(RootFirewallListMode.All) }
    var search by rememberSaveable { mutableStateOf("") }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var firewallPresent by remember {
        mutableStateOf(RootManagedModule.AppFirewall in recovery.currentState()?.modules.orEmpty())
    }
    LaunchedEffect(context) {
        installedApps = withContext(Dispatchers.IO) { context.loadInstalledAppsForRouting() }
        appsLoading = false
    }
    val selectedPackages = mode.packages(config)
    val filteredApps = remember(installedApps, selectedPackages, search) {
        val query = search.trim().lowercase()
        installedApps.asSequence()
            .filter { app ->
                query.isBlank() ||
                    app.label.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query)
            }
            .sortedWith(
                compareByDescending<InstalledAppUi> { it.packageName in selectedPackages }
                    .thenBy { it.uid < MIN_SAFE_FIREWALL_UID }
                    .thenBy { it.isSystem }
                    .thenBy { it.label.lowercase() }
                    .thenBy { it.packageName },
            )
            .toList()
    }
    val sharedUidCounts = remember(installedApps) { installedApps.groupingBy(InstalledAppUi::uid).eachCount() }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Root-файрвол приложений",
                    "Отдельные правила IPv4/IPv6, работающие и без Android VpnService",
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
                    StatusChip(if (firewallPresent) "Есть root-сессия" else "Выключен")
                }
                Text(
                    "Конфигурация хранится локально без резервного копирования. Root запрашивается только при применении или остановке.",
                    style = MaterialTheme.typography.bodySmall,
                )
                WarningText(
                    "«Wi‑Fi» и «Мобильная сеть» относятся к прямым интерфейсам Android. Когда приложение идёт через активный VPN/TUN, дополнительно отметьте режим «VPN» или используйте «Все сети». Это исключает ложное обещание блокировки по физическому каналу после перепаковки трафика VPN-процессом.",
                )
            }
        }
        item {
            CardBlock {
                Text("Какую сеть настраиваем", fontWeight = FontWeight.SemiBold)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RootFirewallListMode.entries.chunked(2).forEach { rowModes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowModes.forEach { itemMode ->
                                FilterChip(
                                    selected = mode == itemMode,
                                    onClick = { mode = itemMode },
                                    label = { Text(itemMode.label) },
                                )
                            }
                        }
                    }
                }
                Text(
                    mode.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Выбрано в этом режиме: ${selectedPackages.size} • всего уникальных пакетов: ${config.allPackages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Поиск приложений") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (appsLoading) {
                    Text("Загружаем локальный список приложений…", style = MaterialTheme.typography.bodySmall)
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = InstalledAppUi::packageName) { app ->
                        val selected = app.packageName in selectedPackages
                        val protectedSystemUid = app.uid < MIN_SAFE_FIREWALL_UID
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy && !protectedSystemUid) {
                                    config = mode.withPackage(config, app.packageName, !selected)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainer
                                },
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                InstalledApplicationIcon(
                                    packageName = app.packageName,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${app.packageName} • UID ${app.uid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    if (sharedUidCounts[app.uid].orZero() > 1) {
                                        Text(
                                            "Общий UID: правило затронет все приложения с этим UID",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                        )
                                    }
                                }
                                when {
                                    protectedSystemUid -> StatusChip("Защищённый UID")
                                    selected -> StatusChip("Блок")
                                    app.isSystem -> StatusChip("Системное")
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            CardBlock {
                WarningText(
                    "Применение меняет системный OUTPUT-файрвол. ViRouteFS создаёт только цепочки VIROUTEFS_FW_* и никогда не выполняет глобальный flush. За один набор ограничено 64 уникальных UID.",
                )
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
                        "Понимаю риск и применяю root-файрвол вручную",
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
                            firewallPresent = RootManagedModule.AppFirewall in recovery.currentState()?.modules.orEmpty()
                            busy = false
                            if (result.successful) acknowledged = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Применяем с откатом…" else "Сохранить и применить")
                }
                OutlinedButton(
                    enabled = firewallPresent && !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.stop() }
                            message = result.message
                            firewallPresent = RootManagedModule.AppFirewall in recovery.currentState()?.modules.orEmpty()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Остановить root-файрвол")
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private enum class RootFirewallListMode(
    val label: String,
    val description: String,
) {
    All("Все сети", "Полный запрет UID независимо от прямого или VPN-интерфейса."),
    Wifi("Wi‑Fi", "Запрет прямого выхода через Wi‑Fi-интерфейсы wlan/swlan."),
    Cellular("Мобильная", "Запрет прямого выхода через rmnet/ccmni/pdp/wwan."),
    Vpn("VPN", "Запрет отправки UID в интерфейсы tun/wg, включая ViRouteFS TUN."),
    ;

    fun packages(config: RootAppFirewallConfig): Set<String> = when (this) {
        All -> config.blockAllPackages
        Wifi -> config.blockWifiPackages
        Cellular -> config.blockCellularPackages
        Vpn -> config.blockVpnPackages
    }

    fun withPackage(config: RootAppFirewallConfig, packageName: String, selected: Boolean): RootAppFirewallConfig {
        fun Set<String>.changed(): Set<String> = if (selected) this + packageName else this - packageName
        return when (this) {
            All -> config.copy(blockAllPackages = config.blockAllPackages.changed()).normalized()
            Wifi -> config.copy(blockWifiPackages = config.blockWifiPackages.changed()).normalized()
            Cellular -> config.copy(blockCellularPackages = config.blockCellularPackages.changed()).normalized()
            Vpn -> config.copy(blockVpnPackages = config.blockVpnPackages.changed()).normalized()
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
private const val MIN_SAFE_FIREWALL_UID = 10_000
