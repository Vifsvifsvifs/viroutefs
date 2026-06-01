package dev.vifs.viroutefs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Article
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.BuildConfig
import dev.vifs.viroutefs.diagnostics.DiagnosticResult
import dev.vifs.viroutefs.diagnostics.DiagnosticStatus
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.route.RouteDiagnosticReport
import dev.vifs.viroutefs.route.RouteDiagnosticRunner
import dev.vifs.viroutefs.routing.RouteDecision
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViRouteFsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViRouteFsApp()
                }
            }
        }
    }
}

private enum class AppScreen(
    @StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.screen_dashboard, Icons.Outlined.Home),
    Vpn(R.string.screen_vpn, Icons.Outlined.Shield),
    Routes(R.string.screen_routes, Icons.Outlined.AltRoute),
    Dns(R.string.screen_dns, Icons.Outlined.Dns),
    Tools(R.string.screen_tools, Icons.Outlined.Build),
    Logs(R.string.screen_logs, Icons.Outlined.Article),
    Settings(R.string.screen_settings, Icons.Outlined.Settings),
}

private data class InfoCardContent(
    val title: String,
    val simpleExplanation: String,
    val technicalDetails: String,
    val recommendedAction: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp() {
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.app_name))
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.labelResId)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(innerPadding)
            AppScreen.Vpn -> VpnScreen(innerPadding)
            AppScreen.Routes -> RoutesScreen(innerPadding)
            AppScreen.Dns -> DnsScreen(innerPadding)
            AppScreen.Tools -> ToolsScreen(innerPadding)
            AppScreen.Logs -> LogsScreen(innerPadding)
            AppScreen.Settings -> SettingsScreen(innerPadding)
        }
    }
}

@Composable
private fun DashboardScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.dashboard_header_title),
                subtitle = stringResource(R.string.dashboard_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_vpn_status_title),
                    simpleExplanation = stringResource(R.string.dashboard_vpn_status_simple),
                    technicalDetails = stringResource(R.string.dashboard_vpn_status_details),
                    recommendedAction = stringResource(R.string.dashboard_vpn_status_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_flow_title),
                    simpleExplanation = stringResource(R.string.dashboard_flow_simple),
                    technicalDetails = stringResource(R.string.dashboard_flow_details),
                    recommendedAction = stringResource(R.string.dashboard_flow_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_privacy_title),
                    simpleExplanation = stringResource(R.string.dashboard_privacy_simple),
                    technicalDetails = stringResource(R.string.dashboard_privacy_details),
                    recommendedAction = stringResource(R.string.dashboard_privacy_action),
                ),
            )
        }
    }
}

@Composable
private fun VpnScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_vpn),
                subtitle = stringResource(R.string.vpn_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.vpn_service_title),
                    simpleExplanation = stringResource(R.string.vpn_service_simple),
                    technicalDetails = stringResource(R.string.vpn_service_details),
                    recommendedAction = stringResource(R.string.vpn_service_action),
                ),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.vpn_future_rules_title),
                body = stringResource(R.string.vpn_future_rules_body),
            )
        }
    }
}

@Composable
private fun RoutesScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val repository = remember(context) { dev.vifs.viroutefs.routing.RoutingConfigRepository(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val defaultInput = stringResource(R.string.routes_default_input)

    var routingConfig by remember { mutableStateOf(dev.vifs.viroutefs.routing.RoutingConfigDefaults.defaultConfig()) }
    var configMessage by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    val validationErrors = remember(routingConfig) { dev.vifs.viroutefs.routing.validateRoutingConfig(routingConfig) }
    val routeEngine = remember(routingConfig) { RouteEngine(routingConfig) }
    val routeDiagnosticRunner = remember(routeEngine) { RouteDiagnosticRunner(routeEngine) }

    var simulatorInput by rememberSaveable { mutableStateOf(defaultInput) }
    var routeDecision by remember { mutableStateOf(routeEngine.simulate(defaultInput)) }

    var diagnosticTarget by rememberSaveable { mutableStateOf("example.com") }
    var diagnosticPort by rememberSaveable { mutableStateOf("443") }
    var diagnosticSni by rememberSaveable { mutableStateOf("example.com") }
    var routeDiagnosticRunning by rememberSaveable { mutableStateOf(false) }
    var routeDiagnosticReport by remember { mutableStateOf<RouteDiagnosticReport?>(null) }
    var routeDiagnosticHistory by remember { mutableStateOf<List<RouteDiagnosticReport>>(emptyList()) }

    fun applyConfig(newConfig: dev.vifs.viroutefs.routing.RoutingConfig, message: String) {
        routingConfig = newConfig
        routeDecision = RouteEngine(newConfig).simulate(simulatorInput)
        configMessage = message
        scope.launch { repository.save(newConfig) }
    }

    LaunchedEffect(Unit) {
        val result = repository.load()
        routingConfig = result.config
        routeDecision = RouteEngine(result.config).simulate(simulatorInput)
        configMessage = result.errorMessage
        loaded = true
    }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_routes),
                subtitle = "Редактируемая локальная конфигурация 0.4: симулятор, диагностика, профили, DNS-политики, правила, сценарии и JSON-импорт/экспорт.",
            )
        }
        item {
            PlaceholderCard(
                title = "Локально и без скрытых проверок",
                body = "Конфигурация хранится только в app-private JSON. Реальное VPN/DNS-маршрутизирование, Xray, OpenVPN, WireGuard, Hysteria2, SOCKS5 и захват пакетов пока не реализованы.",
            )
        }
        configMessage?.let { message ->
            item { PlaceholderCard(title = "Статус конфигурации", body = message) }
        }
        if (validationErrors.isNotEmpty()) {
            item { PlaceholderCard(title = "Ошибки проверки", body = validationErrors.joinToString("\n")) }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Симулятор", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = simulatorInput,
                        onValueChange = { simulatorInput = it },
                        label = { Text(stringResource(R.string.routes_simulator_input_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = { routeDecision = routeEngine.simulate(simulatorInput) }, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.routes_simulator_button))
                    }
                }
            }
        }
        item { RouteDecisionCard(routeDecision) }
        item {
            RouteDiagnosticsInputCard(
                target = diagnosticTarget,
                port = diagnosticPort,
                sni = diagnosticSni,
                isRunning = routeDiagnosticRunning,
                onTargetChange = { diagnosticTarget = it },
                onPortChange = { diagnosticPort = it.filter(Char::isDigit) },
                onSniChange = { diagnosticSni = it },
                onRun = {
                    routeDiagnosticRunning = true
                    scope.launch {
                        val report = routeDiagnosticRunner.run(
                            target = diagnosticTarget,
                            portText = diagnosticPort,
                            sni = diagnosticSni,
                            appVersion = BuildConfig.VERSION_NAME,
                        )
                        routeDiagnosticReport = report
                        routeDiagnosticHistory = (listOf(report) + routeDiagnosticHistory).take(5)
                        routeDiagnosticRunning = false
                    }
                },
            )
        }
        item {
            routeDiagnosticReport?.let { report ->
                RouteDiagnosticReportCard(report = report, onCopy = { context.copyRouteReport(report) }, onShare = { context.shareRouteReport(report) })
            } ?: InfoCard(
                InfoCardContent(
                    title = "Итог",
                    simpleExplanation = "Диагностика маршрута ещё не запускалась.",
                    technicalDetails = "Проверки выполняются только после нажатия кнопки и используют текущее подключение Android.",
                    recommendedAction = "Проверяйте только свои ресурсы или сети, где у вас есть разрешение.",
                ),
            )
        }
        item { SectionTitle("Профили маршрутов") }
        items(routingConfig.profiles) { profile ->
            EditableProfileCard(
                profile = profile,
                usedByRules = routingConfig.rules.any { it.enabled && it.targetProfileId == profile.id },
                onChange = { updated ->
                    applyConfig(routingConfig.copy(profiles = routingConfig.profiles.map { if (it.id == profile.id) updated else it }), "Профиль сохранён")
                },
                onDelete = {
                    applyConfig(routingConfig.copy(profiles = routingConfig.profiles.filterNot { it.id == profile.id }), "Профиль удалён")
                },
            )
        }
        item {
            Button(onClick = {
                val id = "profile_${System.currentTimeMillis()}"
                applyConfig(
                    routingConfig.copy(profiles = routingConfig.profiles + TunnelProfile(id, "Новый профиль", TunnelType.Direct, "Описание профиля", mockOnly = false)),
                    "Создан новый профиль",
                )
            }) { Text("Создать профиль") }
        }
        item { SectionTitle("DNS-политики") }
        items(routingConfig.dnsPolicies) { policy ->
            DnsPolicyCard(policy, routingConfig.profiles)
        }
        item { Text(dev.vifs.viroutefs.routing.DNS_POLICY_LIMITATION) }
        item { SectionTitle("Правила маршрутизации") }
        items(routingConfig.rules.sortedBy { it.priority }) { rule ->
            EditableRouteRuleCard(
                rule = rule,
                profiles = routingConfig.profiles,
                dnsPolicies = routingConfig.dnsPolicies,
                onChange = { updated ->
                    applyConfig(routingConfig.copy(rules = routingConfig.rules.map { if (it.id == rule.id) updated else it }), "Правило сохранено")
                },
                onDelete = { applyConfig(routingConfig.copy(rules = routingConfig.rules.filterNot { it.id == rule.id }), "Правило удалено") },
            )
        }
        item {
            Button(onClick = {
                val id = "rule_${System.currentTimeMillis()}"
                val profileId = routingConfig.profiles.firstOrNull()?.id.orEmpty()
                val dnsId = routingConfig.dnsPolicies.firstOrNull()?.id
                applyConfig(
                    routingConfig.copy(rules = routingConfig.rules + RouteRule(id, "Новое правило", RouteRuleType.DOMAIN, profileId, dnsId, 100, listOf("example.com"), reason = "Пользовательское правило.", technicalDetails = "Создано в редакторе маршрутов.", recommendedAction = "Проверьте правило симулятором.")),
                    "Создано новое правило",
                )
            }) { Text("Создать правило") }
        }
        item { SectionTitle("Сценарии") }
        item {
            ScenarioButtons(onApply = { name, config -> applyConfig(config, "Применён сценарий: $name") })
        }
        item { SectionTitle("Импорт/экспорт") }
        item {
            ImportExportCard(
                canExport = loaded,
                onCopy = {
                    val json = repository.exportJson(routingConfig)
                    context.copyText("ViRouteFS routing config", json)
                    configMessage = "Конфигурация скопирована в буфер обмена"
                },
                onPaste = {
                    val text = context.readClipboardText()
                    if (text.isBlank()) {
                        configMessage = "Буфер обмена пуст или содержит не текст"
                    } else {
                        repository.importJson(text).onSuccess { imported ->
                            applyConfig(imported, "Конфигурация импортирована")
                        }.onFailure { error ->
                            configMessage = "Импорт не выполнен: ${error.message.orEmpty()}"
                        }
                    }
                },
                onReset = { applyConfig(dev.vifs.viroutefs.routing.RoutingConfigDefaults.defaultConfig(), "Восстановлены настройки по умолчанию") },
            )
        }
        item { SectionTitle("Последние проверки") }
        if (routeDiagnosticHistory.isEmpty()) {
            item { Text("История текущей сессии пока пуста. Отчёты не сохраняются в файлы или базу данных.") }
        } else {
            items(routeDiagnosticHistory) { report -> RouteDiagnosticHistoryCard(report) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun EditableProfileCard(
    profile: TunnelProfile,
    usedByRules: Boolean,
    onChange: (TunnelProfile) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(profile.id) { mutableStateOf(profile.name) }
    var description by remember(profile.id) { mutableStateOf(profile.description) }
    var type by remember(profile.id) { mutableStateOf(profile.type) }
    var enabled by remember(profile.id) { mutableStateOf(profile.enabled) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${profile.id}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Вкл.")
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CycleEnumRow(label = "Тип", value = type.label, onNext = { type = TunnelType.entries[(type.ordinal + 1) % TunnelType.entries.size] })
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth())
            if (type.isMockOnly) Text(dev.vifs.viroutefs.routing.MOCK_PROFILE_LIMITATION)
            profile.platformNotes?.let { Text(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onChange(profile.copy(name = name.trim(), description = description.trim(), type = type, enabled = enabled, mockOnly = type.isMockOnly)) }) { Text("Сохранить") }
                Button(onClick = onDelete, enabled = !usedByRules) { Text("Удалить") }
            }
            if (usedByRules) Text("Нельзя удалить: активные правила используют этот профиль.")
        }
    }
}

@Composable
private fun DnsPolicyCard(policy: dev.vifs.viroutefs.routing.DnsPolicy, profiles: List<TunnelProfile>) {
    val profileName = policy.resolveThroughProfileId?.let { id -> profiles.firstOrNull { it.id == id }?.name ?: id }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(policy.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(policy.type.label)
            Text(policy.description)
            policy.serverText?.let { Text("Сервер: $it") }
            profileName?.let { Text("Ожидаемый профиль: $it") }
            Text(if (policy.enabled) "Включена" else "Отключена")
        }
    }
}

@Composable
private fun EditableRouteRuleCard(
    rule: RouteRule,
    profiles: List<TunnelProfile>,
    dnsPolicies: List<dev.vifs.viroutefs.routing.DnsPolicy>,
    onChange: (RouteRule) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var type by remember(rule.id) { mutableStateOf(rule.type) }
    var matchersText by remember(rule.id) { mutableStateOf(rule.matchers.joinToString(", ")) }
    var targetProfileId by remember(rule.id) { mutableStateOf(rule.targetProfileId) }
    var dnsPolicyId by remember(rule.id) { mutableStateOf(rule.dnsPolicyId.orEmpty()) }
    var priorityText by remember(rule.id) { mutableStateOf(rule.priority.toString()) }
    var enabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    val priority = priorityText.toIntOrNull()
    val matchers = matchersText.split(',').map { it.trim() }.filter { it.isNotBlank() }
    val errors = buildList {
        if (name.isBlank()) add("имя пустое")
        if (type != RouteRuleType.DEFAULT && matchers.isEmpty()) add("нет матчеров")
        if (priority == null || priority < 0) add("приоритет некорректен")
        if (targetProfileId !in profiles.map { it.id }) add("профиль не найден")
        if (dnsPolicyId.isNotBlank() && dnsPolicyId !in dnsPolicies.map { it.id }) add("DNS-политика не найдена")
        if (type == RouteRuleType.CIDR) matchers.filterNot { dev.vifs.viroutefs.routing.isValidCidr(it) }.forEach { add("CIDR $it некорректен") }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(rule.id, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                Text("Вкл.")
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Имя правила") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CycleEnumRow(label = "Тип", value = type.name, onNext = { type = RouteRuleType.entries[(type.ordinal + 1) % RouteRuleType.entries.size] })
            OutlinedTextField(value = matchersText, onValueChange = { matchersText = it }, label = { Text("Матчеры через запятую") }, modifier = Modifier.fillMaxWidth())
            CycleChoiceRow("Профиль", profiles, targetProfileId, { it.name }) { targetProfileId = it.id }
            CycleChoiceRow("DNS", dnsPolicies, dnsPolicyId, { it.name }) { dnsPolicyId = it.id }
            OutlinedTextField(value = priorityText, onValueChange = { priorityText = it.filter(Char::isDigit) }, label = { Text("Приоритет") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Причина: ${rule.reason}")
            if (errors.isNotEmpty()) Text("Ошибки: ${errors.joinToString()}", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onChange(rule.copy(name = name.trim(), type = type, matchers = matchers, targetProfileId = targetProfileId, dnsPolicyId = dnsPolicyId.takeIf { it.isNotBlank() }, priority = priority ?: rule.priority, enabled = enabled))
                    },
                    enabled = errors.isEmpty(),
                ) { Text("Сохранить") }
                Button(onClick = onDelete) { Text("Удалить") }
            }
        }
    }
}

@Composable
private fun <T> CycleChoiceRow(label: String, values: List<T>, currentId: String, name: (T) -> String, onSelect: (T) -> Unit) where T : Any {
    val currentIndex = values.indexOfFirst { item ->
        when (item) {
            is TunnelProfile -> item.id == currentId
            is dev.vifs.viroutefs.routing.DnsPolicy -> item.id == currentId
            else -> false
        }
    }.coerceAtLeast(0)
    val currentName = values.getOrNull(currentIndex)?.let(name) ?: "не выбрано"
    CycleEnumRow(label = label, value = currentName, onNext = {
        if (values.isNotEmpty()) onSelect(values[(currentIndex + 1) % values.size])
    })
}

@Composable
private fun CycleEnumRow(label: String, value: String, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label: $value", modifier = Modifier.weight(1f))
        Button(onClick = onNext) { Text("Выбрать") }
    }
}

@Composable
private fun ScenarioButtons(onApply: (String, dev.vifs.viroutefs.routing.RoutingConfig) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Сценарии заменяют текущую конфигурацию преднастроенным локальным набором. Перед применением скопируйте JSON, если хотите сохранить текущие правила.")
        Button(onClick = { onApply("Работа и личное", dev.vifs.viroutefs.routing.RoutingConfigDefaults.workPersonalConfig()) }) { Text("Работа и личное") }
        Text("Корпоративные домены и рабочие CIDR идут через OpenVPN Work mock, личное остаётся по правилам.")
        Button(onClick = { onApply("Медиа через быстрый тоннель", dev.vifs.viroutefs.routing.RoutingConfigDefaults.mediaFastTunnelConfig()) }) { Text("Медиа через быстрый тоннель") }
        Text("YouTube/googlevideo получают высокий приоритет Hysteria2 NL mock.")
        Button(onClick = { onApply("Банки напрямую", dev.vifs.viroutefs.routing.RoutingConfigDefaults.banksDirectConfig()) }) { Text("Банки напрямую") }
        Text("Банки, госуслуги и платежи получают максимальный приоритет Direct DNS/Direct.")
        Button(onClick = { onApply("Безопасный дефолт", dev.vifs.viroutefs.routing.RoutingConfigDefaults.safeDefaultConfig()) }) { Text("Безопасный дефолт") }
        Text("Неизвестные направления блокируются, пока пользователь не добавит явное правило.")
    }
}

@Composable
private fun ImportExportCard(canExport: Boolean, onCopy: () -> Unit, onPaste: () -> Unit, onReset: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("JSON включает version, profiles, dnsPolicies и rules. Буфер обмена используется только по нажатию кнопки.")
            Button(onClick = onCopy, enabled = canExport) { Text("Скопировать конфигурацию") }
            Button(onClick = onPaste) { Text("Вставить конфигурацию") }
            Button(onClick = onReset) { Text("Сбросить к настройкам по умолчанию") }
        }
    }
}

private fun Context.copyText(label: String, text: String) {
    val clipboard = getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
}

private fun Context.readClipboardText(): String =
    getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()

@Composable
private fun DnsScreen(contentPadding: PaddingValues) {
    val defaultDomain = stringResource(R.string.dns_default_domain)
    val defaultDnsServer = stringResource(R.string.dns_default_server)
    val defaultRecordType = stringResource(R.string.dns_default_record_type)
    val scope = rememberCoroutineScope()
    val diagnostic = remember { DnsDiagnostic() }

    var domain by rememberSaveable { mutableStateOf(defaultDomain) }
    var dnsServer by rememberSaveable { mutableStateOf(defaultDnsServer) }
    var recordType by rememberSaveable { mutableStateOf(defaultRecordType) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.dns_header_title),
                subtitle = stringResource(R.string.dns_header_subtitle),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.dns_system_resolver_title),
                body = stringResource(R.string.dns_system_resolver_body),
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(stringResource(R.string.dns_field_domain)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { dnsServer = it },
                        label = { Text(stringResource(R.string.dns_field_server)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = recordType,
                        onValueChange = { recordType = it.uppercase() },
                        label = { Text(stringResource(R.string.dns_field_record_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                result = diagnostic.lookup(domain, dnsServer, recordType)
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (isRunning) stringResource(R.string.action_checking) else stringResource(R.string.action_check))
                    }
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.dns_result_title),
                result = result,
                notRunText = stringResource(R.string.dns_result_not_run),
            )
        }
    }
}

@Composable
private fun ToolsScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val tcpDiagnostic = remember { TcpDiagnostic() }
    val tlsDiagnostic = remember { TlsDiagnostic() }
    val httpDiagnostic = remember { HttpDiagnostic() }

    var tcpHost by rememberSaveable { mutableStateOf("example.com") }
    var tcpPort by rememberSaveable { mutableStateOf("443") }
    var tcpTimeout by rememberSaveable { mutableStateOf("5") }
    var tcpRunning by rememberSaveable { mutableStateOf(false) }
    var tcpResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    var tlsHost by rememberSaveable { mutableStateOf("example.com") }
    var tlsPort by rememberSaveable { mutableStateOf("443") }
    var tlsSni by rememberSaveable { mutableStateOf("example.com") }
    var tlsRunning by rememberSaveable { mutableStateOf(false) }
    var tlsResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    var httpUrl by rememberSaveable { mutableStateOf("https://example.com") }
    var httpRunning by rememberSaveable { mutableStateOf(false) }
    var httpResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_tools),
                subtitle = stringResource(R.string.tools_header_subtitle),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.tools_safety_title),
                body = stringResource(R.string.tools_safety_body),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_tcp_title)) {
                OutlinedTextField(
                    value = tcpHost,
                    onValueChange = { tcpHost = it },
                    label = { Text(stringResource(R.string.tools_field_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tcpPort,
                        onValueChange = { tcpPort = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.tools_field_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tcpTimeout,
                        onValueChange = { tcpTimeout = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.tools_field_timeout)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        tcpRunning = true
                        scope.launch {
                            tcpResult = tcpDiagnostic.check(tcpHost, tcpPort, tcpTimeout)
                            tcpRunning = false
                        }
                    },
                    enabled = !tcpRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (tcpRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_tcp_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_tcp_result_title),
                result = tcpResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_tls_title)) {
                OutlinedTextField(
                    value = tlsHost,
                    onValueChange = { tlsHost = it },
                    label = { Text(stringResource(R.string.tools_field_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tlsPort,
                    onValueChange = { tlsPort = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tools_field_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tlsSni,
                    onValueChange = { tlsSni = it },
                    label = { Text(stringResource(R.string.tools_field_sni)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        tlsRunning = true
                        scope.launch {
                            tlsResult = tlsDiagnostic.check(tlsHost, tlsPort, tlsSni)
                            tlsRunning = false
                        }
                    },
                    enabled = !tlsRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (tlsRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_tls_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_tls_result_title),
                result = tlsResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_http_title)) {
                OutlinedTextField(
                    value = httpUrl,
                    onValueChange = { httpUrl = it },
                    label = { Text(stringResource(R.string.tools_field_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        httpRunning = true
                        scope.launch {
                            httpResult = httpDiagnostic.check(httpUrl)
                            httpRunning = false
                        }
                    },
                    enabled = !httpRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (httpRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_http_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_http_result_title),
                result = httpResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
    }
}

@Composable
private fun LogsScreen(contentPadding: PaddingValues) {
    val events = listOf(
        stringResource(R.string.logs_event_telegram),
        stringResource(R.string.logs_event_browser_dns),
        stringResource(R.string.logs_event_banking),
        stringResource(R.string.logs_event_mtu),
        stringResource(R.string.logs_event_wps),
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_logs),
                subtitle = stringResource(R.string.logs_header_subtitle),
            )
        }
        items(events) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = event,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.logs_event_details_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_settings),
                subtitle = stringResource(R.string.settings_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_local_first_title),
                    simpleExplanation = stringResource(R.string.settings_local_first_simple),
                    technicalDetails = stringResource(R.string.settings_local_first_details),
                    recommendedAction = stringResource(R.string.settings_local_first_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_safety_title),
                    simpleExplanation = stringResource(R.string.settings_safety_simple),
                    technicalDetails = stringResource(R.string.settings_safety_details),
                    recommendedAction = stringResource(R.string.settings_safety_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_version_title),
                    simpleExplanation = stringResource(R.string.settings_version_simple, BuildConfig.VERSION_NAME),
                    technicalDetails = stringResource(R.string.settings_version_details, BuildConfig.VERSION_CODE),
                    recommendedAction = stringResource(R.string.settings_version_action),
                ),
            )
        }
    }
}


@Composable
private fun DiagnosticInputCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun DiagnosticResultCard(
    title: String,
    result: DiagnosticResult?,
    notRunText: String,
) {
    val content = if (result == null) {
        InfoCardContent(
            title = title,
            simpleExplanation = notRunText,
            technicalDetails = stringResource(R.string.diagnostic_not_run_details),
            recommendedAction = stringResource(R.string.diagnostic_not_run_action),
        )
    } else {
        InfoCardContent(
            title = title,
            simpleExplanation = result.simpleExplanation,
            technicalDetails = result.technicalDetailsWithElapsed(),
            recommendedAction = result.recommendedAction,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result?.let { diagnosticResult ->
            AssistChip(
                onClick = {},
                label = { Text(diagnosticResult.status.toRussianLabel()) },
            )
        }
        InfoCard(content = content)
    }
}

private fun DiagnosticStatus.toRussianLabel(): String = when (this) {
    DiagnosticStatus.SUCCESS -> "Успех"
    DiagnosticStatus.WARNING -> "Предупреждение"
    DiagnosticStatus.ERROR -> "Ошибка"
}

@Composable
private fun ScreenList(
    contentPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoCard(content: InfoCardContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LabeledText(
                label = stringResource(R.string.card_label_simple_explanation),
                body = content.simpleExplanation,
            )
            LabeledText(
                label = stringResource(R.string.card_label_technical_details),
                body = content.technicalDetails,
            )
            LabeledText(
                label = stringResource(R.string.card_label_recommended_action),
                body = content.recommendedAction,
            )
        }
    }
}

@Composable
private fun PlaceholderCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.label_placeholder)) })
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = body)
        }
    }
}

@Composable
private fun LabeledText(
    label: String,
    body: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
