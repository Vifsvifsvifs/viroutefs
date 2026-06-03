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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState

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
    val selectedProfile = selectedProfileId?.let { id -> config.profiles.firstOrNull { it.id == id } }
    val visibleProfiles = config.profiles.filter { !it.mockOnly && it.enabled }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(onClick = {}, label = { Text(text.profileCount(visibleProfiles.size)) })
                StatusChip(text.noNetworkProfilesYet)
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
private fun CompactNetworkProfileCard(text: UiText, profile: TunnelProfile, config: RoutingConfig, onOpen: () -> Unit) {
    val routeCount = config.rules.count { it.targetProfileId == profile.id && it.type != RouteRuleType.DEFAULT }
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
