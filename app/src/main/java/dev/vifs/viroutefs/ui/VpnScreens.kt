// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Details
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.UiText
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.engine.EngineCatalog
import dev.vifs.viroutefs.engine.FeatureReadiness
import dev.vifs.viroutefs.engine.ProtocolDescriptor
import dev.vifs.viroutefs.engine.ReadinessItem
import dev.vifs.viroutefs.engine.ReadinessState
import dev.vifs.viroutefs.engine.ReleaseReadinessReport
import dev.vifs.viroutefs.engine.evaluateReleaseReadiness
import dev.vifs.viroutefs.engine.validateXrayProfile
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigBackup
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.ImportDuplicateResolution
import dev.vifs.viroutefs.routing.ProfileImportPreview
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.SingBoxProfileConfig
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.importOpenVpnProfile
import dev.vifs.viroutefs.routing.applyProfileImport
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.previewProfileImport
import dev.vifs.viroutefs.routing.singBoxProfileTemplate
import dev.vifs.viroutefs.routing.singBoxProtocolSchema
import dev.vifs.viroutefs.routing.validateSingBoxProfile
import dev.vifs.viroutefs.routing.withDefaultRoute
import dev.vifs.viroutefs.routing.withoutProfile
import dev.vifs.viroutefs.runtime.tcp.DEV_TCP_BRIDGE_NOTICE
import dev.vifs.viroutefs.runtime.tcp.DEV_TCP_BRIDGE_SECRET_NOTICE
import dev.vifs.viroutefs.runtime.tcp.DevTcpBridgeSnapshot
import dev.vifs.viroutefs.runtime.tcp.TcpSessionId
import dev.vifs.viroutefs.runtime.tcp.TcpSessionState
import dev.vifs.viroutefs.runtime.tcp.VlessDevTcpBridge
import dev.vifs.viroutefs.socks5.Socks5DiagnosticResult
import dev.vifs.viroutefs.socks5.Socks5DiagnosticState
import dev.vifs.viroutefs.socks5.Socks5DiagnosticTestType
import dev.vifs.viroutefs.socks5.Socks5HandshakeTester
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.Socks5ReadinessSummary
import dev.vifs.viroutefs.socks5.Socks5ProfileStatus
import dev.vifs.viroutefs.socks5.Socks5TestHistoryItem
import dev.vifs.viroutefs.socks5.Socks5TestHistoryStore
import dev.vifs.viroutefs.socks5.deriveSocks5ReadinessSummary
import dev.vifs.viroutefs.socks5.toProfileStatus
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import dev.vifs.viroutefs.vless.VLESS_NO_HANDSHAKE_NOTICE
import dev.vifs.viroutefs.vless.VLESS_PROTOCOL_PROBE_NOTICE
import dev.vifs.viroutefs.vless.VLESS_REALITY_UNSUPPORTED_MESSAGE
import dev.vifs.viroutefs.vless.VLESS_RESPONSE_PROBE_METADATA_NOTICE
import dev.vifs.viroutefs.vless.VlessProtocolProbeHistoryItem
import dev.vifs.viroutefs.vless.VlessProtocolProbeHistoryStore
import dev.vifs.viroutefs.vless.VlessProtocolProbeResult
import dev.vifs.viroutefs.vless.VlessProtocolProber
import dev.vifs.viroutefs.vless.VLESS_ROUTE_PREVIEW_ONLY
import dev.vifs.viroutefs.vless.VLESS_RUNTIME_LIMITATION
import dev.vifs.viroutefs.vless.VLESS_TCP_REACHABILITY_NOTICE
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileStatus
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.vlessProbeSniHost
import dev.vifs.viroutefs.vless.VlessTcpReachabilityHistoryItem
import dev.vifs.viroutefs.vless.VlessTcpReachabilityHistoryStore
import dev.vifs.viroutefs.vless.VlessTcpReachabilityResult
import dev.vifs.viroutefs.vless.VlessTcpReachabilityState
import dev.vifs.viroutefs.vless.VlessTcpReachabilityTester
import dev.vifs.viroutefs.vless.toProfileStatus
import dev.vifs.viroutefs.vless.VlessUriParseResult
import dev.vifs.viroutefs.vless.exportVlessUri
import dev.vifs.viroutefs.vless.parseVlessUri
import dev.vifs.viroutefs.vless.validateVlessProfile
import dev.vifs.viroutefs.vpn.SingBoxRuntimeValidator
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import dev.vifs.viroutefs.vpn.VpnServiceController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VpnScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    vpnState: VpnServiceUiState,
    developerMode: Boolean,
    @Suppress("UNUSED_PARAMETER") tunTestRoutePreviewEnabled: Boolean,
    initialImportSource: String?,
    onProfileImportConsumed: () -> Unit,
    onVpnSwitch: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onTunTestRoutePreview: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onClearPacketList: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onPausePacketInspector: (Boolean) -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var addSocks5 by rememberSaveable { mutableStateOf(false) }
    var addVless by rememberSaveable { mutableStateOf(false) }
    var addSingBoxTypeName by rememberSaveable { mutableStateOf<String?>(null) }
    var showProfileImport by rememberSaveable { mutableStateOf(!initialImportSource.isNullOrBlank()) }
    var showConfigBackup by rememberSaveable { mutableStateOf(false) }
    var profileImportSource by rememberSaveable { mutableStateOf(initialImportSource.orEmpty()) }
    LaunchedEffect(initialImportSource) {
        if (!initialImportSource.isNullOrBlank()) {
            profileImportSource = initialImportSource
            showProfileImport = true
        }
    }
    val addSingBoxType = addSingBoxTypeName?.let { name ->
        TunnelType.entries.firstOrNull { it.name == name }
    }
    val selectedProfile = selectedProfileId?.let { id -> config.profiles.firstOrNull { it.id == id } }
    val userProfiles = config.profiles.filter {
        it.id !in setOf(
            RoutingConfigDefaults.SYSTEM_PROFILE_ID,
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
            RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        ) && (!it.mockOnly || it.type == TunnelType.Socks5 || it.type == TunnelType.VLESS || it.singBox != null)
    }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val readinessReport = remember(config) { evaluateReleaseReadiness(config) }
    var readinessExpanded by rememberSaveable { mutableStateOf(false) }
    var nativeCheckRunning by remember { mutableStateOf(false) }
    var nativeCheckMessage by remember(config) {
        mutableStateOf("")
    }
    val devTcpBridge = remember(config.profiles) {
        VlessDevTcpBridge(
            profiles = {
                config.profiles
                    .filter { it.type == TunnelType.VLESS && it.vless != null }
                    .associate { it.id to it.vless!! }
            },
        )
    }
    var devBridgeSnapshot by remember { mutableStateOf(devTcpBridge.snapshot()) }
    var devBridgeMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val profileTestController = remember(context) { VpnServiceController(context.applicationContext) }
    var profileTests by remember { mutableStateOf<Map<String, ProfileTunnelTestReport>>(emptyMap()) }

    fun testProfile(profile: TunnelProfile) {
        if (profileTests[profile.id]?.running == true) return
        profileTests = profileTests + (
            profile.id to ProfileTunnelTestReport(
                running = true,
                configuration = "Конфигурация: проверяем…",
                server = "Сервер: ожидает проверки.",
                tunnel = "Тоннель: ожидает проверки.",
            )
            )
        scope.launch {
            val preflight = withContext(Dispatchers.IO) { runProfileConnectionPreflight(profile) }
            if (!preflight.configurationValid) {
                profileTests = profileTests + (
                    profile.id to ProfileTunnelTestReport(
                        running = false,
                        configuration = preflight.configuration,
                        server = "Сервер: не проверялся из-за ошибки конфигурации.",
                        tunnel = "Тоннель: не запускался.",
                        configurationSuccessful = false,
                        tunnelSuccessful = false,
                    )
                    )
                return@launch
            }
            profileTests = profileTests + (
                profile.id to ProfileTunnelTestReport(
                    running = true,
                    configuration = preflight.configuration,
                    server = preflight.server,
                    tunnel = "Тоннель: выполняется HTTPS-запрос через выбранный профиль…",
                    configurationSuccessful = true,
                    serverSuccessful = preflight.serverReachable,
                )
                )
            profileTestController.testProfileConnection(profile.id) { tunnelResult ->
                val latency = tunnelResult.latencyMillis?.let { " (${it} мс)" }.orEmpty()
                profileTests = profileTests + (
                    profile.id to ProfileTunnelTestReport(
                        running = false,
                        configuration = preflight.configuration,
                        server = preflight.server,
                        tunnel = "Тоннель: ${tunnelResult.summary}$latency",
                        configurationSuccessful = true,
                        serverSuccessful = preflight.serverReachable,
                        tunnelSuccessful = tunnelResult.successful,
                    )
                    )
            }
        }
    }

    if (showProfileImport) {
        ProfileImportScreen(
            padding = padding,
            config = config,
            initialSource = profileImportSource,
            onBack = {
                showProfileImport = false
                profileImportSource = ""
                onProfileImportConsumed()
            },
            onConfig = { next, message ->
                onConfig(next, message)
                showProfileImport = false
                profileImportSource = ""
                onProfileImportConsumed()
            },
        )
        return
    }

    if (showConfigBackup) {
        ConfigBackupScreen(
            padding = padding,
            config = config,
            onBack = { showConfigBackup = false },
            onConfig = { next, message ->
                onConfig(next, message)
                showConfigBackup = false
            },
        )
        return
    }

    if (addSocks5) {
        Socks5ProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            profile = null,
            onBack = { addSocks5 = false },
            onConfig = { next, message -> onConfig(next, message) },
        )
        return
    }

    if (addVless) {
        VlessProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            profile = null,
            onBack = { addVless = false },
            onConfig = { next, message -> onConfig(next, message) },
        )
        return
    }

    if (addSingBoxType != null) {
        SingBoxProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            type = addSingBoxType,
            profile = null,
            onBack = { addSingBoxTypeName = null },
            onConfig = { next, message -> onConfig(next, message) },
        )
        return
    }

    if (selectedProfile != null) {
        NetworkProfileDetailsScreen(
            padding = padding,
            text = text,
            profile = selectedProfile,
            config = config,
            onBack = { selectedProfileId = null },
            onConfig = { next, message ->
                onConfig(next, message)
                if (next.profiles.none { it.id == selectedProfile.id }) selectedProfileId = null
            },
        )
        return
    }

    var showAddVpnSheet by remember { mutableStateOf(false) }
    val addVpnSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showAddVpnSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddVpnSheet = false },
            sheetState = addVpnSheetState,
        ) {
            AddVpnTypeSheet(
                onClose = { showAddVpnSheet = false },
                onImport = { source ->
                    showAddVpnSheet = false
                    profileImportSource = source
                    showProfileImport = true
                },
                onAddSocks5 = {
                    showAddVpnSheet = false
                    addSocks5 = true
                },
                onAddVless = {
                    showAddVpnSheet = false
                    addVless = true
                },
                onAddSingBox = { type ->
                    showAddVpnSheet = false
                    addSingBoxTypeName = type.name
                },
            )
        }
    }

    ScreenList(padding) {
        item {
            NetworkControlHero(
                active = vpnState.switchChecked,
                serviceLabel = vpnState.label(text),
                serviceDetail = vpnState.detail,
                onToggle = { onVpnSwitch(!vpnState.switchChecked) },
            )
        }
        item {
            PrimaryInternetCard(
                config = config,
                activationError = defaultRouteActivationError(config),
                ruleCount = config.rules.count { it.enabled && it.type != RouteRuleType.DEFAULT },
                profileCount = userProfiles.size,
                onAddVpn = { showAddVpnSheet = true },
                onUseSystem = {
                    onConfig(
                        config.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID),
                        "Основной маршрут возвращён на обычный интернет телефона: System.",
                    )
                },
            )
        }
        item {
            SecurityControlsCard(
                emergencyBlockEnabled = config.emergencyBlockEnabled,
                byeDpiEnabled = config.profiles.firstOrNull {
                    it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID
                }?.enabled == true,
                vpnActive = vpnState.switchChecked,
                onEmergencyBlockEnabled = { enabled ->
                    onConfig(
                        config.copy(emergencyBlockEnabled = enabled),
                        if (enabled) {
                            "Аварийный запрет сети включён. Весь трафик будет направлен в Block."
                        } else {
                            "Аварийный запрет сети снят. Действуют обычные правила маршрутизации."
                        },
                    )
                },
                onByeDpiEnabled = { enabled ->
                    val existing = config.profiles.firstOrNull {
                        it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID
                    }
                    val nextProfile = (existing ?: RoutingConfigDefaults.byeDpiProfile())
                        .copy(enabled = enabled, mockOnly = false)
                    val nextProfiles = if (existing == null) {
                        config.profiles + nextProfile
                    } else {
                        config.profiles.map {
                            if (it.id == RoutingConfigDefaults.BYEDPI_PROFILE_ID) nextProfile else it
                        }
                    }
                    onConfig(
                        config.copy(profiles = nextProfiles),
                        if (enabled) {
                            "Совместимость TCP/TLS включена. Она доступна как отдельный маршрут и не заменяет VPN-шифрование."
                        } else {
                            "Совместимость TCP/TLS выключена. Правила, которые указывают на неё, будут заблокированы."
                        },
                    )
                },
            )
        }
        item {
            ReleaseReadinessCard(
                report = readinessReport,
                expanded = readinessExpanded,
                nativeCheckRunning = nativeCheckRunning,
                nativeCheckMessage = nativeCheckMessage,
                onToggleExpanded = { readinessExpanded = !readinessExpanded },
                onRunNativeCheck = {
                    nativeCheckRunning = true
                    nativeCheckMessage = "Проверяем конфигурацию…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            SingBoxRuntimeValidator.validate(context.applicationContext, config)
                        }
                        result
                            .onSuccess { warnings ->
                                nativeCheckMessage = if (warnings.isEmpty()) {
                                    "Конфигурация готова к запуску."
                                } else {
                                    "Конфигурация принята с предупреждениями: ${warnings.size}."
                                }
                            }
                            .onFailure {
                                nativeCheckMessage = "Конфигурация отклонена. Откройте подробности и исправьте отмеченные пункты."
                            }
                        nativeCheckRunning = false
                    }
                },
            )
        }
        item {
            ConfigurationBackupCard(
                onOpen = { showConfigBackup = true },
            )
        }
        item {
            ProfilesHeader(
                profileCount = userProfiles.size,
                onAdd = { showAddVpnSheet = true },
            )
        }
        if (userProfiles.isEmpty()) {
            item {
                CardBlock {
                    Text("VPN-профили не добавлены", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Это нормально: контроль сети работает через обычный интернет телефона. Добавьте VPN только для нужных правил.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (developerMode && !vpnState.switchChecked) {
            item {
                TcpSessionRuntimeCard(
                    config = config,
                    vpnState = vpnState,
                    devSnapshot = devBridgeSnapshot,
                    message = devBridgeMessage,
                    onOpenDevSession = { profileId, targetHost, targetPort ->
                        scope.launch {
                            devBridgeMessage = "Opening dev TCP session…"
                            runCatching {
                                withContext(Dispatchers.IO) { devTcpBridge.openDevSession(profileId, targetHost, targetPort) }
                            }.onSuccess {
                                devBridgeSnapshot = devTcpBridge.snapshot()
                                devBridgeMessage = "Dev TCP session open."
                            }.onFailure { error ->
                                devBridgeSnapshot = devTcpBridge.snapshot()
                                devBridgeMessage = error.localizedMessage ?: "Could not open dev TCP session."
                            }
                        }
                    },
                    onCloseDevSession = { sessionId ->
                        scope.launch {
                            withContext(Dispatchers.IO) { devTcpBridge.closeDevSession(sessionId) }
                            devBridgeSnapshot = devTcpBridge.snapshot()
                            devBridgeMessage = "Dev TCP session closed."
                        }
                    },
                    onSendTestData = { sessionId ->
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    devTcpBridge.sendTestData(sessionId, "ViRouteFS dev TCP test".encodeToByteArray())
                                    devTcpBridge.receiveTestData(sessionId)
                                }
                            }.onSuccess { received ->
                                devBridgeSnapshot = devTcpBridge.snapshot()
                                devBridgeMessage = "Received ${received.size} bytes."
                            }.onFailure { error ->
                                devBridgeSnapshot = devTcpBridge.snapshot()
                                devBridgeMessage = error.localizedMessage ?: "Dev TCP send/read failed."
                            }
                        }
                    },
                )
            }
        }
        items(userProfiles, key = { it.id }) { profile ->
            CompactNetworkProfileCard(
                text = text,
                profile = profile,
                config = config,
                testReport = profileTests[profile.id],
                onTest = { testProfile(profile) },
                onOpen = { selectedProfileId = profile.id },
            )
        }
    }
}


@Composable
private fun NetworkControlHero(
    active: Boolean,
    serviceLabel: String,
    serviceDetail: String?,
    onToggle: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (active) Color(0xFF132A1D) else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "network-control-container",
    )
    val accentColor by animateColorAsState(
        targetValue = if (active) Color(0xFF40D97B) else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "network-control-accent",
    )
    val buttonColor by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "network-control-button",
    )
    val iconSize by animateDpAsState(targetValue = if (active) 52.dp else 48.dp, label = "network-control-icon-size")
    val buttonScale by animateFloatAsState(targetValue = if (active) 1.01f else 1f, label = "network-control-button-scale")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (active) "Контроль сети активен" else "Контроль сети выключен",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color(0xFFEAF6EE) else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (active) {
                            "Правила маршрутизации применяются ко всему трафику телефона."
                        } else {
                            "Сейчас используется обычное подключение без контроля правил."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) Color(0xFFC5D8CC) else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(iconSize)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = if (active) 0.22f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }

            Button(
                onClick = onToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(30.dp))
                    Text(
                        text = if (active) "Отключить контроль сети" else "Активировать контроль сети",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            StatusStrip(
                active = active,
                serviceLabel = serviceLabel,
                serviceDetail = serviceDetail,
            )
        }
    }
}

@Composable
private fun StatusStrip(active: Boolean, serviceLabel: String, serviceDetail: String?) {
    val stripColor = if (active) Color(0xFF244B33) else MaterialTheme.colorScheme.surfaceContainerLow
    val labelColor = if (active) Color(0xFFBDEFCB) else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = stripColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (active) {
                    "Статус: контроль активен • сервис: $serviceLabel"
                } else {
                    "Статус: обычное подключение • сервис: $serviceLabel"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = labelColor,
            )
            serviceDetail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = labelColor.copy(alpha = 0.82f))
            }
        }
    }
}

@Composable
private fun SecurityControlsCard(
    emergencyBlockEnabled: Boolean,
    byeDpiEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") vpnActive: Boolean,
    onEmergencyBlockEnabled: (Boolean) -> Unit,
    onByeDpiEnabled: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (emergencyBlockEnabled) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Быстрые действия",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            QuickControlRow(
                title = "Запретить всю сеть",
                subtitle = if (emergencyBlockEnabled) "Весь трафик заблокирован" else "Аварийный kill switch",
                checked = emergencyBlockEnabled,
                onCheckedChange = onEmergencyBlockEnabled,
            )
            QuickControlRow(
                title = "Совместимость TCP/TLS",
                subtitle = if (byeDpiEnabled) "Доступен как отдельный маршрут" else "Совместимость TCP/TLS, не VPN",
                checked = byeDpiEnabled,
                onCheckedChange = onByeDpiEnabled,
            )
        }
    }
}

@Composable
private fun QuickControlRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PrimaryInternetCard(
    config: RoutingConfig,
    activationError: String?,
    ruleCount: Int,
    profileCount: Int,
    onAddVpn: () -> Unit,
    onUseSystem: () -> Unit,
) {
    val profile = config.defaultProfileId?.let { id ->
        config.profiles.firstOrNull { it.id == id }
    }
    val group = config.defaultProfileId?.let { id ->
        config.profileGroups.firstOrNull { it.id == id }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (activationError == null) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Основной маршрут", style = MaterialTheme.typography.labelMedium)
                    Text(
                        profile?.let { "${it.name} • ${it.type.label}" }
                            ?: group?.let {
                                "${it.name} • ${
                                    when (it.mode) {
                                        dev.vifs.viroutefs.routing.ProfileGroupMode.Manual -> "ручная группа"
                                        dev.vifs.viroutefs.routing.ProfileGroupMode.Latency -> "минимальная задержка"
                                        dev.vifs.viroutefs.routing.ProfileGroupMode.Failover -> "резерв по порядку"
                                        dev.vifs.viroutefs.routing.ProfileGroupMode.RoundRobin -> "по кругу"
                                    }
                                }"
                            }
                            ?: "Не выбран",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusChip(if (activationError == null) "Готов" else "Ошибка")
            }
            Text(
                activationError
                    ?: if (profile?.type == TunnelType.Direct) {
                        "Обычный мобильный интернет или Wi‑Fi. VPN-профиль не обязателен."
                    } else if (group != null) {
                        "Трафик без отдельного правила идёт через явно настроенную группу."
                    } else {
                        "Трафик без отдельного правила идёт через этот профиль."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Правил: $ruleCount • VPN-профилей: $profileCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (activationError != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onUseSystem) {
                        Text("Использовать System")
                    }
                    OutlinedButton(onClick = onAddVpn) {
                        Text("Открыть VPN")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReleaseReadinessCard(
    report: ReleaseReadinessReport,
    expanded: Boolean,
    nativeCheckRunning: Boolean,
    nativeCheckMessage: String,
    onToggleExpanded: () -> Unit,
    onRunNativeCheck: () -> Unit,
) {
    val statusLabel = when {
        report.blockingCount > 0 -> "Ошибок: ${report.blockingCount}"
        report.attentionCount > 0 -> "Нужна проверка"
        else -> "Готово"
    }
    CardBlock {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Состояние настройки", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Маршруты, профили и DNS проверяются перед запуском.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(statusLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilledTonalButton(
                enabled = !nativeCheckRunning,
                onClick = onRunNativeCheck,
            ) {
                Text(if (nativeCheckRunning) "Проверяем…" else "Проверить")
            }
            OutlinedButton(onClick = onToggleExpanded) {
                Text(if (expanded) "Скрыть" else "Подробности")
            }
        }
        if (nativeCheckMessage.isNotBlank()) {
            Text(nativeCheckMessage, style = MaterialTheme.typography.bodySmall)
        }
        if (expanded) {
            report.items.forEach { item ->
                ReadinessItemRow(item)
            }
        }
    }
}

@Composable
private fun ReadinessItemRow(item: ReadinessItem) {
    val containerColor = when (item.state) {
        ReadinessState.Ready -> MaterialTheme.colorScheme.surfaceContainer
        ReadinessState.Attention,
        ReadinessState.Planned -> MaterialTheme.colorScheme.secondaryContainer
        ReadinessState.Blocked -> MaterialTheme.colorScheme.errorContainer
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(item.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                StatusChip(item.state.userLabel)
            }
            Text(item.summary, style = MaterialTheme.typography.bodySmall)
            item.recommendedAction?.let { action ->
                Text(
                    action,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ConfigurationBackupCard(
    onOpen: () -> Unit,
) {
    CardBlock {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text("Резервная копия", fontWeight = FontWeight.SemiBold)
                Text(
                    "Зашифрованный полный архив или очищенный файл для диагностики.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onOpen) {
                Text("Открыть")
            }
        }
    }
}

@Composable
private fun ConfigBackupScreen(
    padding: PaddingValues,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { RoutingConfigRepository(context.applicationContext) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var exportPasswordConfirmation by rememberSaveable { mutableStateOf("") }
    var importPassword by rememberSaveable { mutableStateOf("") }
    var pendingEncryptedExport by remember { mutableStateOf<ByteArray?>(null) }
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var importedConfig by remember { mutableStateOf<RoutingConfig?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }

    fun clearSensitiveUiState() {
        exportPassword = ""
        exportPasswordConfirmation = ""
        importPassword = ""
        pendingEncryptedExport?.fill(0)
        pendingEncryptedExport = null
        pendingImport?.fill(0)
        pendingImport = null
        importedConfig = null
    }

    val safeExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching {
                    val bytes = withContext(Dispatchers.Default) {
                        repository.exportJson(config).toByteArray(Charsets.UTF_8)
                    }
                    try {
                        writeConfigDocument(context, uri, bytes)
                    } finally {
                        bytes.fill(0)
                    }
                }.onSuccess {
                    message = "Очищенный диагностический файл сохранён без секретов."
                }.onFailure { error ->
                    message = error.localizedMessage ?: "Не удалось сохранить файл."
                }
                busy = false
            }
        }
    }
    val encryptedExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val payload = pendingEncryptedExport
        pendingEncryptedExport = null
        if (uri == null || payload == null) {
            payload?.fill(0)
        } else {
            busy = true
            scope.launch {
                runCatching {
                    writeConfigDocument(context, uri, payload)
                }.onSuccess {
                    message = "Полная зашифрованная копия сохранена. Храните пароль отдельно."
                    exportPassword = ""
                    exportPasswordConfirmation = ""
                }.onFailure { error ->
                    message = error.localizedMessage ?: "Не удалось сохранить резервную копию."
                }
                payload.fill(0)
                busy = false
            }
        }
    }
    val encryptedImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                runCatching {
                    readConfigDocument(context, uri)
                }.onSuccess { bytes ->
                    pendingImport?.fill(0)
                    pendingImport = bytes
                    importedConfig = null
                    importPassword = ""
                    message = "Файл выбран. Введите его пароль и проверьте содержимое."
                }.onFailure { error ->
                    message = error.localizedMessage ?: "Не удалось прочитать резервную копию."
                }
                busy = false
            }
        }
    }

    ScreenList(padding) {
        item {
            Header(
                "Резервная копия настроек",
                "Экспорт выполняется только по вашему действию. ViRouteFS ничего не отправляет в облако.",
            )
        }
        item {
            CardBlock {
                Text("Очищенный экспорт", fontWeight = FontWeight.SemiBold)
                Text(
                    "Профили, правила и DNS для диагностики. Пароли, UUID, ключи, PSK, cookie и приватные параметры заменяются маркерами; такой файл не является полной копией.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = { safeExportLauncher.launch("viroutefs-settings-redacted.json") },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Сохранить без секретов")
                }
            }
        }
        item {
            CardBlock {
                Text("Полная зашифрованная копия", fontWeight = FontWeight.SemiBold)
                WarningText(
                    "Архив содержит все данные подключения. Он защищён AES-256-GCM, а ключ формируется из пароля через PBKDF2-HMAC-SHA256. Потерянный пароль восстановить нельзя.",
                )
                OutlinedTextField(
                    value = exportPassword,
                    onValueChange = { exportPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Новый пароль — минимум ${RoutingConfigBackup.MIN_PASSWORD_LENGTH} символов") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = exportPasswordConfirmation,
                    onValueChange = { exportPasswordConfirmation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Повторите пароль") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Button(
                    onClick = {
                        val password = exportPassword.toCharArray()
                        val validation = RoutingConfigBackup.validatePassword(password)
                        when {
                            validation != null -> {
                                message = validation
                                password.fill('\u0000')
                            }
                            exportPassword != exportPasswordConfirmation -> {
                                message = "Пароли не совпадают."
                                password.fill('\u0000')
                            }
                            else -> {
                                busy = true
                                scope.launch {
                                    runCatching {
                                        repository.exportEncryptedBackup(config, password)
                                    }.onSuccess { bytes ->
                                        pendingEncryptedExport?.fill(0)
                                        pendingEncryptedExport = bytes
                                        encryptedExportLauncher.launch("viroutefs-backup.vrfs")
                                    }.onFailure { error ->
                                        message = error.localizedMessage ?: "Не удалось создать резервную копию."
                                    }
                                    password.fill('\u0000')
                                    busy = false
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Подготовка…" else "Создать полную копию")
                }
            }
        }
        item {
            CardBlock {
                Text("Восстановление полной копии", fontWeight = FontWeight.SemiBold)
                Text(
                    "Сначала файл расшифровывается и проверяется без изменения текущих настроек. Применение всегда отдельной кнопкой заменит все профили, правила и DNS.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(
                    onClick = {
                        encryptedImportLauncher.launch(
                            arrayOf("application/octet-stream", "application/json", "*/*"),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (pendingImport == null) "Выбрать .vrfs" else "Выбрать другой файл")
                }
                if (pendingImport != null) {
                    Text(
                        "Выбран зашифрованный файл: ${pendingImport!!.size / 1024 + 1} КБ",
                        style = MaterialTheme.typography.labelSmall,
                    )
                    OutlinedTextField(
                        value = importPassword,
                        onValueChange = {
                            importPassword = it
                            importedConfig = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Пароль файла") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                    Button(
                        onClick = {
                            val bytes = pendingImport ?: return@Button
                            val password = importPassword.toCharArray()
                            busy = true
                            scope.launch {
                                runCatching {
                                    repository.previewEncryptedBackup(bytes, password)
                                }.onSuccess { restored ->
                                    importedConfig = restored
                                    message = "Копия расшифрована и структурно проверена."
                                }.onFailure { error ->
                                    importedConfig = null
                                    message = error.localizedMessage ?: "Не удалось проверить резервную копию."
                                }
                                password.fill('\u0000')
                                busy = false
                            }
                        },
                        enabled = !busy && importPassword.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Расшифровать и проверить")
                    }
                }
            }
        }
        importedConfig?.let { restored ->
            item {
                CardBlock {
                    Text("Предварительный просмотр", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Профилей: ${restored.profiles.size}; групп: ${restored.profileGroups.size}; правил: ${restored.rules.size}; DNS-политик: ${restored.dnsPolicies.size}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    restored.profiles
                        .filterNot {
                            it.id in setOf(
                                RoutingConfigDefaults.SYSTEM_PROFILE_ID,
                                RoutingConfigDefaults.BLOCK_PROFILE_ID,
                                RoutingConfigDefaults.BYEDPI_PROFILE_ID,
                            )
                        }
                        .take(10)
                        .forEach { profile ->
                            Text("• ${profile.name} — ${profile.type.label}", style = MaterialTheme.typography.bodySmall)
                        }
                    WarningText("После применения текущая конфигурация будет полностью заменена.")
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val nativeResult = withContext(Dispatchers.IO) {
                                    SingBoxRuntimeValidator.validate(context.applicationContext, restored)
                                }
                                nativeResult.onSuccess {
                                    clearSensitiveUiState()
                                    onConfig(
                                        restored,
                                        "Зашифрованная резервная копия восстановлена и принята сетевым движком.",
                                    )
                                }.onFailure { error ->
                                    message = "Копия не применена: ${error.localizedMessage ?: "движок отклонил конфигурацию"}"
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Заменить текущие настройки")
                    }
                }
            }
        }
        message?.let { currentMessage ->
            item {
                CardBlock {
                    Text(currentMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    clearSensitiveUiState()
                    onBack()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Назад")
            }
        }
    }
}

private suspend fun writeConfigDocument(
    context: Context,
    uri: Uri,
    bytes: ByteArray,
) = withContext(Dispatchers.IO) {
    context.contentResolver.openOutputStream(uri, "w")?.use { output ->
        output.write(bytes)
        output.flush()
    } ?: error("Android не предоставил доступ к выбранному файлу.")
}

private suspend fun readConfigDocument(
    context: Context,
    uri: Uri,
    maxBytes: Int = 16 * 1024 * 1024,
): ByteArray = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Android не предоставил доступ к выбранному файлу.")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Файл слишком большой. Максимум — 16 МБ." }
            output.write(buffer, 0, read)
        }
        output.toByteArray()
    }
}

@Composable
private fun ProfileImportScreen(
    padding: PaddingValues,
    config: RoutingConfig,
    initialSource: String,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val initialPreview = remember(initialSource) {
        initialSource.takeIf(String::isNotBlank)?.let { runCatching { previewProfileImport(it) } }
    }
    var source by rememberSaveable(initialSource) { mutableStateOf(initialSource) }
    var preview by remember(initialSource) { mutableStateOf(initialPreview?.getOrNull()) }
    var error by rememberSaveable(initialSource) {
        mutableStateOf(initialPreview?.exceptionOrNull()?.message)
    }
    var duplicateResolution by rememberSaveable { mutableStateOf(ImportDuplicateResolution.Skip) }
    var fileLoading by remember { mutableStateOf(false) }
    var qrScannerOpen by rememberSaveable { mutableStateOf(false) }

    fun refreshPreview(value: String = source) {
        preview = runCatching { previewProfileImport(value) }
            .onFailure { error = it.message ?: "Не удалось распознать данные профиля." }
            .getOrNull()
        if (preview != null) error = null
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            fileLoading = true
            scope.launch {
                runCatching { readProfileImportFile(context, uri) }
                    .onSuccess { text ->
                        source = text
                        refreshPreview(text)
                    }
                    .onFailure { throwable ->
                        preview = null
                        error = throwable.message ?: "Не удалось прочитать файл."
                    }
                fileLoading = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            qrScannerOpen = true
            error = null
        } else {
            error = "Доступ к камере не предоставлен. Разрешите его в настройках Android или используйте буфер обмена и файл."
        }
    }

    if (qrScannerOpen) {
        ProfileQrScannerScreen(
            padding = padding,
            onBack = { qrScannerOpen = false },
            onDecoded = { value ->
                source = value
                qrScannerOpen = false
                refreshPreview(value)
            },
        )
        return
    }

    ScreenList(padding) {
        item {
            Header(
                "Импорт VPN-профилей",
                "QR-код, ссылки, sing-box JSON, OpenVPN и WireGuard — с безопасным предпросмотром до сохранения.",
            )
        }
        item {
            ProfileSubscriptionsCard(
                config = config,
                onConfig = onConfig,
            )
        }
        item {
            CardBlock {
                Text(
                    "Секреты остаются только на устройстве. В предпросмотре пароли и ключи скрыты, а новые профили сначала выключены.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = {
                        source = it
                        preview = null
                        error = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp),
                    label = { Text("Ссылка, JSON или конфигурация") },
                    placeholder = { Text("vless://…\nvmess://…\n[Interface]\nPrivateKey = …\n{\n  \"type\": \"wireguard\"\n}") },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val text = clipboard.getText()?.text.orEmpty()
                            if (text.isBlank()) {
                                error = "Буфер обмена пуст."
                            } else {
                                source = text
                                refreshPreview(text)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Из буфера")
                    }
                    OutlinedButton(
                        onClick = {
                            filePicker.launch(
                                arrayOf(
                                    "text/*",
                                    "application/json",
                                    "application/x-openvpn-profile",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        enabled = !fileLoading,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (fileLoading) "Чтение…" else "Из файла")
                    }
                }
                OutlinedButton(
                    onClick = {
                        val hasCamera = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
                        if (!hasCamera) {
                            error = "На устройстве не найдена камера. Используйте буфер обмена или файл."
                        } else if (
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            qrScannerOpen = true
                            error = null
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    enabled = !fileLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Сканировать QR-код")
                }
                Text(
                    "Камера работает только на экране сканирования. Кадры обрабатываются локально, не сохраняются и не отправляются.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { refreshPreview() },
                    enabled = source.isNotBlank() && !fileLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Проверить и показать")
                }
                error?.let { WarningText(it) }
            }
        }
        preview?.let { current ->
            item {
                Text(
                    "Найдено профилей: ${current.candidates.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(
                items = current.candidates,
                key = { it.fingerprint },
            ) { candidate ->
                CardBlock {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(candidate.profile.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                candidate.profile.type.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusChip("Выключен")
                    }
                    Text(candidate.maskedPreview, style = MaterialTheme.typography.bodySmall)
                    candidate.warnings.forEach { WarningText(it) }
                }
            }
            if (current.warnings.isNotEmpty()) {
                item {
                    CardBlock {
                        Text("Замечания импорта", fontWeight = FontWeight.SemiBold)
                        current.warnings.forEach { WarningText(it) }
                    }
                }
            }
            if (!current.isEmpty) {
                item {
                    CardBlock {
                        Text("Если такой профиль уже есть", fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ImportDuplicateResolution.entries.forEach { resolution ->
                                FilterChip(
                                    selected = duplicateResolution == resolution,
                                    onClick = { duplicateResolution = resolution },
                                    label = {
                                        Text(
                                            when (resolution) {
                                                ImportDuplicateResolution.Skip -> "Пропустить"
                                                ImportDuplicateResolution.Replace -> "Обновить"
                                                ImportDuplicateResolution.Copy -> "Копия"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val result = applyProfileImport(
                                    config = config,
                                    preview = current,
                                    duplicateResolution = duplicateResolution,
                                )
                                onConfig(
                                    result.config,
                                    "Импорт завершён: добавлено ${result.added}, обновлено ${result.replaced}, пропущено ${result.skipped}. Профили сохранены выключенными.",
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Импортировать")
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Назад без сохранения")
            }
        }
    }
}

private suspend fun readProfileImportFile(
    context: Context,
    uri: Uri,
    maxBytes: Int = 2 * 1024 * 1024,
): String = withContext(Dispatchers.IO) {
    val input = context.contentResolver.openInputStream(uri)
        ?: error("Android не предоставил доступ к выбранному файлу.")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            require(total <= maxBytes) { "Файл слишком большой. Максимум — 2 МБ." }
            output.write(buffer, 0, read)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

@Composable
private fun ProfilesHeader(profileCount: Int, onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("VPN-профили", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (profileCount == 0) "Пока нет" else "Добавлено: $profileCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(6.dp))
            Text("Добавить")
        }
    }
}

@Composable
private fun AddVpnTypeSheet(
    onClose: () -> Unit,
    onImport: (String) -> Unit,
    onAddSocks5: () -> Unit,
    onAddVless: () -> Unit,
    onAddSingBox: (TunnelType) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var search by rememberSaveable { mutableStateOf("") }
    var importError by rememberSaveable { mutableStateOf<String?>(null) }
    val protocols = remember(search) {
        val query = search.trim()
        EngineCatalog.selectableProtocols.filter { descriptor ->
            query.isBlank() ||
                descriptor.type.label.contains(query, ignoreCase = true) ||
                descriptor.backend.label.contains(query, ignoreCase = true) ||
                descriptor.summary.contains(query, ignoreCase = true)
        }
    }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Выбор типа VPN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "OpenVPN, VLESS+REALITY, Hysteria2 и другие",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Закрыть")
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Найти протокол") },
            placeholder = { Text("Например: OpenVPN, WireGuard, IKEv2") },
            singleLine = true,
        )
        Text(
            "В списке показано реальное состояние движков и лицензий. Неработающий протокол нельзя случайно сохранить как активный.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FilledTonalButton(
            onClick = {
                val source = clipboard.getText()?.text.orEmpty().trim()
                if (source.isBlank()) {
                    importError = "Буфер обмена пуст."
                } else {
                    importError = null
                    onImport(source)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Вставить из буфера и распознать")
        }
        OutlinedButton(
            onClick = { onImport("") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Другой способ импорта")
        }
        importError?.let { WarningText(it) }
        protocols.forEach { protocol ->
            ProtocolCatalogRow(
                protocol = protocol,
                onAdd = when (protocol.type) {
                    TunnelType.Socks5 -> onAddSocks5
                    TunnelType.VLESS,
                    TunnelType.XrayVlessReality -> onAddVless
                    else -> if (
                        protocol.canCreateProfile &&
                        singBoxProtocolSchema(protocol.type) != null
                    ) {
                        { onAddSingBox(protocol.type) }
                    } else {
                        null
                    }
                },
            )
        }
    }
}

@Composable
private fun ProtocolCatalogRow(
    protocol: ProtocolDescriptor,
    onAdd: (() -> Unit)?,
) {
    val ready = protocol.canCreateProfile && onAdd != null
    val accent = when (protocol.readiness) {
        FeatureReadiness.ProductionReady,
        FeatureReadiness.DeviceVerified -> Color(0xFF1B7F46)
        FeatureReadiness.RuntimeIntegrated -> MaterialTheme.colorScheme.primary
        FeatureReadiness.ConfigSupported,
        FeatureReadiness.ModelOnly -> Color(0xFF8A5B00)
        FeatureReadiness.LegacyRestricted,
        FeatureReadiness.Unavailable -> Color(0xFFB3261E)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    protocol.type.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.14f))
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                ) {
                    Text(
                        when (protocol.readiness) {
                            FeatureReadiness.ProductionReady -> "Готов"
                            FeatureReadiness.DeviceVerified -> "Проверен"
                            FeatureReadiness.RuntimeIntegrated -> "Нужен тест"
                            FeatureReadiness.ConfigSupported -> "Конфигурация"
                            FeatureReadiness.ModelOnly -> "В плане"
                            FeatureReadiness.LegacyRestricted -> "Legacy"
                            FeatureReadiness.Unavailable -> "Недоступен"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text(protocol.summary, style = MaterialTheme.typography.bodySmall)
            Text(
                "Движок: ${protocol.backend.label} • лицензия: ${protocol.backend.licenseDecision}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (ready) {
                FilledTonalButton(onClick = requireNotNull(onAdd), modifier = Modifier.fillMaxWidth()) {
                    Text("Добавить профиль")
                }
            } else {
                Text(
                    protocol.readiness.userLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
            }
        }
    }
}

@Composable
private fun TcpSessionRuntimeCard(
    config: RoutingConfig,
    vpnState: VpnServiceUiState,
    devSnapshot: DevTcpBridgeSnapshot,
    message: String?,
    onOpenDevSession: (String, String, Int) -> Unit,
    onCloseDevSession: (TcpSessionId) -> Unit,
    onSendTestData: (TcpSessionId) -> Unit,
) {
    val vlessProfiles = config.profiles.filter { it.type == TunnelType.VLESS && it.vless != null && it.enabled }
    val selectedVlessProfile = vlessProfiles.firstOrNull()
    val targetHost = "example.com"
    val targetPort = 80
    CardBlock {
        Text("Dev VLESS TCP bridge", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Safe developer-only TCP stream for validating one VLESS profile. This card is separate from the active VPN router.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WarningText(DEV_TCP_BRIDGE_NOTICE)
        WarningText(DEV_TCP_BRIDGE_SECRET_NOTICE)
        Text("State: ${devSnapshot.state.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        CounterLine("bytesIn", devSnapshot.bytesIn)
        CounterLine("bytesOut", devSnapshot.bytesOut)
        CounterLine("Received byte count", devSnapshot.bytesIn)
        devSnapshot.lastEvent?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Text(
            "Test target: $targetHost:$targetPort • VLESS profile: ${selectedVlessProfile?.name ?: "none configured"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = selectedVlessProfile != null && !devSnapshot.hasOpenSession,
                onClick = { selectedVlessProfile?.let { onOpenDevSession(it.id, targetHost, targetPort) } },
            ) { Text("Open Dev TCP session") }
            OutlinedButton(
                enabled = devSnapshot.sessionId != null && devSnapshot.hasOpenSession,
                onClick = { devSnapshot.sessionId?.let(onCloseDevSession) },
            ) { Text("Close Dev TCP session") }
        }
        OutlinedButton(
            enabled = devSnapshot.sessionId != null && devSnapshot.hasOpenSession,
            onClick = { devSnapshot.sessionId?.let(onSendTestData) },
        ) { Text("Send test data") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Packet inspector remains metadata-only; Android traffic is not forwarded into this bridge.", style = MaterialTheme.typography.bodySmall)
        Text("TCP session preparation counters", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        CounterLine("Active sessions count", vpnState.activeTcpSessions.toLong())
        TcpSessionState.entries.forEach { state ->
            CounterLine(state.name, (vpnState.tcpSessionStateStats[state] ?: 0).toLong())
        }
    }
}

@Composable
private fun CounterLine(label: String, value: Long) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Text(value.toString(), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
}

private data class ProfileTunnelTestReport(
    val running: Boolean,
    val configuration: String,
    val server: String,
    val tunnel: String,
    val configurationSuccessful: Boolean? = null,
    val serverSuccessful: Boolean? = null,
    val tunnelSuccessful: Boolean? = null,
)

private data class ProfileConnectionPreflight(
    val configurationValid: Boolean,
    val configuration: String,
    val server: String,
    val serverReachable: Boolean?,
)

private data class ProfileServerEndpoint(
    val host: String,
    val port: Int,
)

private fun runProfileConnectionPreflight(profile: TunnelProfile): ProfileConnectionPreflight {
    val validationErrors = when (profile.type) {
        TunnelType.Socks5 -> profile.socks5?.let(::validateSocks5Profile)
            ?: listOf("Отсутствуют параметры SOCKS5.")
        TunnelType.VLESS -> profile.vless?.let(::validateVlessProfile)
            ?: listOf("Отсутствуют параметры VLESS.")
        TunnelType.XrayVlessReality -> validateXrayProfile(profile)
        else -> profile.singBox?.let { validateSingBoxProfile(profile.type, it) }
            ?: listOf("Профиль не содержит конфигурацию рабочего движка.")
    }
    if (validationErrors.isNotEmpty()) {
        return ProfileConnectionPreflight(
            configurationValid = false,
            configuration = "Конфигурация: ${validationErrors.joinToString(" ").take(500)}",
            server = "Сервер: не проверялся.",
            serverReachable = null,
        )
    }

    val configurationLine = if (profile.enabled) {
        "Конфигурация: корректна, профиль включён."
    } else {
        "Конфигурация: корректна, но профиль выключен."
    }
    val endpoint = profile.serverEndpoint()
        ?: return ProfileConnectionPreflight(
            configurationValid = true,
            configuration = configurationLine,
            server = "Сервер: отдельный адрес скрыт внутри формата профиля; его проверит движок.",
            serverReachable = null,
        )
    if (profile.usesUdpTransport()) {
        return ProfileConnectionPreflight(
            configurationValid = true,
            configuration = configurationLine,
            server = "Сервер: ${endpoint.host}:${endpoint.port}; для UDP отдельная TCP-проверка неприменима.",
            serverReachable = null,
        )
    }

    val startedAt = System.nanoTime()
    return runCatching {
        Socket().use { socket ->
            socket.connect(
                InetSocketAddress(endpoint.host, endpoint.port),
                PROFILE_SERVER_CONNECT_TIMEOUT_MS,
            )
        }
        val latency = (System.nanoTime() - startedAt) / 1_000_000L
        ProfileConnectionPreflight(
            configurationValid = true,
            configuration = configurationLine,
            server = "Сервер: ${endpoint.host}:${endpoint.port} доступен по TCP (${latency} мс).",
            serverReachable = true,
        )
    }.getOrElse { error ->
        ProfileConnectionPreflight(
            configurationValid = true,
            configuration = configurationLine,
            server = "Сервер: ${endpoint.host}:${endpoint.port} не ответил по TCP: " +
                (error.localizedMessage ?: error::class.java.simpleName).take(240),
            serverReachable = false,
        )
    }
}

private fun TunnelProfile.serverEndpoint(): ProfileServerEndpoint? {
    socks5?.let { return ProfileServerEndpoint(it.host.trim(), it.port) }
    vless?.let { return ProfileServerEndpoint(it.host.trim(), it.port) }
    val root = singBox?.optionsJson?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
    return root.findServerEndpoint()
}

private fun Any?.findServerEndpoint(): ProfileServerEndpoint? = when (this) {
    is JSONObject -> {
        val host = optString("server").takeIf(String::isNotBlank)
            ?: optString("address").takeIf(String::isNotBlank)
        val port = when {
            optInt("server_port") in 1..65535 -> optInt("server_port")
            optInt("port") in 1..65535 -> optInt("port")
            else -> 0
        }
        if (host != null && port > 0) {
            ProfileServerEndpoint(host, port)
        } else {
            keys().asSequence().mapNotNull { key -> opt(key).findServerEndpoint() }.firstOrNull()
        }
    }
    is JSONArray -> (0 until length()).asSequence()
        .mapNotNull { index -> opt(index).findServerEndpoint() }
        .firstOrNull()
    else -> null
}

private fun TunnelProfile.usesUdpTransport(): Boolean = type in setOf(
    TunnelType.WireGuard,
    TunnelType.Hysteria,
    TunnelType.Hysteria2,
    TunnelType.Tuic,
    TunnelType.Ikev2IpSec,
    TunnelType.IpSecXAuth,
    TunnelType.IpSecPsk,
)

private const val PROFILE_SERVER_CONNECT_TIMEOUT_MS = 4_000

@Composable
private fun CompactNetworkProfileCard(
    text: UiText,
    profile: TunnelProfile,
    config: RoutingConfig,
    testReport: ProfileTunnelTestReport?,
    onTest: () -> Unit,
    onOpen: () -> Unit,
) {
    val routeCount = config.rules.count { it.targetProfileId == profile.id && it.type != RouteRuleType.DEFAULT }
    val context = LocalContext.current
    val historyStore = remember(context) { Socks5TestHistoryStore(context) }
    var readiness by remember(profile.id) { mutableStateOf<Socks5ReadinessSummary?>(null) }
    LaunchedEffect(profile.id, profile.socks5?.status) {
        readiness = if (profile.type == TunnelType.Socks5) {
            deriveSocks5ReadinessSummary(historyStore.recentForProfile(profile.id))
        } else {
            null
        }
    }
    CardBlock {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(profile.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (config.defaultProfileId == profile.id) StatusChip(text.defaultProfile)
                }
                Text(profile.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text.assignedRoutesCount(routeCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                readiness?.let { summary ->
                    Text(summary.compactListLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    summary.lastConnectSuccessLine?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } ?: profile.socks5?.let { Text(it.status.safeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusChip(if (profile.enabled) text.on else text.off)
        }
        OutlinedButton(
            onClick = onTest,
            enabled = testReport?.running != true,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (testReport?.running == true) "Проверяем…" else "Проверить тоннель")
        }
        testReport?.let { report ->
            @Composable
            fun resultColor(successful: Boolean?): Color = when (successful) {
                true -> Color(0xFF1B7F46)
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(
                report.configuration,
                style = MaterialTheme.typography.labelSmall,
                color = resultColor(report.configurationSuccessful),
            )
            Text(
                report.server,
                style = MaterialTheme.typography.labelSmall,
                color = resultColor(report.serverSuccessful),
            )
            Text(
                report.tunnel,
                style = MaterialTheme.typography.labelSmall,
                color = resultColor(report.tunnelSuccessful),
            )
        }
    }
}

@Composable
private fun NetworkProfileDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    profile: TunnelProfile,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var description by rememberSaveable(profile.id) { mutableStateOf(profile.description) }
    var enabled by rememberSaveable(profile.id) { mutableStateOf(profile.enabled) }
    if (profile.type == TunnelType.Socks5) {
        Socks5ProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            profile = profile,
            onBack = onBack,
            onConfig = onConfig,
        )
        return
    }
    if (profile.type == TunnelType.VLESS || profile.type == TunnelType.XrayVlessReality) {
        VlessProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            profile = profile,
            onBack = onBack,
            onConfig = onConfig,
        )
        return
    }
    if (profile.singBox != null) {
        SingBoxProfileEditorScreen(
            padding = padding,
            text = text,
            config = config,
            type = profile.type,
            profile = profile,
            onBack = onBack,
            onConfig = onConfig,
        )
        return
    }
    val dns = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }
    val usedRuleNames = config.rules.filter { it.targetProfileId == profile.id }.map { it.name }
    val protectedProfile = profile.type in listOf(TunnelType.Direct, TunnelType.Block, TunnelType.ByeDpi)
    val canDelete = !protectedProfile && usedRuleNames.isEmpty()

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(text.profileDetails, text.profileDetailsSubtitle)
            }
        }
        item {
            CardBlock {
                Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(profile.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (config.defaultProfileId == profile.id) {
                    StatusChip(text.defaultProfile)
                } else {
                    OutlinedButton(onClick = { onConfig(config.withDefaultRoute(profile.id), text.defaultChanged) }) { Text(text.makeDefault) }
                }
                StatusChip("DNS: ${dns?.name ?: text.noDns}")
            }
        }
        item {
            CardBlock {
                Text(text.description, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Text(profile.description.ifBlank { text.none }, style = MaterialTheme.typography.bodySmall)
                profile.warningText?.let { WarningText(it) }
                Details(text.details, text.profileAdvancedDetails)
            }
        }
        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text(text.description) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    onConfig(
                        config.copy(profiles = config.profiles.map {
                            if (it.id == profile.id) it.copy(name = name.ifBlank { profile.name }, description = description, enabled = enabled) else it
                        }),
                        text.profileUpdated,
                    )
                    onBack()
                }) { Text(text.save) }
            }
        }
        item {
            CardBlock {
                if (protectedProfile) WarningText(text.protectedProfileMessage)
                if (usedRuleNames.isNotEmpty()) WarningText(text.profileUsedMessage(usedRuleNames.joinToString(" • ")))
                OutlinedButton(
                    enabled = canDelete,
                    onClick = { onConfig(config.withoutProfile(profile.id), text.profileDeleted) },
                ) { Text(text.delete) }
            }
        }
    }
}

private val VpnServiceUiState.switchChecked: Boolean
    get() = status == VpnServiceStatus.Starting ||
        status == VpnServiceStatus.RuntimeActive ||
        status == VpnServiceStatus.ServiceActiveNoTun ||
        status == VpnServiceStatus.TunPreviewActive ||
        status == VpnServiceStatus.TunTestRouteActive

private fun VpnServiceUiState.label(text: UiText): String = when (status) {
    VpnServiceStatus.Off -> text.off
    VpnServiceStatus.PermissionRequired -> text.vpnPermissionRequired
    VpnServiceStatus.NotificationPermissionRequired -> text.vpnNotificationPermissionRequired
    VpnServiceStatus.Starting -> text.vpnStarting
    VpnServiceStatus.RuntimeActive -> "VPN router active"
    VpnServiceStatus.ServiceActiveNoTun -> text.vpnLocalServiceActive
    VpnServiceStatus.TunPreviewActive -> text.vpnTunPreviewActive
    VpnServiceStatus.TunTestRouteActive -> text.vpnTunPreviewActive
    VpnServiceStatus.Stopped -> text.vpnStopped
    VpnServiceStatus.Error -> text.vpnError
}

@Composable
private fun SingBoxProfileEditorScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    type: TunnelType,
    profile: TunnelProfile?,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val schema = requireNotNull(singBoxProtocolSchema(type))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by rememberSaveable(profile?.id ?: "new-${type.name}") {
        mutableStateOf(profile?.name ?: type.label)
    }
    var enabled by rememberSaveable(profile?.id ?: "new-${type.name}-enabled") {
        mutableStateOf(profile?.enabled ?: true)
    }
    var dnsPolicyId by rememberSaveable(profile?.id ?: "new-${type.name}-dns") {
        mutableStateOf(profile?.dnsPolicyId ?: RoutingConfigDefaults.SYSTEM_DNS_ID)
    }
    var optionsJson by rememberSaveable(profile?.id ?: "new-${type.name}-json") {
        mutableStateOf(profile?.singBox?.optionsJson ?: singBoxProfileTemplate(type))
    }
    var openVpnUsername by rememberSaveable(profile?.id ?: "new-${type.name}-username") {
        mutableStateOf(openVpnRoot(optionsJson).optString("username"))
    }
    var openVpnPassword by rememberSaveable(profile?.id ?: "new-${type.name}-password") {
        mutableStateOf(openVpnRoot(optionsJson).optString("password"))
    }
    var errors by remember(profile?.id ?: "new-${type.name}-errors") {
        mutableStateOf(emptyList<String>())
    }
    var nativeCheckMessage by remember(profile?.id ?: "new-${type.name}-native-check") {
        mutableStateOf<String?>(null)
    }
    var checking by remember(profile?.id ?: "new-${type.name}-checking") {
        mutableStateOf(false)
    }

    fun importOpenVpnMaterial(
        uri: Uri?,
        field: String,
        label: String,
        validator: (String) -> Boolean,
    ) {
        if (uri == null) return
        scope.launch {
            runCatching { readProfileImportFile(context, uri) }
                .map { it.trim() }
                .onSuccess { content ->
                    if (!validator(content)) {
                        nativeCheckMessage = "$label не похож на поддерживаемый PEM-файл."
                    } else {
                        optionsJson = updateOpenVpnTlsMaterial(optionsJson, field, content)
                        errors = emptyList()
                        nativeCheckMessage = "$label добавлен в профиль. Содержимое хранится в зашифрованном хранилище приложения."
                    }
                }
                .onFailure { error ->
                    nativeCheckMessage = "Не удалось прочитать $label: ${error.localizedMessage ?: "неизвестная ошибка"}"
                }
        }
    }

    val openVpnCaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        importOpenVpnMaterial(uri, "certificate", "CA-сертификат") {
            it.contains("-----BEGIN CERTIFICATE-----") && it.contains("-----END CERTIFICATE-----")
        }
    }
    val openVpnClientCertificateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        importOpenVpnMaterial(uri, "client_certificate", "Клиентский сертификат") {
            it.contains("-----BEGIN CERTIFICATE-----") && it.contains("-----END CERTIFICATE-----")
        }
    }
    val openVpnClientKeyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        importOpenVpnMaterial(uri, "client_key", "Закрытый ключ") {
            OPENVPN_PRIVATE_KEY_PATTERN.containsMatchIn(it) &&
                !it.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----")
        }
    }
    val openVpnImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val source = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader(Charsets.UTF_8)
                        ?.use { it.readText() }
                        ?: error("Android не смог прочитать выбранный файл.")
                    importOpenVpnProfile(source)
                }
            }
            imported.onSuccess { result ->
                optionsJson = result.optionsJson
                openVpnRoot(result.optionsJson).let { root ->
                    openVpnUsername = root.optString("username")
                    openVpnPassword = root.optString("password")
                }
                errors = emptyList()
                nativeCheckMessage = if (result.warnings.isEmpty()) {
                    "Профиль .ovpn импортирован. Нажмите «Проверить»."
                } else {
                    "Профиль импортирован. Проверьте замечания: ${result.warnings.joinToString(" ")}"
                }
            }.onFailure { error ->
                nativeCheckMessage = "Не удалось импортировать .ovpn: ${error.localizedMessage ?: "неизвестная ошибка"}"
            }
        }
    }
    val usedRules = profile?.let { current ->
        config.rules.filter { it.targetProfileId == current.id }.map { it.name }
    }.orEmpty()
    val openVpnTls = remember(optionsJson) {
        openVpnRoot(optionsJson).optJSONObject("tls")
    }
    val hasOpenVpnCa = openVpnTls.hasOpenVpnMaterial("certificate")
    val hasOpenVpnClientCertificate = openVpnTls.hasOpenVpnMaterial("client_certificate")
    val hasOpenVpnClientKey = openVpnTls.hasOpenVpnMaterial("client_key")

    fun validateDraft(): SingBoxProfileConfig {
        val draft = SingBoxProfileConfig(schema.kind, optionsJson.trim())
        errors = validateSingBoxProfile(type, draft)
        return draft
    }

    fun configWithDraft(draft: SingBoxProfileConfig): RoutingConfig {
        val nextProfile = TunnelProfile(
            id = profile?.id ?: "engine_${UUID.randomUUID()}",
            name = name.trim().ifBlank { type.label },
            type = type,
            description = "${type.label} через локальный sing-box runtime.",
            enabled = enabled,
            mockOnly = false,
            platformNotes = "Проверенный ${schema.kind.name.lowercase()} sing-box ${schema.engineType}.",
            dnsPolicyId = dnsPolicyId,
            singBox = draft,
        )
        val nextProfiles = if (profile == null) {
            config.profiles + nextProfile
        } else {
            config.profiles.map { if (it.id == profile.id) nextProfile else it }
        }
        return config.copy(profiles = nextProfiles)
    }

    fun runNativeCheck(saveAfterCheck: Boolean) {
        val draft = validateDraft()
        if (errors.isNotEmpty()) return
        val nextConfig = configWithDraft(draft)
        checking = true
        nativeCheckMessage = "Проверка всей конфигурации движком…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                SingBoxRuntimeValidator.validate(context.applicationContext, nextConfig)
            }
            checking = false
            result.onSuccess { warnings ->
                nativeCheckMessage = if (warnings.isEmpty()) {
                    "Движок принял профиль и полную конфигурацию маршрутов."
                } else {
                    "Движок принял профиль. Предупреждения: ${warnings.take(2).joinToString(" ")}"
                }
                if (saveAfterCheck) {
                    onConfig(
                        nextConfig,
                        "${type.label}: профиль проверен движком и сохранён. При активном VPN маршрутизатор перезагрузится.",
                    )
                    onBack()
                }
            }.onFailure { error ->
                nativeCheckMessage = "Движок отклонил профиль: ${error.localizedMessage ?: "неизвестная ошибка"}"
            }
        }
    }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(type.label, "Профиль локального sing-box runtime")
            }
        }
        item {
            CardBlock {
                Text("Что нужно", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(schema.beginnerHint, style = MaterialTheme.typography.bodySmall)
                Text(
                    "Вставляется один объект ${schema.kind.name.lowercase()}; полная конфигурация с route/dns/inbounds не принимается. ViRouteFS сам добавит TUN, DNS и правила.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WarningText(
                    "Ключи и пароли остаются только в локальной конфигурации приложения. Резервное копирование Android отключено; в журнал они не выводятся.",
                )
                if (type == TunnelType.OpenVpn) {
                    OutlinedButton(
                        onClick = {
                            openVpnImportLauncher.launch(
                                arrayOf(
                                    "application/x-openvpn-profile",
                                    "text/plain",
                                    "application/octet-stream",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Импортировать файл .ovpn")
                    }
                    Text(
                        "Обычные remote/proto, сертификаты и ключи из inline-блоков будут перенесены автоматически. Если .ovpn ссылается на отдельные файлы, добавьте их ниже. Перед сохранением результат всё равно проверит нативный движок.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (type == TunnelType.OpenVpn) {
            item {
                CardBlock {
                    Text("Авторизация OpenVPN", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Логин и пароль нужны только если их требует провайдер. Пароль и полный JSON профиля сохраняются через Android Keystore.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = openVpnUsername,
                        onValueChange = { value ->
                            openVpnUsername = value
                            optionsJson = updateOpenVpnCredential(optionsJson, "username", value)
                        },
                        label = { Text("Логин") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = openVpnPassword,
                        onValueChange = { value ->
                            openVpnPassword = value
                            optionsJson = updateOpenVpnCredential(optionsJson, "password", value)
                        },
                        label = { Text("Пароль") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
            item {
                CardBlock {
                    Text("Сертификаты OpenVPN", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Поддерживаются текстовые PEM-файлы .pem/.crt/.cer/.key размером до 2 МБ. Зашифрованный закрытый ключ без отдельной поддержки passphrase не принимается.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OpenVpnMaterialPicker(
                        label = "CA-сертификат",
                        loaded = hasOpenVpnCa,
                        required = true,
                        onPick = {
                            openVpnCaLauncher.launch(OPENVPN_PEM_MIME_TYPES)
                        },
                        onRemove = {
                            optionsJson = updateOpenVpnTlsMaterial(optionsJson, "certificate", null)
                        },
                    )
                    OpenVpnMaterialPicker(
                        label = "Клиентский сертификат",
                        loaded = hasOpenVpnClientCertificate,
                        required = false,
                        onPick = {
                            openVpnClientCertificateLauncher.launch(OPENVPN_PEM_MIME_TYPES)
                        },
                        onRemove = {
                            optionsJson = updateOpenVpnTlsMaterial(optionsJson, "client_certificate", null)
                        },
                    )
                    OpenVpnMaterialPicker(
                        label = "Закрытый ключ клиента",
                        loaded = hasOpenVpnClientKey,
                        required = false,
                        onPick = {
                            openVpnClientKeyLauncher.launch(OPENVPN_PEM_MIME_TYPES)
                        },
                        onRemove = {
                            optionsJson = updateOpenVpnTlsMaterial(optionsJson, "client_key", null)
                        },
                    )
                    if (hasOpenVpnClientCertificate != hasOpenVpnClientKey) {
                        WarningText("Клиентский сертификат и закрытый ключ нужно добавить вместе.")
                    }
                }
            }
        }
        item {
            CardBlock {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(text.name) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text.enabled, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (enabled) "Профиль может использоваться правилами." else "Правила на него сработают как Block.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("DNS этого VPN", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    config.dnsPolicies.filter { it.enabled }.forEach { policy ->
                        FilterChip(
                            selected = dnsPolicyId == policy.id,
                            onClick = { dnsPolicyId = policy.id },
                            label = { Text(policy.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = optionsJson,
                    onValueChange = {
                        optionsJson = it
                        errors = emptyList()
                        nativeCheckMessage = null
                    },
                    label = { Text("${schema.kind.name} JSON") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 10,
                    maxLines = 20,
                    supportingText = {
                        Text("Ожидаемый type: ${schema.engineType}. Поле tag будет назначено автоматически.")
                    },
                )
                errors.forEach { WarningText(it) }
                nativeCheckMessage?.let { message ->
                    if (message.startsWith("Движок принял")) {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF1B7F46))
                    } else {
                        WarningText(message)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = !checking,
                        onClick = { runNativeCheck(saveAfterCheck = false) },
                    ) { Text(if (checking) "Проверка…" else "Проверить") }
                    Button(
                        enabled = !checking,
                        onClick = { runNativeCheck(saveAfterCheck = true) },
                    ) { Text(text.save) }
                }
            }
        }
        if (profile != null) {
            item {
                CardBlock {
                    if (config.defaultProfileId == profile.id) {
                        StatusChip(text.defaultProfile)
                    } else {
                        OutlinedButton(
                            onClick = {
                                onConfig(config.withDefaultRoute(profile.id), text.defaultChanged)
                            },
                        ) { Text(text.makeDefault) }
                    }
                    if (usedRules.isNotEmpty()) {
                        WarningText(text.profileUsedMessage(usedRules.joinToString(" • ")))
                    }
                    OutlinedButton(
                        enabled = usedRules.isEmpty(),
                        onClick = {
                            onConfig(
                                config.withoutProfile(profile.id),
                                text.profileDeleted,
                            )
                            onBack()
                        },
                    ) { Text(text.delete) }
                }
            }
        }
    }
}

@Composable
private fun OpenVpnMaterialPicker(
    label: String,
    loaded: Boolean,
    required: Boolean,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                when {
                    loaded -> "Добавлен"
                    required -> "Обязателен"
                    else -> "Не выбран"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (loaded) Color(0xFF1B7F46) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onPick) {
            Text(if (loaded) "Заменить" else "Выбрать")
        }
        if (loaded) {
            TextButton(onClick = onRemove) { Text("Убрать") }
        }
    }
}

private fun openVpnRoot(source: String): JSONObject =
    runCatching { JSONObject(source) }.getOrElse {
        JSONObject().put("type", "openvpn-client")
    }

private fun updateOpenVpnCredential(
    source: String,
    field: String,
    value: String,
): String = openVpnRoot(source)
    .apply {
        if (value.isBlank()) remove(field) else put(field, value)
    }
    .toString(2)

private fun updateOpenVpnTlsMaterial(
    source: String,
    field: String,
    value: String?,
): String = openVpnRoot(source)
    .apply {
        val tls = optJSONObject("tls") ?: JSONObject().also { put("tls", it) }
        if (value.isNullOrBlank()) tls.remove(field) else tls.put(field, value)
    }
    .toString(2)

private fun JSONObject?.hasOpenVpnMaterial(field: String): Boolean {
    if (this == null) return false
    return when (val value = opt(field)) {
        is String -> value.isNotBlank()
        is org.json.JSONArray -> value.length() > 0
        else -> value != null && value != JSONObject.NULL
    }
}

private val OPENVPN_PEM_MIME_TYPES = arrayOf(
    "application/x-pem-file",
    "application/pkix-cert",
    "application/x-x509-ca-cert",
    "text/plain",
    "application/octet-stream",
)

private val OPENVPN_PRIVATE_KEY_PATTERN = Regex(
    """-----BEGIN (?:RSA |EC )?PRIVATE KEY-----""",
)

@Composable
private fun VlessProfileEditorScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    profile: TunnelProfile?,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val vless = profile?.vless
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyStore = remember(context) { VlessTcpReachabilityHistoryStore(context) }
    val protocolProbeHistoryStore = remember(context) { VlessProtocolProbeHistoryStore(context) }
    val clipboardManager = LocalClipboardManager.current
    var name by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.name ?: profile?.name ?: "") }
    var host by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.host ?: "") }
    var portText by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.port?.toString() ?: "443") }
    var uuid by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.uuid.orEmpty()) }
    var transportType by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.transportType.orEmpty()) }
    var securityModeText by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.securityMode?.wireName ?: VlessSecurityMode.NONE.wireName) }
    var encryption by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.encryption.orEmpty()) }
    var flow by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.flow.orEmpty()) }
    var sni by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.sni.orEmpty()) }
    var publicKey by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.publicKey.orEmpty()) }
    var shortId by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.shortId.orEmpty()) }
    var fingerprint by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.fingerprint.orEmpty()) }
    var path by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.path.orEmpty()) }
    var hostHeader by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.hostHeader.orEmpty()) }
    var alpn by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.alpn.orEmpty()) }
    var serviceName by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.serviceName.orEmpty()) }
    var xhttpMode by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.xhttpMode.orEmpty()) }
    var xhttpExtra by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.xhttpExtra.orEmpty()) }
    var pinnedPeerCertSha256 by rememberSaveable(profile?.id ?: "new-vless") {
        mutableStateOf(vless?.pinnedPeerCertSha256.orEmpty())
    }
    var verifyPeerCertByName by rememberSaveable(profile?.id ?: "new-vless") {
        mutableStateOf(vless?.verifyPeerCertByName)
    }
    var enabled by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.enabled ?: profile?.enabled ?: true) }
    var dnsPolicyId by rememberSaveable(profile?.id ?: "new-vless-dns") {
        mutableStateOf(profile?.dnsPolicyId ?: RoutingConfigDefaults.SYSTEM_DNS_ID)
    }
    var importUri by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf("") }
    var pendingImport by remember(profile?.id ?: "new-vless") { mutableStateOf<VlessProfileConfig?>(null) }
    var importPreview by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf<String?>(null) }
    var exportUri by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf<String?>(null) }
    var currentReachability by remember(profile?.id ?: "new-vless") { mutableStateOf<VlessTcpReachabilityResult?>(null) }
    var history by remember(profile?.id ?: "new-vless") { mutableStateOf<List<VlessTcpReachabilityHistoryItem>>(emptyList()) }
    var probeTargetHost by rememberSaveable(profile?.id ?: "new-vless-probe-target-host") { mutableStateOf("example.com") }
    var probeTargetPortText by rememberSaveable(profile?.id ?: "new-vless-probe-target-port") { mutableStateOf("80") }
    var currentProtocolProbe by remember(profile?.id ?: "new-vless-probe") { mutableStateOf<VlessProtocolProbeResult?>(null) }
    var protocolProbeHistory by remember(profile?.id ?: "new-vless-probe-history") { mutableStateOf<List<VlessProtocolProbeHistoryItem>>(emptyList()) }
    var errors by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf<List<String>>(emptyList()) }

    fun draft(nextStatus: VlessProfileStatus = vless?.status ?: VlessProfileStatus.NotTested): VlessProfileConfig = VlessProfileConfig(
        name = name.trim(),
        host = host.trim(),
        port = portText.toIntOrNull() ?: -1,
        uuid = uuid.trim(),
        transportType = transportType.trim().takeIf { it.isNotBlank() }?.lowercase(),
        securityMode = securityModeText.toVlessSecurityMode(),
        encryption = encryption.trim().takeIf { it.isNotBlank() },
        flow = flow.trim().takeIf { it.isNotBlank() },
        sni = sni.trim().takeIf { it.isNotBlank() },
        publicKey = publicKey.trim().takeIf { it.isNotBlank() },
        shortId = shortId.trim().takeIf { it.isNotBlank() },
        fingerprint = fingerprint.trim().takeIf { it.isNotBlank() },
        path = path.trim().takeIf { it.isNotBlank() },
        hostHeader = hostHeader.trim().takeIf { it.isNotBlank() },
        alpn = alpn.trim().takeIf { it.isNotBlank() },
        serviceName = serviceName.trim().takeIf { it.isNotBlank() },
        xhttpMode = xhttpMode.trim().takeIf { it.isNotBlank() },
        xhttpExtra = xhttpExtra.trim().takeIf { it.isNotBlank() },
        pinnedPeerCertSha256 = pinnedPeerCertSha256.trim().takeIf { it.isNotBlank() },
        verifyPeerCertByName = verifyPeerCertByName,
        enabled = enabled,
        status = nextStatus,
    )

    LaunchedEffect(profile?.id) {
        if (profile != null) {
            history = historyStore.recentForProfile(profile.id)
            protocolProbeHistory = protocolProbeHistoryStore.recentForProfile(profile.id)
        }
    }

    fun applyVlessProfile(parsed: VlessProfileConfig) {
        name = parsed.name
        host = parsed.host
        portText = parsed.port.toString()
        uuid = parsed.uuid
        transportType = parsed.transportType.orEmpty()
        securityModeText = parsed.securityMode.wireName
        encryption = parsed.encryption.orEmpty()
        flow = parsed.flow.orEmpty()
        sni = parsed.sni.orEmpty()
        publicKey = parsed.publicKey.orEmpty()
        shortId = parsed.shortId.orEmpty()
        fingerprint = parsed.fingerprint.orEmpty()
        path = parsed.path.orEmpty()
        hostHeader = parsed.hostHeader.orEmpty()
        alpn = parsed.alpn.orEmpty()
        serviceName = parsed.serviceName.orEmpty()
        xhttpMode = parsed.xhttpMode.orEmpty()
        xhttpExtra = parsed.xhttpExtra.orEmpty()
        pinnedPeerCertSha256 = parsed.pinnedPeerCertSha256.orEmpty()
        verifyPeerCertByName = parsed.verifyPeerCertByName
        pendingImport = null
        importPreview = parsed.maskedPreview()
        exportUri = null
    }

    fun saveVlessStatus(nextStatus: VlessProfileStatus) {
        if (profile == null) return
        val updatedVless = draft(nextStatus)
        val updatedProfile = profile.copy(
            name = updatedVless.name,
            description = vlessDescription(updatedVless),
            enabled = updatedVless.enabled,
            vless = updatedVless,
        )
        onConfig(config.copy(profiles = config.profiles.map { if (it.id == profile.id) updatedProfile else it }), null)
    }

    suspend fun recordReachabilityHistory(result: VlessTcpReachabilityResult) {
        if (profile == null) return
        historyStore.add(
            VlessTcpReachabilityHistoryItem(
                profileId = profile.id,
                profileNameSnapshot = name.trim(),
                host = result.host,
                port = result.port,
                timestamp = result.timestamp,
                state = result.state,
                message = result.message,
                elapsedMs = result.elapsedMs,
            ),
        )
        history = historyStore.recentForProfile(profile.id)
    }

    suspend fun recordProtocolProbeHistory(result: VlessProtocolProbeResult) {
        if (profile == null) return
        protocolProbeHistoryStore.add(
            VlessProtocolProbeHistoryItem(
                profileId = profile.id,
                profileNameSnapshot = name.trim(),
                serverHost = result.serverHost,
                serverPort = result.serverPort,
                targetHost = result.targetHost,
                targetPort = result.targetPort,
                timestamp = result.timestamp,
                state = result.state,
                message = result.message,
                elapsedMs = result.elapsedMs,
                securityMode = result.securityMode,
                responseBytes = result.responseBytes,
                classification = result.classification,
            ),
        )
        protocolProbeHistory = protocolProbeHistoryStore.recentForProfile(profile.id)
    }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(
                    if (profile == null) "Add VLESS profile" else "Edit VLESS profile",
                    "VLESS через sing-box; XHTTP через отдельный локальный Xray-core",
                )
            }
        }
        item {
            CardBlock {
                Text(VLESS_RUNTIME_LIMITATION, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(VLESS_ROUTE_PREVIEW_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(VLESS_NO_HANDSHAKE_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(VLESS_TCP_REACHABILITY_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WarningText("UUID и XHTTP extra хранятся локально в зашифрованном хранилище Android Keystore и скрыты из обычного файла настроек, сводок и диагностики.")
                Text("Security mode supports none, TLS and REALITY. Incomplete REALITY parameters fail closed at activation.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CardBlock {
                Text("Import / Export VLESS URI", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Paste a vless:// URI to fill this local profile. Connection tests run only on request; routing starts only with the main VPN switch.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    importUri,
                    { value ->
                        importUri = value
                        pendingImport = null
                        importPreview = null
                        exportUri = null
                    },
                    label = { Text("Paste URI") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedButton(onClick = {
                    when (val result = parseVlessUri(importUri)) {
                        is VlessUriParseResult.Success -> {
                            errors = emptyList()
                            pendingImport = result.profile
                            importPreview = result.profile.maskedPreview()
                            exportUri = null
                        }
                        is VlessUriParseResult.Error -> {
                            errors = result.messages
                            pendingImport = null
                            importPreview = null
                        }
                    }
                }) { Text("Preview URI") }
                importPreview?.let { preview ->
                    Text("Preview with masked UUID", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                    Details("Preview with masked UUID", preview)
                }
                pendingImport?.let { parsed ->
                    Button(onClick = { applyVlessProfile(parsed) }) { Text("Apply imported profile") }
                }
            }
        }
        item {
            CardBlock {
                Text("Readiness: ${vlessReadinessLabel(vless?.status ?: VlessProfileStatus.NotTested, history)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Test TCP reachability opens a plain TCP socket to host:port, closes it immediately after connect, and sends no bytes or UUID.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = profile != null && vless?.status != VlessProfileStatus.Testing,
                    onClick = {
                        val testDraft = draft(VlessProfileStatus.Testing)
                        errors = validateVlessProfile(testDraft)
                        if (errors.isEmpty()) {
                            saveVlessStatus(VlessProfileStatus.Testing)
                            scope.launch {
                                val result = VlessTcpReachabilityTester().test(testDraft.host, testDraft.port)
                                currentReachability = result
                                saveVlessStatus(result.toProfileStatus())
                                recordReachabilityHistory(result)
                            }
                        }
                    },
                ) { Text("Test TCP reachability") }
                currentReachability?.let { Text("Current result: ${it.displayMessage}", style = MaterialTheme.typography.bodySmall) }
                if (profile == null) {
                    Text("Save the VLESS profile first so the manual result can be stored in local no-backup history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("This button checks TCP reachability only: it does not perform a VLESS/TLS/REALITY handshake. The saved profile is used separately by the VPN router.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        val probeDraft = draft()
        val supportsManualProtocolProbe = probeDraft.transportType.isNullOrBlank() ||
            probeDraft.transportType.equals("tcp", ignoreCase = true) ||
            probeDraft.transportType.equals("raw", ignoreCase = true)
        if (supportsManualProtocolProbe) {
            item {
                CardBlock {
                    Text("Manual VLESS protocol probe", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    WarningText(VLESS_PROTOCOL_PROBE_NOTICE)
                val probeSecurityMode = probeDraft.securityMode
                Text("Current security mode: ${probeSecurityMode.wireName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (probeSecurityMode == VlessSecurityMode.TLS) {
                    Text("SNI used for TLS: ${probeDraft.vlessProbeSniHost()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("security=none uses the existing plain TCP probe path. security=tls opens TLS first, sends the same minimal VLESS request frame, and sends no HTTP payload. REALITY reports: $VLESS_REALITY_UNSUPPORTED_MESSAGE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WarningText(VLESS_RESPONSE_PROBE_METADATA_NOTICE)
                OutlinedTextField(probeTargetHost, { probeTargetHost = it }, label = { Text("Target host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(probeTargetPortText, { probeTargetPortText = it }, label = { Text("Target port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(
                    enabled = profile != null && vless?.status != VlessProfileStatus.Testing,
                    onClick = {
                        val candidate = draft(VlessProfileStatus.Testing)
                        val targetPort = probeTargetPortText.toIntOrNull() ?: -1
                        errors = validateVlessProfile(candidate)
                        if (errors.isEmpty()) {
                            saveVlessStatus(VlessProfileStatus.Testing)
                            scope.launch {
                                val result = VlessProtocolProber().probe(candidate, probeTargetHost, targetPort)
                                currentProtocolProbe = result
                                saveVlessStatus(result.toProfileStatus())
                                recordProtocolProbeHistory(result)
                            }
                        }
                    },
                ) { Text("Run VLESS probe") }
                currentProtocolProbe?.let { result ->
                    Text("Response classification: ${result.classification.label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    Text("Result: ${result.displayMessage}", style = MaterialTheme.typography.bodySmall)
                    Text("Response bytes: ${result.responseBytes}", style = MaterialTheme.typography.bodySmall)
                    result.elapsedMs?.let { Text("Elapsed: $it ms", style = MaterialTheme.typography.bodySmall) }
                    if (result.steps.isNotEmpty()) {
                        Text("States: ${result.steps.joinToString(" → ") { it.label }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (profile == null) {
                    Text("Save the VLESS profile first so the manual protocol probe result can be stored in local no-backup history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("The probe sends only the locally built VLESS TCP request frame and no HTTP payload, Android traffic, DNS proxy traffic, or packets back to TUN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Only elapsed time, response classification, response byte count, target, and security mode are stored; response payload bytes are discarded immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            item {
                CardBlock {
                    Text("Проверка профиля", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Для транспорта ${probeDraft.transportType.orEmpty().uppercase()} простая TCP-проба неприменима: она не воспроизводит транспортный обмен и могла бы дать ложный результат. Полная проверка выполняется профильным движком при запуске маршрутизатора.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (profile != null) {
            item {
                CardBlock {
                    Text("Last manual VLESS protocol probe history", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text("Stored locally in app no-backup storage; newest first, last 20 per profile. UUID, raw frame bytes, and response payload bytes are never stored in this history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (protocolProbeHistory.isEmpty()) {
                        Text(text.none, style = MaterialTheme.typography.bodySmall)
                    } else {
                        protocolProbeHistory.forEach { item ->
                            Text(item.historyLabel(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (profile != null) {
            item {
                CardBlock {
                    Text("Recent local VLESS TCP reachability history", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text("Stored locally in app no-backup storage; newest first, last 20 per profile. UUID is never stored in this history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history.isEmpty()) {
                        Text(text.none, style = MaterialTheme.typography.bodySmall)
                    } else {
                        history.forEach { item ->
                            Text(item.historyLabel(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            historyStore.clearProfile(profile.id)
                            history = emptyList()
                        }
                    }) { Text("Clear local VLESS TCP history for this profile") }
                }
            }
        }

        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    uuid,
                    { uuid = it },
                    label = { Text("UUID (hidden)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(transportType, { transportType = it.lowercase().take(16) }, label = { Text("Transport: tcp/raw/ws/grpc/xhttp") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(securityModeText, { securityModeText = it.lowercase().take(16) }, label = { Text("Security mode: none/tls/reality") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(encryption, { encryption = it }, label = { Text("Encryption (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(flow, { flow = it }, label = { Text("Flow (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(sni, { sni = it }, label = { Text("SNI (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(publicKey, { publicKey = it }, label = { Text("Public key / pbk placeholder (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(shortId, { shortId = it }, label = { Text("Short ID / sid placeholder (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(fingerprint, { fingerprint = it }, label = { Text("Fingerprint / fp placeholder (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(path, { path = it }, label = { Text("Path (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(hostHeader, { hostHeader = it }, label = { Text("Host header (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(alpn, { alpn = it }, label = { Text("ALPN (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(serviceName, { serviceName = it }, label = { Text("gRPC serviceName (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (transportType.equals("xhttp", ignoreCase = true)) {
                    OutlinedTextField(
                        xhttpMode,
                        { xhttpMode = it.lowercase().take(32) },
                        label = { Text("XHTTP mode: auto/packet-up/stream-up/stream-one") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        xhttpExtra,
                        { xhttpExtra = it },
                        label = { Text("XHTTP extra JSON (зашифровано)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                    )
                    Text(
                        "XHTTP запускается отдельным локальным Xray-core, а единый sing-box TUN отправляет в него только трафик выбранных правил.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("DNS этого VPN", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    config.dnsPolicies.filter { it.enabled }.forEach { policy ->
                        FilterChip(
                            selected = dnsPolicyId == policy.id,
                            onClick = { dnsPolicyId = policy.id },
                            label = { Text(policy.name) },
                        )
                    }
                }
                errors.forEach { WarningText(it) }
                OutlinedButton(onClick = {
                    val candidate = draft()
                    errors = validateVlessProfile(candidate)
                    if (errors.isEmpty()) {
                        exportUri = exportVlessUri(candidate)
                    }
                }) { Text("Export VLESS URI") }
                exportUri?.let { uri: String ->
                    WarningText("Exported VLESS URI contains connection identifiers. Share it carefully.")
                    Details("Exported VLESS URI", uri)
                    OutlinedButton(onClick = { clipboardManager.setText(AnnotatedString(uri)) }) { Text("Copy exported URI") }
                }
                Button(onClick = {
                    val candidate = draft()
                    errors = validateVlessProfile(candidate)
                    if (errors.isEmpty()) {
                        val readyVless = candidate.copy(status = VlessProfileStatus.ConfigReady)
                        val usesXray = readyVless.transportType.equals("xhttp", ignoreCase = true)
                        val nextProfile = TunnelProfile(
                            id = profile?.id ?: "vless-${UUID.randomUUID()}",
                            name = readyVless.name,
                            type = if (usesXray) TunnelType.XrayVlessReality else TunnelType.VLESS,
                            description = vlessDescription(readyVless),
                            enabled = readyVless.enabled,
                            mockOnly = false,
                            platformNotes = if (usesXray) {
                                "VLESS/XHTTP runs in the app-private Xray-core process behind the single sing-box TUN."
                            } else {
                                "VLESS/TLS/REALITY outbound compiled into the local sing-box TUN runtime."
                            },
                            dnsPolicyId = dnsPolicyId,
                            vless = readyVless,
                        )
                        val nextProfiles = if (profile == null) config.profiles + nextProfile else config.profiles.map { if (it.id == profile.id) nextProfile else it }
                        onConfig(
                            config.copy(profiles = nextProfiles),
                            if (usesXray) {
                                "VLESS/XHTTP профиль сохранён для локального Xray-core."
                            } else {
                                "VLESS профиль сохранён для локального маршрутизатора sing-box."
                            },
                        )
                        onBack()
                    }
                }) { Text(text.save) }
            }
        }
        if (profile != null) {
            item {
                CardBlock {
                    OutlinedButton(
                        onClick = {
                            onConfig(
                                config.withoutProfile(profile.id),
                                text.profileDeleted,
                            )
                            onBack()
                        },
                    ) { Text(text.delete) }
                }
            }
        }
    }
}

private fun String.toVlessSecurityMode(): VlessSecurityMode = VlessSecurityMode.entries.firstOrNull {
    it.wireName.equals(trim(), ignoreCase = true) || it.name.equals(trim(), ignoreCase = true)
} ?: VlessSecurityMode.NONE

private fun vlessDescription(profile: VlessProfileConfig): String =
    if (profile.transportType.equals("xhttp", ignoreCase = true)) {
        "VLESS/XHTTP ${profile.host}:${profile.port} (${profile.securityMode.wireName}). Профиль работает через локальный Xray-core за единым маршрутизатором. UUID и XHTTP extra скрыты из сводок и диагностики."
    } else {
        "VLESS ${profile.host}:${profile.port} (${profile.securityMode.wireName}). Профиль доступен маршрутизатору sing-box, включая TLS/REALITY. Ручные проверки не доказывают прохождение Android-трафика. UUID скрыт из сводок и диагностики."
    }

private fun vlessReadinessLabel(status: VlessProfileStatus, history: List<VlessTcpReachabilityHistoryItem>): String = when {
    status == VlessProfileStatus.TcpReachable || history.firstOrNull()?.state == VlessTcpReachabilityState.Reachable -> "TCP reachable"
    status == VlessProfileStatus.LastTestFailed || history.isNotEmpty() -> "Last test failed"
    else -> "Not tested"
}

private fun VlessTcpReachabilityHistoryItem.historyLabel(): String {
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    val latency = elapsedMs?.let { " (${it} ms)" }.orEmpty()
    return "$time • $host:$port • ${state.label}$latency • ${message.sanitizeForHistoryLabel()}"
}

private fun VlessProtocolProbeHistoryItem.historyLabel(): String {
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    val latency = elapsedMs?.let { " (${it} ms)" }.orEmpty()
    return "$time • $serverHost:$serverPort → $targetHost:$targetPort • security=${securityMode.wireName} • ${classification.label} • responseBytes=$responseBytes$latency • ${message.sanitizeForHistoryLabel()}"
}

@Composable
private fun Socks5ProfileEditorScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    profile: TunnelProfile?,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val historyStore = remember(context) { Socks5TestHistoryStore(context) }
    val socks5 = profile?.socks5
    var name by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.name ?: profile?.name ?: "") }
    var host by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.host ?: "") }
    var portText by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.port?.toString() ?: "1080") }
    var username by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.username.orEmpty()) }
    var password by remember(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.password.orEmpty()) }
    var revealPassword by remember(profile?.id ?: "new-socks5") { mutableStateOf(false) }
    var enabled by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf(socks5?.enabled ?: profile?.enabled ?: true) }
    var dnsPolicyId by rememberSaveable(profile?.id ?: "new-socks5-dns") {
        mutableStateOf(profile?.dnsPolicyId ?: RoutingConfigDefaults.SYSTEM_DNS_ID)
    }
    var status by remember(profile?.id ?: "new-socks5") { mutableStateOf<Socks5ProfileStatus>(socks5?.status ?: Socks5ProfileStatus.NotTested) }
    var errors by rememberSaveable(profile?.id ?: "new-socks5") { mutableStateOf<List<String>>(emptyList()) }
    var targetHost by rememberSaveable(profile?.id ?: "new-socks5-target-host") { mutableStateOf("example.com") }
    var targetPortText by rememberSaveable(profile?.id ?: "new-socks5-target-port") { mutableStateOf("443") }
    var currentDiagnostic by remember(profile?.id ?: "new-socks5-diagnostic") { mutableStateOf<Socks5DiagnosticResult?>(null) }
    var history by remember(profile?.id ?: "new-socks5-history") { mutableStateOf<List<Socks5TestHistoryItem>>(emptyList()) }

    LaunchedEffect(profile?.id) {
        history = profile?.id?.let { historyStore.recentForProfile(it) }.orEmpty()
    }

    fun draft(nextStatus: Socks5ProfileStatus = status): Socks5ProfileConfig = Socks5ProfileConfig(
        name = name.trim(),
        host = host.trim(),
        port = portText.toIntOrNull() ?: -1,
        username = username.trim().ifBlank { null },
        password = password.ifBlank { null },
        enabled = enabled,
        status = nextStatus,
    )

    fun saveStatus(nextStatus: Socks5ProfileStatus) {
        status = nextStatus
        profile?.let { current ->
            val nextSocks5 = draft(nextStatus)
            onConfig(
                config.copy(
                    profiles = config.profiles.map {
                        if (it.id == current.id) {
                            it.copy(
                                name = nextSocks5.name,
                                description = socks5Description(nextSocks5),
                                enabled = nextSocks5.enabled,
                                socks5 = nextSocks5,
                            )
                        } else {
                            it
                        }
                    },
                ),
                null,
            )
        }
    }

    suspend fun recordHistory(result: Socks5DiagnosticResult, testType: Socks5DiagnosticTestType, targetHostForHistory: String? = null, targetPortForHistory: Int? = null) {
        val currentProfile = profile ?: return
        historyStore.add(
            Socks5TestHistoryItem(
                profileId = currentProfile.id,
                profileNameSnapshot = name.trim().ifBlank { currentProfile.name },
                testType = testType,
                targetHost = targetHostForHistory,
                targetPort = targetPortForHistory,
                timestamp = System.currentTimeMillis(),
                state = result.state,
                message = result.message,
                elapsedMs = result.elapsedMs,
            ),
        )
        history = historyStore.recentForProfile(currentProfile.id)
    }

    val readiness = remember(history) { deriveSocks5ReadinessSummary(history) }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(if (profile == null) "Add SOCKS5 profile" else "Edit SOCKS5 profile", "Local-only configuration and explicit manual SOCKS5 diagnostics")
            }
        }
        item {
            CardBlock {
                Text("SOCKS5-профиль используется локальным VPN-маршрутизатором. Проверка самого сервера запускается только вручную.", style = MaterialTheme.typography.bodySmall)
                Text("SOCKS5 is used by the router when selected by a rule. Connectivity testing still runs only when requested.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("The test below only verifies the SOCKS5 server and target CONNECT behavior; it does not change routes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                StatusChip(readiness.badgeLabel)
                Text(readiness.userSafeMessage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                readiness.lastHandshake?.let { Text("Last handshake: ${it.state.label} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.timestamp))}", style = MaterialTheme.typography.bodySmall) }
                readiness.lastConnect?.let { item ->
                    val target = if (item.targetHost != null && item.targetPort != null) " • ${item.targetHost}:${item.targetPort}" else ""
                    Text("Last CONNECT: ${item.state.label}$target • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(item.timestamp))}", style = MaterialTheme.typography.bodySmall)
                }
                readiness.lastConnectSuccessLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                currentDiagnostic?.let { Text(it.displayMessage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) }
            }
        }
        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(host, { host = it }, label = { Text("Host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(portText, { portText = it.filter(Char::isDigit).take(5) }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    value = if (revealPassword) password else password.takeIf { it.isNotEmpty() }?.let { "••••••••" }.orEmpty(),
                    onValueChange = { if (revealPassword) password = it },
                    label = { Text("Password (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = revealPassword,
                )
                OutlinedButton(onClick = { revealPassword = !revealPassword }) { Text(if (revealPassword) "Hide password" else "Reveal/edit password") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("DNS этого VPN", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    config.dnsPolicies.filter { it.enabled }.forEach { policy ->
                        FilterChip(
                            selected = dnsPolicyId == policy.id,
                            onClick = { dnsPolicyId = policy.id },
                            label = { Text(policy.name) },
                        )
                    }
                }
                errors.forEach { WarningText(it) }
                Button(onClick = {
                    val nextSocks5 = draft(Socks5ProfileStatus.NotTested)
                    errors = validateSocks5Profile(
                        candidate = nextSocks5,
                        existingProfiles = config.profiles.mapNotNull { it.socks5 },
                        originalName = socks5?.name,
                    )
                    if (errors.isEmpty()) {
                        val nextProfile = TunnelProfile(
                            id = profile?.id ?: "socks5-${UUID.randomUUID()}",
                            name = nextSocks5.name,
                            type = TunnelType.Socks5,
                            description = socks5Description(nextSocks5),
                            enabled = nextSocks5.enabled,
                            mockOnly = false,
                            platformNotes = "SOCKS5 outbound compiled into the local sing-box TUN runtime. Manual diagnostics remain opt-in.",
                            dnsPolicyId = dnsPolicyId,
                            socks5 = nextSocks5,
                        )
                        val nextProfiles = if (profile == null) config.profiles + nextProfile else config.profiles.map { if (it.id == profile.id) nextProfile else it }
                        onConfig(config.copy(profiles = nextProfiles), "SOCKS5 profile saved for the local VPN runtime. Manual diagnostics remain opt-in.")
                        onBack()
                    }
                }) { Text(text.save) }
            }
        }
        item {
            CardBlock {
                Text("Manual SOCKS5 diagnostics", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("This manual test only verifies the SOCKS5 server and target CONNECT behavior; runtime routing uses the saved profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(targetHost, { targetHost = it }, label = { Text("Target host") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(targetPortText, { targetPortText = it.filter(Char::isDigit).take(5) }, label = { Text("Target port") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = status != Socks5ProfileStatus.Testing,
                        onClick = {
                            val testDraft = draft(Socks5ProfileStatus.Testing)
                            errors = validateSocks5Profile(testDraft, config.profiles.mapNotNull { it.socks5 }, socks5?.name)
                            if (errors.isEmpty()) {
                                status = Socks5ProfileStatus.Testing
                                scope.launch {
                                    val result = Socks5HandshakeTester().testHandshake(testDraft)
                                    currentDiagnostic = result
                                    saveStatus(result.toProfileStatus())
                                    recordHistory(result, Socks5DiagnosticTestType.Handshake)
                                }
                            }
                        },
                    ) { Text("Test SOCKS5 handshake") }
                    Button(
                        enabled = status != Socks5ProfileStatus.Testing,
                        onClick = {
                            val connectPort = targetPortText.toIntOrNull() ?: -1
                            val testDraft = draft(Socks5ProfileStatus.Testing)
                            errors = validateSocks5Profile(testDraft, config.profiles.mapNotNull { it.socks5 }, socks5?.name)
                            if (targetHost.isBlank()) errors = errors + "Target host must not be blank."
                            if (connectPort !in 1..65535) errors = errors + "Target port must be in range 1..65535."
                            if (errors.isEmpty()) {
                                status = Socks5ProfileStatus.Testing
                                scope.launch {
                                    val result = Socks5HandshakeTester().testConnect(testDraft, targetHost, connectPort)
                                    currentDiagnostic = result
                                    saveStatus(
                                        if (result.state == Socks5DiagnosticState.ConnectSuccess) Socks5ProfileStatus.Reachable else result.toProfileStatus(),
                                    )
                                    recordHistory(result, Socks5DiagnosticTestType.Connect, targetHost.trim(), connectPort)
                                }
                            }
                        },
                    ) { Text("Test CONNECT target") }
                }
                currentDiagnostic?.let { Text("Current result: ${it.displayMessage}", style = MaterialTheme.typography.bodySmall) }
                Text("No HTTP request or application payload is sent after CONNECT succeeds. Passwords are not logged or saved in test history.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (profile != null) {
            item {
                CardBlock {
                    Text("Recent local SOCKS5 test history", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text("Stored locally in app no-backup storage; newest first, last 20 per profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history.isEmpty()) {
                        Text(text.none, style = MaterialTheme.typography.bodySmall)
                    } else {
                        history.forEach { item ->
                            Text(item.historyLabel(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = {
                        scope.launch {
                            historyStore.clearProfile(profile.id)
                            history = emptyList()
                        }
                    }) { Text("Clear local history for this profile") }
                }
            }
        }
        if (profile != null) {
            item {
                CardBlock {
                    OutlinedButton(
                        onClick = {
                            onConfig(
                                config.withoutProfile(profile.id),
                                text.profileDeleted,
                            )
                            onBack()
                        },
                    ) { Text(text.delete) }
                }
            }
        }
    }
}

private fun Socks5TestHistoryItem.historyLabel(): String {
    val time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(timestamp))
    val target = if (testType == Socks5DiagnosticTestType.Connect && targetHost != null && targetPort != null) " → $targetHost:$targetPort" else ""
    val latency = elapsedMs?.let { " (${it} ms)" }.orEmpty()
    return "$time • ${testType.name}$target • ${state.label}$latency • ${message.sanitizeForHistoryLabel()}"
}

private fun String.sanitizeForHistoryLabel(): String = replace(Regex("(?i)(uuid|password|pass|pwd|secret|token)=\\S+"), "$1=***")
    .replace(Regex("(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b"), "[uuid redacted]")

private fun socks5Description(profile: Socks5ProfileConfig): String =
    "SOCKS5 ${profile.host}:${profile.port}. Routed by the local sing-box TUN runtime when selected. Passwords are stored locally and are not logged."
