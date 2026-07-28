package dev.vifs.viroutefs.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.engine.SING_BOX_BLOCK_TAG
import dev.vifs.viroutefs.engine.SING_BOX_DIRECT_TAG
import dev.vifs.viroutefs.engine.runtimeProfileTag
import dev.vifs.viroutefs.vpn.Ipv4Protocol
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreview
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreviewer
import dev.vifs.viroutefs.vpn.PacketSummary
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import dev.vifs.viroutefs.vpn.VpnConnectionFlow
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
    val appPackages: List<String> = emptyList(),
) {
    val target: String = buildString {
        append(domain)
        val port = portProtocol.substringBefore(" /").takeIf { it.all { char -> char.isDigit() } }
        if (port != null) append(":").append(port)
    }
}

@Composable
internal fun FlowScannerScreen(
    padding: PaddingValues,
    text: UiText,
    vpnState: VpnServiceUiState,
    config: RoutingConfig,
    onClear: () -> Unit,
    onPause: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var selectedEventIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedAppPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var liveDetailsOpen by rememberSaveable { mutableStateOf(false) }
    var runtimeDetailsOpen by rememberSaveable { mutableStateOf(false) }
    val allEvents = remember(vpnState.connectionFlows, vpnState.packetSummaries, config, context) {
        val previewer = LiveRouteDecisionPreviewer(config)
        vpnState.connectionFlows.map { flow -> flow.toFlowEvent(context, config) } +
            vpnState.packetSummaries.map { packet -> packet.toFlowEvent(previewer.preview(packet)) }
    }
    val events = remember(allEvents, selectedAppPackage) {
        selectedAppPackage?.let { packageName ->
            allEvents.filter { packageName in it.appPackages }
        } ?: allEvents
    }
    val appFilters = remember(vpnState.connectionFlows, context) {
        vpnState.connectionFlows
            .flatMap { it.appPackages }
            .distinct()
            .sortedBy { context.applicationLabel(it).lowercase() }
    }
    val selectedEvent = selectedEventIndex?.let { events.getOrNull(it) }
    val showLiveTestRoute = vpnState.tunTestRouteActive || vpnState.packetSummaries.isNotEmpty()
    val showRuntime = vpnState.status == VpnServiceStatus.RuntimeActive

    when {
        runtimeDetailsOpen -> FlowRuntimeDetailsScreen(
            padding = padding,
            text = text,
            vpnState = vpnState,
            onBack = { runtimeDetailsOpen = false },
        )
        liveDetailsOpen -> FlowTunTestRouteDetailsScreen(
            padding = padding,
            text = text,
            vpnState = vpnState,
            onBack = { liveDetailsOpen = false },
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
            showRuntime = showRuntime,
            events = events,
            appFilters = appFilters,
            selectedAppPackage = selectedAppPackage,
            onAppFilter = {
                selectedAppPackage = it
                selectedEventIndex = null
            },
            onClear = {
                selectedEventIndex = null
                onClear()
            },
            onPause = onPause,
            onRuntimeEvent = { runtimeDetailsOpen = true },
            onLiveEvent = { liveDetailsOpen = true },
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
    showRuntime: Boolean,
    events: List<FlowEventUi>,
    appFilters: List<String>,
    selectedAppPackage: String?,
    onAppFilter: (String?) -> Unit,
    onClear: () -> Unit,
    onPause: (Boolean) -> Unit,
    onRuntimeEvent: () -> Unit,
    onLiveEvent: () -> Unit,
    onEvent: (Int) -> Unit,
) = ScreenList(padding) {
    item { Header(text.flowScannerTitle, text.flowScannerSubtitle) }
    item { FlowControlCard(text, vpnState, onClear, onPause) }
    if (appFilters.isNotEmpty()) {
        item {
            CardBlock {
                Text("Отслеживать приложение", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = selectedAppPackage == null,
                        onClick = { onAppFilter(null) },
                        label = { Text("Все") },
                    )
                    appFilters.forEach { packageName ->
                        FilterChip(
                            selected = selectedAppPackage == packageName,
                            onClick = { onAppFilter(packageName) },
                            label = { Text(LocalContext.current.applicationLabel(packageName), maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
    if (showRuntime) {
        item { FlowRuntimeRow(vpnState = vpnState, onClick = onRuntimeEvent) }
    }
    if (showLiveTestRoute) {
        item { FlowTunTestRouteRow(text = text, vpnState = vpnState, onClick = onLiveEvent) }
    }
    if (events.isEmpty() && !showLiveTestRoute && !showRuntime) {
        item { CardBlock { Text(text.flowEmptyState, style = MaterialTheme.typography.bodySmall) } }
    } else {
        items(events.size) { index ->
            FlowEventRow(text = text, event = events[index], onClick = { onEvent(index) })
        }
    }
    item { FlowLimitCard(text) }
}

@Composable
private fun FlowControlCard(
    text: UiText,
    vpnState: VpnServiceUiState,
    onClear: () -> Unit,
    onPause: (Boolean) -> Unit,
) = CardBlock {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
        Text("Что показывает сканер", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Для полученных событий: адрес назначения, порт, тип соединения, выбранное правило, VPN-профиль, риск и понятную рекомендацию.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Содержимое сайтов, сообщения, пароли и файлы не записываются. События поступают только от локального VPN-движка. Статус службы: ${vpnState.status.name}.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onPause(!vpnState.packetInspectorPaused) }) {
                Text(if (vpnState.packetInspectorPaused) "Продолжить" else "Пауза")
            }
            OutlinedButton(onClick = onClear) { Text("Очистить") }
        }
    }
}

@Composable
private fun FlowRuntimeRow(vpnState: VpnServiceUiState, onClick: () -> Unit) = CardBlock {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("VPN-маршрутизатор", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                StatusChip("Активен")
            }
            Text(
                "Локальный движок применяет правила к IPv4/IPv6 и DNS. Нажмите для простого объяснения.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Соединений: ${vpnState.connectionFlows.size} • активно: ${vpnState.connectionFlows.count { it.isActive }} • принято/отправлено: ${vpnState.connectionFlows.sumOf { it.downlinkBytes }}/${vpnState.connectionFlows.sumOf { it.uplinkBytes }} байт",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("›", style = MaterialTheme.typography.titleLarge)
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
private fun FlowRuntimeDetailsScreen(
    padding: PaddingValues,
    text: UiText,
    vpnState: VpnServiceUiState,
    onBack: () -> Unit,
) = ScreenList(padding) {
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text(text.back) }
            Header("VPN-маршрутизатор", "Что сейчас происходит")
        }
    }
    item {
        CardBlock {
            FlowField("Состояние", "Активен")
            FlowField("Движок", "sing-box через Android TUN")
            FlowField("IPv4", "Маршрутизируется")
            FlowField("IPv6", "Маршрутизируется")
            FlowField("DNS", "Перехватывается внутри TUN; применяются пользовательские domain-политики")
            FlowField("События отдельных соединений", "${vpnState.connectionFlows.size}, из них активно ${vpnState.connectionFlows.count { it.isActive }}")
            FlowField("Содержимое трафика", "Не записывается")
        }
    }
    item {
        CardBlock {
            Text("Простыми словами", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Android передаёт сетевые соединения приложений в ViRouteFS. Маршрутизатор сверяет их с правилами сверху вниз и отправляет в System, Block или в настроенный sing-box-профиль. Если выбранный профиль не готов, соединение блокируется.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text("Что намеренно не видно", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Сканер не расшифровывает HTTPS и не сохраняет содержимое пакетов. Домен может отсутствовать, если приложение подключилось сразу по IP или протокол не передал имя.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vpnState.detail?.let { Details(text.details, it) }
        }
    }
}

private fun VpnConnectionFlow.toFlowEvent(context: Context, config: RoutingConfig): FlowEventUi {
    val destinationHost = endpointHost(destination)
    val destinationPort = endpointPort(destination)
    val displayDomain = domain.ifBlank { destinationHost.ifBlank { destination } }
    val decisionInput = appPackages.firstOrNull()
        ?: domain.takeIf(String::isNotBlank)
        ?: destinationHost
    val decision = RouteEngine(config).simulate(decisionInput)
    val routeName = when (outboundTag) {
        SING_BOX_BLOCK_TAG -> "Block / Блокировать"
        SING_BOX_DIRECT_TAG -> "System / Система"
        else -> config.profiles.firstOrNull { runtimeProfileTag(it.id) == outboundTag }?.name
            ?: outboundTag.ifBlank { decision.tunnelProfile.name }
    }
    val blocked = outboundTag == SING_BOX_BLOCK_TAG || outboundType.equals("block", true)
    val direct = outboundTag == SING_BOX_DIRECT_TAG || outboundType.equals("direct", true)
    val service = destinationService(destinationPort, network)
    val appName = appPackages.firstOrNull()?.let(context::applicationLabel)
        ?: processPath.substringAfterLast('/').takeIf(String::isNotBlank)
        ?: "Приложение не определено"
    return FlowEventUi(
        appName = appName,
        domain = displayDomain,
        resolvedIp = destinationHost.takeIf { it.isNotBlank() && it != displayDomain },
        portProtocol = "${destinationPort ?: "—"} / ${network.ifBlank { protocol }.uppercase()}",
        dnsPolicy = decision.dnsPolicySummary,
        selectedRoute = routeName,
        routeReason = matchedRule.takeIf(String::isNotBlank)
            ?: "Локальный движок выбрал маршрут «$routeName» по действующим правилам.",
        riskWarning = when {
            blocked -> "Соединение заблокировано выбранным правилом."
            direct -> "Это соединение идёт напрямую через System как явное исключение и не использует туннель провайдера."
            destinationPort == 80 -> "Порт 80 обычно означает незашифрованный HTTP. Не вводите пароли без HTTPS."
            else -> null
        },
        recommendation = when {
            blocked -> "Если блокировка неожиданна, откройте правило и проверьте приложение, адрес и приоритет."
            service.isKnown -> "Это похоже на ${service.name}. Проверьте, что выбранный туннель и DNS соответствуют задаче."
            else -> "Неизвестный порт сам по себе не означает угрозу. Сверьте адрес с приложением и временем подключения."
        },
        status = when {
            blocked -> "Заблокировано"
            isActive -> "Активно"
            else -> "Завершено"
        },
        technicalDetails = buildString {
            appendLine("ID: $id")
            appendLine("Источник: $source")
            appendLine("Назначение: $destination")
            appendLine("Домен: ${domain.ifBlank { "не получен" }}")
            appendLine("Сеть/протокол: $network / ${protocol.ifBlank { "не определён" }}")
            appendLine("Пакеты приложений: ${appPackages.joinToString().ifBlank { "не определены" }}")
            appendLine("Процесс: ${processPath.ifBlank { "не определён" }}")
            appendLine("Outbound: $outboundTag ($outboundType)")
            appendLine("Отправлено: $uplinkBytes байт")
            appendLine("Получено: $downlinkBytes байт")
            append("Создано: ${DateFormat.getDateTimeInstance().format(Date(createdAt))}")
        },
        sourceLabel = "Живой поток",
        appPackages = appPackages,
    )
}

private fun endpointHost(endpoint: String): String = when {
    endpoint.startsWith("[") -> endpoint.substringAfter('[').substringBefore(']')
    endpoint.count { it == ':' } == 1 -> endpoint.substringBeforeLast(':')
    else -> endpoint
}

private fun endpointPort(endpoint: String): Int? = when {
    endpoint.startsWith("[") -> endpoint.substringAfter("]:", "").toIntOrNull()
    endpoint.count { it == ':' } == 1 -> endpoint.substringAfterLast(':').toIntOrNull()
    else -> null
}

private fun destinationService(port: Int?, network: String): DestinationService = when (port) {
    22 -> DestinationService("защищённое подключение SSH", true)
    25, 465, 587 -> DestinationService("отправку электронной почты", true)
    53 -> DestinationService("запрос DNS — поиск IP-адреса сайта", true)
    80 -> DestinationService("обычный незашифрованный веб-трафик HTTP", true)
    123 -> DestinationService("синхронизацию времени NTP", true)
    443 -> DestinationService(
        if (network.equals("udp", true)) "QUIC/HTTP3 (обычно защищённый веб-трафик)" else "HTTPS (обычно защищённый веб-трафик)",
        true,
    )
    993 -> DestinationService("защищённое получение электронной почты", true)
    3478, 5349 -> DestinationService("STUN/TURN для звонков и видеосвязи", true)
    else -> DestinationService("неизвестный или пользовательский сервис", false)
}

@Suppress("DEPRECATION")
private fun Context.applicationLabel(packageName: String): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        packageManager.getApplicationInfo(packageName, 0)
    }
    packageManager.getApplicationLabel(info).toString()
}.getOrDefault(packageName)

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

private fun PacketSummary.toFlowEvent(preview: LiveRouteDecisionPreview): FlowEventUi {
    val service = destinationService()
    val blocked = preview.selectedProfileType.equals("Block", ignoreCase = true)
    return FlowEventUi(
        appName = "Приложение не определено",
        domain = dstIp,
        resolvedIp = dstIp,
        portProtocol = "${dstPort ?: "—"} / ${protocol.name.uppercase()}",
        dnsPolicy = "DNS уже завершён или не использовался",
        selectedRoute = preview.selectedProfileName,
        routeReason = "Назначение $dstIp проверено по CIDR/IP-правилам; выбрано правило «${preview.matchedRuleName ?: "по умолчанию"}».",
        riskWarning = when {
            blocked -> "Соединение будет заблокировано выбранным правилом."
            protocol == Ipv4Protocol.Tcp && dstPort == 80 -> "Порт 80 обычно означает незашифрованный HTTP. Не вводите пароли без HTTPS."
            else -> preview.warnings.firstOrNull()
        },
        recommendation = when {
            blocked -> "Если блокировка неожиданна, откройте правило и проверьте его сеть и приоритет."
            service.isKnown -> "Похоже на ${service.name}. Проверьте, что выбранный профиль и DNS-политика соответствуют вашей задаче."
            else -> "Неизвестный порт не означает угрозу. Сверьте адрес с приложением, которое вы только что открыли."
        },
        status = if (blocked) "Блокировать" else "Маршрут рассчитан",
        technicalDetails = buildString {
            appendLine("Источник: $srcIp:${srcPort ?: "—"}")
            appendLine("Назначение: $dstIp:${dstPort ?: "—"}")
            appendLine("Протокол: ${protocol.name}")
            appendLine("Размер IP-пакета: $packetSize байт")
            appendLine("Вероятный сервис: ${service.name}")
            append(preview.displayLines.joinToString("\n"))
        },
        sourceLabel = "Метаданные TUN",
    )
}

private data class DestinationService(val name: String, val isKnown: Boolean)

private fun PacketSummary.destinationService(): DestinationService = when (dstPort) {
    22 -> DestinationService("защищённое подключение SSH", true)
    25, 465, 587 -> DestinationService("отправку электронной почты", true)
    53 -> DestinationService("запрос DNS — поиск IP-адреса сайта", true)
    80 -> DestinationService("обычный незашифрованный веб-трафик HTTP", true)
    123 -> DestinationService("синхронизацию времени NTP", true)
    443 -> DestinationService(
        if (protocol == Ipv4Protocol.Udp) "QUIC/HTTP3 (обычно защищённый веб-трафик)" else "HTTPS (обычно защищённый веб-трафик)",
        true,
    )
    993 -> DestinationService("защищённое получение электронной почты", true)
    3478, 5349 -> DestinationService("STUN/TURN для звонков и видеосвязи", true)
    else -> DestinationService("неизвестный или пользовательский сервис", false)
}
