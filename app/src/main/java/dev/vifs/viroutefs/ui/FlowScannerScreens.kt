package dev.vifs.viroutefs.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
) {
    val target: String = buildString {
        append(domain)
        val port = portProtocol.substringBefore(" /").takeIf { it.all { char -> char.isDigit() } }
        if (port != null) append(":").append(port)
    }
}

@Composable
internal fun FlowScannerScreen(padding: PaddingValues, text: UiText) {
    var selectedEventIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val events = demoFlowEvents(text)
    val selectedEvent = selectedEventIndex?.let { events.getOrNull(it) }

    if (selectedEvent != null) {
        FlowEventDetailsScreen(
            padding = padding,
            text = text,
            event = selectedEvent,
            onBack = { selectedEventIndex = null },
        )
    } else {
        FlowScannerListScreen(
            padding = padding,
            text = text,
            events = events,
            onEvent = { selectedEventIndex = it },
        )
    }
}

@Composable
private fun FlowScannerListScreen(
    padding: PaddingValues,
    text: UiText,
    events: List<FlowEventUi>,
    onEvent: (Int) -> Unit,
) = ScreenList(padding) {
    item { Header(text.flowScannerTitle, text.flowScannerSubtitle) }
    item { FlowControlCard(text) }
    items(events.size) { index ->
        FlowEventRow(text = text, event = events[index], onClick = { onEvent(index) })
    }
    item { FlowLimitCard(text) }
}

@Composable
private fun FlowControlCard(text: UiText) = CardBlock {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(text.flowAppFilter, style = MaterialTheme.typography.labelSmall)
            Text(text.flowAllAppsPlaceholder, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = {}) { Text(text.flowStartAnalysis) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(text.flowDemoMode)
        Text(text.flowPreviewOnly, style = MaterialTheme.typography.labelSmall)
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
            Text(event.appName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
            Text(event.target, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(0.9f)) {
            Text(event.selectedRoute, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            StatusChip(event.status)
        }
    }
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

private fun demoFlowEvents(text: UiText): List<FlowEventUi> = listOf(
    FlowEventUi(
        appName = "Telegram",
        domain = "api.telegram.org",
        resolvedIp = "149.154.167.220",
        portProtocol = "443 / TCP TLS",
        dnsPolicy = text.flowDnsPolicySecure,
        selectedRoute = "Xray Germany",
        routeReason = text.telegramRouteReason,
        riskWarning = null,
        recommendation = text.telegramRecommendation,
        status = text.flowAllowedStatus,
        technicalDetails = text.telegramTechnical,
    ),
    FlowEventUi(
        appName = text.browserApp,
        domain = "youtube.com / googlevideo.com",
        resolvedIp = "142.250.185.78",
        portProtocol = "443 / TCP QUIC preview",
        dnsPolicy = text.flowDnsPolicyMedia,
        selectedRoute = "Media tunnel",
        routeReason = text.mediaRouteReason,
        riskWarning = null,
        recommendation = text.mediaRecommendation,
        status = text.flowMediaStatus,
        technicalDetails = text.mediaTechnical,
    ),
    FlowEventUi(
        appName = "Bank / Госуслуги",
        domain = "gosuslugi.ru",
        resolvedIp = "109.207.1.97",
        portProtocol = "443 / TCP TLS",
        dnsPolicy = text.flowDnsPolicyLocal,
        selectedRoute = "Direct",
        routeReason = text.govRouteReason,
        riskWarning = null,
        recommendation = text.govRecommendation,
        status = text.flowDirectStatus,
        technicalDetails = text.govTechnical,
    ),
    FlowEventUi(
        appName = text.workApp,
        domain = "gitlab.corp",
        resolvedIp = "10.44.8.15",
        portProtocol = "443 / TCP TLS",
        dnsPolicy = text.flowDnsPolicyCorp,
        selectedRoute = "Work VPN",
        routeReason = text.workRouteReason,
        riskWarning = null,
        recommendation = text.workRecommendation,
        status = text.flowWorkStatus,
        technicalDetails = text.workTechnical,
    ),
    FlowEventUi(
        appName = text.trackerApp,
        domain = "tracker.example.com",
        resolvedIp = null,
        portProtocol = "443 / TCP TLS",
        dnsPolicy = text.flowDnsPolicyBlock,
        selectedRoute = "Block",
        routeReason = text.trackerRouteReason,
        riskWarning = text.trackerWarning,
        recommendation = text.trackerRecommendation,
        status = text.flowBlockedStatus,
        technicalDetails = text.trackerTechnical,
    ),
)
