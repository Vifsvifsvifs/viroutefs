// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.AltRoute
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import dev.vifs.viroutefs.routing.DomainMatcherMode
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.AppMatcher
import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.ProfileGroup
import dev.vifs.viroutefs.routing.ProfileGroupMode
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RouteTransport
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.encodeDomainMatcher
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.findConflictsForCandidate
import dev.vifs.viroutefs.routing.findExactRouteConflicts
import dev.vifs.viroutefs.routing.hasRuntimeConfiguration
import dev.vifs.viroutefs.routing.isValidIpOrCidr
import dev.vifs.viroutefs.routing.moveExplicitRule
import dev.vifs.viroutefs.routing.parseDestinationPortRanges
import dev.vifs.viroutefs.routing.parseDomainMatcher
import dev.vifs.viroutefs.routing.toDisplayText
import dev.vifs.viroutefs.routing.validateRouteEditorDraft
import dev.vifs.viroutefs.routing.validateDomainMatcher
import dev.vifs.viroutefs.routing.validateRoutingConfig
import dev.vifs.viroutefs.routing.withDefaultRoute
import dev.vifs.viroutefs.socks5.Socks5ReadinessSummary
import dev.vifs.viroutefs.socks5.Socks5TestHistoryStore
import dev.vifs.viroutefs.socks5.deriveSocks5ReadinessSummary
import dev.vifs.viroutefs.settings.AppLanguage
import dev.vifs.viroutefs.settings.AppSettings
import dev.vifs.viroutefs.settings.AppSettingsRepository
import dev.vifs.viroutefs.settings.AppThemeMode
import dev.vifs.viroutefs.ui.DnsScreen
import dev.vifs.viroutefs.ui.FlowScannerScreen
import dev.vifs.viroutefs.ui.InstalledApplicationIcon
import dev.vifs.viroutefs.ui.VpnScreen
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import dev.vifs.viroutefs.update.GITHUB_RELEASES_WEB_URL
import dev.vifs.viroutefs.update.DownloadProgress
import dev.vifs.viroutefs.update.ReleaseInfo
import dev.vifs.viroutefs.update.UpdateApkDownloader
import dev.vifs.viroutefs.update.UpdateDownloadState
import dev.vifs.viroutefs.update.UpdateCheckResult
import dev.vifs.viroutefs.update.formatBytes
import dev.vifs.viroutefs.update.UpdateChecker
import dev.vifs.viroutefs.vpn.VpnServiceController
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vless.VLESS_RUNTIME_LIMITATION
import dev.vifs.viroutefs.vless.VLESS_ROUTE_PREVIEW_ONLY
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var incomingProfileImport by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingProfileImport = intent.profileImportSource()
        replaceSensitiveIntent()
        val settingsRepository = AppSettingsRepository(applicationContext)
        setContent {
            var settings by remember { mutableStateOf(settingsRepository.load()) }
            ViRouteFsTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ViRouteFsApp(
                        settings = settings,
                        incomingProfileImport = incomingProfileImport,
                        onProfileImportConsumed = { incomingProfileImport = null },
                        onSettings = { next ->
                            settings = next
                            settingsRepository.save(next)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incomingProfileImport = intent.profileImportSource()
        replaceSensitiveIntent()
    }

    private fun replaceSensitiveIntent() {
        setIntent(
            Intent(Intent.ACTION_MAIN)
                .setClass(this, MainActivity::class.java),
        )
    }
}

private fun Intent.profileImportSource(): String? = when (action) {
    Intent.ACTION_SEND -> getCharSequenceExtra(Intent.EXTRA_TEXT)
        ?.toString()
        ?.trim()
        ?.takeIf(String::isNotBlank)
    Intent.ACTION_VIEW -> dataString
        ?.trim()
        ?.takeIf { value ->
            value.substringBefore("://").lowercase(Locale.ROOT) in PROFILE_IMPORT_SCHEMES
        }
    else -> null
}

private val PROFILE_IMPORT_SCHEMES = setOf(
    "vless",
    "vmess",
    "trojan",
    "ss",
    "hysteria2",
    "hy2",
    "tuic",
    "socks",
    "socks5",
)

internal enum class AppScreen(val icon: ImageVector) {
    Vpn(Icons.Outlined.Shield),
    Routes(Icons.AutoMirrored.Outlined.AltRoute),
    Dns(Icons.Outlined.Dns),
    Fs(Icons.Outlined.Security),
    More(Icons.Outlined.MoreHoriz),
    Tools(Icons.Outlined.Build),
    Settings(Icons.Outlined.Settings),
}

private val bottomScreens = listOf(AppScreen.Vpn, AppScreen.Routes, AppScreen.Fs, AppScreen.More)
private val moreScreens = setOf(AppScreen.Dns, AppScreen.Tools, AppScreen.Settings)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp(
    settings: AppSettings,
    incomingProfileImport: String?,
    onProfileImportConsumed: () -> Unit,
    onSettings: (AppSettings) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val text = remember(settings.language) { UiText(settings.language) }
    val repository = remember(context) { RoutingConfigRepository(context.applicationContext) }
    val configSaveMutex = remember { Mutex() }
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

    LaunchedEffect(incomingProfileImport) {
        if (!incomingProfileImport.isNullOrBlank()) selectedScreen = AppScreen.Vpn
    }

    fun startAfterPermissions(testRoutePreviewEnabled: Boolean = tunTestRoutePreviewEnabled) {
        if (!testRoutePreviewEnabled && !config.emergencyBlockEnabled) {
            defaultRouteActivationError(config)?.let { error ->
                selectedScreen = AppScreen.Vpn
                vpnState = VpnServiceUiState(VpnServiceStatus.Error, error)
                return
            }
        }
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
        if (enabled && (vpnState.status == VpnServiceStatus.Starting || vpnState.status == VpnServiceStatus.RuntimeActive || vpnState.status == VpnServiceStatus.ServiceActiveNoTun || vpnState.status == VpnServiceStatus.TunPreviewActive || vpnState.status == VpnServiceStatus.TunTestRouteActive)) {
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
        val reloadActiveVpn = vpnState.status == VpnServiceStatus.RuntimeActive ||
            vpnState.status == VpnServiceStatus.TunPreviewActive ||
            vpnState.status == VpnServiceStatus.TunTestRouteActive
        val reloadTestRoutePreview = vpnState.tunTestRouteActive
        config = normalizedConfig
        message = note ?: text.saved
        scope.launch {
            val saveResult = configSaveMutex.withLock {
                runCatching { repository.save(normalizedConfig) }
            }
            saveResult
                .onSuccess {
                    if (reloadActiveVpn) {
                        vpnController.reloadLocalService(reloadTestRoutePreview)
                    }
                }
                .onFailure { error ->
                        message = "Не удалось сохранить конфигурацию: ${error.localizedMessage ?: error::class.java.simpleName}"
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(text.screen(selectedScreen), style = MaterialTheme.typography.titleMedium)
                        Text("ViRouteFS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (selectedScreen in moreScreens) {
                        IconButton(onClick = { selectedScreen = AppScreen.More }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = text.back)
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen || (screen == AppScreen.More && selectedScreen in moreScreens),
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
            AppScreen.Vpn -> VpnScreen(
                padding = padding,
                text = text,
                config = config,
                vpnState = vpnState,
                developerMode = settings.developerMode,
                tunTestRoutePreviewEnabled = tunTestRoutePreviewEnabled,
                initialImportSource = incomingProfileImport,
                onProfileImportConsumed = onProfileImportConsumed,
                onVpnSwitch = ::setVpnEnabled,
                onTunTestRoutePreview = ::setTunTestRoutePreviewEnabled,
                onClearPacketList = vpnController::clearPacketSummaries,
                onPausePacketInspector = vpnController::setPacketInspectorPaused,
                onConfig = ::updateConfig,
            )
            AppScreen.Routes -> RoutesScreen(padding, text, config, ::updateConfig)
            AppScreen.Dns -> DnsScreen(padding, text, config, ::updateConfig)
            AppScreen.Fs -> FlowScannerScreen(
                padding,
                text,
                vpnState,
                config,
                vpnController::clearPacketSummaries,
                vpnController::setPacketInspectorPaused,
            )
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
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingRoute by rememberSaveable { mutableStateOf(false) }
    var draftRoute by remember { mutableStateOf<RouteRule?>(null) }
    var selectedGroupId by rememberSaveable { mutableStateOf<String?>(null) }
    var creatingGroup by rememberSaveable { mutableStateOf(false) }
    val installedApps = remember(context) { context.loadInstalledAppsForRouting() }
    val historyStore = remember(context) { Socks5TestHistoryStore(context) }
    var readinessByProfile by remember(config.profiles) { mutableStateOf<Map<String, Socks5ReadinessSummary>>(emptyMap()) }
    LaunchedEffect(config.profiles) {
        readinessByProfile = config.profiles
            .filter { it.type == TunnelType.Socks5 }
            .associate { it.id to deriveSocks5ReadinessSummary(historyStore.recentForProfile(it.id)) }
    }
    val userRules = config.rules
        .filter { it.type != RouteRuleType.DEFAULT }
        .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name }.thenBy { it.id })
    val selectedRoute = userRules.firstOrNull { it.id == selectedRouteId }
    val selectedGroup = config.profileGroups.firstOrNull { it.id == selectedGroupId }
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

    if (selectedGroup != null || creatingGroup) {
        ProfileGroupEditorScreen(
            padding = padding,
            group = selectedGroup,
            config = config,
            onBack = {
                selectedGroupId = null
                creatingGroup = false
            },
            onConfig = { next, message ->
                onConfig(next, message)
                selectedGroupId = null
                creatingGroup = false
            },
        )
        return
    }

    ScreenList(padding) {
        item {
            CardBlock {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Куда направлять трафик", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Выберите приложение, домен, отдельный IP или диапазон сети, затем назначьте туннель и DNS.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Button(onClick = {
                        draftRoute = newRouteDraft(config)
                        creatingRoute = true
                    }) { Text(text.addRoute) }
                }
                val defaultRouteName = config.defaultProfileId
                    ?.let { id -> routeTargetName(config, id) }
                    ?: "не выбран"
                Text(
                    "По умолчанию: $defaultRouteName • правил: ${userRules.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Группы маршрутов", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Ручной выбор или проверка задержки между явно выбранными профилями. Скрытого fallback на System нет.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(onClick = { creatingGroup = true }) {
                        Text("Создать")
                    }
                }
                config.profileGroups.forEach { group ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedGroupId = group.id }
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${group.mode.displayName()} • участников: ${group.memberProfileIds.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip(
                            when {
                                !group.enabled -> "Выключена"
                                !group.isRunnable(config) -> "Нужна проверка"
                                config.defaultProfileId == group.id -> "По умолчанию"
                                else -> "Готова"
                            },
                        )
                    }
                }
                if (config.profileGroups.isEmpty()) {
                    Text(
                        "Групп пока нет. Обычные правила продолжают указывать прямо на профиль.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (userRules.isEmpty()) {
            item { CompactCard(text, text.noRoutesConfigured, text.routeEmptyState, text.routeIsolationNote) }
        } else {
            itemsIndexed(userRules, key = { _, rule -> rule.id }) { index, rule ->
                RouteRuleCard(
                    text = text,
                    rule = rule,
                    profileName = routeTargetName(config, rule.targetProfileId),
                    warnings = conflictsByRuleId[rule.id].orEmpty() + unavailableTargetWarning(config, rule),
                    canMoveUp = index > 0,
                    canMoveDown = index < userRules.lastIndex,
                    onMoveUp = {
                        onConfig(
                            config.moveExplicitRule(rule.id, -1),
                            "Правило «${rule.name}» поднято выше. Меньший номер применяется раньше.",
                        )
                    },
                    onMoveDown = {
                        onConfig(
                            config.moveExplicitRule(rule.id, 1),
                            "Правило «${rule.name}» опущено ниже. Порядок пересчитан детерминированно.",
                        )
                    },
                    onOpen = { selectedRouteId = rule.id },
                )
            }
        }
    }
}

@Composable
private fun ProfileGroupEditorScreen(
    padding: PaddingValues,
    group: ProfileGroup?,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val groupId = rememberSaveable(group?.id) { group?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable(groupId) { mutableStateOf(group?.name.orEmpty()) }
    var modeName by rememberSaveable(groupId) {
        mutableStateOf((group?.mode ?: ProfileGroupMode.Manual).name)
    }
    var memberIds by rememberSaveable(groupId) {
        mutableStateOf(group?.memberProfileIds.orEmpty())
    }
    var selectedProfileId by rememberSaveable(groupId) {
        mutableStateOf(group?.selectedProfileId)
    }
    var testUrl by rememberSaveable(groupId) {
        mutableStateOf(group?.testUrl ?: "https://www.gstatic.com/generate_204")
    }
    var intervalText by rememberSaveable(groupId) {
        mutableStateOf((group?.testIntervalSeconds ?: 180).toString())
    }
    var toleranceText by rememberSaveable(groupId) {
        mutableStateOf((group?.toleranceMs ?: 50).toString())
    }
    var enabled by rememberSaveable(groupId) { mutableStateOf(group?.enabled ?: true) }
    var useAsDefault by rememberSaveable(groupId) {
        mutableStateOf(config.defaultProfileId == groupId)
    }
    var errors by rememberSaveable(groupId) { mutableStateOf<List<String>>(emptyList()) }
    val mode = ProfileGroupMode.entries.firstOrNull { it.name == modeName }
        ?: ProfileGroupMode.Manual
    val availableProfiles = config.profiles.filter { profile ->
        profile.type != TunnelType.Block &&
            (
                profile.id in memberIds ||
                    (
                        profile.enabled &&
                            (
                                profile.type == TunnelType.Direct ||
                                    profile.type == TunnelType.ByeDpi ||
                                    profile.type == TunnelType.Socks5 ||
                                    profile.type == TunnelType.VLESS ||
                                    profile.type == TunnelType.XrayVlessReality ||
                                    profile.singBox != null
                                )
                        )
                )
    }
    val draft = ProfileGroup(
        id = groupId,
        name = name.trim(),
        mode = mode,
        memberProfileIds = memberIds.distinct(),
        selectedProfileId = selectedProfileId,
        testUrl = testUrl.trim(),
        testIntervalSeconds = intervalText.toIntOrNull() ?: -1,
        toleranceMs = toleranceText.toIntOrNull() ?: -1,
        enabled = enabled,
    )

    ScreenList(padding) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack) { Text("Назад") }
                Header(
                    if (group == null) "Новая группа маршрутов" else "Группа «${group.name}»",
                    "Группа становится отдельной целью для приложений, доменов, IP, сетей и основного маршрута.",
                )
            }
        }
        item {
            CardBlock {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errors = emptyList()
                    },
                    label = { Text("Название группы") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Режим", fontWeight = FontWeight.SemiBold)
                ChipRow {
                    FilterChip(
                        selected = mode == ProfileGroupMode.Manual,
                        onClick = { modeName = ProfileGroupMode.Manual.name },
                        label = { Text("Ручной выбор") },
                    )
                    FilterChip(
                        selected = mode == ProfileGroupMode.Latency,
                        onClick = { modeName = ProfileGroupMode.Latency.name },
                        label = { Text("Минимальная задержка") },
                    )
                    FilterChip(
                        selected = mode == ProfileGroupMode.Failover,
                        onClick = { modeName = ProfileGroupMode.Failover.name },
                        label = { Text("Резерв по порядку") },
                    )
                    FilterChip(
                        selected = mode == ProfileGroupMode.RoundRobin,
                        onClick = { modeName = ProfileGroupMode.RoundRobin.name },
                        label = { Text("По кругу") },
                    )
                }
                Text(
                    when (mode) {
                        ProfileGroupMode.Manual ->
                            "ViRouteFS всегда использует выбранного участника. Автоматического перехода, в том числе на System, нет."
                        ProfileGroupMode.Latency ->
                            "Ядро периодически проверяет участников и выбирает доступный маршрут с меньшей задержкой."
                        ProfileGroupMode.Failover ->
                            "Используется первый доступный участник сверху вниз. System попадёт в резерв только при вашем явном выборе."
                        ProfileGroupMode.RoundRobin ->
                            "Каждое следующее новое соединение направляется к следующему доступному участнику по кругу."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            CardBlock {
                Text("Участники — минимум два", fontWeight = FontWeight.SemiBold)
                Text(
                    "Добавить можно только включённые и настроенные цели. Уже выбранный, но ставший недоступным профиль остаётся видимым, чтобы его можно было убрать. System используется только при явном выборе.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (availableProfiles.isEmpty()) {
                    WarningText("Сначала добавьте и включите хотя бы два рабочих профиля.")
                } else {
                    availableProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    memberIds = if (profile.id in memberIds) {
                                        memberIds - profile.id
                                    } else {
                                        memberIds + profile.id
                                    }
                                    if (selectedProfileId !in memberIds) {
                                        selectedProfileId = memberIds.firstOrNull()
                                    }
                                    errors = emptyList()
                                }
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(routeTargetName(config, profile.id), fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (profile.id == RoutingConfigDefaults.SYSTEM_PROFILE_ID) {
                                        "Обычный интернет телефона — явный fallback"
                                    } else if (!profile.enabled || !profile.hasRuntimeConfiguration()) {
                                        "Недоступен — уберите из группы или исправьте профиль"
                                    } else {
                                        profile.type.label
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = profile.id in memberIds,
                                onCheckedChange = null,
                            )
                        }
                    }
                }
            }
        }
        if (mode == ProfileGroupMode.Manual && memberIds.isNotEmpty()) {
            item {
                CardBlock {
                    Text("Активный участник", fontWeight = FontWeight.SemiBold)
                    ChipRow {
                        memberIds.mapNotNull { id -> config.profiles.firstOrNull { it.id == id } }
                            .forEach { profile ->
                                FilterChip(
                                    selected = selectedProfileId == profile.id,
                                    onClick = { selectedProfileId = profile.id },
                                    label = { Text(routeTargetName(config, profile.id), maxLines = 1) },
                                )
                            }
                    }
                }
            }
        }
        if (mode in setOf(ProfileGroupMode.Failover, ProfileGroupMode.RoundRobin) &&
            memberIds.size > 1
        ) {
            item {
                CardBlock {
                    Text("Порядок участников", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (mode == ProfileGroupMode.Failover) {
                            "Первый — основной, остальные — резервы сверху вниз."
                        } else {
                            "Новые соединения проходят по этому списку по кругу."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    memberIds.forEachIndexed { index, id ->
                        val profile = config.profiles.firstOrNull { it.id == id } ?: return@forEachIndexed
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${index + 1}. ${routeTargetName(config, profile.id)}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            OutlinedButton(
                                enabled = index > 0,
                                onClick = {
                                    memberIds = memberIds.toMutableList().apply {
                                        val value = removeAt(index)
                                        add(index - 1, value)
                                    }
                                },
                            ) { Text("↑") }
                            OutlinedButton(
                                enabled = index < memberIds.lastIndex,
                                onClick = {
                                    memberIds = memberIds.toMutableList().apply {
                                        val value = removeAt(index)
                                        add(index + 1, value)
                                    }
                                },
                            ) { Text("↓") }
                        }
                    }
                }
            }
        }
        if (mode != ProfileGroupMode.Manual) {
            item {
                CardBlock {
                    Text("Проверка доступности", fontWeight = FontWeight.SemiBold)
                    WarningText(
                        "Это явная фоновая проверка: указанный HTTPS-сервер увидит обычные запросы доступности от выбранных подключений. Другие данные приложения не отправляются.",
                    )
                    OutlinedTextField(
                        value = testUrl,
                        onValueChange = { testUrl = it },
                        label = { Text("HTTPS-адрес проверки") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = intervalText,
                            onValueChange = { intervalText = it.filter(Char::isDigit) },
                            label = { Text("Интервал, сек") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        if (mode == ProfileGroupMode.Latency) {
                            OutlinedTextField(
                                value = toleranceText,
                                onValueChange = { toleranceText = it.filter(Char::isDigit) },
                                label = { Text("Допуск, мс") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                            )
                        }
                    }
                }
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Группа включена", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Выключенная группа блокирует связанные правила без перехода на другой маршрут.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Использовать по умолчанию", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Трафик без отдельного правила пойдёт в эту группу.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Switch(checked = useAsDefault, onCheckedChange = { useAsDefault = it })
                }
            }
        }
        if (errors.isNotEmpty()) {
            item {
                CardBlock {
                    Text("Нужно исправить", fontWeight = FontWeight.SemiBold)
                    errors.forEach { WarningText(it) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val groups = if (group == null) {
                            config.profileGroups + draft
                        } else {
                            config.profileGroups.map { if (it.id == group.id) draft else it }
                        }
                        var next = config.copy(profileGroups = groups)
                        next = when {
                            useAsDefault -> next.withDefaultRoute(draft.id)
                            config.defaultProfileId == draft.id ->
                                next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
                            else -> next
                        }
                        val validationErrors = buildList {
                            if (useAsDefault && !enabled) {
                                add("Группа по умолчанию должна быть включена.")
                            }
                            addAll(validateRoutingConfig(next))
                        }.distinct()
                        errors = validationErrors
                        if (validationErrors.isEmpty()) {
                            onConfig(
                                next,
                                "Группа «${draft.name}» сохранена. System не добавлялся автоматически.",
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Сохранить")
                }
                if (group != null) {
                    OutlinedButton(
                        onClick = {
                            var next = config.copy(
                                profileGroups = config.profileGroups.filterNot { it.id == group.id },
                                rules = config.rules.map { rule ->
                                    if (rule.targetProfileId == group.id) {
                                        rule.copy(targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID)
                                    } else {
                                        rule
                                    }
                                },
                                dnsPolicies = config.dnsPolicies.map { policy ->
                                    if (policy.resolveThroughProfileId == group.id) {
                                        policy.copy(
                                            enabled = false,
                                            resolveThroughProfileId = null,
                                            description = "${policy.description} Disabled because route group was removed.",
                                        )
                                    } else {
                                        policy
                                    }
                                },
                            )
                            if (config.defaultProfileId == group.id) {
                                next = next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
                            }
                            onConfig(
                                next,
                                "Группа удалена. Связанные явные правила переведены в Block; основной маршрут возвращён на System.",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Удалить")
                    }
                }
            }
        }
    }
}

private enum class RouteMatcherKind { App, Domain, Ip, Network }

data class InstalledAppUi(
    val label: String,
    val packageName: String,
    val isSystem: Boolean,
)

@Suppress("DEPRECATION")
internal fun android.content.Context.loadInstalledAppsForRouting(): List<InstalledAppUi> {
    val applications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getInstalledApplications(
            PackageManager.ApplicationInfoFlags.of(0),
        )
    } else {
        packageManager.getInstalledApplications(0)
    }
    return applications
        .asSequence()
        .filterNot { it.packageName == packageName }
        .map { info ->
            InstalledAppUi(
                label = info.loadLabel(packageManager).toString(),
                packageName = info.packageName,
                isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            )
        }
        .distinctBy { it.packageName }
        .sortedWith(
            compareBy<InstalledAppUi> { it.isSystem }
                .thenBy { it.label.lowercase(Locale.ROOT) }
                .thenBy { it.packageName },
        )
        .toList()
}

@Composable
private fun RouteRuleCard(
    text: UiText,
    rule: RouteRule,
    profileName: String,
    warnings: List<Any>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onOpen: () -> Unit,
) {
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
                    StatusChip("Приоритет ${rule.priority}")
                    StatusChip(if (rule.enabled) text.on else text.off)
                    if (warnings.isNotEmpty()) StatusChip(text.warning)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onMoveUp, enabled = canMoveUp) { Text("Выше") }
                OutlinedButton(onClick = onMoveDown, enabled = canMoveDown) { Text("Ниже") }
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
    var matcherKind by rememberSaveable(rule.id) { mutableStateOf(rule.toMatcherKind()) }
    var targetProfileId by rememberSaveable(rule.id) { mutableStateOf(rule.targetProfileId) }
    var dnsPolicyId by rememberSaveable(rule.id) { mutableStateOf(rule.dnsPolicyId) }
    var selectedAppPackages by rememberSaveable(rule.id) {
        mutableStateOf(rule.appMatchers.map { it.value })
    }
    val existingDomainMatcher = parseDomainMatcher(rule.matchers.firstOrNull().orEmpty())
    var domainMatcherModeName by rememberSaveable(rule.id) {
        mutableStateOf(existingDomainMatcher.mode.name)
    }
    var matcherText by rememberSaveable(rule.id) {
        mutableStateOf(
            if (rule.type == RouteRuleType.DOMAIN) {
                existingDomainMatcher.value
            } else {
                rule.matchers.firstOrNull().orEmpty()
            },
        )
    }
    var transportName by rememberSaveable(rule.id) { mutableStateOf(rule.transport.name) }
    var destinationPortsText by rememberSaveable(rule.id) {
        mutableStateOf(rule.destinationPorts.toDisplayText())
    }
    var appSearch by rememberSaveable { mutableStateOf("") }
    var saveErrors by rememberSaveable(rule.id) { mutableStateOf<List<String>>(emptyList()) }
    val availableProfiles = config.profiles.filter { profile ->
        profile.type == TunnelType.Direct || profile.type == TunnelType.Block || profile.type == TunnelType.Socks5 || profile.type == TunnelType.VLESS || !profile.mockOnly
    }
    val availableGroups = config.profileGroups.filter { it.enabled }
    val targetProfile = config.profiles.firstOrNull { it.id == targetProfileId }
    val targetGroup = config.profileGroups.firstOrNull { it.id == targetProfileId }
    val context = LocalContext.current
    val historyStore = remember(context) { Socks5TestHistoryStore(context) }
    var selectedSocks5Readiness by remember(targetProfileId) { mutableStateOf<Socks5ReadinessSummary?>(null) }
    LaunchedEffect(targetProfileId, targetProfile?.type) {
        selectedSocks5Readiness = if (targetProfile?.type == TunnelType.Socks5) {
            deriveSocks5ReadinessSummary(historyStore.recentForProfile(targetProfile.id))
        } else {
            null
        }
    }
    val filteredApps = remember(installedApps, appSearch) {
        val query = appSearch.trim().lowercase(Locale.ROOT)
        installedApps.filter { app ->
            query.isBlank() || app.label.lowercase(Locale.ROOT).contains(query) || app.packageName.lowercase(Locale.ROOT).contains(query)
        }
    }
    val appsByPackage = remember(installedApps) { installedApps.associateBy { it.packageName } }
    val parsedDestinationPorts = remember(destinationPortsText) {
        runCatching { parseDestinationPortRanges(destinationPortsText) }
    }
    val domainMatcherMode = DomainMatcherMode.entries
        .firstOrNull { it.name == domainMatcherModeName }
        ?: DomainMatcherMode.Suffix
    val draft = remember(
        name,
        enabled,
        matcherKind,
        targetProfileId,
        dnsPolicyId,
        selectedAppPackages,
        matcherText,
        domainMatcherMode,
        transportName,
        parsedDestinationPorts,
        appsByPackage,
        rule,
    ) {
        rule.copy(
            name = name.trim().ifBlank { rule.name },
            enabled = enabled,
            type = matcherKind.toRuleType(),
            targetProfileId = targetProfileId,
            dnsPolicyId = dnsPolicyId,
            matchers = when (matcherKind) {
                RouteMatcherKind.App -> emptyList()
                RouteMatcherKind.Domain ->
                    listOf(encodeDomainMatcher(domainMatcherMode, matcherText)).filter {
                        parseDomainMatcher(it).value.isNotBlank()
                    }
                RouteMatcherKind.Ip,
                RouteMatcherKind.Network -> listOf(matcherText.trim()).filter { it.isNotBlank() }
            },
            appMatchers = if (matcherKind == RouteMatcherKind.App) {
                selectedAppPackages.distinct().map { packageName ->
                    AppMatcher(
                        AppMatcherPlatform.Android,
                        packageName,
                        appsByPackage[packageName]?.label ?: packageName,
                    )
                }
            } else {
                emptyList()
            },
            reason = routeReason(matcherKind),
            technicalDetails = routeTechnicalDetails(matcherKind),
            recommendedAction = routeRecommendedAction(targetProfileId),
            transport = RouteTransport.entries.firstOrNull { it.name == transportName } ?: RouteTransport.Any,
            destinationPorts = parsedDestinationPorts.getOrDefault(emptyList()),
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
                    FilterChip(selected = matcherKind == RouteMatcherKind.Ip, onClick = { matcherKind = RouteMatcherKind.Ip }, label = { Text("IP-адрес") })
                    FilterChip(selected = matcherKind == RouteMatcherKind.Network, onClick = { matcherKind = RouteMatcherKind.Network }, label = { Text("Диапазон сети") })
                }
                RouteMatcherEditor(
                    text = text,
                    kind = matcherKind,
                    installedApps = filteredApps,
                    selectedAppPackages = selectedAppPackages,
                    appSearch = appSearch,
                    matcherText = matcherText,
                    domainMatcherMode = domainMatcherMode,
                    onAppSearch = { appSearch = it },
                    onToggleAppPackage = { packageName ->
                        selectedAppPackages = if (packageName in selectedAppPackages) {
                            selectedAppPackages - packageName
                        } else {
                            selectedAppPackages + packageName
                        }
                    },
                    onMatcherText = { matcherText = it },
                    onDomainMatcherMode = {
                        domainMatcherModeName = it.name
                        saveErrors = emptyList()
                    },
                )
                Text("Транспорт и порты назначения", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(
                    "Необязательно. Оставьте «Любой» и пустое поле, чтобы правило работало для всего трафика выбранного приложения, домена или сети.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow {
                    RouteTransport.entries.forEach { transport ->
                        FilterChip(
                            selected = transportName == transport.name,
                            onClick = { transportName = transport.name },
                            label = {
                                Text(
                                    when (transport) {
                                        RouteTransport.Any -> "Любой"
                                        RouteTransport.Tcp -> "TCP"
                                        RouteTransport.Udp -> "UDP"
                                    },
                                )
                            },
                        )
                    }
                }
                OutlinedTextField(
                    value = destinationPortsText,
                    onValueChange = {
                        destinationPortsText = it
                        saveErrors = emptyList()
                    },
                    label = { Text("Порт или диапазон портов") },
                    placeholder = { Text("443, 8000-8100") },
                    supportingText = { Text("Несколько значений разделяйте запятой или пробелом.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = parsedDestinationPorts.isFailure,
                )
                parsedDestinationPorts.exceptionOrNull()?.message?.let { WarningText(it) }
                Text(text.targetProfile, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                ChipRow {
                    availableProfiles.forEach { profile ->
                        FilterChip(
                            selected = targetProfileId == profile.id,
                            onClick = { targetProfileId = profile.id },
                            label = { Text(routeTargetName(config, profile.id), maxLines = 1) },
                        )
                    }
                    availableGroups.forEach { group ->
                        FilterChip(
                            selected = targetProfileId == group.id,
                            onClick = { targetProfileId = group.id },
                            label = { Text("Группа: ${group.name}", maxLines = 1) },
                        )
                    }
                }
                if (availableProfiles.isEmpty() && availableGroups.isEmpty()) StatusChip(text.systemBlockOnly)
                targetWarning.forEach { StatusChip(it) }
                Text("DNS для этого правила", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(
                    "Для приложения или домена можно выбрать отдельный DNS. Запросы к нему пойдут через профиль, указанный в DNS-политике.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChipRow {
                    config.dnsPolicies.filter { it.enabled }.forEach { policy ->
                        FilterChip(
                            selected = dnsPolicyId == policy.id,
                            onClick = { dnsPolicyId = policy.id },
                            label = { Text(policy.name, maxLines = 1) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text(
                    "${text.targetProfile}: ${targetProfile?.name ?: targetGroup?.name ?: targetProfileId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (targetProfile?.type == TunnelType.Socks5) {
                    Text(
                        selectedSocks5Readiness?.routeExplanationLine ?: "SOCKS5 ещё не проверен.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Details(
                    text.advanced,
                    "${text.routeIsolationNote}\n\n${rule.reason}\n${rule.technicalDetails}\n${rule.recommendedAction}",
                )
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
                    val matcherShapeErrors = when (matcherKind) {
                        RouteMatcherKind.Ip -> if (matcherText.isNotBlank() && '/' in matcherText) {
                            listOf("Для одиночного IP не указывайте маску сети. Пример: 192.0.2.10")
                        } else {
                            emptyList()
                        }
                        RouteMatcherKind.Network -> if (matcherText.isNotBlank() && '/' !in matcherText) {
                            listOf("Для диапазона сети укажите маску CIDR. Пример: 192.168.50.0/24")
                        } else {
                            emptyList()
                        }
                        else -> emptyList()
                    }
                    val portErrors = parsedDestinationPorts.exceptionOrNull()?.message?.let(::listOf).orEmpty()
                    val errors = validateRouteEditorDraft(draft, config.rules) + matcherShapeErrors + portErrors
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
    selectedAppPackages: List<String>,
    appSearch: String,
    matcherText: String,
    domainMatcherMode: DomainMatcherMode,
    onAppSearch: (String) -> Unit,
    onToggleAppPackage: (String) -> Unit,
    onMatcherText: (String) -> Unit,
    onDomainMatcherMode: (DomainMatcherMode) -> Unit,
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
            Text(
                "Выбрано: ${selectedAppPackages.size}. Список читается только на устройстве и никуда не отправляется.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (installedApps.isEmpty()) {
                Text(text.noInstalledApps, style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(installedApps, key = { it.packageName }) { app ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onToggleAppPackage(app.packageName) },
                            colors = CardDefaults.cardColors(
                                containerColor = if (app.packageName in selectedAppPackages) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
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
                                    modifier = Modifier.size(44.dp),
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(app.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (app.isSystem) StatusChip("Системное")
                                if (app.packageName in selectedAppPackages) StatusChip("Выбрано")
                            }
                        }
                    }
                }
            }
        }
        RouteMatcherKind.Domain -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Как сравнивать домен", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
            ChipRow {
                DomainMatcherMode.entries.forEach { mode ->
                    FilterChip(
                        selected = domainMatcherMode == mode,
                        onClick = { onDomainMatcherMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    DomainMatcherMode.Exact -> "Точный"
                                    DomainMatcherMode.Suffix -> "Домен и поддомены"
                                    DomainMatcherMode.Keyword -> "Содержит"
                                    DomainMatcherMode.Regex -> "Регулярное выражение"
                                },
                            )
                        },
                    )
                }
            }
            Text(
                when (domainMatcherMode) {
                    DomainMatcherMode.Exact -> "Совпадёт только example.org, но не sub.example.org."
                    DomainMatcherMode.Suffix -> "Совпадут example.org и все его поддомены."
                    DomainMatcherMode.Keyword -> "Совпадёт домен, в имени которого встречается указанная часть."
                    DomainMatcherMode.Regex -> "Экспертный режим: шаблон применяется ко всему имени домена."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val validationError = validateDomainMatcher(domainMatcherMode, matcherText)
            OutlinedTextField(
                value = matcherText,
                onValueChange = onMatcherText,
                label = {
                    Text(
                        if (domainMatcherMode == DomainMatcherMode.Regex) {
                            "Регулярное выражение"
                        } else {
                            text.domainHostInput
                        },
                    )
                },
                placeholder = {
                    Text(
                        when (domainMatcherMode) {
                            DomainMatcherMode.Regex -> "(^|\\.)example\\.org$"
                            DomainMatcherMode.Keyword -> "example"
                            else -> "example.org"
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = validationError != null,
            )
            validationError?.let { WarningText(it) }
        }
        RouteMatcherKind.Ip -> OutlinedTextField(
            value = matcherText,
            onValueChange = onMatcherText,
            label = { Text("IPv4 или IPv6 устройства/сервера") },
            placeholder = { Text("192.0.2.10 или 2001:db8::10") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = matcherText.isNotBlank() && (!isValidIpOrCidr(matcherText) || '/' in matcherText),
        )
        RouteMatcherKind.Network -> OutlinedTextField(
            value = matcherText,
            onValueChange = onMatcherText,
            label = { Text("IPv4/IPv6-сеть в формате CIDR") },
            placeholder = { Text("192.168.50.0/24 или 2001:db8::/32") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = matcherText.isNotBlank() && (!isValidIpOrCidr(matcherText) || '/' !in matcherText),
        )
    }
}

private fun newRouteDraft(config: RoutingConfig): RouteRule = RouteRule(
    id = "route_${UUID.randomUUID()}",
    name = "Новое правило",
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

private fun RouteRule.toMatcherKind(): RouteMatcherKind = when (type) {
    RouteRuleType.APP, RouteRuleType.APP_GROUP -> RouteMatcherKind.App
    RouteRuleType.DOMAIN -> RouteMatcherKind.Domain
    RouteRuleType.CIDR -> if (matchers.firstOrNull()?.contains('/') == true) {
        RouteMatcherKind.Network
    } else {
        RouteMatcherKind.Ip
    }
    RouteRuleType.DEFAULT -> RouteMatcherKind.App
}

private fun RouteMatcherKind.toRuleType(): RouteRuleType = when (this) {
    RouteMatcherKind.App -> RouteRuleType.APP
    RouteMatcherKind.Domain -> RouteRuleType.DOMAIN
    RouteMatcherKind.Ip,
    RouteMatcherKind.Network -> RouteRuleType.CIDR
}

private fun RouteMatcherKind.label(text: UiText): String = when (this) {
    RouteMatcherKind.App -> text.matcherApp
    RouteMatcherKind.Domain -> text.matcherDomain
    RouteMatcherKind.Ip -> "IP-адрес"
    RouteMatcherKind.Network -> "Диапазон сети"
}

private fun matcherSummary(rule: RouteRule): String {
    val matcher = when (rule.type) {
        RouteRuleType.APP, RouteRuleType.APP_GROUP -> rule.appMatchers.joinToString(" • ") { app ->
            app.displayName?.let { "$it (${app.value})" } ?: app.value
        }.ifBlank { "App matcher not selected" }
        RouteRuleType.DOMAIN -> rule.matchers.joinToString(" • ") { raw ->
            val parsed = parseDomainMatcher(raw)
            val mode = when (parsed.mode) {
                DomainMatcherMode.Exact -> "Точный домен"
                DomainMatcherMode.Suffix -> "Домен и поддомены"
                DomainMatcherMode.Keyword -> "Содержит"
                DomainMatcherMode.Regex -> "Регулярное выражение"
            }
            "$mode: ${parsed.value}"
        }.ifBlank { "Domain / host not set" }
        RouteRuleType.CIDR -> rule.matchers.joinToString(" • ").ifBlank { "IP / CIDR not set" }
        RouteRuleType.DEFAULT -> "Default System route"
    }
    val constraints = buildList {
        if (rule.transport != RouteTransport.Any) add(rule.transport.name.uppercase(Locale.ROOT))
        rule.destinationPorts.toDisplayText().takeIf(String::isNotBlank)?.let { add("порт $it") }
    }
    return if (constraints.isEmpty()) matcher else "$matcher • ${constraints.joinToString(" • ")}"
}

private fun routeTargetName(config: RoutingConfig, profileId: String): String = when (profileId) {
    RoutingConfigDefaults.SYSTEM_PROFILE_ID -> "System / Система"
    RoutingConfigDefaults.BLOCK_PROFILE_ID -> "Block / Блокировать"
    else -> config.profiles.firstOrNull { it.id == profileId }?.name
        ?: config.profileGroups.firstOrNull { it.id == profileId }?.let { "Группа: ${it.name}" }
        ?: profileId
}

private fun ProfileGroup.isRunnable(config: RoutingConfig): Boolean {
    val memberIds = memberProfileIds.distinct()
    if (memberIds.size < 2) return false
    val availableIds = memberIds.filter { memberId ->
        config.profiles.firstOrNull { it.id == memberId }
            ?.let { it.enabled && it.hasRuntimeConfiguration() } == true
    }
    return when (mode) {
        ProfileGroupMode.Manual ->
            selectedProfileId in availableIds
        ProfileGroupMode.Latency -> availableIds.size >= 2
        ProfileGroupMode.Failover,
        ProfileGroupMode.RoundRobin -> availableIds.isNotEmpty()
    }
}

private fun ProfileGroupMode.displayName(): String = when (this) {
    ProfileGroupMode.Manual -> "Ручной выбор"
    ProfileGroupMode.Latency -> "Минимальная задержка"
    ProfileGroupMode.Failover -> "Резерв по порядку"
    ProfileGroupMode.RoundRobin -> "По кругу"
}

private fun unavailableTargetWarning(config: RoutingConfig, rule: RouteRule): List<String> {
    val profile = config.profiles.firstOrNull { it.id == rule.targetProfileId }
    val group = config.profileGroups.firstOrNull { it.id == rule.targetProfileId }
    return when {
        group != null && !group.enabled -> listOf("Группа выключена: трафик будет заблокирован")
        group != null && group.memberProfileIds.distinct().size < 2 ->
            listOf("В группе меньше двух участников: трафик будет заблокирован")
        group != null && !group.isRunnable(config) ->
            listOf("Группа настроена не полностью: трафик будет заблокирован")
        group != null -> emptyList()
        profile == null -> listOf("Target unavailable: fail closed")
        !profile.enabled -> listOf("Target disabled: fail closed")
        profile.type == TunnelType.Socks5 -> emptyList()
        profile.type == TunnelType.VLESS ||
            profile.type == TunnelType.XrayVlessReality -> emptyList()
        profile.singBox != null -> emptyList()
        profile.mockOnly -> listOf("Target is mock-only")
        else -> emptyList()
    }
}

private fun routeReason(kind: RouteMatcherKind): String = when (kind) {
    RouteMatcherKind.App -> "Explicit app route selected by the local route editor."
    RouteMatcherKind.Domain -> "Explicit domain / host route selected by the local route editor."
    RouteMatcherKind.Ip -> "Explicit IP address route selected by the local route editor."
    RouteMatcherKind.Network -> "Explicit CIDR network route selected by the local route editor."
}

private fun routeTechnicalDetails(kind: RouteMatcherKind): String = "Matcher type: ${kind.name}. Exact duplicate conflicts are validated locally before save. Active VPN rules are enforced by the fail-closed local runtime."

private fun routeRecommendedAction(targetProfileId: String): String = if (targetProfileId == RoutingConfigDefaults.BLOCK_PROFILE_ID) {
    "Traffic matching this rule should be blocked when runtime enforcement is implemented."
} else {
    "Keep the target profile available; explicit rules are fail-closed and must not silently fall back."
}

@Composable
private fun ToolsScreen(padding: PaddingValues, text: UiText, config: RoutingConfig) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyStore = remember(context) { Socks5TestHistoryStore(context) }
    var readinessByProfile by remember(config.profiles) { mutableStateOf<Map<String, Socks5ReadinessSummary>>(emptyMap()) }
    LaunchedEffect(config.profiles) {
        readinessByProfile = config.profiles
            .filter { it.type == TunnelType.Socks5 }
            .associate { it.id to deriveSocks5ReadinessSummary(historyStore.recentForProfile(it.id)) }
    }
    var host by rememberSaveable { mutableStateOf("example.com") }
    var port by rememberSaveable { mutableStateOf("443") }
    var sni by rememberSaveable { mutableStateOf("example.com") }
    var url by rememberSaveable { mutableStateOf("https://example.com") }
    var routeTarget by rememberSaveable { mutableStateOf("youtube.com") }
    var tcp by remember { mutableStateOf<DiagnosticResult?>(null) }
    var tls by remember { mutableStateOf<DiagnosticResult?>(null) }
    var http by remember { mutableStateOf<DiagnosticResult?>(null) }
    ScreenList(padding) {
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
                if (d.tunnelProfile.type == TunnelType.Socks5) {
                    Text("Selected profile: SOCKS5. The local VPN runtime can route matching traffic through it.")
                    Text(readinessByProfile[d.tunnelProfile.id]?.routeExplanationLine ?: "SOCKS5 profile has not been tested yet.", style = MaterialTheme.typography.bodySmall)
                }
                if (d.tunnelProfile.type == TunnelType.VLESS ||
                    d.tunnelProfile.type == TunnelType.XrayVlessReality
                ) {
                    Text(VLESS_RUNTIME_LIMITATION, style = MaterialTheme.typography.bodySmall)
                    Text(VLESS_ROUTE_PREVIEW_ONLY, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { CompactCard(text, "MTU", text.mtuShort, text.mtuDetails) }
    }
}

@Composable
private fun MoreScreen(padding: PaddingValues, text: UiText, onOpen: (AppScreen) -> Unit) = ScreenList(padding) {
    item { MoreEntry(text.dns, text.dnsSubtitle, Icons.Outlined.Dns) { onOpen(AppScreen.Dns) } }
    item { MoreEntry(text.tools, text.toolsSubtitle, Icons.Outlined.Build) { onOpen(AppScreen.Tools) } }
    item { MoreEntry(text.settings, text.settingsSubtitle, Icons.Outlined.Settings) { onOpen(AppScreen.Settings) } }
}

@Composable
private fun MoreEntry(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) = CardBlock {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    val scope = rememberCoroutineScope()
    var updateResult by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var updateDownloadState by remember { mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle) }
    var releaseHistory by remember { mutableStateOf<List<ReleaseInfo>>(emptyList()) }
    var updateChecking by remember { mutableStateOf(false) }
    val apkDownloader = remember(context) { UpdateApkDownloader(context.applicationContext) }
    var supportExpanded by rememberSaveable { mutableStateOf(false) }
    var helpExpanded by rememberSaveable { mutableStateOf(false) }
    var beginnerExpanded by rememberSaveable { mutableStateOf(false) }
    var adminExpanded by rememberSaveable { mutableStateOf(false) }
    var developerExpanded by rememberSaveable { mutableStateOf(false) }
    val donationUrl = remember {
        BuildConfig.DONATION_URL.trim().takeIf { url ->
            url.startsWith("https://", ignoreCase = true)
        }
    }
    ScreenList(padding) {
        item {
            CardBlock {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { helpExpanded = !helpExpanded },
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text.help, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        Text(text.helpShort, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { helpExpanded = !helpExpanded }) {
                        Text(if (helpExpanded) text.hideHelp else text.showHelp)
                    }
                }
                if (helpExpanded) {
                    CompactCard(text, text.aboutViroutefs, text.projectOverviewShort, text.projectOverviewDetails)
                    CompactCard(text, text.projectPurposeTitle, text.projectPurposeShort, text.projectPurposeDetails)
                    CompactCard(text, text.licenseSummaryTitle, text.licenseSummaryShort, text.licenseSummaryDetails)
                    CompactCard(text, text.privacy, text.privacyShort, text.privacyDetails)
                    CompactCard(text, text.currentBetaLimitations, text.betaLimitationsShort, text.betaLimitationsDetails)
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
        item {
            UpdateSettingsCard(
                text = text,
                result = updateResult,
                checking = updateChecking,
                downloadState = updateDownloadState,
                releaseHistory = releaseHistory,
                canRequestPackageInstalls = apkDownloader.canRequestPackageInstalls(),
                onCheck = {
                    updateResult = null
                    updateDownloadState = UpdateDownloadState.Checking
                    updateChecking = true
                    scope.launch {
                        try {
                            val checked = UpdateChecker().check(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
                            updateResult = checked
                            releaseHistory = when (checked) {
                                is UpdateCheckResult.NewerRelease -> checked.releases
                                is UpdateCheckResult.UpToDate -> checked.releases
                                else -> emptyList()
                            }
                            updateDownloadState = when (checked) {
                                is UpdateCheckResult.NewerRelease -> UpdateDownloadState.UpdateAvailable(checked.release)
                                is UpdateCheckResult.UpToDate -> UpdateDownloadState.UpToDate
                                UpdateCheckResult.NoReleaseFound -> UpdateDownloadState.NoRelease
                                is UpdateCheckResult.Error -> UpdateDownloadState.Idle
                            }
                        } finally {
                            updateChecking = false
                        }
                    }
                },
                onDownloadApk = { release ->
                    scope.launch {
                        updateDownloadState = UpdateDownloadState.Downloading(release, DownloadProgress(0L, release.apkAsset?.sizeBytes))
                        updateDownloadState = apkDownloader.download(release) { progress ->
                            updateDownloadState = UpdateDownloadState.Downloading(release, progress)
                        }
                    }
                },
                onInstallApk = { file ->
                    runCatching { context.startActivity(apkDownloader.installIntent(file)) }
                        .onFailure { updateDownloadState = UpdateDownloadState.DownloadFailed((updateDownloadState as? UpdateDownloadState.ReadyToInstall)?.release, it.message ?: it::class.java.simpleName) }
                },
                onDeleteApk = { file ->
                    apkDownloader.delete(file)
                    updateDownloadState = UpdateDownloadState.Idle
                },
                onOpenInstallPermissionSettings = {
                    context.startActivity(apkDownloader.unknownAppSourcesIntent())
                },
                onOpenReleases = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_WEB_URL)))
                },
            )
        }
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
                            Text("Режим разработчика", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Показывает тесты движка и низкоуровневый VLESS-мост. Для обычного использования они не нужны.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = settings.developerMode,
                            onCheckedChange = { onSettings(settings.copy(developerMode = it)) },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(text.vpnTestRoute, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                            Text(text.vpnNormalInternetUnchanged, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = tunTestRoutePreviewEnabled, onCheckedChange = onTunTestRoutePreview)
                    }
                    Details(
                        text.details,
                        "${text.vpnPacketsRead}: ${vpnState.packetsRead}\n" +
                            "${text.vpnBytesRead}: ${vpnState.bytesRead}\n" +
                            "${text.vpnIpv4PacketsRead}: ${vpnState.ipv4PacketsRead}\n" +
                            "${text.vpnTcpPacketsRead}: ${vpnState.tcpPacketsRead}\n" +
                            "${text.vpnUdpPacketsRead}: ${vpnState.udpPacketsRead}\n" +
                            "${text.vpnIcmpPacketsRead}: ${vpnState.icmpPacketsRead}\n" +
                            text.vpnHowToTestTun,
                    )
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
                    Text(text.supportDonationDisclaimer, style = MaterialTheme.typography.bodySmall)
                    val links = buildList {
                        donationUrl?.let { add(text.voluntarySupport to it) }
                        add("GitHub" to "https://github.com/Vifsvifsvifs/viroutefs")
                    }
                    links.forEach { (label, url) -> OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) { Text(label) } }
                    if (donationUrl == null) {
                        Text(text.supportLinkNotConfigured, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateSettingsCard(
    text: UiText,
    result: UpdateCheckResult?,
    checking: Boolean,
    downloadState: UpdateDownloadState,
    releaseHistory: List<ReleaseInfo>,
    canRequestPackageInstalls: Boolean,
    onCheck: () -> Unit,
    onDownloadApk: (ReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onDeleteApk: (File) -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onOpenReleases: () -> Unit,
) {
    val context = LocalContext.current
    CardBlock {
        Text(text.updates, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    Text(text.currentVersion(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodySmall)
    Text(text.updateChannelBeta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onCheck) { Text(text.checkForUpdates) }
        OutlinedButton(onClick = onOpenReleases) { Text(text.openGithubReleases) }
    }
    UpdateResultView(text, result, checking, downloadState, canRequestPackageInstalls, onDownloadApk, onInstallApk, onDeleteApk, onOpenInstallPermissionSettings)
        ReleaseHistoryView(text, releaseHistory, downloadState, onDownloadApk, onInstallApk, onDeleteApk, onOpenRelease = { release ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl)))
        })
    }
}

@Composable
private fun UpdateResultView(
    text: UiText,
    result: UpdateCheckResult?,
    checking: Boolean,
    downloadState: UpdateDownloadState,
    canRequestPackageInstalls: Boolean,
    onDownloadApk: (ReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onDeleteApk: (File) -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
) {
    when {
        checking -> Text(text.updateChecking, style = MaterialTheme.typography.bodySmall)
        result == null -> Text(text.updateManualOnly, style = MaterialTheme.typography.bodySmall)
        result is UpdateCheckResult.Error -> WarningText(text.updateError(result.message))
        result is UpdateCheckResult.NewerRelease -> ReleaseResult(text, result.release, text.updateAvailable(result.release.displayVersion), downloadStateForRelease(downloadState, result.release), canRequestPackageInstalls, onDownloadApk, onInstallApk, onDeleteApk, onOpenInstallPermissionSettings)
        result == UpdateCheckResult.NoReleaseFound -> Text(text.noReleaseFound, style = MaterialTheme.typography.bodySmall)
        result is UpdateCheckResult.UpToDate -> {
            Text(text.upToDate, style = MaterialTheme.typography.bodySmall)
            result.latest?.let { latest ->
                ReleaseResult(text, latest, text.latestRelease, downloadStateForRelease(downloadState, latest), canRequestPackageInstalls, onDownloadApk, onInstallApk, onDeleteApk, onOpenInstallPermissionSettings)
            }
        }
    }
}

@Composable
private fun ReleaseResult(
    text: UiText,
    release: ReleaseInfo,
    title: String,
    downloadState: UpdateDownloadState,
    canRequestPackageInstalls: Boolean,
    onDownloadApk: (ReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onDeleteApk: (File) -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
) {
    val context = LocalContext.current
    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    Text(release.name, style = MaterialTheme.typography.bodySmall)
    Text(text.releaseVersionName(release.versionName), style = MaterialTheme.typography.bodySmall)
    release.versionCode?.let { Text("versionCode $it", style = MaterialTheme.typography.bodySmall) }
    release.publishedAt?.let { Text(text.publishedAt(it), style = MaterialTheme.typography.bodySmall) }
    if (release.prerelease) Text(text.prerelease, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val apkAsset = release.apkAsset
    if (apkAsset != null) {
        Text(text.apkAssetName(apkAsset.name), style = MaterialTheme.typography.bodySmall)
        apkAsset.sizeBytes?.let { Text(text.apkAssetSize(formatBytes(it)), style = MaterialTheme.typography.bodySmall) }
    } else {
        WarningText(text.apkAssetNotFound)
    }
    Text(text.unknownAppsHelp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!canRequestPackageInstalls) {
        OutlinedButton(onClick = onOpenInstallPermissionSettings) { Text(text.openInstallPermissionSettings) }
    }
    when (downloadState) {
        is UpdateDownloadState.Downloading -> Text(text.downloadProgress(downloadState.progress.bytesDownloaded, downloadState.progress.totalBytes, downloadState.progress.percent), style = MaterialTheme.typography.bodySmall)
        is UpdateDownloadState.ReadyToInstall -> {
            Text(text.apkDownloaded(formatBytes(downloadState.file.length())), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { onInstallApk(downloadState.file) }) { Text(text.installUpdate) }
                OutlinedButton(onClick = { onDeleteApk(downloadState.file) }) { Text(text.deleteDownloadedApk) }
            }
        }
        is UpdateDownloadState.DownloadFailed -> WarningText(text.downloadFailed(downloadState.message))
        else -> Unit
    }
    release.notes?.let { Details(text.details, it) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (apkAsset != null && downloadState !is UpdateDownloadState.Downloading && downloadState !is UpdateDownloadState.ReadyToInstall) {
            Button(onClick = { onDownloadApk(release) }) { Text(text.downloadApk) }
        }
        OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))) }) {
            Text(text.openReleasePage)
        }
    }
}

private fun downloadStateForRelease(state: UpdateDownloadState, release: ReleaseInfo): UpdateDownloadState = when (state) {
    is UpdateDownloadState.UpdateAvailable -> if (state.release.sameReleaseAs(release)) state else UpdateDownloadState.Idle
    is UpdateDownloadState.Downloading -> if (state.release.sameReleaseAs(release)) state else UpdateDownloadState.Idle
    is UpdateDownloadState.ReadyToInstall -> if (state.release.sameReleaseAs(release)) state else UpdateDownloadState.Idle
    is UpdateDownloadState.DownloadFailed -> if (state.release?.sameReleaseAs(release) != false) state else UpdateDownloadState.Idle
    else -> state
}

private fun ReleaseInfo.sameReleaseAs(other: ReleaseInfo): Boolean = htmlUrl == other.htmlUrl || displayVersion == other.displayVersion

@Composable
private fun ReleaseHistoryView(
    text: UiText,
    releases: List<ReleaseInfo>,
    downloadState: UpdateDownloadState,
    onDownloadApk: (ReleaseInfo) -> Unit,
    onInstallApk: (File) -> Unit,
    onDeleteApk: (File) -> Unit,
    onOpenRelease: (ReleaseInfo) -> Unit,
) {
    Text(text.recentReleases, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    if (releases.isEmpty()) {
        Text(text.releaseHistoryManualOnly, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    releases.take(8).forEach { release ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text(release.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(release.displayVersion, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                release.publishedAt?.let { Text(text.publishedAt(it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (release.prerelease) Text(text.prerelease, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            release.notes?.let { Text(it, maxLines = 3, style = MaterialTheme.typography.bodySmall) }
            val releaseDownloadState = downloadStateForRelease(downloadState, release)
            when (releaseDownloadState) {
                is UpdateDownloadState.Downloading -> Text(text.downloadProgress(releaseDownloadState.progress.bytesDownloaded, releaseDownloadState.progress.totalBytes, releaseDownloadState.progress.percent), style = MaterialTheme.typography.bodySmall)
                is UpdateDownloadState.ReadyToInstall -> Text(text.apkDownloaded(formatBytes(releaseDownloadState.file.length())), style = MaterialTheme.typography.bodySmall)
                is UpdateDownloadState.DownloadFailed -> WarningText(text.downloadFailed(releaseDownloadState.message))
                else -> Unit
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onOpenRelease(release) }) { Text(text.openReleasePage) }
                if (releaseDownloadState is UpdateDownloadState.ReadyToInstall) {
                    TextButton(onClick = { onInstallApk(releaseDownloadState.file) }) { Text(text.installUpdate) }
                    TextButton(onClick = { onDeleteApk(releaseDownloadState.file) }) { Text(text.deleteDownloadedApk) }
                } else if (release.apkAsset != null && releaseDownloadState !is UpdateDownloadState.Downloading) {
                    TextButton(onClick = { onDownloadApk(release) }) { Text(text.downloadApk) }
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
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun ScreenList(padding: PaddingValues, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, top = padding.calculateTopPadding() + 12.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    content = content,
)

@Composable
internal fun StatusChip(label: String) = Surface(
    shape = RoundedCornerShape(999.dp),
    color = MaterialTheme.colorScheme.primaryContainer,
) {
    Text(
        label,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
    )
}

@Composable
internal fun WarningText(value: String) = Text(value, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

@Composable
internal fun Details(label: String, text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { Text(if (open) "− $label" else "+ $label") }
    if (open) Text(text, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) = Row(
    modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    content()
}


@Suppress("TooManyFunctions")
internal class UiText(private val language: AppLanguage) {
    val dashboard = t("Главная", "Home", "主页")
    val networks = t("Контроль", "Control", "控制")
    val vpn = networks
    val routes = t("Маршруты", "Routes", "路由")
    val dns = "DNS"
    val fs = t("Сканер", "Scanner", "扫描器")
    val tools = t("Инструменты", "Tools", "工具")
    val settings = t("Настройки", "Settings", "设置")
    val more = t("Ещё", "More", "更多")
    val dashboardSubtitle = t("Короткий статус приложения и приватности.", "Short app and privacy status.", "应用和隐私状态概览。")
    val networksSubtitle = t("Профили сети, маршруты и безопасный локальный контроль.", "Network profiles, routes, and safe local control.", "网络配置、路由和安全本地控制。")
    val vpnSubtitle = networksSubtitle
    val routesSubtitle = t("Какой тоннель используется. Нажмите маршрут для деталей.", "Which tunnel is used. Tap a route for details.", "查看使用哪个隧道。点按路由查看详情。")
    val dnsSubtitle = t("Проверка DNS и локальные политики.", "DNS checks and local policies.", "DNS 检查和本地策略。")
    val fsSubtitle = t("Живые соединения по приложениям, адресам и маршрутам.", "Live connections by app, address, and route.", "按应用、地址和路由显示实时连接。")
    val toolsSubtitle = t("TCP, TLS, HTTP и маршрутная диагностика.", "TCP, TLS, HTTP, and route diagnostics.", "TCP、TLS、HTTP 和路由诊断。")
    val settingsSubtitle = t("Язык, тема, справка и расширенная диагностика.", "Language, theme, help, and advanced diagnostics.", "语言、主题、帮助和高级诊断。")
    val moreSubtitle = t("Инструменты и настройки без перегруза навигации.", "Tools and settings without crowded navigation.", "工具和设置不再挤占导航栏。")
    val version = t("Версия", "Version", "版本")
    val privacy = t("Приватность", "Privacy", "隐私")
    val privacyShort = t("Локально: без рекламы, аналитики и скрытой отправки.", "Local-first: no ads, analytics, or hidden uploads.", "本地优先：无广告、分析或隐藏上传。")
    val privacyDetails = t("Метаданные соединений, настройки и список установленных приложений обрабатываются только на устройстве. Список нужен для выбора приложения в маршрутах и Flow Scanner, никуда не загружается и не продаётся. Камера QR-импорта работает только на открытом экране сканирования: кадры не сохраняются и не отправляются. Полный URL подписки шифруется Android Keystore; загрузка выполняется только вручную обычным HTTPS-запросом без данных о приложениях и маршрутах. Содержимое пакетов не записывается; экспорт возможен только по явному действию пользователя.", "Connection metadata, settings, and the installed-app list are processed only on the device. The list is used for the route picker and Flow Scanner, and is never uploaded or sold. The QR import camera runs only while its scanner screen is open; frames are neither stored nor uploaded. Complete subscription URLs are encrypted with Android Keystore and fetched only by a manual ordinary HTTPS request without app or route data. Packet contents are not recorded; export requires an explicit user action.", "连接元数据、设置和已安装应用列表仅在设备上处理。该列表只用于路由选择器和 Flow Scanner，绝不会上传或出售。QR 导入相机仅在扫描界面打开时运行；画面不会保存或上传。完整订阅 URL 由 Android Keystore 加密，只能手动通过普通 HTTPS 请求获取，且不包含应用或路由数据。不会记录数据包内容；导出需要用户明确操作。")
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
    val vpnLocalPreviewTitle = t("Активировать контроль сети", "Activate network control", "激活网络控制")
    val activateNetworkControl = vpnLocalPreviewTitle
    val vpnNoTrafficRoutingYet = t("При включении Android направляет IPv4/IPv6 и DNS в локальный маршрутизатор; недоступные цели блокируются.", "When enabled, Android sends IPv4/IPv6 and DNS through the local router; unavailable targets are blocked.", "启用后，Android 会将 IPv4/IPv6 和 DNS 交给本地路由器；不可用目标会被阻止。")
    val networkControlSummary = vpnNoTrafficRoutingYet
    val vpnNoHiddenInterception = t("Нет скрытого перехвата", "No hidden interception", "没有隐藏拦截")
    val vpnTestRoutePreview = t("Тестовый маршрут", "Test route preview", "测试路由预览")
    val vpnTestRoute = t("Тестовый маршрут: 203.0.113.0/24", "Test route: 203.0.113.0/24", "测试路由：203.0.113.0/24")
    val vpnPacketsRead = t("Прочитано пакетов", "Packets read", "已读取数据包")
    val vpnBytesRead = t("Прочитано байт", "Bytes read", "已读取字节")
    val vpnIpv4PacketsRead = t("IPv4 пакеты", "IPv4 packets", "IPv4 数据包")
    val vpnTcpPacketsRead = "TCP"
    val vpnUdpPacketsRead = "UDP"
    val vpnIcmpPacketsRead = "ICMP"
    val vpnPacketInspector = t("Инспектор пакетов", "Packet inspector", "数据包检查器")
    val vpnPacketInspectorPrivacy = t("Только тестовый маршрут TEST-NET: пакеты читаются и отбрасываются. Показываются IP, TCP/UDP-порты, протокол, размер и время — без payload, hostname, PCAP и сохранения.", "TEST-NET route only: packets are read and dropped. The view shows IPs, TCP/UDP ports, protocol, size, and time—without payloads, hostnames, PCAP, or persistence.", "仅限 TEST-NET 测试路由：数据包会被读取并丢弃。仅显示 IP、TCP/UDP 端口、协议、大小和时间，不包含 payload、主机名、PCAP 或持久化。")
    val vpnPacketInspectorEmpty = t("Пакеты пока не наблюдались", "No packets observed yet", "尚未观察到数据包")
    val vpnClearPacketList = t("Очистить список пакетов", "Clear packet list", "清除数据包列表")
    val vpnPausePacketInspector = t("Пауза инспектора пакетов", "Pause packet inspector", "暂停数据包检查器")
    val vpnPacketInspectorPaused = t("Инспектор на паузе: новые сводки не добавляются, сервис остаётся активным.", "Inspector paused: new summaries are not appended while the service remains active.", "检查器已暂停：服务保持活动，但不会追加新的摘要。")
    val vpnPacketListLastUpdate = t("Последнее обновление списка", "Packet list last update", "数据包列表最后更新")
    val vpnNormalInternetUnchanged = t("Обычный интернет должен остаться без изменений", "Normal internet should remain unchanged", "正常互联网应保持不变")
    val vpnHowToTestTun = t("Откройте http://203.0.113.1 в браузере или выполните тестовое подключение к 203.0.113.1. Пакеты могут появиться здесь и будут отброшены.", "Open http://203.0.113.1 in a browser or run a test connection to 203.0.113.1. Packets may appear here and will be dropped.", "在浏览器中打开 http://203.0.113.1，或对 203.0.113.1 运行测试连接。数据包可能会显示在这里，并将被丢弃。")
    val vpnPermissionRequired = t("Требуется разрешение", "Permission required", "需要权限")
    val vpnStarting = t("Запуск…", "Starting…", "正在启动…")
    val vpnLocalServiceActive = t("Сервис активен, TUN не создан", "Service active, no TUN", "服务活动，未创建 TUN")
    val vpnTunPreviewActive = t("TUN preview active", "TUN preview active", "TUN 预览已活动")
    val vpnTunActive = t("TUN: active", "TUN: active", "TUN：活动")
    val vpnTunInactive = t("TUN: inactive", "TUN: inactive", "TUN：未活动")
    val vpnNotificationPermissionRequired = t("Требуется разрешение уведомлений", "Notification permission required", "需要通知权限")
    val vpnNotificationPermissionRequiredDetail = t("Требуется разрешение на уведомления, чтобы Android разрешил запустить VPN-маршрутизатор в фоне.", "Notification permission is required so Android can run the VPN router in the foreground.", "需要通知权限，Android 才能以前台服务运行 VPN 路由器。")
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
    val mockProfileDescription = t("Профиль сохранён локально. Перед использованием приложение проверит его через текущий движок.", "Profile saved locally. The app validates it with the current engine before use.", "配置保存在本地。应用会在使用前通过当前引擎进行验证。")
    val profileAdded = t("Профиль добавлен локально.", "Profile added locally.", "配置已本地添加。")
    val create = t("Создать", "Create", "创建")
    val profileDetails = t("Детали профиля", "Profile details", "配置详情")
    val profileDetailsSubtitle = t("Настройте профиль без перегруза основного списка.", "Edit the profile without crowding the main list.", "编辑配置而不挤占主列表。")
    val addProfileSubtitle = t("Выберите рабочий протокол; недоступные и устаревшие варианты помечены отдельно.", "Choose a working protocol; unavailable and legacy options are marked separately.", "请选择可用协议；不可用和旧协议会单独标记。")
    val importOptions = t("Импорт", "Import", "导入")
    val noDns = t("Использует системный DNS Android", "Uses Android system DNS", "使用 Android 系统 DNS")
    val defaultProfile = t("Основной маршрут", "Default route", "默认路由")
    val defaultChanged = t("Основной маршрут изменён.", "Default route changed.", "默认路由已更改。")
    val makeDefault = t("Сделать основным маршрутом", "Use as default route", "设为默认路由")
    val delete = t("Удалить", "Delete", "删除")
    val profileDeleted = t("Профиль удалён.", "Profile deleted.", "配置已删除。")
    val protectedProfileMessage = t("System, Block и «Совместимость TCP/TLS» — встроенные профили, их нельзя удалить.", "System, Block, and TCP/TLS Compatibility are built-in profiles and cannot be deleted.", "System、Block 和 TCP/TLS 兼容模式是内置配置，无法删除。")
    val mockOnly = t("Демо: реальный тоннель не запускается.", "Demo: no real tunnel is started.", "演示：不会启动真实隧道。")
    val cancel = t("Отмена", "Cancel", "取消")
    val edit = t("Изменить", "Edit", "编辑")
    val profileUpdated = t("Профиль обновлён.", "Profile updated.", "配置已更新。")
    val save = t("Сохранить", "Save", "保存")
    val simulation = t("Проверка правила", "Rule check", "规则检查")
    val routeEmptyState = t("Без отдельных правил приложения используют обычный интернет телефона через System.", "Without specific rules, apps use the phone's normal internet connection through System.", "没有单独规则时，应用通过 System 使用手机的普通网络连接。")
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
    val systemBlockOnly = t("Нет включённых пользовательских профилей. Доступны встроенные System и Block.", "No enabled user profiles. Built-in System and Block are available.", "没有已启用的用户配置。可使用内置 System 和 Block。")
    val runtimeRoutingFuture = t("При включённом контроле сети Android передаёт IPv4/IPv6 и DNS в локальный маршрутизатор. Недоступная цель правила блокируется без скрытого перехода на System.", "When network control is active, Android sends IPv4/IPv6 and DNS through the local router. An unavailable rule target is blocked without a silent fallback to System.", "网络控制启用时，Android 会将 IPv4/IPv6 和 DNS 交给本地路由器。不可用的规则目标会被阻止，不会静默回退到 System。")
    val lookup = t("DNS-запрос", "DNS lookup", "DNS 查询")
    val domain = t("Домен", "Domain", "域名")
    val type = t("Тип", "Type", "类型")
    val dnsServer = t("DNS-сервер", "DNS server", "DNS 服务器")
    val check = t("Проверить", "Check", "检查")
    val dnsResult = t("Результат DNS", "DNS result", "DNS 结果")
    val policies = t("Политики", "Policies", "策略")
    val dnsPolicyLimit = t("Пользовательский DNS применяется внутри VPN-маршрутизатора и при необходимости отправляется через выбранный профиль. Недоступный путь DNS блокируется.", "Custom DNS is applied inside the VPN router and can be sent through the selected profile. An unavailable DNS path is blocked.", "自定义 DNS 在 VPN 路由器内应用，并可通过所选配置发送。不可用的 DNS 路径会被阻止。")
    val dnsPolicyDetails = t("Детали DNS-политики", "DNS policy details", "DNS 策略详情")
    val dnsPolicyDetailsSubtitle = t("Адрес DNS и профиль, через который нужно отправлять запросы.", "DNS address and the profile used to send its requests.", "DNS 地址以及发送其请求所用的配置。")
    val usedByProfiles = t("Используется профилями", "Used by profiles", "配置使用")
    val usedByRoutes = t("Используется маршрутами", "Used by routes", "路由使用")
    val hostOverrides = t("Host overrides", "Host overrides", "主机覆盖")
    val hostOverridesSubtitle = t("Локальные hosts-подстановки рабочего DNS-маршрутизатора.", "Local hosts-like mappings for the active DNS router.", "工作 DNS 路由器的本地主机映射。")
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
    val fsLimitShort = t("Сканер показывает метаданные соединений, но не расшифровывает и не сохраняет содержимое HTTPS, сообщений, паролей или файлов.", "The scanner shows connection metadata but does not decrypt or store HTTPS contents, messages, passwords, or files.", "扫描器显示连接元数据，但不会解密或保存 HTTPS 内容、消息、密码或文件。")
    val fsLimitDetails = t("События поступают напрямую от локального sing-box внутри ViRouteFS: приложение, адрес, домен при наличии, протокол, маршрут и объём. Это понятный анализ потоков, а не перехват полезной нагрузки и не замена криминалистическому Wireshark/PCAP.", "Events come directly from the local sing-box inside ViRouteFS: app, address, domain when known, protocol, route, and traffic volume. This is understandable flow analysis, not payload interception or a replacement for forensic Wireshark/PCAP.", "事件直接来自 ViRouteFS 内的本地 sing-box：应用、地址、已知域名、协议、路由和流量。它是易懂的流分析，不会截获有效载荷，也不能替代取证级 Wireshark/PCAP。")
    val flowScannerTitle = "Flow Scanner"
    val flowScannerSubtitle = t("кто куда подключается и почему", "who connects where and why", "谁连接到哪里以及原因")
    val flowAppFilter = t("Приложения", "Apps", "应用")
    val flowAllAppsPlaceholder = t("Все приложения", "All apps", "所有应用")
    val flowStartAnalysis = t("Начать анализ", "Start analysis", "开始分析")
    val flowDemoMode = t("локально", "local", "本地")
    val flowEmptyTitle = t("Потоки пока не записаны", "No flows captured yet", "尚未捕获流量")
    val flowEmptyState = t("Включите контроль сети и откройте нужное приложение. Здесь появятся его реальные соединения; содержимое трафика не записывается.", "Enable network control and open an app. Its real connections will appear here; traffic contents are not recorded.", "启用网络控制并打开应用。真实连接会显示在这里；不会记录流量内容。")
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
    val telegramRouteReason = t("Пример объясняет, как доменное правило выбирает профиль.", "This sample explains how a domain rule selects a profile.", "此示例说明域名规则如何选择配置。")
    val telegramRecommendation = t("Проверьте, что профиль выбран осознанно, затем включайте реальный режим только вручную.", "Confirm the profile is intentional, then enable any real mode only manually.", "确认配置选择正确；真实模式只能手动启用。")
    val telegramTechnical = t("Учебный пример, не событие реального трафика. Рабочий движок пока не передаёт интерфейсу поток событий каждого соединения.", "Training sample, not a real traffic event. The runtime does not yet emit a per-connection event stream to the UI.", "教学示例，并非真实流量事件。运行时尚未向界面输出每个连接的事件流。")
    val mediaRouteReason = t("Домены могут соответствовать пользовательскому правилу выбранного профиля.", "Domains can match a user rule for a selected profile.", "域名可以匹配所选配置的用户规则。")
    val mediaRecommendation = t("Если соединение работает медленно, позже проверьте выбранный профиль и MTU.", "If a connection is slow, later check the selected profile and MTU.", "如果连接较慢，稍后检查所选配置和 MTU。")
    val mediaTechnical = t("Событие показывает целевой домен и предполагаемый протокол; реальный QUIC/TCP анализ не выполняется.", "The event shows target domains and expected protocol; real QUIC/TCP analysis is not performed.", "事件显示目标域和预期协议；未执行真实 QUIC/TCP 分析。")
    val govRouteReason = t("Чувствительные локальные сервисы оставлены на System, чтобы не отправлять их в сторонний тоннель.", "Sensitive local services stay on System so they are not sent through a third-party tunnel.", "敏感本地服务保持 System，避免经过第三方隧道。")
    val govRecommendation = t("Оставьте System для банков и госуслуг, если у вас нет отдельной доверенной политики.", "Keep System for banks and public services unless you have a separate trusted policy.", "除非有单独可信策略，否则银行和公共服务建议保持 System。")
    val govTechnical = t("Демо не проверяет сертификат и не читает содержимое соединения.", "The demo does not validate certificates or read connection contents.", "演示不验证证书，也不读取连接内容。")
    val workRouteReason = t("Внутренний домен gitlab.corp совпал с рабочим правилом и DNS-политикой.", "The internal gitlab.corp domain matched the work rule and DNS policy.", "内部域 gitlab.corp 匹配工作规则和 DNS 策略。")
    val workRecommendation = t("Используйте рабочий VPN только для корпоративных доменов и приложений.", "Use the work VPN only for corporate domains and apps.", "仅对公司域名和应用使用工作 VPN。")
    val workTechnical = t("Это учебная карточка с частным IP и вымышленным корпоративным доменом; она не является событием реального трафика.", "This is a training card with a private IP and a fictional corporate domain; it is not a real traffic event.", "这是包含私有 IP 和虚构公司域名的教学卡片，并非真实流量事件。")
    val trackerRouteReason = t("Домен совпал с демонстрационным правилом Block для нежелательных трекеров.", "The domain matched a demo Block rule for unwanted trackers.", "该域匹配不需要跟踪器的演示 Block 规则。")
    val trackerWarning = t("Возможный трекер: соединение предлагается блокировать.", "Possible tracker: blocking is recommended.", "可能的跟踪器：建议阻止。")
    val trackerRecommendation = t("Оставьте блокировку, если этот домен не нужен приложению для основной функции.", "Keep it blocked if the app does not need this domain for its core function.", "如果应用核心功能不需要该域，请保持阻止。")
    val trackerTechnical = t("Это учебная карточка: она сама не выполняет DNS-запрос и не включает блокировку. Реальные правила Block применяются только после запуска VPN-маршрутизатора.", "This training card does not itself run DNS or enable blocking. Real Block rules apply only while the VPN router is running.", "这是一张教学卡片，本身不会执行 DNS 或启用阻止；真实 Block 规则仅在 VPN 路由器运行时生效。")
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
    val helpShort = t("Краткая справка скрыта до раскрытия раздела.", "Brief help stays collapsed until you expand this section.", "简短帮助默认折叠，展开后查看。")
    val showHelp = t("Показать справку", "Show help", "显示帮助")
    val hideHelp = t("Скрыть справку", "Hide help", "隐藏帮助")
    val aboutViroutefs = t("About ViRouteFS", "About ViRouteFS", "关于 ViRouteFS")
    val projectOverviewShort = t("Visual Route & Flow Scanner — локальный Android-инструмент для видимости маршрутов и потоков.", "Visual Route & Flow Scanner is a local Android tool for route and flow visibility.", "Visual Route & Flow Scanner 是用于路由和流量可见性的本地 Android 工具。")
    val projectOverviewDetails = t("Home больше не отдельная вкладка: обзор, статус, приватность и цели живут здесь, в Settings → Help.", "Home is no longer a separate tab: overview, status, privacy, and goals live here in Settings → Help.", "Home 不再是单独标签：概览、状态、隐私和目标位于 Settings → Help。")
    val projectPurposeTitle = t("Назначение и законное использование", "Purpose and lawful use", "用途与合法使用")
    val projectPurposeShort = t("ViRouteFS создан для нормальной VPN-маршрутизации, диагностики и контроля собственного трафика.", "ViRouteFS is built for ordinary VPN routing, diagnostics, and control of your own traffic.", "ViRouteFS 用于常规 VPN 路由、诊断和控制您自己的流量。")
    val projectPurposeDetails = t("Приложение не предназначено и не продвигается как средство обхода блокировок. Используйте его только законно, с разрешёнными сетями и VPN-провайдерами. Дополнительный режим совместимости с DPI не является VPN, не шифрует трафик и не скрывает IP-адрес.", "The app is not intended or marketed as an access-restriction circumvention tool. Use it lawfully and only with authorized networks and VPN providers. The optional DPI compatibility mode is not a VPN, does not encrypt traffic, and does not hide the IP address.", "本应用不用于或宣传为规避访问限制的工具。请仅在合法且获授权的网络和 VPN 服务中使用。可选 DPI 兼容模式不是 VPN，不加密流量，也不隐藏 IP 地址。")
    val licenseSummaryTitle = t("Лицензия GPL-3.0-or-later", "GPL-3.0-or-later license", "GPL-3.0-or-later 许可证")
    val licenseSummaryShort = t("Проект остаётся свободным ПО под GPL-3.0-or-later.", "The project remains free software under GPL-3.0-or-later.", "项目仍是 GPL-3.0-or-later 下的自由软件。")
    val licenseSummaryDetails = t("Вы можете изучать, изменять и распространять код при соблюдении GPL. В APK включены тексты GPL-3.0, лицензии sing-box, MPL-2.0 для Xray-core, MIT-лицензии ByeDPI и Apache-2.0 для AndroidX/CameraX/ZXing/SnakeYAML Engine; точные версии, хэши и скрипты воспроизводимой сборки находятся в репозитории.", "You may study, modify, and redistribute the code under the GPL. The APK includes GPL-3.0, the sing-box license, MPL-2.0 for Xray-core, the ByeDPI MIT license, and Apache-2.0 for AndroidX/CameraX/ZXing/SnakeYAML Engine; exact versions, hashes, and reproducible build scripts are in the repository.", "可按 GPL 条款研究、修改和再分发代码。APK 内含 GPL-3.0、sing-box 许可证、Xray-core 的 MPL-2.0、ByeDPI MIT 许可证以及 AndroidX/CameraX/ZXing/SnakeYAML Engine 的 Apache-2.0；精确版本、哈希和可复现构建脚本位于代码仓库中。")
    val currentBetaLimitations = t("Границы beta", "Beta boundaries", "Beta 限制")
    val betaLimitationsShort = t("Нужна проверка на реальном arm64-телефоне. OpenVPN/OpenConnect и живые события Flow Scanner подключены, но IKEv2/IPsec и устаревшие L2TP/PPTP/SSTP ещё требуют отдельных Android-движков.", "Real arm64 device testing is still required. OpenVPN/OpenConnect and live Flow Scanner events are connected, while IKEv2/IPsec and legacy L2TP/PPTP/SSTP still need separate Android engines.", "仍需在真实 arm64 设备上测试。OpenVPN/OpenConnect 和实时 Flow Scanner 事件已接入，而 IKEv2/IPsec 与旧版 L2TP/PPTP/SSTP 仍需要单独的 Android 引擎。")
    val betaLimitationsDetails = t("Beta можно включать без VPN: System использует обычный мобильный интернет или Wi‑Fi, а отдельные правила направляют выбранный трафик в VPN, Block или режим совместимости TCP/TLS. APK содержит sing-box 1.14 alpha для большинства протоколов и отдельный локальный Xray-core для VLESS/XHTTP. Flow Scanner показывает метаданные без содержимого пакетов и расшифровки HTTPS. Все внешние туннели ещё требуют физической проверки.", "The beta can start without a VPN: System uses normal mobile data or Wi-Fi, while explicit rules send selected traffic to a VPN, Block, or TCP/TLS Compatibility. The APK contains sing-box 1.14 alpha for most protocols and an app-private Xray-core process for VLESS/XHTTP. Flow Scanner shows metadata without packet payloads or HTTPS decryption. Every external tunnel still requires physical verification.", "Beta 无需 VPN 即可启动：System 使用普通移动数据或 Wi-Fi，明确规则可将选定流量发送到 VPN、Block 或 TCP/TLS 兼容模式。APK 对多数协议使用 sing-box 1.14 alpha，并为 VLESS/XHTTP 内置独立的本地 Xray-core 进程。Flow Scanner 仅显示元数据，不记录数据包内容，也不解密 HTTPS。所有外部隧道仍需真机验证。")
    val projectGoals = t("Цели проекта", "Project goals", "项目目标")
    val projectGoalsShort = t("Сети, маршруты, DNS, Flow Scanner, диагностика и безопасные локальные аудиты.", "Networks, routes, DNS, Flow Scanner, diagnostics, and safe local audits.", "网络、路由、DNS、Flow Scanner、诊断和安全本地审计。")
    val projectGoalsDetails = t("Один VpnService, локальный sing-box, правила по приложениям/доменам/IP/CIDR, DNS через выбранный туннель, понятная диагностика и fail-closed без телеметрии и записи содержимого трафика.", "A single VpnService, local sing-box, app/domain/IP/CIDR rules, DNS through a selected tunnel, readable diagnostics, and fail-closed behavior without telemetry or traffic-content logging.", "单个 VpnService、本地 sing-box、按应用/域名/IP/CIDR 的规则、通过所选隧道的 DNS、清晰诊断，以及无遥测或流量内容记录的 fail-closed 行为。")
    val beginnerMode = t("Для самых маленьких", "Beginner mode", "初学者模式")
    val beginnerHelp = t("Контроль сети можно включить без добавления VPN. По умолчанию всё продолжит работать через обычный мобильный интернет или Wi‑Fi телефона (System). Маршруты — это исключения: выбранное приложение, домен, IP или диапазон сети можно отправить в конкретный VPN, режим совместимости TCP/TLS или Block.", "Network control can start without adding a VPN. By default, everything keeps using the phone's normal mobile data or Wi-Fi connection (System). Routes are exceptions: a selected app, domain, IP, or network range can use a specific VPN, TCP/TLS Compatibility, or Block.", "无需添加 VPN 即可启动网络控制。默认情况下，所有流量继续使用手机的普通移动数据或 Wi-Fi（System）。路由是例外：可将指定应用、域名、IP 或网段发送到特定 VPN、TCP/TLS 兼容模式或 Block。")
    val adminMode = t("Для админов", "Admin mode", "管理员模式")
    val adminHelp = t("Модель: трафик без более точного правила → System, то есть обычный uplink телефона; app/domain/IP/CIDR → назначенный VPN/прокси/Block/режим совместимости TCP/TLS; недоступная цель явного правила → Block / fail closed. DNS перехватывается внутри единого sing-box TUN и может идти через отдельный профиль. Arm64 APK содержит sing-box 1.14 alpha, отдельный локальный Xray-core для VLESS/XHTTP и MIT-движок ByeDPI, без Naive/Cronet. Flow Scanner получает метаданные соединений, но не содержимое пакетов. IKEv2/IPsec требует strongSwan; L2TP/PPTP/SSTP не выдаются за рабочие без проверенного движка.", "Model: unmatched traffic → System, the phone's normal uplink; app/domain/IP/CIDR → the assigned VPN/proxy/Block/TCP-TLS Compatibility target; an unavailable explicit target → Block / fail closed. DNS is intercepted inside the single sing-box TUN and can use a separate profile. The arm64 APK embeds sing-box 1.14 alpha, an app-private Xray-core process for VLESS/XHTTP, and the MIT-licensed ByeDPI engine, without Naive/Cronet. Flow Scanner receives connection metadata but no packet content. IKEv2/IPsec needs strongSwan; L2TP/PPTP/SSTP are not claimed as working without an audited engine.", "模型：未匹配流量使用 System，即手机普通网络；app/domain/IP/CIDR 使用指定 VPN、代理、Block 或 TCP/TLS 兼容模式；明确目标不可用时执行 Block / fail closed。DNS 在唯一的 sing-box TUN 内拦截，并可经单独配置发送。arm64 APK 内置 sing-box 1.14 alpha、用于 VLESS/XHTTP 的本地 Xray-core 进程和 MIT 许可的 ByeDPI，不含 Naive/Cronet。Flow Scanner 仅接收连接元数据。IKEv2/IPsec 仍需 strongSwan；没有审计引擎时不会声称 L2TP/PPTP/SSTP 可用。")
    val developerDiagnostics = t("Developer diagnostics", "Developer diagnostics", "开发者诊断")
    val developerDiagnosticsWarning = t("Не обычная функция пользователя. Используется для внутренней проверки безопасного TEST-NET маршрута.", "Not a normal user feature. Used for internal validation of the safe TEST-NET route.", "不是普通用户功能。用于安全 TEST-NET 路由的内部验证。")
    val supportProject = t("Поддержать проект", "Support project", "支持项目")
    val supportShort = t("Добровольная поддержка без покупки функций и преимуществ.", "Voluntary support without purchasing features or benefits.", "自愿支持，不购买任何功能或权益。")
    val supportDonationDisclaimer = t("Номер банковской карты в приложение не встраивается и не сохраняется. Кнопка открывает только настроенную владельцем HTTPS-страницу оплаты; приложение не обрабатывает платёжные данные.", "No bank card number is embedded or stored. The button only opens an owner-configured HTTPS payment page; the app does not process payment data.", "应用不会嵌入或保存银行卡号。按钮仅打开所有者配置的 HTTPS 支付页面，应用不处理支付数据。")
    val voluntarySupport = t("Добровольно поддержать", "Voluntary support", "自愿支持")
    val supportLinkNotConfigured = t("Безопасная платёжная ссылка пока не настроена в сборке.", "No secure payment link is configured in this build.", "此版本尚未配置安全支付链接。")
    val updates = t("Обновления", "Updates", "更新")
    val updateChannelBeta = t("Канал обновлений: Beta", "Update channel: Beta", "更新频道：Beta")
    val checkForUpdates = t("Проверить обновления", "Check for updates", "检查更新")
    val openGithubReleases = t("Открыть релизы GitHub", "Open GitHub releases", "打开 GitHub 发布页")
    val openReleasePage = t("Открыть страницу релиза", "Open release page", "打开发布页面")
    val latestRelease = t("Последний релиз", "Latest release", "最新发布")
    val recentReleases = t("Недавние релизы", "Recent releases", "近期发布")
    val releaseHistoryManualOnly = t("История появится после ручной проверки обновлений.", "Release history appears after a manual update check.", "手动检查更新后会显示发布历史。")
    val prerelease = t("Предрелиз", "Prerelease", "预发布")
    val updateManualOnly = t("Проверка выполняется только вручную после нажатия кнопки. Автоматических фоновых проверок нет.", "Update checking runs only when you press the button. There are no automatic background checks.", "只会在按下按钮后手动检查更新，没有自动后台检查。")
    val updateChecking = t("Проверяем GitHub Releases…", "Checking GitHub Releases…", "正在检查 GitHub Releases…")
    val upToDate = t("Установлена актуальная версия для этого канала.", "You are up to date for this channel.", "当前频道已是最新版本。")
    val noReleaseFound = t("Опубликованных релизов пока нет. APK можно установить из артефактов GitHub Actions.", "No published release found yet. You can still install APK artifacts from GitHub Actions.", "尚未找到已发布版本。仍可从 GitHub Actions 构建产物安装 APK。")
    val downloadApk = t("Скачать APK", "Download APK", "下载 APK")
    val installUpdate = t("Установить обновление", "Install update", "安装更新")
    val deleteDownloadedApk = t("Удалить скачанный APK", "Delete downloaded APK", "删除已下载 APK")
    val apkAssetNotFound = t("Найдена новая версия, но APK-файл в релизе не найден.", "Newer release found, but APK asset was not found.", "找到了新版本，但未找到 APK 资源。")
    val unknownAppsHelp = t("Android может показать предупреждение о непроверенном приложении, потому что APK установлен не из Google Play. Это системное предупреждение для sideload APK.", "Android may show an unverified app warning because this APK is installed outside Google Play. This is a system warning for sideloaded APKs.", "Android 可能会显示未验证应用警告，因为此 APK 是在 Google Play 之外安装的。这是 sideload APK 的系统警告。")
    val openInstallPermissionSettings = t("Открыть разрешение установки", "Open install permission settings", "打开安装权限设置")
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

    fun currentVersion(versionName: String, versionCode: Int): String =
        t("Текущая версия: $versionName (versionCode $versionCode)", "Current version: $versionName (versionCode $versionCode)", "当前版本：$versionName (versionCode $versionCode)")

    fun updateAvailable(version: String): String =
        t("Доступна новая версия: $version", "New version available: $version", "有新版本可用：$version")

    fun releaseVersionName(version: String): String =
        t("Версия: $version", "Version name: $version", "版本名称：$version")

    fun apkAssetName(name: String): String =
        t("APK: $name", "APK asset: $name", "APK 资源：$name")

    fun apkAssetSize(size: String): String =
        t("Размер APK: $size", "APK size: $size", "APK 大小：$size")

    fun apkDownloaded(size: String): String =
        t("APK скачан: $size", "APK downloaded: $size", "APK 已下载：$size")

    fun downloadFailed(message: String): String =
        t("Ошибка скачивания APK: $message", "APK download failed: $message", "APK 下载失败：$message")

    fun downloadProgress(bytesDownloaded: Long, totalBytes: Long?, percent: Int?): String {
        val downloaded = formatBytes(bytesDownloaded)
        val total = totalBytes?.let(::formatBytes)
        val percentText = percent?.let { " ($it%)" } ?: ""
        return if (total != null) {
            t("Скачивание: $downloaded / $total$percentText", "Downloading: $downloaded / $total$percentText", "正在下载：$downloaded / $total$percentText")
        } else {
            t("Скачивание: $downloaded", "Downloading: $downloaded", "正在下载：$downloaded")
        }
    }

    fun publishedAt(value: String): String =
        t("Опубликовано: $value", "Published: $value", "发布时间：$value")

    fun updateError(message: String): String =
        t("Ошибка проверки обновлений: $message", "Update check error: $message", "更新检查错误：$message")

    private fun t(ru: String, en: String, zh: String): String = when (language) {
        AppLanguage.Russian -> ru
        AppLanguage.English -> en
        AppLanguage.ChineseSimplified -> zh
    }
}
