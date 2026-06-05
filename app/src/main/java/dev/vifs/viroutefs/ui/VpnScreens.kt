// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import dev.vifs.viroutefs.vpn.Ipv4Protocol
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreview
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreviewer
import dev.vifs.viroutefs.vpn.PacketSummary
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
internal fun VpnScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    vpnState: VpnServiceUiState,
    @Suppress("UNUSED_PARAMETER") tunTestRoutePreviewEnabled: Boolean,
    onVpnSwitch: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") onTunTestRoutePreview: (Boolean) -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var addSocks5 by rememberSaveable { mutableStateOf(false) }
    val selectedProfile = selectedProfileId?.let { id -> config.profiles.firstOrNull { it.id == id } }
    val visibleProfiles = config.profiles.filter { !it.mockOnly || it.type == TunnelType.Socks5 }
    val routeDecisionPreviewer = remember(config) { LiveRouteDecisionPreviewer(config) }

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

    ScreenList(padding) {
        item { Header(text.networks, text.networksSubtitle) }
        item {
            CardBlock {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text.activateNetworkControl, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        StatusChip(vpnState.label(text))
                        Text(text.networkControlSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vpnState.switchChecked, onCheckedChange = onVpnSwitch)
                }
                Details(text.details, text.vpnLifecycleOnlyDetails)
                vpnState.detail?.let { WarningText(it) }
            }
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
            CardBlock {
                Text(text.vpnPacketInspector, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(text.vpnPacketInspectorPrivacy, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(text.profileCount(visibleProfiles.size)) })
                OutlinedButton(onClick = { addSocks5 = true }) { Text("Add SOCKS5") }
            }
        }
        item {
            CardBlock {
                Text("SOCKS5-профиль добавлен. Проверка подключения запускается только вручную. Полная маршрутизация трафика устройства через SOCKS5 будет добавлена позже.", style = MaterialTheme.typography.bodySmall)
                Text("SOCKS5 profile added. Connectivity testing runs only manually. Full device traffic routing through SOCKS5 will be added later.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        routeDecisionPreview.warnings.forEach { warning ->
            WarningText(warning)
        }
    }
}

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

private fun String.sanitizeForHistoryLabel(): String = replace(Regex("(?i)(password|pass|pwd|secret)=\\S+"), "$1=***")

private fun socks5Description(profile: Socks5ProfileConfig): String =
    "SOCKS5 ${profile.host}:${profile.port}. Manual connectivity testing only; full device traffic routing through SOCKS5 will be added later. Passwords are stored locally and are not logged."
