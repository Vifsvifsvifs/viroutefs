// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.VPN_GATE_AUTOMATIC_APP_RULE_ID
import dev.vifs.viroutefs.routing.VPN_GATE_AUTOMATIC_GROUP_ID
import dev.vifs.viroutefs.routing.VPN_GATE_VOLUNTEER_WARNING
import dev.vifs.viroutefs.routing.VpnGateCatalogClient
import dev.vifs.viroutefs.routing.createAutomaticVpnGateRoute
import dev.vifs.viroutefs.routing.withAutomaticVpnGateApps
import dev.vifs.viroutefs.routing.withAutomaticVpnGateEnabled
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun EasySetupScreen(
    padding: PaddingValues,
    config: RoutingConfig,
    onBack: () -> Unit,
    onReady: (RoutingConfig, String) -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(context) { VpnGateCatalogClient(context.applicationContext) }
    var installedApps by remember(context) { mutableStateOf<List<InstalledAppUi>>(emptyList()) }
    var loadingApps by remember(context) { mutableStateOf(true) }
    var configuring by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val currentSelection = remember(config.rules) {
        config.rules
            .firstOrNull { it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID }
            ?.appMatchers
            .orEmpty()
            .mapTo(linkedSetOf()) { it.value }
    }
    var selectedPackages by remember(config.rules) { mutableStateOf<Set<String>>(currentSelection) }

    val otherVpnTargets = remember(config.profiles, config.profileGroups) {
        val builtInIds = setOf(
            RoutingConfigDefaults.SYSTEM_PROFILE_ID,
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
            RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        )
        buildSet {
            addAll(config.profiles.map { it.id }.filterNot(builtInIds::contains))
            addAll(config.profileGroups.map { it.id }.filterNot { it == VPN_GATE_AUTOMATIC_GROUP_ID })
        }
    }
    val packagesUsedByOtherVpn = remember(config.profiles, config.rules, otherVpnTargets) {
        buildSet {
            config.profiles
                .filter { it.id in otherVpnTargets }
                .flatMapTo(this) { it.appRoutingPackages }
            config.rules
                .filter { it.targetProfileId in otherVpnTargets }
                .flatMapTo(this) { rule -> rule.appMatchers.map { it.value } }
        }
    }
    val availableApps = remember(installedApps, packagesUsedByOtherVpn, currentSelection) {
        installedApps.filter { app ->
            app.packageName !in packagesUsedByOtherVpn || app.packageName in currentSelection
        }
    }

    LaunchedEffect(context) {
        installedApps = withContext(Dispatchers.IO) { context.loadInstalledAppsForRouting() }
        if (selectedPackages.isEmpty()) {
            installedApps
                .firstOrNull { it.packageName == "com.google.android.youtube" }
                ?.packageName
                ?.takeUnless(packagesUsedByOtherVpn::contains)
                ?.let { selectedPackages = setOf(it) }
        }
        loadingApps = false
    }

    val visibleApps = remember(availableApps, selectedPackages, query) {
        val needle = query.trim().lowercase(Locale.ROOT)
        availableApps
            .filter { app ->
                needle.isBlank() ||
                    app.label.lowercase(Locale.ROOT).contains(needle) ||
                    app.packageName.lowercase(Locale.ROOT).contains(needle)
            }
            .sortedWith(
                compareByDescending<InstalledAppUi> { it.packageName in selectedPackages }
                    .thenBy { it.isSystem }
                    .thenBy { it.label.lowercase(Locale.ROOT) }
                    .thenBy { it.packageName },
            )
    }

    fun configure() {
        if (configuring || selectedPackages.isEmpty()) return
        configuring = true
        message = "Ищем свежие серверы VPNGate и измеряем опубликованную задержку…"
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val snapshot = client.fetch(config)
                    createAutomaticVpnGateRoute(
                        config = config,
                        servers = snapshot.servers,
                        excludedCountryCode = detectDeviceCountryCode(context).ifBlank { "RU" },
                        makeDefault = false,
                    ).config
                        .withAutomaticVpnGateApps(selectedPackages)
                        .withAutomaticVpnGateEnabled(true)
                }
            }.onSuccess { next ->
                onReady(
                    next,
                    "Готово: выбранные приложения (${selectedPackages.size}) направлены через автоматический VPNGate; остальные используют обычный интернет телефона.",
                )
            }.onFailure { error ->
                message = "Не удалось настроить VPNGate: ${error.localizedMessage ?: "каталог сейчас недоступен"}. Попробуйте ещё раз."
                configuring = false
            }
        }
    }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header("Настрой всё за меня", "Выберите приложения — остальное ViRouteFS сделает автоматически")
                OutlinedButton(onClick = onBack, enabled = !configuring) { Text("← Назад") }
            }
        }
        item {
            CardBlock {
                Text("Какие приложения не работают?", fontWeight = FontWeight.SemiBold)
                Text(
                    "Отметьте, например, YouTube. Только выбранные приложения пойдут через VPNGate; банковские и остальные приложения останутся на обычном интернете.",
                    style = MaterialTheme.typography.bodySmall,
                )
                WarningText(VPN_GATE_VOLUNTEER_WARNING)
                if (packagesUsedByOtherVpn.isNotEmpty()) {
                    Text(
                        "Приложения, уже назначенные другому VPN, здесь скрыты: ${packagesUsedByOtherVpn.size}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Найти приложение") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !configuring,
                )
                Text(
                    if (loadingApps) "Загружаем приложения…" else "Выбрано: ${selectedPackages.size}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        items(visibleApps, key = InstalledAppUi::packageName) { app ->
            val selected = app.packageName in selectedPackages
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !configuring) {
                        selectedPackages = if (selected) {
                            selectedPackages - app.packageName
                        } else {
                            selectedPackages + app.packageName
                        }
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
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = null,
                        enabled = !configuring,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (app.isSystem) StatusChip("Системное")
                }
            }
        }
        item {
            CardBlock {
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(
                    onClick = ::configure,
                    enabled = selectedPackages.isNotEmpty() && !configuring && !loadingApps,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (configuring) "Настраиваем…" else "Настроить и включить")
                }
                Text(
                    "При каждом следующем включении контроля сети каталог будет загружен заново. ViRouteFS выберет серверы другой страны с малой задержкой и автоматически переключится при отказе.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
