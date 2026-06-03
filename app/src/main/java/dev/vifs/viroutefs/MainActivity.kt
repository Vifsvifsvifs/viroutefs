// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.routing.CURRENT_ROUTING_CONFIG_VERSION
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.settings.AppLanguage
import dev.vifs.viroutefs.settings.AppSettings
import dev.vifs.viroutefs.settings.AppSettingsRepository
import dev.vifs.viroutefs.settings.AppThemeMode
import dev.vifs.viroutefs.ui.DnsScreen
import dev.vifs.viroutefs.ui.FlowScannerScreen
import dev.vifs.viroutefs.ui.VpnScreen
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import dev.vifs.viroutefs.vpn.VpnServiceController
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
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

internal enum class AppScreen(val icon: ImageVector) {
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
    val vpnController = remember(context) { VpnServiceController(context.applicationContext) }
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Dashboard) }
    var config by remember { mutableStateOf(RoutingConfigDefaults.defaultConfig()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var vpnState by remember { mutableStateOf(vpnController.currentState()) }
    var tunTestRoutePreviewEnabled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = repository.load()
        config = result.config
        message = result.errorMessage
        loaded = true
    }

    fun startAfterPermissions(testRoutePreviewEnabled: Boolean = tunTestRoutePreviewEnabled) {
        if (!vpnController.notificationPermissionGranted()) {
            vpnState = VpnServiceUiState(
                VpnServiceStatus.NotificationPermissionRequired,
                text.vpnNotificationPermissionRequiredDetail,
            )
            return
        }
        vpnState = VpnServiceUiState(VpnServiceStatus.Starting, tunTestRouteActive = testRoutePreviewEnabled)
        runCatching { vpnController.startLocalService(testRoutePreviewEnabled) }
            .onFailure { error ->
                vpnState = VpnServiceUiState(VpnServiceStatus.Error, error.localizedMessage ?: text.vpnStartFailed)
            }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startAfterPermissions()
        } else {
            vpnState = VpnServiceUiState(
                VpnServiceStatus.NotificationPermissionRequired,
                text.vpnNotificationPermissionRequiredDetail,
            )
        }
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (!vpnController.notificationPermissionGranted()) {
                vpnState = VpnServiceUiState(
                    VpnServiceStatus.NotificationPermissionRequired,
                    text.vpnNotificationPermissionRequiredDetail,
                )
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startAfterPermissions()
            }
        } else {
            vpnState = VpnServiceUiState(VpnServiceStatus.PermissionRequired, text.vpnPermissionDenied)
        }
    }

    LaunchedEffect(Unit) {
        vpnState = vpnController.currentState()
    }

    androidx.compose.runtime.DisposableEffect(vpnController) {
        val receiver = vpnController.registerStateReceiver { state -> vpnState = state }
        onDispose { vpnController.unregisterStateReceiver(receiver) }
    }

    fun setVpnEnabled(enabled: Boolean) {
        if (enabled) {
            val permissionIntent = vpnController.prepareIntent()
            if (permissionIntent != null) {
                vpnState = VpnServiceUiState(VpnServiceStatus.PermissionRequired)
                vpnPermissionLauncher.launch(permissionIntent)
            } else if (!vpnController.notificationPermissionGranted()) {
                vpnState = VpnServiceUiState(
                    VpnServiceStatus.NotificationPermissionRequired,
                    text.vpnNotificationPermissionRequiredDetail,
                )
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                startAfterPermissions()
            }
        } else {
            runCatching { vpnController.stopLocalService() }
                .onSuccess { vpnState = VpnServiceUiState(VpnServiceStatus.Stopped) }
                .onFailure { error ->
                    vpnState = VpnServiceUiState(VpnServiceStatus.Error, error.localizedMessage ?: text.vpnStopFailed)
                }
        }
    }

    fun setTunTestRoutePreviewEnabled(enabled: Boolean) {
        tunTestRoutePreviewEnabled = enabled
        if (vpnState.status == VpnServiceStatus.Starting || vpnState.status == VpnServiceStatus.ServiceActiveNoTun || vpnState.status == VpnServiceStatus.TunPreviewActive || vpnState.status == VpnServiceStatus.TunTestRouteActive) {
            if (!vpnController.notificationPermissionGranted()) {
                vpnState = VpnServiceUiState(
                    VpnServiceStatus.NotificationPermissionRequired,
                    text.vpnNotificationPermissionRequiredDetail,
                    tunTestRouteActive = enabled,
                )
            } else {
                startAfterPermissions(enabled)
            }
        }
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
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(padding, text, loaded, message)
            AppScreen.Vpn -> VpnScreen(padding, text, config, vpnState, tunTestRoutePreviewEnabled, ::setVpnEnabled, ::setTunTestRoutePreviewEnabled, ::updateConfig)
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
private fun RoutesScreen(padding: PaddingValues, text: UiText, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var target by rememberSaveable { mutableStateOf("telegram") }
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedRoute = config.rules.firstOrNull { it.id == selectedRouteId }
    val decision = remember(config, target) { RouteEngine(config).simulate(target) }

    if (selectedRoute != null) {
        RouteDetailsScreen(
            padding = padding,
            text = text,
            rule = selectedRoute,
            config = config,
            onBack = { selectedRouteId = null },
            onConfig = { next, message ->
                onConfig(next, message)
                if (next.rules.none { it.id == selectedRoute.id }) selectedRouteId = null
            },
        )
        return
    }

    ScreenList(padding) {
        item { Header(text.routes, text.routesSubtitle) }
        item {
            CardBlock {
                Text(text.simulation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(target, { target = it }, label = { Text(text.domainIpApp) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("${decision.input} → ${decision.tunnelProfile.name}", style = MaterialTheme.typography.bodySmall)
                StatusChip(decision.dnsPolicySummary)
            }
        }
        items(config.rules, key = { it.id }) { rule ->
            RouteRuleCard(
                text = text,
                rule = rule,
                profileName = config.profiles.firstOrNull { it.id == rule.targetProfileId }?.name ?: rule.targetProfileId,
                onOpen = { selectedRouteId = rule.id },
            )
        }
    }
}

@Composable
private fun RouteRuleCard(text: UiText, rule: RouteRule, profileName: String, onOpen: () -> Unit) {
    CardBlock {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("→ $profileName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (rule.enabled) text.on else text.off)
        }
    }
}

@Composable
private fun RouteDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    rule: RouteRule,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var name by rememberSaveable(rule.id) { mutableStateOf(rule.name) }
    var enabled by rememberSaveable(rule.id) { mutableStateOf(rule.enabled) }
    var targetProfileId by rememberSaveable(rule.id) { mutableStateOf(rule.targetProfileId) }
    val dnsPolicy = rule.dnsPolicyId?.let { id -> config.dnsPolicies.firstOrNull { it.id == id } }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(text.routeDetails, text.routeDetailsSubtitle)
            }
        }
        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(text.targetProfile, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                ChipRow {
                    config.profiles.forEach { profile ->
                        FilterChip(
                            selected = targetProfileId == profile.id,
                            onClick = { targetProfileId = profile.id },
                            label = { Text(profile.name, maxLines = 1) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                dnsPolicy?.let { StatusChip("DNS: ${it.name}") }
            }
        }
        item {
            CardBlock {
                Text(text.apps, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(rule.appMatchers.map { it.displayName ?: it.value }.joinToString(" • ").ifBlank { text.none }, style = MaterialTheme.typography.bodySmall)
                Text(text.domainsIps, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(rule.matchers.joinToString(" • ").ifBlank { text.none }, style = MaterialTheme.typography.bodySmall)
                Details(text.details, "${rule.reason}\n${rule.technicalDetails}\n${rule.recommendedAction}")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onConfig(
                        config.copy(rules = config.rules.map {
                            if (it.id == rule.id) it.copy(name = name.ifBlank { rule.name }, enabled = enabled, targetProfileId = targetProfileId) else it
                        }),
                        text.saved,
                    )
                    onBack()
                }) { Text(text.save) }
                OutlinedButton(onClick = { onConfig(config.copy(rules = config.rules.filterNot { it.id == rule.id }), text.routeDeleted) }) { Text(text.delete) }
            }
        }
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
                Text(text.languageLabel, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                ChipRow {
                    AppLanguage.entries.forEach { languageOption ->
                        FilterChip(selected = settings.language == languageOption, onClick = { onSettings(settings.copy(language = languageOption)) }, label = { Text(languageOption.nativeName) })
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
internal fun Header(title: String, subtitle: String) = Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
internal fun CardBlock(content: @Composable ColumnScope.() -> Unit) = Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
) {
    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
internal fun ScreenList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 10.dp, top = padding.calculateTopPadding() + 8.dp, end = 10.dp, bottom = padding.calculateBottomPadding() + 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
    content = content,
)

@Composable
internal fun StatusChip(label: String) = AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })

@Composable
internal fun WarningText(value: String) = Text(value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

@Composable
internal fun Details(label: String, text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { Text(if (open) "− $label" else "+ $label") }
    if (open) Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }


@Suppress("TooManyFunctions")
internal class UiText(private val language: AppLanguage) {
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
    val routesSubtitle = t("Какой тоннель используется. Нажмите маршрут для деталей.", "Which tunnel is used. Tap a route for details.", "查看使用哪个隧道。点按路由查看详情。")
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
    val vpnLocalPreviewTitle = t("Превью локального VPN-сервиса", "Local VPN service preview", "本地 VPN 服务预览")
    val vpnNoTrafficRoutingYet = t("Маршруты трафика не установлены", "No traffic routes installed", "未安装流量路由")
    val vpnNoHiddenInterception = t("Нет скрытого перехвата", "No hidden interception", "没有隐藏拦截")
    val vpnPacketProcessingLater = t("Интернет должен остаться без изменений.", "Internet should remain unchanged.", "互联网应保持不变。")
    val vpnLifecycleOnlyDetails = t("0.6.1-alpha по умолчанию создаёт минимальный route-less TUN с адресом 10.250.0.2/32 только для проверки VpnService. В режиме тестового маршрута явно добавляется только 203.0.113.0/24 (TEST-NET-3); маршрута по умолчанию, DNS-серверов, логирования payload, пересылки, прокси и VPN-движков нет.", "0.6.1-alpha creates a minimal route-less TUN with address 10.250.0.2/32 by default only to verify VpnService. Test-route mode explicitly adds only 203.0.113.0/24 (TEST-NET-3); there is no default route, DNS servers, payload logging, forwarding, proxying, or VPN engines.", "0.6.1-alpha 默认仅创建地址为 10.250.0.2/32 的最小无路由 TUN 来验证 VpnService。测试路由模式仅显式添加 203.0.113.0/24 (TEST-NET-3)；没有默认路由、DNS 服务器、payload 日志、转发、代理或 VPN 引擎。")
    val vpnTestRoutePreview = t("Тестовый маршрут", "Test route preview", "测试路由预览")
    val vpnTestRoute = t("Тестовый маршрут: 203.0.113.0/24", "Test route: 203.0.113.0/24", "测试路由：203.0.113.0/24")
    val vpnPacketsRead = t("Прочитано пакетов", "Packets read", "已读取数据包")
    val vpnBytesRead = t("Прочитано байт", "Bytes read", "已读取字节")
    val vpnNormalInternetUnchanged = t("Обычный интернет должен остаться без изменений", "Normal internet should remain unchanged", "正常互联网应保持不变")
    val vpnHowToTestTun = t("Откройте http://203.0.113.1 в браузере или выполните тестовое подключение к 203.0.113.1. Пакеты могут появиться здесь и будут отброшены.", "Open http://203.0.113.1 in a browser or run a test connection to 203.0.113.1. Packets may appear here and will be dropped.", "在浏览器中打开 http://203.0.113.1，或对 203.0.113.1 运行测试连接。数据包可能会显示在这里，并将被丢弃。")
    val vpnPermissionRequired = t("Требуется разрешение", "Permission required", "需要权限")
    val vpnStarting = t("Запуск…", "Starting…", "正在启动…")
    val vpnLocalServiceActive = t("Сервис активен, TUN не создан", "Service active, no TUN", "服务活动，未创建 TUN")
    val vpnTunPreviewActive = t("TUN preview active", "TUN preview active", "TUN 预览已活动")
    val vpnTunActive = t("TUN: active", "TUN: active", "TUN：活动")
    val vpnTunInactive = t("TUN: inactive", "TUN: inactive", "TUN：未活动")
    val vpnNotificationPermissionRequired = t("Требуется разрешение уведомлений", "Notification permission required", "需要通知权限")
    val vpnNotificationPermissionRequiredDetail = t("Требуется разрешение на уведомления, чтобы запустить локальный сервис превью VPN", "Notification permission required to start local VPN preview service", "需要通知权限才能启动本地 VPN 预览服务")
    val vpnStopped = t("Остановлен", "Stopped", "已停止")
    val vpnError = t("Ошибка", "Error", "错误")
    val vpnPermissionDenied = t("Разрешение Android VPN не выдано.", "Android VPN permission was not granted.", "未授予 Android VPN 权限。")
    val vpnStartFailed = t("Не удалось запустить локальный VPN-сервис.", "Failed to start the local VPN service.", "无法启动本地 VPN 服务。")
    val vpnStopFailed = t("Не удалось остановить локальный VPN-сервис.", "Failed to stop the local VPN service.", "无法停止本地 VPN 服务。")
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
    val profileDetails = t("Детали профиля", "Profile details", "配置详情")
    val profileDetailsSubtitle = t("Настройте профиль без перегруза основного списка.", "Edit the profile without crowding the main list.", "编辑配置而不挤占主列表。")
    val addProfileSubtitle = t("Импорт — заглушки, ручное создание сохраняется локально.", "Import options are placeholders; manual creation saves locally.", "导入选项为占位；手动创建会本地保存。")
    val importOptions = t("Импорт", "Import", "导入")
    val noDns = t("DNS не выбран", "No DNS", "未选择 DNS")
    val defaultProfile = t("Основной", "Default", "默认")
    val defaultChanged = t("Основной профиль изменён.", "Default profile changed.", "默认配置已更改。")
    val makeDefault = t("Основной", "Default", "默认")
    val delete = t("Удалить", "Delete", "删除")
    val profileDeleted = t("Профиль удалён.", "Profile deleted.", "配置已删除。")
    val protectedProfileMessage = t("Direct и Block — системные профили, их нельзя удалить.", "Direct and Block are system profiles and cannot be deleted.", "Direct 和 Block 是系统配置，无法删除。")
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
    val back = t("Назад", "Back", "返回")
    val routeDetails = t("Детали маршрута", "Route details", "路由详情")
    val routeDetailsSubtitle = t("Настройте цель и состояние маршрута.", "Adjust the route target and state.", "调整路由目标和状态。")
    val targetProfile = t("Тоннель / профиль", "Tunnel / profile", "隧道 / 配置")
    val enabled = t("Включён", "Enabled", "已启用")
    val apps = t("Приложения", "Apps", "应用")
    val domainsIps = t("Домены и IP/CIDR", "Domains and IP/CIDR", "域名和 IP/CIDR")
    val routeDeleted = t("Маршрут удалён.", "Route deleted.", "路由已删除。")
    val lookup = t("DNS-запрос", "DNS lookup", "DNS 查询")
    val domain = t("Домен", "Domain", "域名")
    val type = t("Тип", "Type", "类型")
    val dnsServer = t("DNS-сервер", "DNS server", "DNS 服务器")
    val check = t("Проверить", "Check", "检查")
    val dnsResult = t("Результат DNS", "DNS result", "DNS 结果")
    val policies = t("Политики", "Policies", "策略")
    val dnsPolicyLimit = t("Политики описывают желаемое поведение; реальное DNS-маршрутизирование будет позже.", "Policies describe desired behavior; real DNS routing comes later.", "策略描述目标行为；真实 DNS 路由稍后实现。")
    val dnsPolicyDetails = t("Детали DNS-политики", "DNS policy details", "DNS 策略详情")
    val dnsPolicyDetailsSubtitle = t("Краткие поля и ограничения будущего DNS-движка.", "Compact fields and future DNS-engine limits.", "紧凑字段和未来 DNS 引擎限制。")
    val usedByProfiles = t("Используется профилями", "Used by profiles", "配置使用")
    val usedByRoutes = t("Используется маршрутами", "Used by routes", "路由使用")
    val hostOverrides = t("Host overrides", "Host overrides", "主机覆盖")
    val hostOverridesSubtitle = t("Локальные hosts-подстановки для будущего DNS-движка.", "Local hosts-like mappings for the future DNS engine.", "用于未来 DNS 引擎的本地主机映射。")
    val hostOverridesShort = t("Локальная hosts-like подстановка. Реальный DNS-движок применит её позже.", "Local hosts-like mapping. Real DNS engine will apply it later.", "本地 hosts 类映射。真实 DNS 引擎稍后会应用。")
    val addHostOverride = t("Добавить запись", "Add override", "添加覆盖")
    val ipAddress = t("IP", "IP", "IP")
    val noteOptional = t("Заметка (необязательно)", "Note (optional)", "备注（可选）")
    val hostOverrideAdded = t("Host override сохранён локально.", "Host override saved locally.", "主机覆盖已保存到本地。")
    val hostOverrideDeleted = t("Host override удалён.", "Host override deleted.", "主机覆盖已删除。")
    val none = t("нет", "none", "无")
    val system = t("система", "system", "系统")
    val localOnly = t("локально", "local", "本地")
    val simulated = t("симуляция", "simulated", "模拟")
    val certOk = t("сертификат OK", "certificate OK", "证书正常")
    val waiting = t("ожидание", "waiting", "等待")
    val limitation = t("Ограничение", "Limitation", "限制")
    val fsLimitShort = t("Демо/превью будущих потоков. Захвата пакетов в этой версии нет.", "Demo/preview of future flows. There is no packet capture in this version.", "未来流量的演示/预览。本版本没有抓包。")
    val fsLimitDetails = t("FS заработает только после явного включения пользователем будущей обработки трафика. Сейчас нет скрытого перехвата, packet capture, чтения TUN-пакетов, VPN-движков и загрузки логов в облако.", "FS will work only after the user explicitly enables future traffic processing. There is currently no hidden interception, packet capture, TUN packet reading, VPN engines, or cloud upload of logs.", "FS 只会在用户未来明确启用流量处理后工作。目前没有隐藏拦截、抓包、TUN 数据包读取、VPN 引擎或日志云上传。")
    val flowScannerTitle = "Flow Scanner"
    val flowScannerSubtitle = t("кто куда подключается и почему", "who connects where and why", "谁连接到哪里以及原因")
    val flowAppFilter = t("Приложения", "Apps", "应用")
    val flowAllAppsPlaceholder = t("Все приложения (заглушка)", "All apps (placeholder)", "所有应用（占位）")
    val flowStartAnalysis = t("Начать анализ", "Start analysis", "开始分析")
    val flowDemoMode = t("демо / локально", "demo / local", "演示 / 本地")
    val flowPreviewOnly = t("Превью: без захвата пакетов", "Preview: no packet capture", "预览：无抓包")
    val flowEventDetailsTitle = t("Событие потока", "Flow event", "流量事件")
    val flowApp = t("Приложение", "App", "应用")
    val flowDomain = t("Домен", "Domain", "域名")
    val flowResolvedIp = t("IP", "IP", "IP")
    val flowPortProtocol = t("Порт / протокол", "Port / protocol", "端口 / 协议")
    val flowDnsPolicy = t("DNS-политика", "DNS policy", "DNS 策略")
    val flowSelectedRoute = t("Маршрут / тоннель", "Route / tunnel", "路由 / 隧道")
    val flowReason = t("Почему выбран маршрут", "Why this route was selected", "选择此路由的原因")
    val flowRecommendation = t("Рекомендация", "Recommendation", "建议")
    val flowAllowedStatus = t("разрешено", "allowed", "允许")
    val flowMediaStatus = t("медиа", "media", "媒体")
    val flowDirectStatus = t("прямо", "direct", "直连")
    val flowWorkStatus = t("рабочий", "work", "工作")
    val flowBlockedStatus = t("блок", "blocked", "阻止")
    val flowDnsPolicySecure = t("Secure DNS", "Secure DNS", "安全 DNS")
    val flowDnsPolicyMedia = t("Media DNS", "Media DNS", "媒体 DNS")
    val flowDnsPolicyLocal = t("Локальная/системная", "Local/system", "本地/系统")
    val flowDnsPolicyCorp = t("Корпоративная", "Corporate", "公司")
    val flowDnsPolicyBlock = t("Не запрашивать", "Do not resolve", "不解析")
    val browserApp = t("Браузер", "Browser", "浏览器")
    val workApp = t("Рабочее приложение", "Work app", "工作应用")
    val trackerApp = t("Пример трекера", "Tracker example", "跟踪器示例")
    val telegramRouteReason = t("Правило домена telegram.org отправляет мессенджер в профиль Xray Germany.", "A telegram.org domain rule sends the messenger to the Xray Germany profile.", "telegram.org 域名规则将该应用发送到 Xray Germany 配置。")
    val telegramRecommendation = t("Проверьте, что профиль выбран осознанно, затем включайте реальный режим только вручную.", "Confirm the profile is intentional, then enable any real mode only manually.", "确认配置选择正确；真实模式只能手动启用。")
    val telegramTechnical = t("Демо-событие: будущий источник — локальный VpnService flow log после явного разрешения пользователя.", "Demo event: future source is the local VpnService flow log after explicit user consent.", "演示事件：未来来源是在用户明确同意后的本地 VpnService 流日志。")
    val mediaRouteReason = t("Медиа-домены youtube.com и googlevideo.com соответствуют правилу Media tunnel.", "Media domains youtube.com and googlevideo.com match the Media tunnel rule.", "媒体域 youtube.com 和 googlevideo.com 匹配 Media tunnel 规则。")
    val mediaRecommendation = t("Если видео работает медленно, позже проверьте профиль Media tunnel и MTU.", "If video is slow, later check the Media tunnel profile and MTU.", "如果视频较慢，稍后检查 Media tunnel 配置和 MTU。")
    val mediaTechnical = t("Событие показывает целевой домен и предполагаемый протокол; реальный QUIC/TCP анализ не выполняется.", "The event shows target domains and expected protocol; real QUIC/TCP analysis is not performed.", "事件显示目标域和预期协议；未执行真实 QUIC/TCP 分析。")
    val govRouteReason = t("Чувствительные локальные сервисы оставлены Direct, чтобы не отправлять их в сторонний тоннель.", "Sensitive local services stay Direct so they are not sent through a third-party tunnel.", "敏感本地服务保持 Direct，避免经过第三方隧道。")
    val govRecommendation = t("Оставьте Direct для банков и госуслуг, если у вас нет отдельной доверенной политики.", "Keep Direct for banks and public services unless you have a separate trusted policy.", "除非有单独可信策略，否则银行和公共服务建议保持 Direct。")
    val govTechnical = t("Демо не проверяет сертификат и не читает содержимое соединения.", "The demo does not validate certificates or read connection contents.", "演示不验证证书，也不读取连接内容。")
    val workRouteReason = t("Внутренний домен gitlab.corp совпал с рабочим правилом и DNS-политикой.", "The internal gitlab.corp domain matched the work rule and DNS policy.", "内部域 gitlab.corp 匹配工作规则和 DNS 策略。")
    val workRecommendation = t("Используйте рабочий VPN только для корпоративных доменов и приложений.", "Use the work VPN only for corporate domains and apps.", "仅对公司域名和应用使用工作 VPN。")
    val workTechnical = t("IP показан как пример частного адреса; реальный корпоративный DNS ещё не подключён.", "The IP is a sample private address; real corporate DNS is not connected yet.", "IP 是示例私有地址；尚未接入真实公司 DNS。")
    val trackerRouteReason = t("Домен совпал с демонстрационным правилом Block для нежелательных трекеров.", "The domain matched a demo Block rule for unwanted trackers.", "该域匹配不需要跟踪器的演示 Block 规则。")
    val trackerWarning = t("Возможный трекер: соединение предлагается блокировать.", "Possible tracker: blocking is recommended.", "可能的跟踪器：建议阻止。")
    val trackerRecommendation = t("Оставьте блокировку, если этот домен не нужен приложению для основной функции.", "Keep it blocked if the app does not need this domain for its core function.", "如果应用核心功能不需要该域，请保持阻止。")
    val trackerTechnical = t("В демо нет DNS-запроса и сетевого блокирования; это только пример будущего решения политики.", "The demo does not perform DNS lookup or network blocking; it only previews a future policy decision.", "演示不执行 DNS 查询或网络阻止；仅预览未来策略决策。")
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
    val languageLabel = t("Язык", "Language", "语言")
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
    fun profileUsedMessage(routes: String): String = t("Профиль используется маршрутами: $routes. Сначала измените или удалите эти правила.", "Profile is used by routes: $routes. Change or delete those rules first.", "配置正被路由使用：$routes。请先更改或删除这些规则。")
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
