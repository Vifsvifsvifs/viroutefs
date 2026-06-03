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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.diagnostics.DiagnosticResult
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.routing.CURRENT_ROUTING_CONFIG_VERSION
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.MOCK_PROFILE_LIMITATION
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.settings.AppLanguage
import dev.vifs.viroutefs.settings.AppSettings
import dev.vifs.viroutefs.settings.AppSettingsRepository
import dev.vifs.viroutefs.settings.AppThemeMode
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsRepository = AppSettingsRepository(applicationContext)
        setContent {
            var settings by remember { mutableStateOf(settingsRepository.load()) }
            ViRouteFsTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ViRouteFsApp(
                        settings = settings,
                        onSettings = { next ->
                            settings = next
                            settingsRepository.save(next)
                        },
                    )
                }
            }
        }
    }
}

private enum class AppScreen(val icon: ImageVector) {
    Dashboard(Icons.Outlined.Home),
    Vpn(Icons.Outlined.Shield),
    Routes(Icons.Outlined.AltRoute),
    Dns(Icons.Outlined.Dns),
    Fs(Icons.Outlined.Security),
    More(Icons.Outlined.MoreHoriz),
    Tools(Icons.Outlined.Build),
    Settings(Icons.Outlined.Settings),
}

private val bottomScreens = listOf(AppScreen.Dashboard, AppScreen.Vpn, AppScreen.Routes, AppScreen.Dns, AppScreen.Fs, AppScreen.More)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember(settings.language) { UiText(settings.language) }
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
        message = note ?: text.saved
        scope.launch { repository.save(normalizedConfig) }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("ViRouteFS", style = MaterialTheme.typography.titleMedium)
                    Text("Visual Route & Flow Scanner", style = MaterialTheme.typography.labelSmall)
                }
            })
        },
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(text.screen(screen), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(padding, text, loaded, message)
            AppScreen.Vpn -> VpnScreen(padding, text, config, ::updateConfig)
            AppScreen.Routes -> RoutesScreen(padding, text, config, ::updateConfig)
            AppScreen.Dns -> DnsScreen(padding, text, config, ::updateConfig)
            AppScreen.Fs -> FlowScannerScreen(padding, text)
            AppScreen.More -> MoreScreen(padding, text, onOpen = { selectedScreen = it })
            AppScreen.Tools -> ToolsScreen(padding, text, config)
            AppScreen.Settings -> SettingsScreen(padding, text, settings, onSettings)
        }
    }
}

@Composable
private fun DashboardScreen(padding: PaddingValues, text: UiText, loaded: Boolean, message: String?) = ScreenList(padding) {
    item { Header(text.dashboard, text.dashboardSubtitle) }
    item { CompactCard(text, text.version, "ViRouteFS ${BuildConfig.VERSION_NAME}", "versionCode ${BuildConfig.VERSION_CODE}") }
    item { CompactCard(text, text.privacy, text.privacyShort, text.privacyDetails) }
    item { CompactCard(text, text.configStatus, if (loaded) text.configLoaded else text.loading, message ?: text.ready) }
}

@Composable
private fun VpnScreen(padding: PaddingValues, text: UiText, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var vpnEnabled by rememberSaveable { mutableStateOf(false) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TunnelType.WireGuard) }

    ScreenList(padding) {
        item { Header(text.vpn, text.vpnSubtitle) }
        item {
            CardBlock {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(text.masterSwitch, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        StatusChip(if (vpnEnabled) text.on else text.off)
                    }
                    Switch(checked = vpnEnabled, onCheckedChange = { vpnEnabled = it })
                }
                Details(text.details, text.vpnDemoDetails)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { showAdd = !showAdd }) { Text(if (showAdd) text.hide else text.addProfile) }
                AssistChip(onClick = {}, label = { Text(text.profileCount(config.profiles.size)) })
            }
        }
        if (showAdd) {
            item {
                CardBlock {
                    Text(text.addProfile, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(text.qr, text.clipboard, text.file, text.manual).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    TunnelTypeDropdown(text, type, onSelect = { type = it })
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(text.description) }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val id = "profile_${System.currentTimeMillis()}"
                        val dnsId = when (type) {
                            TunnelType.Direct -> RoutingConfigDefaults.DIRECT_DNS_ID
                            TunnelType.Block -> RoutingConfigDefaults.SYSTEM_DNS_ID
                            else -> RoutingConfigDefaults.TUNNEL_DNS_ID
                        }
                        val profile = TunnelProfile(
                            id = id,
                            name = name.ifBlank { type.label },
                            type = type,
                            description = desc.ifBlank { text.mockProfileDescription },
                            mockOnly = type.isMockOnly,
                            platformNotes = MOCK_PROFILE_LIMITATION.takeIf { type.isMockOnly },
                            dnsPolicyId = dnsId,
                        )
                        onConfig(config.copy(profiles = config.profiles + profile), text.profileAdded)
                        name = ""
                        desc = ""
                        showAdd = false
                    }) { Text(text.create) }
                }
            }
        }
        items(config.profiles, key = { it.id }) { profile -> ProfileCard(text, profile, config, onConfig) }
    }
}

@Composable
private fun ProfileCard(text: UiText, profile: TunnelProfile, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(false) }
    var edit by rememberSaveable(profile.id) { mutableStateOf(false) }
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var desc by rememberSaveable(profile.id) { mutableStateOf(profile.description) }
    val used = config.rules.any { it.targetProfileId == profile.id }
    val dns = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }?.name ?: text.noDns
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("${profile.type.label} • DNS: $dns", style = MaterialTheme.typography.labelSmall)
            }
            StatusChip(if (profile.enabled) text.on else text.off)
            Switch(profile.enabled, onCheckedChange = { onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) it.copy(enabled = !it.enabled) else it }), null) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (config.defaultProfileId == profile.id) AssistChip(onClick = {}, label = { Text(text.defaultProfile) }) else OutlinedButton(onClick = { onConfig(config.copy(defaultProfileId = profile.id), text.defaultChanged) }) { Text(text.makeDefault) }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) text.less else text.details) }
            OutlinedButton(enabled = !used && profile.type !in listOf(TunnelType.Direct, TunnelType.Block), onClick = { onConfig(config.copy(profiles = config.profiles.filterNot { it.id == profile.id }), text.profileDeleted) }) { Text(text.delete) }
        }
        if (expanded) {
            Text(profile.description, style = MaterialTheme.typography.bodySmall)
            if (profile.mockOnly) WarningText(text.mockOnly)
            profile.warningText?.let { WarningText(it) }
            TextButton(onClick = { edit = !edit }) { Text(if (edit) text.cancel else text.edit) }
        }
        if (expanded && edit) {
            OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(desc, { desc = it }, label = { Text(text.description) }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) it.copy(name = name, description = desc) else it }), text.profileUpdated); edit = false }) { Text(text.save) }
        }
    }
}

@Composable
private fun RoutesScreen(padding: PaddingValues, text: UiText, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var target by rememberSaveable { mutableStateOf("telegram") }
    val decision = remember(config, target) { RouteEngine(config).simulate(target) }
    ScreenList(padding) {
        item { Header(text.routes, text.routesSubtitle) }
        item {
            CardBlock {
                Text(text.simulation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(target, { target = it }, label = { Text(text.domainIpApp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("${decision.input} → ${decision.tunnelProfile.name}", style = MaterialTheme.typography.bodySmall)
                StatusChip(decision.dnsPolicySummary)
                Details(text.details, "${decision.plainReason}\n${decision.technicalDetails}\n${decision.recommendedAction}")
            }
        }
        items(config.rules, key = { it.id }) { rule -> RouteRuleCard(text, rule, config, onConfig) }
    }
}

@Composable
private fun RouteRuleCard(text: UiText, rule: RouteRule, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var expanded by rememberSaveable(rule.id) { mutableStateOf(false) }
    val profile = config.profiles.firstOrNull { it.id == rule.targetProfileId }?.name ?: rule.targetProfileId
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("${rule.type} • #${rule.priority} • ${rule.matchers.size + rule.appMatchers.size} ${text.matchers}", style = MaterialTheme.typography.labelSmall)
            }
            StatusChip(if (rule.enabled) text.on else text.off)
        }
        Text("→ $profile", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onConfig(config.copy(rules = config.rules.map { if (it.id == rule.id) it.copy(enabled = !it.enabled) else it }), text.saved) }) { Text(if (rule.enabled) text.disable else text.enable) }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) text.less else text.editors) }
        }
        if (expanded) {
            Text(text.appsDomainsIps, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            Text((rule.appMatchers.map { it.displayName ?: it.value } + rule.matchers).joinToString(" • "), style = MaterialTheme.typography.bodySmall)
            Details(text.details, "${rule.reason}\n${rule.technicalDetails}\n${rule.recommendedAction}")
        }
    }
}

@Composable
private fun DnsScreen(padding: PaddingValues, text: UiText, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var domain by rememberSaveable { mutableStateOf("example.com") }
    var server by rememberSaveable { mutableStateOf("1.1.1.1") }
    var record by rememberSaveable { mutableStateOf("A") }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }
    ScreenList(padding) {
        item { Header(text.dns, text.dnsSubtitle) }
        item {
            CardBlock {
                Text(text.lookup, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(domain, { domain = it }, label = { Text(text.domain) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(record, { record = it }, label = { Text(text.type) }, modifier = Modifier.weight(0.45f), singleLine = true)
                }
                OutlinedTextField(server, { server = it }, label = { Text(text.dnsServer) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { scope.launch { result = DnsDiagnostic().lookup(domain, server, record) } }) { Text(text.check) }
                result?.let { DiagnosticCard(text, text.dnsResult, it) }
            }
        }
        item { CompactCard(text, text.policies, text.policyCount(config.dnsPolicies.size), text.dnsPolicyLimit) }
        items(config.dnsPolicies, key = { it.id }) { policy -> DnsPolicyCard(text, policy, config, onConfig) }
        item { CompactCard(text, text.hostOverrides, text.overrideCount(config.hostOverrides.size), config.hostOverrides.joinToString("\n") { "${it.hostname} → ${it.ipAddress}" }.ifBlank { text.none }) }
    }
}

@Composable
private fun DnsPolicyCard(text: UiText, policy: DnsPolicy, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var expanded by rememberSaveable(policy.id) { mutableStateOf(false) }
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(policy.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("${policy.type.label} • ${policy.serverText ?: text.system}", style = MaterialTheme.typography.labelSmall)
            }
            StatusChip(if (policy.enabled) text.on else text.off)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onConfig(config.copy(dnsPolicies = config.dnsPolicies.map { if (it.id == policy.id) it.copy(enabled = !it.enabled) else it }), text.saved) }) { Text(if (policy.enabled) text.disable else text.enable) }
            OutlinedButton(onClick = { expanded = !expanded }) { Text(if (expanded) text.less else text.details) }
        }
        if (expanded) Text(policy.description, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FlowScannerScreen(padding: PaddingValues, text: UiText) {
    val events = listOf(
        text.fsEvent("DNS", "example.com", "System DNS", text.localOnly),
        text.fsEvent("TCP", "93.184.216.34:443", "Direct", text.simulated),
        text.fsEvent("TLS", "SNI example.com", "Direct", text.certOk),
        text.fsEvent("HTTP", "GET /", "Direct", text.waiting),
    )
    ScreenList(padding) {
        item { Header(text.fs, text.fsSubtitle) }
        items(events) { event -> EventRow(event) }
        item { CompactCard(text, text.limitation, text.fsLimitShort, text.fsLimitDetails) }
    }
}

@Composable
private fun ToolsScreen(padding: PaddingValues, text: UiText, config: RoutingConfig) {
    val scope = rememberCoroutineScope()
    var host by rememberSaveable { mutableStateOf("example.com") }
    var port by rememberSaveable { mutableStateOf("443") }
    var sni by rememberSaveable { mutableStateOf("example.com") }
    var url by rememberSaveable { mutableStateOf("https://example.com") }
    var routeTarget by rememberSaveable { mutableStateOf("youtube.com") }
    var tcp by remember { mutableStateOf<DiagnosticResult?>(null) }
    var tls by remember { mutableStateOf<DiagnosticResult?>(null) }
    var http by remember { mutableStateOf<DiagnosticResult?>(null) }
    ScreenList(padding) {
        item { Header(text.tools, text.toolsSubtitle) }
        item {
            CardBlock {
                Text(text.tcpTls, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(host, { host = it }, label = { Text(text.host) }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(port, { port = it }, label = { Text(text.port) }, modifier = Modifier.weight(0.45f), singleLine = true)
                }
                OutlinedTextField(sni, { sni = it }, label = { Text("SNI") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = { scope.launch { tcp = TcpDiagnostic().check(host, port, "5") } }) { Text(text.tcpCheck) }
                    Button(onClick = { scope.launch { tls = TlsDiagnostic().check(host, port, sni) } }) { Text(text.tlsCheck) }
                }
                tcp?.let { DiagnosticCard(text, text.tcpResult, it) }
                tls?.let { DiagnosticCard(text, text.tlsResult, it) }
            }
        }
        item {
            CardBlock {
                Text("HTTP", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(onClick = { scope.launch { http = HttpDiagnostic().check(url) } }) { Text(text.httpCheck) }
                http?.let { DiagnosticCard(text, text.httpResult, it) }
            }
        }
        item {
            CardBlock {
                val d = RouteEngine(config).simulate(routeTarget)
                Text(text.routeDiagnostics, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(routeTarget, { routeTarget = it }, label = { Text(text.domainIpApp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("${d.input} → ${d.tunnelProfile.name} • ${d.dnsPolicySummary}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item { CompactCard(text, "MTU", text.mtuShort, text.mtuDetails) }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, text: UiText, onOpen: (AppScreen) -> Unit) = ScreenList(padding) {
    item { Header(text.more, text.moreSubtitle) }
    item { MoreEntry(text.tools, text.toolsSubtitle, Icons.Outlined.Build) { onOpen(AppScreen.Tools) } }
    item { MoreEntry(text.settings, text.settingsSubtitle, Icons.Outlined.Settings) { onOpen(AppScreen.Settings) } }
}

@Composable
private fun MoreEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) = CardBlock {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Button(onClick = onClick) { Text("›") }
    }
}

@Composable
private fun SettingsScreen(padding: PaddingValues, text: UiText, settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    val context = LocalContext.current
    var supportExpanded by rememberSaveable { mutableStateOf(false) }
    ScreenList(padding) {
        item { Header(text.settings, text.settingsSubtitle) }
        item {
            CardBlock {
                Text(text.language, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                ChipRow {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(selected = settings.language == language, onClick = { onSettings(settings.copy(language = language)) }, label = { Text(language.nativeName) })
                    }
                }
            }
        }
        item {
            CardBlock {
                Text(text.theme, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                ChipRow {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(selected = settings.themeMode == mode, onClick = { onSettings(settings.copy(themeMode = mode)) }, label = { Text(text.themeMode(mode)) })
                    }
                }
                Text(text.amoledNote, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text.supportProject, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { supportExpanded = !supportExpanded }) { Text(if (supportExpanded) text.less else text.details) }
                }
                Text(text.supportShort, style = MaterialTheme.typography.bodySmall)
                if (supportExpanded) {
                    val links = listOf(
                        "GitHub" to "https://github.com/Vifsvifsvifs/viroutefs",
                        "GitHub Sponsors" to "https://github.com/sponsors",
                        "Boosty" to "https://boosty.to",
                        "DonationAlerts" to "https://www.donationalerts.com",
                        "Project page" to "https://github.com/Vifsvifsvifs/viroutefs",
                    )
                    links.forEach { (label, url) -> OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) } }
                }
            }
        }
        item { CompactCard(text, text.version, "ViRouteFS ${BuildConfig.VERSION_NAME}", "versionCode ${BuildConfig.VERSION_CODE}") }
    }
}

@Composable
private fun DiagnosticCard(text: UiText, title: String, result: DiagnosticResult) = CompactCard(text, title, result.simpleExplanation, result.technicalDetailsWithElapsed(), text.actionPrefix + result.recommendedAction)

@Composable
private fun Header(title: String, subtitle: String) = Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun CompactCard(text: UiText, title: String, simple: String, details: String, action: String? = null) = CardBlock {
    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Text(simple, style = MaterialTheme.typography.bodySmall)
    Details(text.details, buildString { append(details); action?.let { append('\n').append(it) } })
}

@Composable
private fun CardBlock(content: @Composable ColumnScope.() -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun ScreenList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 10.dp, top = padding.calculateTopPadding() + 8.dp, end = 10.dp, bottom = padding.calculateBottomPadding() + 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
)

@Composable
private fun StatusChip(label: String) = AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })

@Composable
private fun WarningText(value: String) = Text(value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

@Composable
private fun Details(label: String, text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { Text(if (open) "− $label" else "+ $label") }
    if (open) Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }

@Composable
private fun EventRow(text: String) = CardBlock { Text(text, style = MaterialTheme.typography.bodySmall) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunnelTypeDropdown(text: UiText, value: TunnelType, onSelect: (TunnelType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = value.label, onValueChange = {}, readOnly = true, label = { Text(text.type) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TunnelType.entries.filterNot { it.name.endsWith("Mock") }.forEach { type -> DropdownMenuItem(text = { Text(type.label) }, onClick = { onSelect(type); expanded = false }) }
        }
    }
}

@Suppress("TooManyFunctions")
private class UiText(private val language: AppLanguage) {
    val dashboard = t("Главная", "Home", "主页")
    val vpn = "VPN"
    val routes = t("Маршруты", "Routes", "路由")
    val dns = "DNS"
    val fs = "FS"
    val tools = t("Инструменты", "Tools", "工具")
    val settings = t("Настройки", "Settings", "设置")
    val more = t("Ещё", "More", "更多")
    val dashboardSubtitle = t("Короткий статус приложения и приватности.", "Short app and privacy status.", "应用和隐私状态概览。")
    val vpnSubtitle = t("Профили и общий демонстрационный переключатель.", "Profiles and the master demo switch.", "配置文件和主演示开关。")
    val routesSubtitle = t("Симуляция правил без реального VPN-движка.", "Rule simulation without a real VPN engine.", "无真实 VPN 引擎的规则模拟。")
    val dnsSubtitle = t("Проверка DNS и локальные политики.", "DNS checks and local policies.", "DNS 检查和本地策略。")
    val fsSubtitle = t("Плотная лента событий, пока только пример.", "Dense event feed, sample only for now.", "紧凑事件列表，目前仅为示例。")
    val toolsSubtitle = t("TCP, TLS, HTTP и маршрутная диагностика.", "TCP, TLS, HTTP, and route diagnostics.", "TCP、TLS、HTTP 和路由诊断。")
    val settingsSubtitle = t("Реальные локальные язык и тема.", "Real local language and theme settings.", "真实的本地语言和主题设置。")
    val moreSubtitle = t("Инструменты и настройки без перегруза навигации.", "Tools and settings without crowded navigation.", "工具和设置不再挤占导航栏。")
    val version = t("Версия", "Version", "版本")
    val privacy = t("Приватность", "Privacy", "隐私")
    val privacyShort = t("Локально: без рекламы, аналитики и скрытой отправки.", "Local-first: no ads, analytics, or hidden uploads.", "本地优先：无广告、分析或隐藏上传。")
    val privacyDetails = t("Логи и будущие экспорты остаются на устройстве до явного действия пользователя.", "Logs and future exports stay on the device until the user explicitly exports them.", "日志和未来导出会留在设备上，除非用户明确导出。")
    val configStatus = t("Конфигурация", "Configuration", "配置")
    val configLoaded = t("Локальная конфигурация загружена.", "Local configuration loaded.", "本地配置已加载。")
    val loading = t("Загрузка…", "Loading…", "正在加载…")
    val ready = t("Готово.", "Ready.", "就绪。")
    val saved = t("Сохранено локально.", "Saved locally.", "已保存到本地。")
    val masterSwitch = t("Активные соединения", "Active connections", "活动连接")
    val on = t("вкл", "on", "开")
    val off = t("выкл", "off", "关")
    val details = t("Подробнее", "Details", "详情")
    val less = t("Скрыть", "Less", "收起")
    val vpnDemoDetails = t("Переключатель показывает состояние UI. VPN-движки в этой версии не добавлены.", "The switch controls UI state only. VPN engines are not added in this release.", "此开关仅控制界面状态。本版本未添加 VPN 引擎。")
    val hide = t("Скрыть", "Hide", "隐藏")
    val addProfile = t("+ Профиль", "+ Profile", "+ 配置")
    val qr = t("QR", "QR", "二维码")
    val clipboard = t("Буфер", "Clipboard", "剪贴板")
    val file = t("Файл", "File", "文件")
    val manual = t("Вручную", "Manual", "手动")
    val name = t("Название", "Name", "名称")
    val description = t("Описание", "Description", "描述")
    val mockProfileDescription = t("Профиль добавлен вручную. Движок пока не реализован.", "Profile added manually. The engine is not implemented yet.", "已手动添加配置。引擎尚未实现。")
    val profileAdded = t("Профиль добавлен локально.", "Profile added locally.", "配置已本地添加。")
    val create = t("Создать", "Create", "创建")
    val noDns = t("DNS не выбран", "No DNS", "未选择 DNS")
    val defaultProfile = t("Основной", "Default", "默认")
    val defaultChanged = t("Основной профиль изменён.", "Default profile changed.", "默认配置已更改。")
    val makeDefault = t("Основной", "Default", "默认")
    val delete = t("Удалить", "Delete", "删除")
    val profileDeleted = t("Профиль удалён.", "Profile deleted.", "配置已删除。")
    val mockOnly = t("Демо: реальный тоннель не запускается.", "Demo: no real tunnel is started.", "演示：不会启动真实隧道。")
    val cancel = t("Отмена", "Cancel", "取消")
    val edit = t("Изменить", "Edit", "编辑")
    val profileUpdated = t("Профиль обновлён.", "Profile updated.", "配置已更新。")
    val save = t("Сохранить", "Save", "保存")
    val simulation = t("Симуляция", "Simulation", "模拟")
    val domainIpApp = t("домен/IP/приложение", "domain/IP/app", "域名/IP/应用")
    val matchers = t("условий", "matchers", "匹配项")
    val disable = t("Выкл", "Disable", "禁用")
    val enable = t("Вкл", "Enable", "启用")
    val editors = t("Редакторы", "Editors", "编辑器")
    val appsDomainsIps = t("Приложения / домены / IP", "Apps / domains / IPs", "应用 / 域名 / IP")
    val lookup = t("DNS-запрос", "DNS lookup", "DNS 查询")
    val domain = t("Домен", "Domain", "域名")
    val type = t("Тип", "Type", "类型")
    val dnsServer = t("DNS-сервер", "DNS server", "DNS 服务器")
    val check = t("Проверить", "Check", "检查")
    val dnsResult = t("Результат DNS", "DNS result", "DNS 结果")
    val policies = t("Политики", "Policies", "策略")
    val dnsPolicyLimit = t("Политики описывают желаемое поведение; реальное DNS-маршрутизирование будет позже.", "Policies describe desired behavior; real DNS routing comes later.", "策略描述目标行为；真实 DNS 路由稍后实现。")
    val hostOverrides = t("Host overrides", "Host overrides", "主机覆盖")
    val none = t("нет", "none", "无")
    val system = t("система", "system", "系统")
    val localOnly = t("локально", "local", "本地")
    val simulated = t("симуляция", "simulated", "模拟")
    val certOk = t("сертификат OK", "certificate OK", "证书正常")
    val waiting = t("ожидание", "waiting", "等待")
    val limitation = t("Ограничение", "Limitation", "限制")
    val fsLimitShort = t("FS сейчас показывает пример событий.", "FS currently shows sample events.", "FS 当前显示示例事件。")
    val fsLimitDetails = t("Нет packet capture и скрытого перехвата. Реальный поток будет только из VpnService и локальных логов.", "No packet capture or hidden interception. Real flow will come only from VpnService and local logs.", "没有抓包或隐藏拦截。真实流量仅来自 VpnService 和本地日志。")
    val tcpTls = "TCP / TLS"
    val host = t("Хост", "Host", "主机")
    val port = t("Порт", "Port", "端口")
    val tcpCheck = t("TCP", "TCP", "TCP")
    val tlsCheck = t("TLS", "TLS", "TLS")
    val tcpResult = t("TCP результат", "TCP result", "TCP 结果")
    val tlsResult = t("TLS результат", "TLS result", "TLS 结果")
    val httpCheck = t("HTTP", "HTTP", "HTTP")
    val httpResult = t("HTTP результат", "HTTP result", "HTTP 结果")
    val routeDiagnostics = t("Маршрут", "Route", "路由")
    val mtuShort = t("Будущая проверка MTU.", "Future MTU check.", "未来的 MTU 检查。")
    val mtuDetails = t("Пока без активных сетевых действий.", "No active network action yet.", "目前不执行主动网络操作。")
    val language = t("Язык", "Language", "语言")
    val theme = t("Тема", "Theme", "主题")
    val amoledNote = t("AMOLED использует настоящий чёрный фон, где это практично.", "AMOLED uses true black where practical.", "AMOLED 在适合处使用纯黑背景。")
    val supportProject = t("Поддержать проект", "Support project", "支持项目")
    val supportShort = t("Ссылки скрыты, чтобы экран оставался компактным.", "Links are collapsed to keep the screen compact.", "链接已折叠，使界面更紧凑。")
    val actionPrefix = t("Что сделать: ", "Recommended action: ", "建议操作：")

    fun screen(screen: AppScreen): String = when (screen) {
        AppScreen.Dashboard -> dashboard
        AppScreen.Vpn -> vpn
        AppScreen.Routes -> routes
        AppScreen.Dns -> dns
        AppScreen.Fs -> fs
        AppScreen.More -> more
        AppScreen.Tools -> tools
        AppScreen.Settings -> settings
    }

    fun profileCount(value: Int): String = t("$value проф.", "$value profiles", "$value 个配置")
    fun policyCount(value: Int): String = t("$value политик", "$value policies", "$value 个策略")
    fun overrideCount(value: Int): String = t("$value записей", "$value entries", "$value 条记录")
    fun fsEvent(kind: String, target: String, route: String, status: String): String = "$kind • $target • $route • $status"
    fun themeMode(mode: AppThemeMode): String = when (mode) {
        AppThemeMode.System -> t("Система", "System", "跟随系统")
        AppThemeMode.Light -> t("Светлая", "Light", "浅色")
        AppThemeMode.Dark -> t("Тёмная", "Dark", "深色")
        AppThemeMode.AmoledBlack -> t("AMOLED", "AMOLED", "AMOLED")
    }

    private fun t(ru: String, en: String, zh: String): String = when (language) {
        AppLanguage.Russian -> ru
        AppLanguage.English -> en
        AppLanguage.ChineseSimplified -> zh
    }
}
