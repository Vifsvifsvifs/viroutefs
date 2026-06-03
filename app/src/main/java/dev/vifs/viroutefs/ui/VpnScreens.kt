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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import dev.vifs.viroutefs.routing.MOCK_PROFILE_LIMITATION
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
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
    onVpnSwitch: (Boolean) -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var selectedProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedProfile = selectedProfileId?.let { id -> config.profiles.firstOrNull { it.id == id } }

    if (selectedProfile != null) {
        VpnProfileDetailsScreen(
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

    if (adding) {
        AddVpnProfileScreen(
            padding = padding,
            text = text,
            config = config,
            onBack = { adding = false },
            onConfig = { next, message ->
                onConfig(next, message)
                adding = false
            },
        )
        return
    }

    ScreenList(padding) {
        item { Header(text.vpn, text.vpnSubtitle) }
        item {
            CardBlock {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(text.vpnLocalPreviewTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        StatusChip(vpnState.label(text))
                        Text(text.vpnNoTrafficRoutingYet, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = vpnState.switchChecked, onCheckedChange = onVpnSwitch)
                }
            }
        }
        item {
            CardBlock {
                Text(text.vpnNoHiddenInterception, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(text.vpnPacketProcessingLater, style = MaterialTheme.typography.bodySmall)
                Details(text.details, text.vpnLifecycleOnlyDetails)
                vpnState.detail?.let { WarningText(it) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { adding = true }) { Text(text.addProfile) }
                AssistChip(onClick = {}, label = { Text(text.profileCount(config.profiles.size)) })
            }
        }
        items(config.profiles, key = { it.id }) { profile ->
            CompactVpnProfileCard(
                text = text,
                profile = profile,
                config = config,
                onOpen = { selectedProfileId = profile.id },
            )
        }
    }
}

@Composable
private fun CompactVpnProfileCard(text: UiText, profile: TunnelProfile, config: RoutingConfig, onOpen: () -> Unit) {
    val dns = config.dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }?.name ?: text.noDns
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
                Text("DNS: $dns", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(if (profile.enabled) text.on else text.off)
        }
    }
}

@Composable
private fun VpnProfileDetailsScreen(
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
                if (profile.mockOnly) WarningText(profile.platformNotes ?: text.mockOnly)
                profile.warningText?.let { WarningText(it) }
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

@Composable
private fun AddVpnProfileScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var desc by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TunnelType.WireGuard) }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(text.addProfile, text.addProfileSubtitle)
            }
        }
        item {
            CardBlock {
                Text(text.importOptions, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(text.qr, text.clipboard, text.file, text.manual).forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
        }
        item {
            CardBlock {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                TunnelTypeDropdown(text, type, onSelect = { type = it })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(text.description) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val dnsId = when (type) {
                        TunnelType.Direct -> RoutingConfigDefaults.DIRECT_DNS_ID
                        TunnelType.Block -> RoutingConfigDefaults.SYSTEM_DNS_ID
                        else -> RoutingConfigDefaults.TUNNEL_DNS_ID
                    }
                    val profile = TunnelProfile(
                        id = "profile_${System.currentTimeMillis()}",
                        name = name.ifBlank { type.label },
                        type = type,
                        description = desc.ifBlank { text.mockProfileDescription },
                        mockOnly = type.isMockOnly,
                        platformNotes = MOCK_PROFILE_LIMITATION.takeIf { type.isMockOnly },
                        dnsPolicyId = dnsId,
                    )
                    onConfig(config.copy(profiles = config.profiles + profile), text.profileAdded)
                }) { Text(text.create) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TunnelTypeDropdown(text: UiText, value: TunnelType, onSelect: (TunnelType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = value.label,
            onValueChange = {},
            readOnly = true,
            label = { Text(text.type) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            TunnelType.entries.filterNot { it.name.endsWith("Mock") }.forEach { type ->
                DropdownMenuItem(text = { Text(type.label) }, onClick = { onSelect(type); expanded = false })
            }
        }
    }
}


private val VpnServiceUiState.switchChecked: Boolean
    get() = status == VpnServiceStatus.Starting || status == VpnServiceStatus.Active

private fun VpnServiceUiState.label(text: UiText): String = when (status) {
    VpnServiceStatus.Off -> text.off
    VpnServiceStatus.PermissionRequired -> text.vpnPermissionRequired
    VpnServiceStatus.NotificationPermissionRequired -> text.vpnNotificationPermissionRequired
    VpnServiceStatus.Starting -> text.vpnStarting
    VpnServiceStatus.Active -> text.vpnLocalServiceActive
    VpnServiceStatus.Stopped -> text.vpnStopped
    VpnServiceStatus.Error -> text.vpnError
}
