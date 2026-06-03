package dev.vifs.viroutefs

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.diagnostics.DiagnosticResult
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.routing.AppMatcher
import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.CURRENT_ROUTING_CONFIG_VERSION
import dev.vifs.viroutefs.routing.DnsHostOverride
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.MOCK_PROFILE_LIMITATION
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViRouteFsTheme {
                Surface(modifier = Modifier.fillMaxSize()) { ViRouteFsApp() }
            }
        }
    }
}

private enum class AppScreen(val label: String, val icon: ImageVector) {
    Dashboard("Главная", Icons.Outlined.Home),
    Vpn("VPN", Icons.Outlined.Shield),
    Routes("Маршруты", Icons.Outlined.AltRoute),
    Dns("DNS", Icons.Outlined.Dns),
    Fs("FS", Icons.Outlined.Security),
    Tools("Инструменты", Icons.Outlined.Build),
    Settings("Настройки", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { RoutingConfigRepository(context.applicationContext) }
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Dashboard) }
    var config by remember { mutableStateOf(RoutingConfigDefaults.defaultConfig()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = repository.load()
        config = result.config
        message = result.errorMessage
        loaded = true
    }

    fun updateConfig(newConfig: RoutingConfig, note: String? = null) {
        val normalizedConfig = newConfig.copy(version = CURRENT_ROUTING_CONFIG_VERSION)
        config = normalizedConfig
        message = note
        scope.launch { repository.save(normalizedConfig) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("ViRouteFS")
                    Text("Visual Route & Flow Scanner", style = MaterialTheme.typography.labelMedium)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(padding, loaded, message)
            AppScreen.Vpn -> VpnScreen(padding, config, ::updateConfig)
            AppScreen.Routes -> RoutesScreen(padding, config, ::updateConfig)
            AppScreen.Dns -> DnsScreen(padding, config, ::updateConfig)
            AppScreen.Fs -> FlowScannerScreen(padding)
            AppScreen.Tools -> ToolsScreen(padding, config)
            AppScreen.Settings -> SettingsScreen(padding)
        }
    }
}

@Composable
private fun DashboardScreen(padding: PaddingValues, loaded: Boolean, message: String?) = ScreenList(padding) {
    item { Header("Главная", "Компактная карта: VPN → Маршруты → DNS → FS.") }
    item { CompactCard("0.4.1-alpha", "Профили подключений настраиваются отдельно от правил маршрутизации.", "versionCode 5 • локальная конфигурация • без телеметрии") }
    item { CompactCard("Приватность", "Все проверки запускаются пользователем. Скрытого перехвата, облачной отправки логов и аналитики нет.", "Будущие PCAP/логи остаются локально до явного экспорта.") }
    item { CompactCard("Статус конфигурации", if (loaded) "Локальная конфигурация загружена." else "Загрузка…", message ?: "Готово.") }
}

@Composable
private fun VpnScreen(padding: PaddingValues, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var vpnEnabled by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TunnelType.WireGuard) }

    ScreenList(padding) {
    item { Header("VPN", "Профили подключений и общий демонстрационный переключатель.") }
    item {
        CardBlock {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Подключить активные соединения", fontWeight = FontWeight.SemiBold)
                Switch(checked = vpnEnabled, onCheckedChange = { vpnEnabled = it })
            }
            Text("В этой версии переключатель управляет демонстрационным состоянием. Реальное Android VpnService-подключение будет добавлено позже.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = !showAdd }) { Text("+ Добавить профиль") }
            AssistChip(onClick = {}, label = { Text("${config.profiles.size} профилей") })
        }
    }
    if (showAdd) {
        item {
            CardBlock {
                Text("Добавить профиль", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("QR-код", "Буфер", "Файл", "Вручную").forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                TunnelTypeDropdown(type, onSelect = { type = it })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val id = "profile_${System.currentTimeMillis()}"
                    val dnsId = when (type) {
                        TunnelType.Direct -> RoutingConfigDefaults.DIRECT_DNS_ID
                        TunnelType.Block -> RoutingConfigDefaults.SYSTEM_DNS_ID
                        else -> RoutingConfigDefaults.TUNNEL_DNS_ID
                    }
                    val profile = TunnelProfile(id, name.ifBlank { type.label }, type, desc.ifBlank { "Профиль добавлен вручную. Движок пока не реализован." }, mockOnly = type.isMockOnly, platformNotes = MOCK_PROFILE_LIMITATION.takeIf { type.isMockOnly }, dnsPolicyId = dnsId)
                    onConfig(config.copy(profiles = config.profiles + profile), "Профиль добавлен локально.")
                    name = ""; desc = ""; showAdd = false
                }) { Text("Создать") }
            }
        }
    }
    items(config.profiles, key = { it.id }) { profile ->
        ProfileCard(profile, config, onConfig)
    }
}

}
@Composable
private fun ProfileCard(profile: TunnelProfile, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var edit by rememberSaveable(profile.id) { mutableStateOf(false) }
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var desc by rememberSaveable(profile.id) { mutableStateOf(profile.description) }
    val used = config.rules.any { it.targetProfileId == profile.id }
    val dns = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }?.name ?: "DNS не выбран"
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${profile.type.label} • ${if (profile.enabled) "включён" else "выключен"} • $dns", style = MaterialTheme.typography.bodySmall)
            }
            Switch(profile.enabled, onCheckedChange = { onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) it.copy(enabled = it.enabled.not()) else it }), null) })
        }
        if (config.defaultProfileId == profile.id) AssistChip(onClick = {}, label = { Text("Основной") }) else OutlinedButton(onClick = { onConfig(config.copy(defaultProfileId = profile.id), "Основной профиль изменён.") }) { Text("Сделать основным") }
        if (profile.mockOnly) Text("Демонстрационный профиль: реальный движок не запускается.", style = MaterialTheme.typography.bodySmall)
        profile.warningText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { edit = !edit }) { Text(if (edit) "Скрыть" else "Изменить") }
            OutlinedButton(enabled = !used && profile.type !in listOf(TunnelType.Direct, TunnelType.Block), onClick = { onConfig(config.copy(profiles = config.profiles.filterNot { it.id == profile.id }), "Профиль удалён.") }) { Text("Удалить") }
        }
        if (edit) {
            OutlinedTextField(name, { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc, { desc = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) it.copy(name = name, description = desc) else it }), "Профиль обновлён."); edit = false }) { Text("Сохранить") }
        }
    }
}

@Composable
private fun RoutesScreen(padding: PaddingValues, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    val engine = remember(config) { RouteEngine(config) }
    var input by rememberSaveable { mutableStateOf("telegram") }
    var decision by remember(config) { mutableStateOf(engine.simulate(input)) }
    ScreenList(padding) {
    item { Header("Маршруты", "Что через какое подключение ходит?") }
    item {
        CardBlock {
            OutlinedTextField(input, { input = it }, label = { Text("домен, IP или приложение") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { decision = engine.simulate(input) }) { Text("Показать маршрут") }
            Text("Профиль: ${decision.tunnelProfile.name}", fontWeight = FontWeight.SemiBold)
            Text("Правило: ${decision.matchedRule.name}")
            Text("DNS: ${decision.dnsPolicySummary}")
            Text("Почему: ${decision.plainReason}", style = MaterialTheme.typography.bodySmall)
            decision.warnings.take(2).forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
    items(config.profiles, key = { it.id }) { profile -> RouteProfileRulesCard(profile, config, onConfig) }
}

}
@Composable
private fun RouteProfileRulesCard(profile: TunnelProfile, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var appText by rememberSaveable(profile.id) { mutableStateOf("") }
    var domainText by rememberSaveable(profile.id) { mutableStateOf("") }
    var cidrText by rememberSaveable(profile.id) { mutableStateOf("") }
    val rules = config.rules.filter { it.targetProfileId == profile.id }
    val apps = rules.filter { it.type == RouteRuleType.APP || it.type == RouteRuleType.APP_GROUP }
    val domains = rules.filter { it.type == RouteRuleType.DOMAIN }
    val cidrs = rules.filter { it.type == RouteRuleType.CIDR }
    CardBlock {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column { Text(profile.name, fontWeight = FontWeight.SemiBold); Text("Apps: ${apps.flatMap { it.matchers + it.appMatchers.map { app -> app.displayName ?: app.value } }.size} • Sites: ${domains.sumOf { it.matchers.size }} • IP/CIDR: ${cidrs.sumOf { it.matchers.size }}", style = MaterialTheme.typography.bodySmall) }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Свернуть" else "Открыть") }
        }
        if (expanded) {
            MatcherSection("Приложения", apps, appText, { appText = it }, "Добавить приложение") {
                addRule(config, profile.id, RouteRuleType.APP, appText, RoutingConfigDefaults.SYSTEM_DNS_ID, onConfig); appText = ""
            }
            MatcherSection("Сайты / домены", domains, domainText, { domainText = it }, "Добавить домен") {
                addRule(config, profile.id, RouteRuleType.DOMAIN, domainText, profile.dnsPolicyId, onConfig); domainText = ""
            }
            MatcherSection("IP / CIDR", cidrs, cidrText, { cidrText = it }, "Добавить IP/CIDR") {
                addRule(config, profile.id, RouteRuleType.CIDR, cidrText, profile.dnsPolicyId, onConfig); cidrText = ""
            }
            rules.filter { it.type != RouteRuleType.DEFAULT }.forEach { rule ->
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(rule.matchers.joinToString().ifBlank { rule.appMatchers.joinToString { it.displayName ?: it.value } }, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onConfig(config.copy(rules = config.rules.filterNot { it.id == rule.id }), "Маршрут удалён.") }) { Text("×") }
                }
            }
        }
    }
}

private fun addRule(config: RoutingConfig, profileId: String, type: RouteRuleType, value: String, dnsPolicyId: String?, onConfig: (RoutingConfig, String?) -> Unit) {
    val v = value.trim()
    if (v.isBlank()) return
    val rule = RouteRule(
        id = "rule_${System.currentTimeMillis()}", name = "$v → $profileId", type = type, targetProfileId = profileId, dnsPolicyId = dnsPolicyId,
        priority = 100 + config.rules.size, matchers = listOf(v), appMatchers = if (type == RouteRuleType.APP) listOf(AppMatcher(AppMatcherPlatform.Any, v, v)) else emptyList(),
        reason = "Добавлено пользователем в компактном редакторе.", technicalDetails = "Скрытые технические поля заполнены автоматически.", recommendedAction = "Проверьте результат симулятором.",
    )
    onConfig(config.copy(rules = config.rules + rule), "Маршрут добавлен.")
}

@Composable
private fun MatcherSection(title: String, rules: List<RouteRule>, value: String, onValue: (String) -> Unit, button: String, onAdd: () -> Unit) {
    Text(title, fontWeight = FontWeight.SemiBold)
    Text(rules.flatMap { it.matchers }.take(6).joinToString().ifBlank { "Пока пусто" }, style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value, onValue, label = { Text(title) }, modifier = Modifier.weight(1f))
        Button(onClick = onAdd) { Text(button) }
    }
}

@Composable
private fun DnsScreen(padding: PaddingValues, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var domain by rememberSaveable { mutableStateOf("example.com") }
    var server by rememberSaveable { mutableStateOf("1.1.1.1") }
    var record by rememberSaveable { mutableStateOf("A") }
    var selectedProfile by rememberSaveable { mutableStateOf(config.defaultProfileId ?: config.profiles.first().id) }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }
    var app by rememberSaveable { mutableStateOf("Telegram") }
    var host by rememberSaveable { mutableStateOf("") }
    var ip by rememberSaveable { mutableStateOf("") }
    ScreenList(padding) {
    item { Header("DNS", "Проверка DNS, приложение, hosts-like overrides и DNS per connection.") }
    item { CardBlock { Text("DNS lookup checker", fontWeight = FontWeight.SemiBold); OutlinedTextField(domain, { domain = it }, label = { Text("адрес/домен") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(server, { server = it }, label = { Text("DNS server") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(record, { record = it }, label = { Text("A / AAAA") }); Text("Профиль: ${config.profiles.firstOrNull { it.id == selectedProfile }?.name ?: selectedProfile}"); Button(onClick = { scope.launch { result = DnsDiagnostic().lookup(domain, server, record) } }) { Text("Проверить DNS") }; Text("В этой версии Android может использовать системный резолвер. Прямой запрос к выбранному DNS будет улучшен позже.", style = MaterialTheme.typography.bodySmall); result?.let { DiagnosticCard("Результат DNS", it) } } }
    item { CardBlock { Text("Проверить приложение", fontWeight = FontWeight.SemiBold); Text("Выберите приложение и посмотрите, через какой DNS и маршрут должны идти его запросы."); OutlinedTextField(app, { app = it }, label = { Text("Приложение") }, modifier = Modifier.fillMaxWidth()); val d = RouteEngine(config).simulate(app); Text("Маршрут: ${d.tunnelProfile.name}"); Text("DNS policy: ${d.dnsPolicySummary}"); Text("Возможный риск: ${d.dnsLeakSummary}", style = MaterialTheme.typography.bodySmall); Text("Реальные домены приложения будут видны в FS после включения локального VPN-режима.", style = MaterialTheme.typography.bodySmall) } }
    item { CardBlock { Text("hosts-like overrides", fontWeight = FontWeight.SemiBold); Text("Локальная привязка имени к IP, аналог hosts. В этой версии используется для конфигурации и симуляции; реальное применение в DNS engine будет добавлено позже.", style = MaterialTheme.typography.bodySmall); OutlinedTextField(host, { host = it }, label = { Text("hostname") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(ip, { ip = it }, label = { Text("IP") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { if (host.isNotBlank() && ip.isNotBlank()) { onConfig(config.copy(hostOverrides = config.hostOverrides + DnsHostOverride("host_${System.currentTimeMillis()}", host, ip, true, null)), "Override добавлен."); host = ""; ip = "" } }) { Text("Добавить override") }; config.hostOverrides.forEach { override -> Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Text("${if (override.enabled) "✓" else "—"} ${override.hostname} → ${override.ipAddress}", modifier = Modifier.weight(1f)); Switch(override.enabled, { onConfig(config.copy(hostOverrides = config.hostOverrides.map { if (it.id == override.id) it.copy(enabled = it.enabled.not()) else it }), null) }); OutlinedButton(onClick = { onConfig(config.copy(hostOverrides = config.hostOverrides.filterNot { it.id == override.id }), "Override удалён.") }) { Text("×") } } } } }
    item { CardBlock { Text("DNS per connection", fontWeight = FontWeight.SemiBold); config.profiles.forEach { profile -> DnsPerProfileRow(profile, config, onConfig) } } }
}

}
@Composable
private fun DnsPerProfileRow(profile: TunnelProfile, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var custom by rememberSaveable(profile.id) { mutableStateOf("") }
    val policy = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId } ?: config.dnsPolicies.first()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
        Text(profile.name, fontWeight = FontWeight.SemiBold)
        Text("${policy.name} • ${policy.serverText ?: policy.type.label}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            config.dnsPolicies.take(4).forEach { p -> FilterChip(selected = p.id == profile.dnsPolicyId, onClick = { onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) it.copy(dnsPolicyId = p.id) else it }), "DNS профиль обновлён.") }, label = { Text(p.type.label.take(10)) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(custom, { custom = it }, label = { Text("Custom DNS") }, modifier = Modifier.weight(1f)); Button(onClick = { if (custom.isNotBlank()) { val id = "dns_${System.currentTimeMillis()}"; val newPolicy = DnsPolicy(id, "Custom DNS ${profile.name}", DnsPolicyType.Custom, custom, profile.id, "Пользовательский DNS для симуляции."); onConfig(config.copy(dnsPolicies = config.dnsPolicies + newPolicy, profiles = config.profiles.map { if (it.id == profile.id) it.copy(dnsPolicyId = id) else it }), "Custom DNS сохранён.") } }) { Text("Set") } }
        Text("DNS per connection — конфигурация/симуляция до реализации DNS engine.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FlowScannerScreen(padding: PaddingValues) {
    var selectedApp by rememberSaveable { mutableStateOf("Telegram") }
    var analyzing by rememberSaveable { mutableStateOf(false) }
    val events = listOf(
        FlowEvent("Telegram", "api.telegram.org → 149.154.x.x:443", "Xray Germany", "rule \"Telegram\"", "Tunnel DNS mock"),
        FlowEvent("Browser", "example.com → 93.184.216.34:443", "Direct", "default direct", "System DNS"),
        FlowEvent("Work", "gitlab.corp → 10.0.0.25:443", "OpenVPN Work", "*.corp", "Work DNS mock"),
    )
    ScreenList(padding) {
    item { Header("Flow Scanner", "кто куда подключается и почему") }
    item { CompactCard("FS", "FS показывает сетевые события после явного включения локального VPN-режима ViRouteFS. Скрытого перехвата нет.", "Демонстрационный режим: реального packet capture и фонового мониторинга нет.") }
    item { CardBlock { OutlinedTextField(selectedApp, { selectedApp = it }, label = { Text("Приложение") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { analyzing = true }) { Text("Старт анализа") }; AssistChip(onClick = {}, label = { Text(if (analyzing) "Демонстрационный режим" else "Ожидание") }) } }
    items(events) { event -> FlowEventCard(event) }
}

}
private data class FlowEvent(val app: String, val target: String, val route: String, val why: String, val dns: String)

@Composable
private fun FlowEventCard(event: FlowEvent) { var details by rememberSaveable(event.target) { mutableStateOf(false) }; CardBlock { Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) { Column { Text("App: ${event.app}", fontWeight = FontWeight.SemiBold); Text("Target: ${event.target}") }; AssistChip(onClick = {}, label = { Text("demo") }) }; Text("Route: ${event.route} • DNS: ${event.dns}"); Text("Why: ${event.why}", style = MaterialTheme.typography.bodySmall); OutlinedButton(onClick = { details = !details }) { Text("Детали") }; if (details) Text("DNS: ${event.dns}\nTCP/TLS/SNI: будет показано после локального VPN-режима\nRoute decision: ${event.why}\nРекомендация: проверьте правило в Routes.", style = MaterialTheme.typography.bodySmall) } }

@Composable
private fun ToolsScreen(padding: PaddingValues, config: RoutingConfig) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("example.com") }
    var port by rememberSaveable { mutableStateOf("443") }
    var sni by rememberSaveable { mutableStateOf("example.com") }
    var url by rememberSaveable { mutableStateOf("https://example.com") }
    var tcp by remember { mutableStateOf<DiagnosticResult?>(null) }
    var tls by remember { mutableStateOf<DiagnosticResult?>(null) }
    var http by remember { mutableStateOf<DiagnosticResult?>(null) }
    var routeTarget by rememberSaveable { mutableStateOf("10.0.0.5") }
    ScreenList(padding) {
    item { Header("Инструменты", "Ручная диагностика: TCP, TLS/SNI, HTTP и маршрутная симуляция.") }
    item { CompactCard("Безопасное использование", "Проверяйте только свои ресурсы или сети, где у вас есть разрешение.", "Инструменты запускаются вручную и не выполняют скрытого мониторинга.") }
    item { CardBlock { Text("TCP / TLS", fontWeight = FontWeight.SemiBold); OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(port, { port = it }, label = { Text("Port") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)); OutlinedTextField(sni, { sni = it }, label = { Text("SNI") }, modifier = Modifier.fillMaxWidth()); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { scope.launch { tcp = TcpDiagnostic().check(host, port, "5") } }) { Text("TCP check") }; Button(onClick = { scope.launch { tls = TlsDiagnostic().check(host, port, sni) } }) { Text("TLS/SNI check") } }; tcp?.let { DiagnosticCard("TCP result", it) }; tls?.let { DiagnosticCard("TLS result", it) } } }
    item { CardBlock { Text("HTTP", fontWeight = FontWeight.SemiBold); OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth()); Button(onClick = { scope.launch { http = HttpDiagnostic().check(url) } }) { Text("HTTP check") }; http?.let { DiagnosticCard("HTTP result", it) } } }
    item { CardBlock { val d = RouteEngine(config).simulate(routeTarget); Text("Route diagnostics", fontWeight = FontWeight.SemiBold); OutlinedTextField(routeTarget, { routeTarget = it }, label = { Text("домен/IP/приложение") }, modifier = Modifier.fillMaxWidth()); Text("${d.input} → ${d.tunnelProfile.name} • ${d.dnsPolicySummary}") } }
    item { CompactCard("MTU", "Плейсхолдер будущей проверки MTU.", "Пока без активных сетевых действий.") }
}

}
@Composable
private fun SettingsScreen(padding: PaddingValues) {
    val context = LocalContext.current
    var language by rememberSaveable { mutableStateOf("Русский") }
    var theme by rememberSaveable { mutableStateOf("System") }
    ScreenList(padding) {
    item { Header("Настройки", "Язык, тема и поддержка проекта.") }
    item { CardBlock { Text("Language", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Русский", "English", "中文简体").forEach { FilterChip(selected = language == it, onClick = { language = it }, label = { Text(it) }) } }; Text("Полное переключение языка будет добавлено позже."); Text("Roadmap: فارسی / Persian • Türkçe / Turkish • العربية / Arabic • Español / Spanish • Português / Portuguese • Bahasa Indonesia • Deutsch", style = MaterialTheme.typography.bodySmall) } }
    item { CardBlock { Text("Theme", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("System", "Light", "Dark", "AMOLED black").forEach { FilterChip(selected = theme == it, onClick = { theme = it }, label = { Text(it) }) } }; Text("AMOLED black будет означать настоящий чёрный фон в будущей теме.", style = MaterialTheme.typography.bodySmall) } }
    item { CardBlock { Text("Поддержать проект", fontWeight = FontWeight.SemiBold); Text("ViRouteFS — свободное open-source приложение без рекламы, телеметрии и облачной зависимости. Поддержка помогает развивать Android, Linux и Windows версии."); val links = listOf("GitHub" to "https://github.com/Vifsvifsvifs/viroutefs", "GitHub Sponsors" to "https://github.com/sponsors", "Boosty" to "https://boosty.to", "DonationAlerts" to "https://www.donationalerts.com", "Project page" to "https://github.com/Vifsvifsvifs/viroutefs"); links.forEach { (label, url) -> OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) } } } }
    item { CompactCard("Версия", "ViRouteFS ${BuildConfig.VERSION_NAME}", "versionCode ${BuildConfig.VERSION_CODE}") }
}

}
@Composable
private fun DiagnosticCard(title: String, result: DiagnosticResult) = CompactCard(title, result.simpleExplanation, result.technicalDetailsWithElapsed() + "\nЧто сделать: " + result.recommendedAction)

@Composable
private fun Header(title: String, subtitle: String) = Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodyMedium) }

@Composable
private fun CompactCard(title: String, simple: String, details: String) = CardBlock { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(simple); Text(details, style = MaterialTheme.typography.bodySmall) }

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) = Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) { Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) }

@Composable
private fun ScreenList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 14.dp, top = padding.calculateTopPadding() + 14.dp, end = 14.dp, bottom = padding.calculateBottomPadding() + 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunnelTypeDropdown(value: TunnelType, onSelect: (TunnelType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = value.label, onValueChange = {}, readOnly = true, label = { Text("Тип") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TunnelType.entries.filterNot { it.name.endsWith("Mock") }.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { onSelect(type); expanded = false }) }
        }
    }
}
