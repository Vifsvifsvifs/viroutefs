// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Block
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.os.Handler
import android.os.Looper
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Details
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.UiText
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
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
import dev.vifs.viroutefs.vpn.Ipv4Protocol
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreview
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreviewer
import dev.vifs.viroutefs.vpn.PacketSummary
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import dev.vifs.viroutefs.vpn.XrayEngineRunner
import dev.vifs.viroutefs.vpn.minimalSmokeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VpnScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    vpnState: VpnServiceUiState,
    @Suppress("UNUSED_PARAMETER") tunTestRoutePreviewEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") onVpnSwitch: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onTunTestRoutePreview: (Boolean) -> Unit,
    onClearPacketList: () -> Unit,
    onPausePacketInspector: (Boolean) -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var addSocks5 by rememberSaveable { mutableStateOf(false) }
    var addVless by rememberSaveable { mutableStateOf(false) }
    val selectedProfile = selectedProfileId?.let { id -> config.profiles.firstOrNull { it.id == id } }
    val visibleProfiles = config.profiles.filter { !it.mockOnly || it.type == TunnelType.Socks5 || it.type == TunnelType.VLESS }
    val routeDecisionPreviewer = remember(config) { LiveRouteDecisionPreviewer(config) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var xrayLogs by remember { mutableStateOf(listOf("Xray smoke test idle. No VPN/TUN traffic is attached.")) }
    var xrayRunning by remember { mutableStateOf(false) }
    fun appendXrayLog(message: String) {
        mainHandler.post {
            xrayLogs = (xrayLogs + message).takeLast(6)
        }
    }
    val xrayEngineRunner = remember(context.applicationContext) {
        XrayEngineRunner(context.applicationContext) { message -> appendXrayLog(message) }
    }
    DisposableEffect(xrayEngineRunner) {
        onDispose { xrayEngineRunner.stop() }
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

    var controlActive by remember { mutableStateOf(vpnState.switchChecked) }
    var showAddVpnSheet by remember { mutableStateOf(false) }
    val addVpnSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showAddVpnSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddVpnSheet = false },
            sheetState = addVpnSheetState,
        ) {
            AddVpnTypeSheet(
                onClose = { showAddVpnSheet = false },
                onAddSocks5 = {
                    showAddVpnSheet = false
                    addSocks5 = true
                },
                onAddVless = {
                    showAddVpnSheet = false
                    addVless = true
                },
            )
        }
    }

    ScreenList(padding) {
        item { Header(text.networks, text.networksSubtitle) }
        item {
            // Redesigned hero: local UI state only, so this visual control does not mutate VpnService runtime behavior yet.
            NetworkControlHero(
                active = controlActive,
                serviceLabel = vpnState.label(text),
                serviceDetail = vpnState.detail,
                onToggle = { controlActive = !controlActive },
            )
        }
        item {
            // Default routes are always visible and intentionally less dominant than user VPN profiles.
            DefaultRoutesSection()
        }
        item {
            AddVpnCard(
                profileCount = visibleProfiles.size,
                onClick = { showAddVpnSheet = true },
            )
        }
        item {
            CardBlock {
                Text(text.vpnPacketsRead, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                CounterLine(text.vpnPacketsRead, vpnState.packetsRead)
                CounterLine(text.vpnBytesRead, vpnState.bytesRead)
                CounterLine(text.vpnIpv4PacketsRead, vpnState.ipv4PacketsRead)
                CounterLine(text.vpnTcpPacketsRead, vpnState.tcpPacketsRead)
                CounterLine(text.vpnUdpPacketsRead, vpnState.udpPacketsRead)
                CounterLine(text.vpnIcmpPacketsRead, vpnState.icmpPacketsRead)
            }
        }
        item {
            XrayEngineSmokeCard(
                running = xrayRunning,
                logs = xrayLogs,
                onStart = {
                    scope.launch {
                        appendXrayLog("Starting dev smoke test…")
                        val result = withContext(Dispatchers.IO) { xrayEngineRunner.start(minimalSmokeConfig()) }
                        xrayRunning = xrayEngineRunner.isRunning()
                        result.onFailure { error ->
                            appendXrayLog(error.localizedMessage ?: "Xray smoke start failed.")
                        }
                    }
                },
                onStop = {
                    scope.launch {
                        withContext(Dispatchers.IO) { xrayEngineRunner.stop() }
                        xrayRunning = xrayEngineRunner.isRunning()
                    }
                },
            )
        }
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
        item {
            CardBlock {
                Text(text.vpnPacketInspector, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(text.vpnPacketInspectorPrivacy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text.vpnPausePacketInspector, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${text.vpnPacketListLastUpdate}: ${vpnState.packetSummaryUpdatedAt.formatPacketTime(text)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = vpnState.packetInspectorPaused, onCheckedChange = onPausePacketInspector)
                }
                if (vpnState.packetInspectorPaused) WarningText(text.vpnPacketInspectorPaused)
                OutlinedButton(onClick = onClearPacketList) { Text(text.vpnClearPacketList) }
                if (vpnState.packetSummaries.isEmpty()) {
                    Text(text.vpnPacketInspectorEmpty, style = MaterialTheme.typography.bodySmall)
                } else {
                    vpnState.packetSummaries.forEach { summary ->
                        PacketSummaryLine(
                            summary = summary,
                            routeDecisionPreview = routeDecisionPreviewer.preview(summary),
                        )
                    }
                }
            }
        }
        item {
            CardBlock {
                Text("Runtime notes", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("SOCKS5-профиль добавлен. Проверка подключения запускается только вручную. Полная маршрутизация трафика устройства через SOCKS5 будет добавлена позже.", style = MaterialTheme.typography.bodySmall)
                Text("SOCKS5 profile added. Connectivity testing runs only manually. Full device traffic routing through SOCKS5 will be added later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(VLESS_RUNTIME_LIMITATION, style = MaterialTheme.typography.bodySmall)
                Text(VLESS_ROUTE_PREVIEW_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(visibleProfiles, key = { it.id }) { profile ->
            CompactNetworkProfileCard(
                text = text,
                profile = profile,
                config = config,
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
        targetValue = if (active) Color(0xFF8B1E23) else Color(0xFFB3261E),
        label = "network-control-button",
    )
    val iconSize by animateDpAsState(targetValue = if (active) 64.dp else 56.dp, label = "network-control-icon-size")
    val buttonScale by animateFloatAsState(targetValue = if (active) 1.02f else 1f, label = "network-control-button-scale")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                            "Весь трафик проходит через приложение. Правила маршрутизации видны ниже."
                        } else {
                            "Приложения используют обычное подключение. Маршруты готовы к настройке."
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
                    .height(84.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    },
                shape = RoundedCornerShape(24.dp),
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
private fun DefaultRoutesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Дефолтные маршруты",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Эти варианты всегда доступны: отправить приложение напрямую через Android или полностью закрыть ему интернет.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DefaultRouteCard(
            title = "System / Система",
            subtitle = "Приложения по умолчанию идут через системное подключение.",
            detail = "Используйте для банков, госуслуг и сервисов, которым нужен прямой доступ без туннеля.",
            icon = Icons.Filled.Public,
            accent = MaterialTheme.colorScheme.primary,
        )
        DefaultRouteCard(
            title = "Block / Блокировать",
            subtitle = "Полная блокировка интернета для выбранных приложений.",
            detail = "Безопасный способ запретить сеть приложению без скрытого перехвата трафика.",
            icon = Icons.Filled.Block,
            accent = Color(0xFFB3261E),
        )
    }
}

@Composable
private fun DefaultRouteCard(
    title: String,
    subtitle: String,
    detail: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun AddVpnCard(profileCount: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB3261E).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Добавить VPN", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "OpenVPN, VLESS+REALITY, Hysteria2 и другие профили будут подключаться отсюда.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AssistChip(onClick = {}, label = { Text("Профилей: $profileCount") })
            }
            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddVpnTypeSheet(
    onClose: () -> Unit,
    onAddSocks5: () -> Unit,
    onAddVless: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 28.dp),
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
        FilledTonalButton(onClick = onAddSocks5, modifier = Modifier.fillMaxWidth()) {
            Text("SOCKS5 (ручная проверка подключения)")
        }
        Button(onClick = onAddVless, modifier = Modifier.fillMaxWidth()) {
            Text("VLESS / REALITY")
        }
        Text(
            "Остальные типы пока показаны как UI-заглушка. Добавление не запускает скрытую передачу логов или PCAP.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun XrayEngineSmokeCard(
    running: Boolean,
    logs: List<String>,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    CardBlock {
        Text("Xray engine smoke test", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Dev/diagnostic lifecycle test only. Starts a local SOCKS listener on 127.0.0.1:10808 and does not create VPN, TUN, or route device traffic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WarningText("Dev-only diagnostic: no payload logging, no VPN service, no Android traffic routing.")
        Text(
            "Status: ${if (running) "running" else "stopped"}",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(enabled = !running, onClick = onStart) { Text("Start") }
            OutlinedButton(enabled = running, onClick = onStop) { Text("Stop") }
        }
        Text("Recent engine logs", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
        logs.forEach { logLine ->
            Text("• $logLine", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            "Safe dev-only TCP stream for validating one VLESS profile. Runtime forwarding is not enabled.",
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

@Composable
private fun PacketSummaryLine(summary: PacketSummary, routeDecisionPreview: LiveRouteDecisionPreview) {
    val time = DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(summary.timestamp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            "${summary.protocol.safeLabel()}  ${summary.endpointLine()}  ${summary.packetSize} B",
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(routeDecisionPreview.decisionLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(routeDecisionPreview.safetyLine, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        routeDecisionPreview.tcpSessionObservationLine?.let { observation ->
            Text(observation, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        routeDecisionPreview.warnings.forEach { warning ->
            WarningText(warning)
        }
    }
}

private fun Long?.formatPacketTime(text: UiText): String = this?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
} ?: text.flowNever

private fun PacketSummary.endpointLine(): String = if (srcPort != null && dstPort != null) {
    "$srcIp:$srcPort → $dstIp:$dstPort"
} else {
    "$srcIp → $dstIp"
}

private fun Ipv4Protocol.safeLabel(): String = when (this) {
    Ipv4Protocol.Tcp -> "TCP"
    Ipv4Protocol.Udp -> "UDP"
    Ipv4Protocol.Icmp -> "ICMP"
    Ipv4Protocol.Other -> "OTHER"
}

@Composable
private fun CompactNetworkProfileCard(text: UiText, profile: TunnelProfile, config: RoutingConfig, onOpen: () -> Unit) {
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
    if (profile.type == TunnelType.VLESS) {
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
    val dns = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }
    val usedRuleNames = config.rules.filter { it.targetProfileId == profile.id }.map { it.name }
    val protectedProfile = profile.type in listOf(TunnelType.Direct, TunnelType.Block)
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
                    OutlinedButton(onClick = { onConfig(config.copy(defaultProfileId = profile.id), text.defaultChanged) }) { Text(text.makeDefault) }
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
                    onClick = { onConfig(config.copy(profiles = config.profiles.filterNot { it.id == profile.id }), text.profileDeleted) },
                ) { Text(text.delete) }
            }
        }
    }
}

private val VpnServiceUiState.switchChecked: Boolean
    get() = status == VpnServiceStatus.Starting ||
        status == VpnServiceStatus.ServiceActiveNoTun ||
        status == VpnServiceStatus.TunPreviewActive ||
        status == VpnServiceStatus.TunTestRouteActive

private fun VpnServiceUiState.label(text: UiText): String = when (status) {
    VpnServiceStatus.Off -> text.off
    VpnServiceStatus.PermissionRequired -> text.vpnPermissionRequired
    VpnServiceStatus.NotificationPermissionRequired -> text.vpnNotificationPermissionRequired
    VpnServiceStatus.Starting -> text.vpnStarting
    VpnServiceStatus.ServiceActiveNoTun -> text.vpnLocalServiceActive
    VpnServiceStatus.TunPreviewActive -> text.vpnTunPreviewActive
    VpnServiceStatus.TunTestRouteActive -> text.vpnTunPreviewActive
    VpnServiceStatus.Stopped -> text.vpnStopped
    VpnServiceStatus.Error -> text.vpnError
}

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
    var enabled by rememberSaveable(profile?.id ?: "new-vless") { mutableStateOf(vless?.enabled ?: profile?.enabled ?: true) }
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
                Header(if (profile == null) "Add VLESS profile" else "Edit VLESS profile", "Local-only VLESS configuration for route decision preview")
            }
        }
        item {
            CardBlock {
                Text(VLESS_RUNTIME_LIMITATION, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                Text(VLESS_ROUTE_PREVIEW_ONLY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(VLESS_NO_HANDSHAKE_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(VLESS_TCP_REACHABILITY_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WarningText("UUID is stored locally in routing_config.json and is hidden from summaries, logs, and diagnostics text.")
                Text("Security mode supports none/tls/reality as configuration placeholders only. REALITY/XTLS runtime is not implemented.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            CardBlock {
                Text("Import / Export VLESS URI", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Paste a vless:// URI to preview and fill this local config form. ViRouteFS does not connect, test, proxy DNS, or forward packets for VLESS.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("TCP reachability only: no VLESS handshake, no TLS, no REALITY, no runtime forwarding, and no packets are written back to TUN.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        item {
            CardBlock {
                Text("Manual VLESS protocol probe", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                WarningText(VLESS_PROTOCOL_PROBE_NOTICE)
                val probeDraft = draft()
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
                OutlinedTextField(transportType, { transportType = it.lowercase().take(16) }, label = { Text("Transport type: tcp/ws/grpc (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text.enabled, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
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
                        val nextProfile = TunnelProfile(
                            id = profile?.id ?: "vless-${UUID.randomUUID()}",
                            name = readyVless.name,
                            type = TunnelType.VLESS,
                            description = vlessDescription(readyVless),
                            enabled = readyVless.enabled,
                            mockOnly = true,
                            platformNotes = "VLESS config-only profile. Runtime forwarding is not implemented yet; route decision preview only.",
                            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                            vless = readyVless,
                        )
                        val nextProfiles = if (profile == null) config.profiles + nextProfile else config.profiles.map { if (it.id == profile.id) nextProfile else it }
                        onConfig(config.copy(profiles = nextProfiles), "VLESS profile saved. Runtime forwarding is not implemented yet.")
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
                                config.copy(
                                    profiles = config.profiles.filterNot { it.id == profile.id },
                                    rules = config.rules.map { rule ->
                                        if (rule.targetProfileId == profile.id) rule.copy(targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID) else rule
                                    },
                                ),
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
    "VLESS ${profile.host}:${profile.port} (${profile.securityMode.wireName}). Manual diagnostics only: TCP reachability and plain-TCP VLESS probe for security=none and manual TLS probe for security=tls; no REALITY/XTLS runtime, DNS proxying, Android traffic forwarding, TUN writes, or runtime forwarding is implemented. UUID is hidden from summaries and diagnostics."

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
                Text("SOCKS5-профиль добавлен. Проверка подключения запускается только вручную. Полная маршрутизация трафика устройства через SOCKS5 будет добавлена позже.", style = MaterialTheme.typography.bodySmall)
                Text("SOCKS5 profile added. Connectivity testing runs only manually. Full device traffic routing through SOCKS5 will be added later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Runtime forwarding is not enabled yet. This test only verifies the SOCKS5 server and target CONNECT behavior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            mockOnly = true,
                            platformNotes = "Configuration and manual SOCKS5 diagnostics only; runtime forwarding is not enabled yet.",
                            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
                            socks5 = nextSocks5,
                        )
                        val nextProfiles = if (profile == null) config.profiles + nextProfile else config.profiles.map { if (it.id == profile.id) nextProfile else it }
                        onConfig(config.copy(profiles = nextProfiles), "SOCKS5 profile saved. Manual diagnostics only; runtime forwarding is not enabled yet.")
                        onBack()
                    }
                }) { Text(text.save) }
            }
        }
        item {
            CardBlock {
                Text("Manual SOCKS5 diagnostics", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text("Runtime forwarding is not enabled yet. This test only verifies the SOCKS5 server and target CONNECT behavior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                config.copy(
                                    profiles = config.profiles.filterNot { it.id == profile.id },
                                    rules = config.rules.map { rule ->
                                        if (rule.targetProfileId == profile.id) rule.copy(targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID) else rule
                                    },
                                ),
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
    "SOCKS5 ${profile.host}:${profile.port}. Manual connectivity testing only; full device traffic routing through SOCKS5 will be added later. Passwords are stored locally and are not logged."
