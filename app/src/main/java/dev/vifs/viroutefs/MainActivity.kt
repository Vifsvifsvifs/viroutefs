// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import dev.vifs.viroutefs.routing.AppMatcher
import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.findConflictsForCandidate
import dev.vifs.viroutefs.routing.findExactRouteConflicts
import dev.vifs.viroutefs.routing.isValidIpOrCidr
import dev.vifs.viroutefs.routing.validateRouteEditorDraft
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
import java.util.Locale
import java.util.UUID

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
    Vpn(Icons.Outlined.Shield),
    Routes(Icons.Outlined.AltRoute),
    Dns(Icons.Outlined.Dns),
    Fs(Icons.Outlined.Security),
    More(Icons.Outlined.MoreHoriz),
    Tools(Icons.Outlined.Build),
    Settings(Icons.Outlined.Settings),
}

private val bottomScreens = listOf(AppScreen.Vpn, AppScreen.Routes, AppScreen.Dns, AppScreen.Fs, AppScreen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp(settings: AppSettings, onSettings: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember(settings.language) { UiText(settings.language) }
    val repository = remember(context) { RoutingConfigRepository(context.applicationContext) }
    val vpnController = remember(context) { VpnServiceController(context.applicationContext) }
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Vpn) }
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
        if (enabled && (vpnState.status == VpnServiceStatus.Starting || vpnState.status == VpnServiceStatus.ServiceActiveNoTun || vpnState.status == VpnServiceStatus.TunPreviewActive || vpnState.status == VpnServiceStatus.TunTestRouteActive)) {
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
            AppScreen.Vpn -> VpnScreen(padding, text, config, vpnState, tunTestRoutePreviewEnabled, ::setVpnEnabled, ::setTunTestRoutePreviewEnabled, ::updateConfig)
            AppScreen.Routes -> RoutesScreen(padding, text, config, ::updateConfig)
            AppScreen.Dns -> DnsScreen(padding, text, config, ::updateConfig)
            AppScreen.Fs -> FlowScannerScreen(padding, text, vpnState)
            AppScreen.More -> MoreScreen(padding, text, onOpen = { selectedScreen = it })
            AppScreen.Tools -> ToolsScreen(padding, text, config)
            AppScreen.Settings -> SettingsScreen(padding, text, settings, loaded, message, tunTestRoutePreviewEnabled, ::setTunTestRoutePreviewEnabled, vpnState, onSettings)
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
    val context = LocalContext.current
    var target by rememberSaveable { mutableStateOf("") }
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedAppPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingRoute by rememberSaveable { mutableStateOf(false) }
    var draftRoute by remember { mutableStateOf<RouteRule?>(null) }
    val installedApps = remember(context) { context.loadLaunchableApps() }
    val userRules = config.rules.filter { it.type != RouteRuleType.DEFAULT }
    val selectedRoute = userRules.firstOrNull { it.id == selectedRouteId }
    val simulationInput = selectedAppPackage ?: target.ifBlank { "example.com" }
    val decision = remember(config, simulationInput) { RouteEngine(config).simulate(simulationInput) }
    val conflicts = remember(config.rules) { findExactRouteConflicts(config.rules) }
    val conflictsByRuleId = remember(conflicts) { conflicts.flatMap { conflict -> conflict.ruleIds.map { it to conflict } }.groupBy({ it.first }, { it.second }) }

    if (selectedRoute != null || creatingRoute) {
        RouteDetailsScreen(
            padding = padding,
            text = text,
            rule = selectedRoute ?: draftRoute ?: newRouteDraft(config).also { draftRoute = it },
            config = config,
            installedApps = installedApps,
            isNew = selectedRoute == null,
            onBack = {
                selectedRouteId = null
                creatingRoute = false
                draftRoute = null
            },
            onConfig = { next, message ->
                onConfig(next, message)
                if (selectedRoute != null && next.rules.none { it.id == selectedRoute.id }) selectedRouteId = null
                creatingRoute = false
                draftRoute = null
            },
        )
        return
    }

    ScreenList(padding) {
        item { Header(text.routes, text.routesSubtitle) }
        item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text.simulation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        draftRoute = newRouteDraft(config)
                        creatingRoute = true
                    }) { Text(text.addRoute) }
                }
                Text(text.routeEmptyState, style = MaterialTheme.typography.bodySmall)
                if (installedApps.isNotEmpty()) {
                    Text(text.installedApps, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    ChipRow {
                        installedApps.take(6).forEach { app ->
                            FilterChip(
                                selected = selectedAppPackage == app.packageName,
                                onClick = { selectedAppPackage = if (selectedAppPackage == app.packageName) null else app.packageName },
                                label = { Text(app.label, maxLines = 1) },
                            )
                        }
                    }
                } else {
                    StatusChip(text.noInstalledApps)
                }
                OutlinedTextField(
                    target,
                    {
                        target = it
                        selectedAppPackage = null
                    },
                    label = { Text(text.domainIpApp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("${decision.input} → ${decision.tunnelProfile.name}", style = MaterialTheme.typography.bodySmall)
                Details(text.details, text.routeIsolationNote)
            }
        }
        if (userRules.isEmpty()) {
            item { CompactCard(text, text.noRoutesConfigured, text.routeEmptyState, text.routeIsolationNote) }
        } else {
            items(userRules, key = { it.id }) { rule ->
                RouteRuleCard(
                    text = text,
                    rule = rule,
                    profileName = routeTargetName(config, rule.targetProfileId),
                    warnings = conflictsByRuleId[rule.id].orEmpty() + unavailableTargetWarning(config, rule),
                    onOpen = { selectedRouteId = rule.id },
                )
            }
        }
    }
}

private enum class RouteMatcherKind { App, Domain, Cidr }

data class InstalledAppUi(val label: String, val packageName: String)

private fun android.content.Context.loadLaunchableApps(): List<InstalledAppUi> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .map { info ->
            InstalledAppUi(
                label = info.loadLabel(packageManager).toString(),
                packageName = info.activityInfo.packageName,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(compareBy<InstalledAppUi> { it.label.lowercase(Locale.ROOT) }.thenBy { it.packageName })
}

@Composable
private fun RouteRuleCard(text: UiText, rule: RouteRule, profileName: String, warnings: List<Any>, onOpen: () -> Unit) {
    CardBlock {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(rule.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(matcherSummary(rule), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("→ $profileName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(if (rule.enabled) text.on else text.off)
                    if (warnings.isNotEmpty()) StatusChip(text.warning)
                }
            }
        }
    }
}

@Composable
private fun RouteDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    rule: RouteRule,
    config: RoutingConfig,
    installedApps: List<InstalledAppUi>,
    isNew: Boolean,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var name by rememberSaveable(rule.id) { mutableStateOf(rule.name) }
    var enabled by rememberSaveable(rule.id) { mutableStateOf(rule.enabled) }
    var matcherKind by rememberSaveable(rule.id) { mutableStateOf(rule.type.toMatcherKind()) }
    var targetProfileId by rememberSaveable(rule.id) { mutableStateOf(rule.targetProfileId) }
    var selectedAppPackage by rememberSaveable(rule.id) { mutableStateOf(rule.appMatchers.firstOrNull()?.value.orEmpty()) }
    var matcherText by rememberSaveable(rule.id) { mutableStateOf(rule.matchers.firstOrNull().orEmpty()) }
    var appSearch by rememberSaveable { mutableStateOf("") }
    var saveErrors by rememberSaveable(rule.id) { mutableStateOf<List<String>>(emptyList()) }
    val availableProfiles = config.profiles.filter { profile ->
        profile.type == TunnelType.Direct || profile.type == TunnelType.Block || !profile.mockOnly
    }
    val targetProfile = config.profiles.firstOrNull { it.id == targetProfileId }
    val filteredApps = remember(installedApps, appSearch) {
        val query = appSearch.trim().lowercase(Locale.ROOT)
        installedApps.filter { app ->
            query.isBlank() || app.label.lowercase(Locale.ROOT).contains(query) || app.packageName.lowercase(Locale.ROOT).contains(query)
        }.take(30)
    }
    val selectedApp = installedApps.firstOrNull { it.packageName == selectedAppPackage }
    val draft = remember(name, enabled, matcherKind, targetProfileId, selectedAppPackage, matcherText, selectedApp, rule) {
        rule.copy(
            name = name.trim().ifBlank { rule.name },
            enabled = enabled,
            type = matcherKind.toRuleType(),
            targetProfileId = targetProfileId,
            matchers = when (matcherKind) {
                RouteMatcherKind.App -> emptyList()
                RouteMatcherKind.Domain -> listOf(matcherText.trim().trimEnd('.').lowercase(Locale.ROOT)).filter { it.isNotBlank() }
                RouteMatcherKind.Cidr -> listOf(matcherText.trim()).filter { it.isNotBlank() }
            },
            appMatchers = if (matcherKind == RouteMatcherKind.App && selectedAppPackage.isNotBlank()) {
                listOf(AppMatcher(AppMatcherPlatform.Android, selectedAppPackage, selectedApp?.label ?: selectedAppPackage))
            } else {
                emptyList()
            },
            reason = routeReason(matcherKind),
            technicalDetails = routeTechnicalDetails(matcherKind),
            recommendedAction = routeRecommendedAction(targetProfileId),
        )
    }
    val liveConflicts = remember(draft, config.rules) { findConflictsForCandidate(draft, config.rules) }
    val targetWarning = unavailableTargetWarning(config, draft)

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(if (isNew) text.addRoute else text.routeDetails, text.routeDetailsSubtitle)
            }
        }
        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.routeName) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(text.matcherType, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                ChipRow {
                    FilterChip(selected = matcherKind == RouteMatcherKind.App, onClick = { matcherKind = RouteMatcherKind.App }, label = { Text(text.matcherApp) })
                    FilterChip(selected = matcherKind == RouteMatcherKind.Domain, onClick = { matcherKind = RouteMatcherKind.Domain }, label = { Text(text.matcherDomain) })
                    FilterChip(selected = matcherKind == RouteMatcherKind.Cidr, onClick = { matcherKind = RouteMatcherKind.Cidr }, label = { Text(text.matcherCidr) })
                }
                RouteMatcherEditor(
                    text = text,
                    kind = matcherKind,
                    installedApps = filteredApps,
                    selectedAppPackage = selectedAppPackage,
                    appSearch = appSearch,
                    matcherText = matcherText,
                    onAppSearch = { appSearch = it },
                    onSelectedAppPackage = { selectedAppPackage = it },
                    onMatcherText = { matcherText = it },
                )
                Text(text.targetProfile, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                ChipRow {
                    availableProfiles.forEach { profile ->
                        FilterChip(
                            selected = targetProfileId == profile.id,
                            onClick = { targetProfileId = profile.id },
                            label = { Text(routeTargetName(config, profile.id), maxLines = 1) },
                        )
                    }
                }
                if (availableProfiles.isEmpty()) StatusChip(text.systemBlockOnly)
                targetWarning.forEach { StatusChip(it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("${text.targetProfile}: ${targetProfile?.name ?: targetProfileId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CardBlock {
                Text(text.details, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                RouteDetailLine(text.matcherType, matcherKind.label(text))
                RouteDetailLine(text.selectedMatcher, matcherSummary(draft))
                RouteDetailLine(text.targetProfile, routeTargetName(config, targetProfileId))
                Details(text.advanced, "${text.routeIsolationNote}\n\n${text.runtimeRoutingFuture}\n\n${rule.reason}\n${rule.technicalDetails}\n${rule.recommendedAction}")
            }
        }
        if (liveConflicts.isNotEmpty() || saveErrors.isNotEmpty()) {
            item {
                CardBlock {
                    Text(text.validation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                    (saveErrors + liveConflicts.map { it.message }).distinct().forEach { error ->
                        Text("• $error", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val errors = validateRouteEditorDraft(draft, config.rules)
                    saveErrors = errors
                    if (errors.isEmpty()) {
                        val nextRules = if (isNew) {
                            config.rules + draft
                        } else {
                            config.rules.map { if (it.id == rule.id) draft else it }
                        }
                        onConfig(config.copy(rules = nextRules), text.saved)
                        onBack()
                    }
                }) { Text(text.save) }
                if (!isNew) {
                    OutlinedButton(onClick = { onConfig(config.copy(rules = config.rules.filterNot { it.id == rule.id }), text.routeDeleted) }) { Text(text.delete) }
                }
            }
        }
    }
}

@Composable
private fun RouteMatcherEditor(
    text: UiText,
    kind: RouteMatcherKind,
    installedApps: List<InstalledAppUi>,
    selectedAppPackage: String,
    appSearch: String,
    matcherText: String,
    onAppSearch: (String) -> Unit,
    onSelectedAppPackage: (String) -> Unit,
    onMatcherText: (String) -> Unit,
) {
    when (kind) {
        RouteMatcherKind.App -> {
            OutlinedTextField(
                value = appSearch,
                onValueChange = onAppSearch,
                label = { Text(text.searchApps) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (installedApps.isEmpty()) {
                Text(text.noInstalledApps, style = MaterialTheme.typography.bodySmall)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    installedApps.forEach { app ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectedAppPackage(app.packageName) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (selectedAppPackage == app.packageName) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            ),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(app.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        RouteMatcherKind.Domain -> OutlinedTextField(
            value = matcherText,
            onValueChange = onMatcherText,
            label = { Text(text.domainHostInput) },
            placeholder = { Text("example.org") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = matcherText.isBlank(),
        )
        RouteMatcherKind.Cidr -> OutlinedTextField(
            value = matcherText,
            onValueChange = onMatcherText,
            label = { Text(text.ipCidrInput) },
            placeholder = { Text("192.0.2.0/24") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = matcherText.isNotBlank() && !isValidIpOrCidr(matcherText),
        )
    }
}

@Composable
private fun RouteDetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private fun newRouteDraft(config: RoutingConfig): RouteRule = RouteRule(
    id = "route_${UUID.randomUUID()}",
    name = "New route",
    type = RouteRuleType.APP,
    targetProfileId = config.defaultProfileId ?: RoutingConfigDefaults.SYSTEM_PROFILE_ID,
    dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
    priority = (config.rules.maxOfOrNull { it.priority } ?: 1000) + 10,
    matchers = emptyList(),
    appMatchers = emptyList(),
    reason = routeReason(RouteMatcherKind.App),
    technicalDetails = routeTechnicalDetails(RouteMatcherKind.App),
    recommendedAction = routeRecommendedAction(config.defaultProfileId ?: RoutingConfigDefaults.SYSTEM_PROFILE_ID),
)

private fun RouteRuleType.toMatcherKind(): RouteMatcherKind = when (this) {
    RouteRuleType.APP, RouteRuleType.APP_GROUP -> RouteMatcherKind.App
    RouteRuleType.DOMAIN -> RouteMatcherKind.Domain
    RouteRuleType.CIDR -> RouteMatcherKind.Cidr
    RouteRuleType.DEFAULT -> RouteMatcherKind.App
}

private fun RouteMatcherKind.toRuleType(): RouteRuleType = when (this) {
    RouteMatcherKind.App -> RouteRuleType.APP
    RouteMatcherKind.Domain -> RouteRuleType.DOMAIN
    RouteMatcherKind.Cidr -> RouteRuleType.CIDR
}

private fun RouteMatcherKind.label(text: UiText): String = when (this) {
    RouteMatcherKind.App -> text.matcherApp
    RouteMatcherKind.Domain -> text.matcherDomain
    RouteMatcherKind.Cidr -> text.matcherCidr
}

private fun matcherSummary(rule: RouteRule): String = when (rule.type) {
    RouteRuleType.APP, RouteRuleType.APP_GROUP -> rule.appMatchers.joinToString(" • ") { matcher ->
        matcher.displayName?.let { "$it (${matcher.value})" } ?: matcher.value
    }.ifBlank { "App matcher not selected" }
    RouteRuleType.DOMAIN -> rule.matchers.joinToString(" • ").ifBlank { "Domain / host not set" }
    RouteRuleType.CIDR -> rule.matchers.joinToString(" • ").ifBlank { "IP / CIDR not set" }
    RouteRuleType.DEFAULT -> "Default System route"
}

private fun routeTargetName(config: RoutingConfig, profileId: String): String = when (profileId) {
    RoutingConfigDefaults.SYSTEM_PROFILE_ID -> "System / Система"
    RoutingConfigDefaults.BLOCK_PROFILE_ID -> "Block / Блокировать"
    else -> config.profiles.firstOrNull { it.id == profileId }?.name ?: profileId
}

private fun unavailableTargetWarning(config: RoutingConfig, rule: RouteRule): List<String> {
    val profile = config.profiles.firstOrNull { it.id == rule.targetProfileId }
    return when {
        profile == null -> listOf("Target unavailable: fail closed")
        !profile.enabled -> listOf("Target disabled: fail closed")
        profile.mockOnly -> listOf("Target is mock-only")
        else -> emptyList()
    }
}

private fun routeReason(kind: RouteMatcherKind): String = when (kind) {
    RouteMatcherKind.App -> "Explicit app route selected by the local route editor."
    RouteMatcherKind.Domain -> "Explicit domain / host route selected by the local route editor."
    RouteMatcherKind.Cidr -> "Explicit IP / CIDR route selected by the local route editor."
}

private fun routeTechnicalDetails(kind: RouteMatcherKind): String = "Matcher type: ${kind.name}. Exact duplicate conflicts are validated locally before save. Runtime packet enforcement is still planned."

private fun routeRecommendedAction(targetProfileId: String): String = if (targetProfileId == RoutingConfigDefaults.BLOCK_PROFILE_ID) {
    "Traffic matching this rule should be blocked when runtime enforcement is implemented."
} else {
    "Keep the target profile available; explicit rules are fail-closed and must not silently fall back."
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
private fun SettingsScreen(
    padding: PaddingValues,
    text: UiText,
    settings: AppSettings,
    loaded: Boolean,
    message: String?,
    tunTestRoutePreviewEnabled: Boolean,
    onTunTestRoutePreview: (Boolean) -> Unit,
    vpnState: VpnServiceUiState,
    onSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    var supportExpanded by rememberSaveable { mutableStateOf(false) }
    var helpExpanded by rememberSaveable { mutableStateOf(true) }
    var beginnerExpanded by rememberSaveable { mutableStateOf(false) }
    var adminExpanded by rememberSaveable { mutableStateOf(false) }
    var developerExpanded by rememberSaveable { mutableStateOf(false) }
    ScreenList(padding) {
        item { Header(text.settings, text.settingsSubtitle) }
        item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text.help, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { helpExpanded = !helpExpanded }) { Text(if (helpExpanded) text.less else text.details) }
                }
                if (helpExpanded) {
                    CompactCard(text, text.aboutViroutefs, text.projectOverviewShort, text.projectOverviewDetails)
                    CompactCard(text, text.licenseSummaryTitle, text.licenseSummaryShort, text.licenseSummaryDetails)
                    CompactCard(text, text.privacy, text.privacyShort, text.privacyDetails)
                    CompactCard(text, text.currentAlphaLimitations, text.alphaLimitationsShort, text.alphaLimitationsDetails)
                    CompactCard(text, text.projectGoals, text.projectGoalsShort, text.projectGoalsDetails)
                    ExpandableHelpBlock(text.beginnerMode, beginnerExpanded, { beginnerExpanded = !beginnerExpanded }, text.beginnerHelp)
                    ExpandableHelpBlock(text.adminMode, adminExpanded, { adminExpanded = !adminExpanded }, text.adminHelp)
                }
            }
        }
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
        item { CompactCard(text, text.version, "ViRouteFS ${BuildConfig.VERSION_NAME}", "versionCode ${BuildConfig.VERSION_CODE}") }
        item { CompactCard(text, text.configStatus, if (loaded) text.configLoaded else text.loading, message ?: text.ready) }
        item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text.developerDiagnostics, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { developerExpanded = !developerExpanded }) { Text(if (developerExpanded) text.less else text.details) }
                }
                if (developerExpanded) {
                    Text(text.developerDiagnosticsWarning, style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(text.vpnTestRoute, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(text.vpnNormalInternetUnchanged, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = tunTestRoutePreviewEnabled, onCheckedChange = onTunTestRoutePreview)
                    }
                    Details(text.details, "${text.vpnPacketsRead}: ${vpnState.packetsRead}\n${text.vpnBytesRead}: ${vpnState.bytesRead}\n${text.vpnHowToTestTun}")
                }
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
    }
}

@Composable
private fun ExpandableHelpBlock(title: String, expanded: Boolean, onToggle: () -> Unit, body: String) {
    Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onToggle) { Text(if (expanded) "−" else "+") }
    }
    if (expanded) Text(body, style = MaterialTheme.typography.bodySmall)
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
    val networks = t("Сети", "Networks", "网络")
    val vpn = networks
    val routes = t("Маршруты", "Routes", "路由")
    val dns = "DNS"
    val fs = "FS"
    val tools = t("Инструменты", "Tools", "工具")
    val settings = t("Настройки", "Settings", "设置")
    val more = t("Ещё", "More", "更多")
    val dashboardSubtitle = t("Короткий статус приложения и приватности.", "Short app and privacy status.", "应用和隐私状态概览。")
    val networksSubtitle = t("Профили сети, маршруты и безопасный локальный контроль.", "Network profiles, routes, and safe local control.", "网络配置、路由和安全本地控制。")
    val vpnSubtitle = networksSubtitle
    val routesSubtitle = t("Какой тоннель используется. Нажмите маршрут для деталей.", "Which tunnel is used. Tap a route for details.", "查看使用哪个隧道。点按路由查看详情。")
    val dnsSubtitle = t("Проверка DNS и локальные политики.", "DNS checks and local policies.", "DNS 检查和本地策略。")
    val fsSubtitle = t("Локальные счётчики и будущая видимость потоков без обещаний полного анализа.", "Local counters and future flow visibility without claiming full traffic analysis.", "本地计数器和未来流量可见性，不声称完整流量分析。")
    val toolsSubtitle = t("TCP, TLS, HTTP и маршрутная диагностика.", "TCP, TLS, HTTP, and route diagnostics.", "TCP、TLS、HTTP 和路由诊断。")
    val settingsSubtitle = t("Язык, тема, справка и расширенная диагностика.", "Language, theme, help, and advanced diagnostics.", "语言、主题、帮助和高级诊断。")
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
    val masterSwitch = t("Активировать контроль сети", "Activate network control", "激活网络控制")
    val on = t("вкл", "on", "开")
    val off = t("выкл", "off", "关")
    val details = t("Подробнее", "Details", "详情")
    val less = t("Скрыть", "Less", "收起")
    val vpnDemoDetails = t("Переключатель показывает состояние UI. VPN-движки в этой версии не добавлены.", "The switch controls UI state only. VPN engines are not added in this release.", "此开关仅控制界面状态。本版本未添加 VPN 引擎。")
    val vpnLocalPreviewTitle = t("Активировать контроль сети", "Activate network control", "激活网络控制")
    val activateNetworkControl = vpnLocalPreviewTitle
    val vpnNoTrafficRoutingYet = t("Сейчас это безопасный preview; полное применение модели — будущая runtime-задача.", "Safe preview now; full model enforcement is a future runtime task.", "当前是安全预览；完整模型执行是未来运行时任务。")
    val networkControlSummary = vpnNoTrafficRoutingYet
    val vpnNoHiddenInterception = t("Нет скрытого перехвата", "No hidden interception", "没有隐藏拦截")
    val vpnPacketProcessingLater = t("Интернет должен остаться без изменений.", "Internet should remain unchanged.", "互联网应保持不变。")
    val vpnLifecycleOnlyDetails = t("0.6.6-alpha по умолчанию создаёт минимальный route-less TUN с адресом 10.250.0.2/32 только для проверки VpnService. В режиме тестового маршрута явно добавляется только 203.0.113.0/24 (TEST-NET-3); runtime default-route enforcement, DNS-серверов, логирования payload, пересылки, прокси и VPN-движков нет.", "0.6.6-alpha creates a minimal route-less TUN with address 10.250.0.2/32 by default only to verify VpnService. Test-route mode explicitly adds only 203.0.113.0/24 (TEST-NET-3); there is no runtime default-route enforcement, DNS servers, payload logging, forwarding, proxying, or VPN engines.", "0.6.6-alpha 默认仅创建地址为 10.250.0.2/32 的最小无路由 TUN 来验证 VpnService。测试路由模式仅显式添加 203.0.113.0/24 (TEST-NET-3)；没有运行时默认路由执行、DNS 服务器、payload 日志、转发、代理或 VPN 引擎。")
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
    val noNetworkProfilesYet = t("Встроены System и Block; внешних профилей пока нет.", "Built-in System and Block; no external profiles yet.", "内置 System 和 Block；暂无外部配置。")
    val profileAdvancedDetails = t("Экспериментальные состояния и будущие движки не отображаются как готовые пользовательские функции.", "Experimental state and future engines are not presented as ready user features.", "实验状态和未来引擎不会作为就绪用户功能展示。")
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
    val noDns = t("Использует системный DNS Android", "Uses Android system DNS", "使用 Android 系统 DNS")
    val defaultProfile = t("Основной", "Default", "默认")
    val defaultChanged = t("Основной профиль изменён.", "Default profile changed.", "默认配置已更改。")
    val makeDefault = t("Основной", "Default", "默认")
    val delete = t("Удалить", "Delete", "删除")
    val profileDeleted = t("Профиль удалён.", "Profile deleted.", "配置已删除。")
    val protectedProfileMessage = t("System и Block — встроенные профили, их нельзя удалить.", "System and Block are built-in profiles and cannot be deleted.", "System 和 Block 是内置配置，无法删除。")
    val mockOnly = t("Демо: реальный тоннель не запускается.", "Demo: no real tunnel is started.", "演示：不会启动真实隧道。")
    val cancel = t("Отмена", "Cancel", "取消")
    val edit = t("Изменить", "Edit", "编辑")
    val profileUpdated = t("Профиль обновлён.", "Profile updated.", "配置已更新。")
    val save = t("Сохранить", "Save", "保存")
    val simulation = t("Проверка правила", "Rule check", "规则检查")
    val routeEmptyState = t("Приложения без правил идут через маршрут Система.", "Apps without rules use System route.", "没有规则的应用使用 System 路由。")
    val noRoutesConfigured = t("Маршруты не настроены", "No routes configured yet", "尚未配置路由")
    val installedApps = t("Установленные приложения", "Installed apps", "已安装应用")
    val noInstalledApps = t("Список приложений недоступен на этом устройстве.", "Installed app list is not available on this device.", "此设备无法使用已安装应用列表。")
    val routeIsolationNote = t("Это эксклюзивное правило. Если выбранный профиль недоступен, трафик блокируется, а не уходит через другой профиль. Когда контроль сети включён, весь трафик концептуально входит в ViRouteFS.", "This rule is exclusive. If the selected profile is unavailable, traffic must be blocked, not sent through another profile. When network control is active, all traffic conceptually enters ViRouteFS.", "网络控制激活时，所有流量概念上进入 ViRouteFS。路由是排他的：不静默回退；所选配置不可用时 Block / fail closed。")
    val domainIpApp = t("домен/IP/приложение", "domain/IP/app", "域名/IP/应用")
    val matchers = t("условий", "matchers", "匹配项")
    val disable = t("Выкл", "Disable", "禁用")
    val enable = t("Вкл", "Enable", "启用")
    val editors = t("Редакторы", "Editors", "编辑器")
    val appsDomainsIps = t("Приложения / домены / IP", "Apps / domains / IPs", "应用 / 域名 / IP")
    val back = t("Назад", "Back", "返回")
    val routeDetails = t("Детали маршрута", "Route details", "路由详情")
    val routeDetailsSubtitle = t("Настройте цель и состояние маршрута.", "Adjust the route target and state.", "调整路由目标和状态。")
    val targetProfile = t("Маршрут / профиль", "Route / profile", "路由 / 配置")
    val enabled = t("Включён", "Enabled", "已启用")
    val apps = t("Приложения", "Apps", "应用")
    val domainsIps = t("Домены и IP/CIDR", "Domains and IP/CIDR", "域名和 IP/CIDR")
    val routeDeleted = t("Маршрут удалён.", "Route deleted.", "路由已删除。")
    val addRoute = t("+ Маршрут", "+ Route", "+ 路由")
    val routeName = t("Название маршрута", "Route name", "路由名称")
    val matcherType = t("Тип условия", "Matcher type", "匹配类型")
    val matcherApp = t("Приложение", "App", "应用")
    val matcherDomain = t("Домен / host", "Domain / host", "域名 / 主机")
    val matcherCidr = t("IP / CIDR", "IP / CIDR", "IP / CIDR")
    val selectedMatcher = t("Выбранное условие", "Selected matcher", "已选匹配项")
    val searchApps = t("Поиск по названию или package", "Search label or package", "搜索名称或包名")
    val domainHostInput = t("Домен или host", "Domain or host", "域名或主机")
    val ipCidrInput = t("IPv4 или CIDR", "IPv4 or CIDR", "IPv4 或 CIDR")
    val advanced = t("Дополнительно", "Advanced", "高级")
    val validation = t("Проверка конфликтов", "Conflict validation", "冲突检查")
    val warning = t("предупреждение", "warning", "警告")
    val systemBlockOnly = t("Доступны встроенные System и Block; внешних реальных профилей нет.", "Built-in System and Block are available; no real external profiles exist.", "可用内置 System 和 Block；没有真实外部配置。")
    val runtimeRoutingFuture = t("Runtime enforcement ещё не добавлен: ViRouteFS пока не захватывает default route и не пересылает пакеты через профили.", "Runtime enforcement is still planned: ViRouteFS does not capture the default route or forward packets through profiles yet.", "运行时执行仍在计划中：ViRouteFS 尚未捕获默认路由或通过配置转发数据包。")
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
    val fsLimitShort = t("Пока нет полного анализа трафика; доступны только локальные счётчики при явном включении диагностики.", "No full traffic analysis yet; only local counters are available when diagnostics are explicitly enabled.", "尚无完整流量分析；仅在明确启用诊断时可用本地计数器。")
    val fsLimitDetails = t("FS 0.6.6 показывает локальные счётчики только при явном включении developer diagnostics. Это не полный packet capture: нет runtime default-route enforcement, DNS в VPN, payload logging, извлечения доменов, forwarding/proxying или облачной загрузки.", "FS 0.6.6 shows local counters only when developer diagnostics are explicitly enabled. This is not full packet capture: there is no runtime default-route enforcement, VPN DNS, payload logging, domain extraction, forwarding/proxying, or cloud upload.", "FS 0.6.6 仅在明确启用开发者诊断时显示本地计数。这不是完整抓包：没有运行时默认路由执行、VPN DNS、负载日志、域名提取、转发/代理或云上传。")
    val flowScannerTitle = "Flow Scanner"
    val flowScannerSubtitle = t("кто куда подключается и почему", "who connects where and why", "谁连接到哪里以及原因")
    val flowAppFilter = t("Приложения", "Apps", "应用")
    val flowAllAppsPlaceholder = t("Все приложения", "All apps", "所有应用")
    val flowStartAnalysis = t("Начать анализ", "Start analysis", "开始分析")
    val flowDemoMode = t("локально", "local", "本地")
    val flowEmptyTitle = t("Потоки пока не записаны", "No flows captured yet", "尚未捕获流量")
    val flowEmptyState = t("Flow Scanner покажет локальные счётчики, когда они доступны. Полный анализ трафика приложений ещё не заявляется.", "Flow Scanner shows local counters when available. Full app traffic analysis is not claimed yet.", "Flow Scanner 会在可用时显示本地计数器。目前不声称完整应用流量分析。")
    val flowPreviewOnly = t("Локальная видимость без payload logging, forwarding/proxying или облачной загрузки.", "Local visibility without payload logging, forwarding/proxying, or cloud upload.", "本地可见性，无 payload 日志、转发/代理或云上传。")
    val flowLiveTestRoute = t("Live test route", "Live test route", "实时测试路由")
    val flowLiveLocalTestData = t("live local test data", "live local test data", "实时本地测试数据")
    val flowDemoPreview = t("demo / preview", "demo / preview", "演示 / 预览")
    val flowSource = t("Источник", "Source", "来源")
    val flowRoute = t("Маршрут", "Route", "路由")
    val flowPacketsRead = t("Пакеты прочитаны", "Packets read", "已读数据包")
    val flowBytesRead = t("Байты прочитаны", "Bytes read", "已读字节")
    val flowLastPacket = t("Последний пакет", "Last packet", "最后数据包")
    val flowStatus = t("Статус", "Status", "状态")
    val flowActive = t("активен", "active", "活跃")
    val flowInactive = t("неактивен", "inactive", "不活跃")
    val flowNever = t("никогда", "never", "从未")
    val flowVpnMode = t("VPN mode", "VPN mode", "VPN 模式")
    val flowSafety = t("Безопасность", "Safety", "安全")
    val flowHowToTest = t("Как проверить", "How to test", "如何测试")
    val flowTunSafetyDetails = t(
        """No default route
No DNS
No payload logging
Packets are dropped after counting""",
        """No default route
No DNS
No payload logging
Packets are dropped after counting""",
        """无默认路由
无 DNS
无负载日志
数据包计数后丢弃""",
    )
    val flowTunHowToTest = t("Откройте http://203.0.113.1 или попробуйте подключиться к 203.0.113.1", "Open http://203.0.113.1 or try connecting to 203.0.113.1", "打开 http://203.0.113.1 或尝试连接 203.0.113.1")
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
    val telegramRouteReason = t("Правило домена показывает будущий выбор выбранного профиля.", "A domain rule shows future selected-profile routing.", "域名规则展示未来所选配置路由。")
    val telegramRecommendation = t("Проверьте, что профиль выбран осознанно, затем включайте реальный режим только вручную.", "Confirm the profile is intentional, then enable any real mode only manually.", "确认配置选择正确；真实模式只能手动启用。")
    val telegramTechnical = t("Демо-событие: будущий источник — локальный VpnService flow log после явного разрешения пользователя.", "Demo event: future source is the local VpnService flow log after explicit user consent.", "演示事件：未来来源是在用户明确同意后的本地 VpnService 流日志。")
    val mediaRouteReason = t("Домены могут соответствовать пользовательскому правилу выбранного профиля.", "Domains can match a user rule for a selected profile.", "域名可以匹配所选配置的用户规则。")
    val mediaRecommendation = t("Если соединение работает медленно, позже проверьте выбранный профиль и MTU.", "If a connection is slow, later check the selected profile and MTU.", "如果连接较慢，稍后检查所选配置和 MTU。")
    val mediaTechnical = t("Событие показывает целевой домен и предполагаемый протокол; реальный QUIC/TCP анализ не выполняется.", "The event shows target domains and expected protocol; real QUIC/TCP analysis is not performed.", "事件显示目标域和预期协议；未执行真实 QUIC/TCP 分析。")
    val govRouteReason = t("Чувствительные локальные сервисы оставлены на System, чтобы не отправлять их в сторонний тоннель.", "Sensitive local services stay on System so they are not sent through a third-party tunnel.", "敏感本地服务保持 System，避免经过第三方隧道。")
    val govRecommendation = t("Оставьте System для банков и госуслуг, если у вас нет отдельной доверенной политики.", "Keep System for banks and public services unless you have a separate trusted policy.", "除非有单独可信策略，否则银行和公共服务建议保持 System。")
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
    val help = t("Справка", "Help", "帮助")
    val aboutViroutefs = t("About ViRouteFS", "About ViRouteFS", "关于 ViRouteFS")
    val projectOverviewShort = t("Visual Route & Flow Scanner — локальный Android-инструмент для видимости маршрутов и потоков.", "Visual Route & Flow Scanner is a local Android tool for route and flow visibility.", "Visual Route & Flow Scanner 是用于路由和流量可见性的本地 Android 工具。")
    val projectOverviewDetails = t("Home больше не отдельная вкладка: обзор, статус, приватность и цели живут здесь, в Settings → Help.", "Home is no longer a separate tab: overview, status, privacy, and goals live here in Settings → Help.", "Home 不再是单独标签：概览、状态、隐私和目标位于 Settings → Help。")
    val licenseSummaryTitle = t("Лицензия GPL-3.0-or-later", "GPL-3.0-or-later license", "GPL-3.0-or-later 许可证")
    val licenseSummaryShort = t("Проект остаётся свободным ПО под GPL-3.0-or-later.", "The project remains free software under GPL-3.0-or-later.", "项目仍是 GPL-3.0-or-later 下的自由软件。")
    val licenseSummaryDetails = t("Вы можете изучать, изменять и распространять код при соблюдении условий GPL; лицензия не заменяется и не ослабляется.", "You may study, modify, and redistribute the code under GPL terms; the license is not replaced or weakened.", "可按 GPL 条款研究、修改和再分发代码；许可证不会被替换或削弱。")
    val currentAlphaLimitations = t("Текущие ограничения alpha", "Current alpha limitations", "当前 alpha 限制")
    val alphaLimitationsShort = t("Нет runtime default-route enforcement, DNS в VPN builder, payload logging, forwarding/proxying или готовых внешних тоннелей.", "No runtime default-route enforcement, VPN builder DNS, payload logging, forwarding/proxying, or ready external tunnels.", "没有运行时默认路由执行、VPN builder DNS、payload 日志、转发/代理或可用外部隧道。")
    val alphaLimitationsDetails = t("System — внутренний маршрут по умолчанию в модели ViRouteFS, не bypass. Полное runtime-применение всех потоков ещё не добавлено; TEST-NET маршрут оставлен только в Developer diagnostics.", "System is the internal default route in the ViRouteFS model, not bypass. Full runtime enforcement for all flows is not added yet; the TEST-NET route remains only in Developer diagnostics.", "System 是 ViRouteFS 模型中的内部默认路由，不是绕过。尚未添加所有流量的完整运行时执行；TEST-NET 路由仅保留在开发者诊断中。")
    val projectGoals = t("Цели проекта", "Project goals", "项目目标")
    val projectGoalsShort = t("Сети, маршруты, DNS, Flow Scanner, диагностика и безопасные локальные аудиты.", "Networks, routes, DNS, Flow Scanner, diagnostics, and safe local audits.", "网络、路由、DNS、Flow Scanner、诊断和安全本地审计。")
    val projectGoalsDetails = t("Один VpnService, внутренние политики маршрутизации, Xray/OpenVPN позже, DNS/TCP/TLS/HTTP/UDP/MTU диагностика, понятные логи и локальный PCAP export по явному действию пользователя.", "A single VpnService, internal routing policies, Xray/OpenVPN later, DNS/TCP/TLS/HTTP/UDP/MTU diagnostics, readable logs, and local PCAP export only by explicit user action.", "单个 VpnService、内部路由策略、未来 Xray/OpenVPN、DNS/TCP/TLS/HTTP/UDP/MTU 诊断、可读日志，以及仅用户明确操作的本地 PCAP 导出。")
    val beginnerMode = t("Для самых маленьких", "For beginners", "初学者")
    val beginnerHelp = t("Маршруты — это правила вида: это приложение, домен или IP идёт через этот маршрут. System / Система — обычный системный путь Android внутри модели ViRouteFS для приложений без явного правила. Block / Блокировать означает: совпавший трафик должен быть закрыт.", "Routes are rules like: this app, domain, or IP goes through this route. System is the normal Android system path inside the ViRouteFS model for apps without an explicit rule. Block means matching traffic should be denied.", "网络控制开启时，流量通过 ViRouteFS。没有规则的应用使用 System。网络显示流量可去向；路由选择规则；DNS 规划名称处理。")
    val adminMode = t("Для админов", "For admins", "管理员")
    val adminHelp = t("Модель: app/domain/IP/CIDR matchers; точные дубликаты блокируются перед сохранением; unmatched → System; matched → только выбранный профиль; unavailable → Block / fail closed. Runtime enforcement ещё планируется: нет default-route capture, DNS в builder, payload logging или forwarding/proxying.", "Model: app/domain/IP/CIDR matchers; exact duplicates are blocked before save; unmatched → System; matched → selected profile only; unavailable → Block / fail closed. Runtime enforcement is still planned: no default-route capture, builder DNS, payload logging, or forwarding/proxying.", "模型：app/domain/IP/CIDR 匹配；保存前阻止精确重复；未匹配 → System；已匹配 → 仅所选配置；不可用 → Block / fail closed。运行时执行仍在计划中：没有默认路由捕获、builder DNS、payload 日志或转发/代理。")
    val developerDiagnostics = t("Developer diagnostics", "Developer diagnostics", "开发者诊断")
    val developerDiagnosticsWarning = t("Не обычная функция пользователя. Используется для внутренней проверки безопасного TEST-NET маршрута.", "Not a normal user feature. Used for internal validation of the safe TEST-NET route.", "不是普通用户功能。用于安全 TEST-NET 路由的内部验证。")
    val supportProject = t("Поддержать проект", "Support project", "支持项目")
    val supportShort = t("Ссылки скрыты, чтобы экран оставался компактным.", "Links are collapsed to keep the screen compact.", "链接已折叠，使界面更紧凑。")
    val actionPrefix = t("Что сделать: ", "Recommended action: ", "建议操作：")

    fun screen(screen: AppScreen): String = when (screen) {
        AppScreen.Vpn -> networks
        AppScreen.Routes -> routes
        AppScreen.Dns -> dns
        AppScreen.Fs -> fs
        AppScreen.More -> more
        AppScreen.Tools -> tools
        AppScreen.Settings -> settings
    }

    fun profileCount(value: Int): String = t("$value проф.", "$value profiles", "$value 个配置")
    fun assignedRoutesCount(value: Int): String = t("Маршрутов: $value", "Routes: $value", "路由：$value")
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
