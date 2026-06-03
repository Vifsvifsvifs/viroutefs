package dev.vifs.viroutefs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.UiText
import dev.vifs.viroutefs.diagnostics.DiagnosticResult
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.routing.DNS_POLICY_LIMITATION
import dev.vifs.viroutefs.routing.DnsHostOverride
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.RoutingConfig
import kotlinx.coroutines.launch

private enum class DnsRoute {
    Main,
    HostOverrides,
}

@Composable
internal fun DnsScreen(padding: PaddingValues, text: UiText, config: RoutingConfig, onConfig: (RoutingConfig, String?) -> Unit) {
    var selectedRoute by rememberSaveable { mutableStateOf(DnsRoute.Main) }
    var selectedPolicyId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPolicy = config.dnsPolicies.firstOrNull { it.id == selectedPolicyId }

    when {
        selectedPolicy != null -> DnsPolicyDetailsScreen(
            padding = padding,
            text = text,
            policy = selectedPolicy,
            config = config,
            onBack = { selectedPolicyId = null },
            onConfig = onConfig,
        )

        selectedRoute == DnsRoute.HostOverrides -> HostOverridesScreen(
            padding = padding,
            text = text,
            config = config,
            onBack = { selectedRoute = DnsRoute.Main },
            onConfig = onConfig,
        )

        else -> DnsMainScreen(
            padding = padding,
            text = text,
            config = config,
            onPolicy = { selectedPolicyId = it.id },
            onHostOverrides = { selectedRoute = DnsRoute.HostOverrides },
        )
    }
}

@Composable
private fun DnsMainScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    onPolicy: (DnsPolicy) -> Unit,
    onHostOverrides: () -> Unit,
) = ScreenList(padding) {
    item { Header(text.dns, text.dnsSubtitle) }
    item { DnsLookupCheckerCard(text) }
    item {
        SectionHeader(
            title = text.policies,
            subtitle = text.policyCount(config.dnsPolicies.size),
        )
    }
    items(config.dnsPolicies, key = { it.id }) { policy -> DnsPolicySummaryCard(text, policy, config, onOpen = { onPolicy(policy) }) }
    item {
        HostOverridesSummaryCard(
            text = text,
            overrides = config.hostOverrides,
            onOpen = onHostOverrides,
        )
    }
}

@Composable
private fun DnsLookupCheckerCard(text: UiText) {
    val scope = rememberCoroutineScope()
    var domain by rememberSaveable { mutableStateOf("example.com") }
    var server by rememberSaveable { mutableStateOf("1.1.1.1") }
    var record by rememberSaveable { mutableStateOf("A") }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }

    CardBlock {
        Text(text.lookup, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(domain, { domain = it }, label = { Text(text.domain) }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(record, { record = it }, label = { Text(text.type) }, modifier = Modifier.weight(0.45f), singleLine = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(server, { server = it }, label = { Text(text.dnsServer) }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = { scope.launch { result = DnsDiagnostic().lookup(domain, server, record) } }) { Text(text.check) }
        }
        result?.let { CompactDnsResult(text, it) }
    }
}

@Composable
private fun CompactDnsResult(text: UiText, result: DiagnosticResult) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(result.simpleExplanation, style = MaterialTheme.typography.bodySmall)
        Details(text.details, result.technicalDetailsWithElapsed() + "\n" + text.actionPrefix + result.recommendedAction)
    }
}

@Composable
private fun DnsPolicySummaryCard(text: UiText, policy: DnsPolicy, config: RoutingConfig, onOpen: () -> Unit) {
    CardBlock {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(policy.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(policy.type.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val boundProfile = policy.resolveThroughProfileId?.let { id -> config.profiles.firstOrNull { it.id == id && !it.mockOnly } }
                Text("${text.targetProfile}: ${boundProfile?.name ?: text.none}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                policy.serverText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            StatusChip(if (policy.enabled) text.on else text.off)
        }
    }
}

@Composable
private fun DnsPolicyDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    policy: DnsPolicy,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var name by rememberSaveable(policy.id) { mutableStateOf(policy.name) }
    var serverText by rememberSaveable(policy.id) { mutableStateOf(policy.serverText.orEmpty()) }
    var description by rememberSaveable(policy.id) { mutableStateOf(policy.description) }
    var enabled by rememberSaveable(policy.id) { mutableStateOf(policy.enabled) }
    val profileNames = config.profiles.filter { it.dnsPolicyId == policy.id && !it.mockOnly }.map { it.name }
    val routeNames = config.rules.filter { it.dnsPolicyId == policy.id }.map { it.name }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(text.dnsPolicyDetails, text.dnsPolicyDetailsSubtitle)
            }
        }
        item {
            CardBlock {
                OutlinedTextField(name, { name = it }, label = { Text(text.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text(policy.type.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(serverText, { serverText = it }, label = { Text(text.dnsServer) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text(text.enabled, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CardBlock {
                Text(text.usedByProfiles, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(profileNames.joinToString().ifBlank { text.none }, style = MaterialTheme.typography.bodySmall)
                if (routeNames.isNotEmpty()) {
                    Text(text.usedByRoutes, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(routeNames.joinToString(), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            CardBlock {
                OutlinedTextField(description, { description = it }, label = { Text(text.description) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Details(text.details, DNS_POLICY_LIMITATION)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    onConfig(
                        config.copy(
                            dnsPolicies = config.dnsPolicies.map {
                                if (it.id == policy.id) {
                                    it.copy(
                                        name = name.ifBlank { policy.name },
                                        serverText = serverText.ifBlank { null },
                                        description = description.ifBlank { policy.description },
                                        enabled = enabled,
                                    )
                                } else {
                                    it
                                }
                            },
                        ),
                        text.saved,
                    )
                    onBack()
                }) { Text(text.save) }
                OutlinedButton(onClick = onBack) { Text(text.cancel) }
            }
        }
    }
}

@Composable
private fun HostOverridesSummaryCard(text: UiText, overrides: List<DnsHostOverride>, onOpen: () -> Unit) {
    CardBlock {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text.hostOverrides, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(text.overrideCount(overrides.size), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(overrides.take(2).joinToString(" • ") { "${it.hostname} → ${it.ipAddress}" }.ifBlank { text.none }, style = MaterialTheme.typography.labelSmall)
            }
            StatusChip(text.details)
        }
    }
}

@Composable
private fun HostOverridesScreen(
    padding: PaddingValues,
    text: UiText,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    var hostname by rememberSaveable { mutableStateOf("") }
    var ipAddress by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text(text.back) }
                Header(text.hostOverrides, text.hostOverridesSubtitle)
            }
        }
        item {
            CardBlock {
                Text(text.hostOverridesShort, style = MaterialTheme.typography.bodySmall)
            }
        }
        items(config.hostOverrides, key = { it.id }) { override ->
            HostOverrideRow(
                text = text,
                override = override,
                onToggle = {
                    onConfig(config.copy(hostOverrides = config.hostOverrides.map { if (it.id == override.id) it.copy(enabled = !it.enabled) else it }), text.saved)
                },
                onDelete = {
                    onConfig(config.copy(hostOverrides = config.hostOverrides.filterNot { it.id == override.id }), text.hostOverrideDeleted)
                },
            )
        }
        item {
            CardBlock {
                Text(text.addHostOverride, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(hostname, { hostname = it }, label = { Text(text.host) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(ipAddress, { ipAddress = it }, label = { Text(text.ipAddress) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text(text.noteOptional) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Button(
                    onClick = {
                        val host = hostname.trim()
                        val ip = ipAddress.trim()
                        if (host.isNotBlank() && ip.isNotBlank()) {
                            onConfig(
                                config.copy(
                                    hostOverrides = config.hostOverrides + DnsHostOverride(
                                        id = "host_${System.currentTimeMillis()}",
                                        hostname = host,
                                        ipAddress = ip,
                                        note = note.trim().ifBlank { null },
                                    ),
                                ),
                                text.hostOverrideAdded,
                            )
                            hostname = ""
                            ipAddress = ""
                            note = ""
                        }
                    },
                ) { Text(text.create) }
            }
        }
    }
}

@Composable
private fun HostOverrideRow(text: UiText, override: DnsHostOverride, onToggle: () -> Unit, onDelete: () -> Unit) {
    CardBlock {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${override.hostname} → ${override.ipAddress}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                override.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            StatusChip(if (override.enabled) text.on else text.off)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onToggle) { Text(if (override.enabled) text.disable else text.enable) }
            OutlinedButton(onClick = onDelete) { Text(text.delete) }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.fillMaxWidth(),
) {
    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Details(label: String, text: String) {
    var open by rememberSaveable(text) { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) { Text(if (open) "− $label" else "+ $label") }
    if (open) Text(text, style = MaterialTheme.typography.bodySmall)
}
