package dev.vifs.viroutefs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import dev.vifs.viroutefs.routing.LiveRouteDecisionPreview
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import java.text.DateFormat
import java.util.Date

private const val TEST_ROUTE_CIDR = "203.0.113.0/24"
private const val TEST_ROUTE_SOURCE = "Developer TEST-NET counter"
private const val TEST_ROUTE_MODE = "Developer diagnostics"

internal data class FlowEventUi(
    val appName: String,
    val domain: String,
    val resolvedIp: String?,
    val portProtocol: String,
    val dnsPolicy: String,
    val selectedRoute: String,
    val routeReason: String,
    val riskWarning: String?,
    val recommendation: String,
    val status: String,
    val technicalDetails: String,
    val sourceLabel: String,
) {
    val target: String = buildString {
        append(domain)
        val port = portProtocol.substringBefore(" /").takeIf { it.all { char -> char.isDigit() } }
        if (port != null) append(":").append(port)
    }
}

@Composable
internal fun FlowScannerScreen(padding: PaddingValues, text: UiText, vpnState: VpnServiceUiState) {
    var selectedEventIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var liveDetailsOpen by rememberSaveable { mutableStateOf(false) }
    var selectedPreviewIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val events = emptyList<FlowEventUi>()
    val livePreviews = vpnState.packetRoutePreviews
    val selectedEvent = selectedEventIndex?.let { events.getOrNull(it) }
    val selectedPreview = selectedPreviewIndex?.let { livePreviews.getOrNull(it) }
    val showLiveTestRoute = vpnState.tunTestRouteActive || vpnState.packetsRead > 0L || vpnState.bytesRead > 0L

    when {
        liveDetailsOpen -> FlowTunTestRouteDetailsScreen(
            padding = padding,
            text = text,
            vpnState = vpnState,
            onBack = { liveDetailsOpen = false },
        )
        selectedPreview != null -> LiveRoutePreviewDetailsScreen(
            padding = padding,
            text = text,
            preview = selectedPreview,
            onBack = { selectedPreviewIndex = null },
        )
        selectedEvent != null -> FlowEventDetailsScreen(
            padding = padding,
            text = text,
            event = selectedEvent,
            onBack = { selectedEventIndex = null },
        )
        else -> FlowScannerListScreen(
            padding = padding,
            text = text,
            vpnState = vpnState,
            showLiveTestRoute = showLiveTestRoute,
            events = events,
            livePreviews = livePreviews,
            onLiveEvent = { liveDetailsOpen = true },
            onPreview = { selectedPreviewIndex = it },
            onEvent = { selectedEventIndex = it },
        )
    }
}

@Composable
private fun FlowScannerListScreen(
    padding: PaddingValues,
    text: UiText,
    vpnState: VpnServiceUiState,
    showLiveTestRoute: Boolean,
    events: List<FlowEventUi>,
    livePreviews: List<LiveRouteDecisionPreview>,
    onLiveEvent: () -> Unit,
    onPreview: (Int) -> Unit,
    onEvent: (Int) -> Unit,
) = ScreenList(padding) {
    item { Header(text.flowScannerTitle, text.flowScannerSubtitle) }
    item { FlowControlCard(text) }
    if (showLiveTestRoute) {
        item { FlowTunTestRouteRow(text = text, vpnState = vpnState, onClick = onLiveEvent) }
    }
    if (events.isEmpty() && livePreviews.isEmpty() && !showLiveTestRoute) {
        item { CardBlock { Text(text.flowEmptyState, style = MaterialTheme.typography.bodySmall) } }
    } else {
        items(livePreviews.size) { index ->
            LiveRoutePreviewRow(text = text, preview = livePreviews[index], onClick = { onPreview(index) })
        }
        items(events.size) { index ->
            FlowEventRow(text = text, event = events[index], onClick = { onEvent(index) })
        }
    }
    item { FlowLimitCard(text) }
}

@Composable
private fun FlowControlCard(text: UiText) = CardBlock {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text.flowAppFilter, style = MaterialTheme.typography.labelSmall)
        Text(text.flowEmptyTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(text.flowPreviewOnly, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FlowTunTestRouteRow(text: UiText, vpnState: VpnServiceUiState, onClick: () -> Unit) = CardBlock {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(TEST_ROUTE_SOURCE, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                StatusChip(text.flowLiveLocalTestData)
            }
            Text("${text.flowRoute}: $TEST_ROUTE_CIDR", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(
                "${text.flowPacketsRead}: ${vpnState.packetsRead} • ${text.vpnTcpPacketsRead}: ${vpnState.tcpPacketsRead} • ${text.vpnUdpPacketsRead}: ${vpnState.udpPacketsRead} • ${text.vpnIcmpPacketsRead}: ${vpnState.icmpPacketsRead}",
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(0.7f)) {
            Text(text.flowLastPacket, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text(vpnState.lastPacketAt.formatPacketTime(text), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            StatusChip(if (vpnState.isTunTestRouteActive) text.flowActive else text.flowInactive)
        }
    }
}


@Composable
private fun LiveRoutePreviewRow(text: UiText, preview: LiveRouteDecisionPreview, onClick: () -> Unit) = CardBlock {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${preview.protocol} ${preview.destination}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                StatusChip(text.flowLiveLocalTestData)
            }
            Text("${text.flowSource}: ${preview.source}", style = MaterialTheme.typography.bodySmall, maxLines = 1)
            Text("${text.flowReason}: ${preview.matchedRuleName ?: "default rule fallback"}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(0.9f)) {
            Text(preview.selectedProfileName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            StatusChip(preview.selectedProfileType)
        }
    }
}

@Composable
private fun FlowEventRow(text: UiText, event: FlowEventUi, onClick: () -> Unit) = CardBlock {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(event.appName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                StatusChip(event.sourceLabel)
            }
            Text(event.target, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(0.9f)) {
            Text(event.selectedRoute, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            StatusChip(event.status)
        }
    }
}

@Composable
private fun FlowTunTestRouteDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    vpnState: VpnServiceUiState,
    onBack: () -> Unit,
) = ScreenList(padding) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(text.back) }
            Header(text.flowLiveTestRoute, TEST_ROUTE_SOURCE)
        }
    }
    item {
        CardBlock {
            FlowField(text.flowSource, TEST_ROUTE_SOURCE)
            FlowField(text.flowRoute, TEST_ROUTE_CIDR)
            FlowField(text.flowVpnMode, TEST_ROUTE_MODE)
            FlowField(text.flowPacketsRead, vpnState.packetsRead.toString())
            FlowField(text.flowBytesRead, vpnState.bytesRead.toString())
            FlowField(text.vpnIpv4PacketsRead, vpnState.ipv4PacketsRead.toString())
            FlowField(text.vpnTcpPacketsRead, vpnState.tcpPacketsRead.toString())
            FlowField(text.vpnUdpPacketsRead, vpnState.udpPacketsRead.toString())
            FlowField(text.vpnIcmpPacketsRead, vpnState.icmpPacketsRead.toString())
            FlowField(text.flowLastPacket, vpnState.lastPacketAt.formatPacketTime(text))
            FlowField(text.flowStatus, if (vpnState.isTunTestRouteActive) text.flowActive else text.flowInactive)
        }
    }
    item {
        CardBlock {
            Text(text.flowSafety, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Details(text.details, text.flowTunSafetyDetails)
            Text(text.flowHowToTest, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(text.flowTunHowToTest, style = MaterialTheme.typography.bodySmall)
        }
    }
    item { FlowLimitCard(text) }
}


@Composable
private fun LiveRoutePreviewDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    preview: LiveRouteDecisionPreview,
    onBack: () -> Unit,
) = ScreenList(padding) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(text.back) }
            Header("Live route decision", preview.destination)
        }
    }
    item {
        CardBlock {
            FlowField(text.flowSource, preview.source)
            FlowField(text.flowResolvedIp, preview.destination)
            FlowField(text.flowPortProtocol, preview.protocol)
            FlowField("Matched rule", preview.matchedRuleName ?: "Default rule fallback")
            FlowField(text.flowSelectedRoute, preview.selectedProfileName)
            FlowField("Profile type", preview.selectedProfileType)
            FlowField(text.flowLastPacket, preview.observedAt.formatPacketTime(text))
        }
    }
    item {
        CardBlock {
            Text(text.flowReason, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(preview.decisionText, style = MaterialTheme.typography.bodySmall)
            preview.warning?.let { WarningText(it) }
            Text(text.flowSafety, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text("Observation-only preview. No packet forwarding, no TUN writes, no DNS proxying, no payload capture, and no traffic leaves the device through ViRouteFS.", style = MaterialTheme.typography.bodySmall)
        }
    }
    item { FlowLimitCard(text) }
}

@Composable
private fun FlowEventDetailsScreen(padding: PaddingValues, text: UiText, event: FlowEventUi, onBack: () -> Unit) = ScreenList(padding) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(text.back) }
            Header(text.flowEventDetailsTitle, event.appName)
        }
    }
    item {
        CardBlock {
            FlowField(text.flowSource, event.sourceLabel)
            FlowField(text.flowApp, event.appName)
            FlowField(text.flowDomain, event.domain)
            FlowField(text.flowResolvedIp, event.resolvedIp ?: text.none)
            FlowField(text.flowPortProtocol, event.portProtocol)
            FlowField(text.flowDnsPolicy, event.dnsPolicy)
            FlowField(text.flowSelectedRoute, event.selectedRoute)
        }
    }
    item {
        CardBlock {
            Text(text.flowReason, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(event.routeReason, style = MaterialTheme.typography.bodySmall)
            event.riskWarning?.let {
                WarningText(it)
            }
            Text(text.flowRecommendation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(event.recommendation, style = MaterialTheme.typography.bodySmall)
            Details(text.details, event.technicalDetails)
        }
    }
    item { FlowLimitCard(text) }
}

@Composable
private fun FlowField(label: String, value: String) = Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.Top,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.75f))
    Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
}

@Composable
private fun FlowLimitCard(text: UiText) = CardBlock {
    Text(text.limitation, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    Text(text.fsLimitShort, style = MaterialTheme.typography.bodySmall)
    Details(text.details, text.fsLimitDetails)
}

private val VpnServiceUiState.isTunTestRouteActive: Boolean
    get() = tunTestRouteActive && status == VpnServiceStatus.TunTestRouteActive

private fun Long?.formatPacketTime(text: UiText): String = this?.let {
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(it))
} ?: text.flowNever
