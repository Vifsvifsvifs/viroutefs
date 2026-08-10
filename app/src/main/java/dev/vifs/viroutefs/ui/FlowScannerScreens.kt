package dev.vifs.viroutefs.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.vifs.viroutefs.InstalledAppUi
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.UiText
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.loadInstalledAppsForRouting
import dev.vifs.viroutefs.root.RootSocketSnapshot
import dev.vifs.viroutefs.root.RootSocketSnapshotScanner
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteDecision
import dev.vifs.viroutefs.routing.RouteQuery
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RouteTransport
import dev.vifs.viroutefs.engine.SING_BOX_BLOCK_TAG
import dev.vifs.viroutefs.engine.SING_BOX_DIRECT_TAG
import dev.vifs.viroutefs.engine.runtimeProfileTag
import dev.vifs.viroutefs.vpn.Ipv4Protocol
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreview
import dev.vifs.viroutefs.vpn.LiveRouteDecisionPreviewer
import dev.vifs.viroutefs.vpn.PacketSummary
import dev.vifs.viroutefs.vpn.ProfileGroupRuntimeEvent
import dev.vifs.viroutefs.vpn.DnsFallbackRuntimeEvent
import dev.vifs.viroutefs.vpn.ProfileGroupRuntimeReason
import dev.vifs.viroutefs.vpn.VpnServiceStatus
import dev.vifs.viroutefs.vpn.VpnServiceUiState
import dev.vifs.viroutefs.vpn.VpnConnectionFlow
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TEST_ROUTE_CIDR = "203.0.113.0/24"
private const val TEST_ROUTE_SOURCE = "Developer TEST-NET counter"
private const val TEST_ROUTE_MODE = "Developer diagnostics"

internal enum class FlowProtocolFilter(val label: String) {
    All("Все"),
    Tcp("TCP"),
    Udp("UDP"),
    Icmp("ICMP"),
    Other("Другие"),
}

internal enum class FlowLifecycle {
    Active,
    Closed,
    Snapshot,
}

internal enum class FlowLifecycleFilter(val label: String) {
    All("Все"),
    Active("Активные"),
    Closed("Завершённые"),
}

internal enum class FlowActionFilter(val label: String) {
    All("Любое действие"),
    Allowed("Разрешено"),
    Blocked("Заблокировано"),
}

internal enum class FlowIpVersion {
    Ipv4,
    Ipv6,
    Unknown,
}

internal enum class FlowIpVersionFilter(val label: String) {
    All("IPv4 и IPv6"),
    Ipv4("IPv4"),
    Ipv6("IPv6"),
}

internal enum class FlowTimeFilter(val label: String, val maxAgeMillis: Long?) {
    All("За всё время", null),
    Last5Minutes("5 минут", 5 * 60 * 1_000L),
    Last30Minutes("30 минут", 30 * 60 * 1_000L),
    LastHour("1 час", 60 * 60 * 1_000L),
}

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
    val routeCheck: String = "Предварительный расчёт",
    val appPackages: List<String> = emptyList(),
    val lifecycle: FlowLifecycle = FlowLifecycle.Snapshot,
    val isBlocked: Boolean = false,
    val ipVersion: FlowIpVersion = FlowIpVersion.Unknown,
    val observedAt: Long = 0L,
    val finishedAt: Long? = null,
    val durationMillis: Long? = null,
    val closeReason: String? = null,
) {
    val target: String = buildString {
        append(domain)
        val port = portProtocol.substringBefore(" /").takeIf { it.all { char -> char.isDigit() } }
        if (port != null) append(":").append(port)
    }
}

private fun RootSocketSnapshot.toFlowEvent(context: Context, observedAt: Long): FlowEventUi {
    val remoteIsUnspecified = remoteAddress in setOf("0.0.0.0", "::", "0:0:0:0:0:0:0:0")
    val endpoint = if (remoteIsUnspecified || remotePort == 0) {
        "локальный сокет"
    } else {
        remoteAddress
    }
    val packages = packageNames.distinct()
    val appLabel = packages
        .map(context::applicationLabel)
        .distinct()
        .joinToString()
        .ifBlank { "UID $uid" }
    val displayPort = if (remotePort > 0) remotePort else localPort
    return FlowEventUi(
        appName = appLabel,
        domain = endpoint,
        resolvedIp = remoteAddress.takeUnless { remoteIsUnspecified },
        portProtocol = "$displayPort / $protocol",
        dnsPolicy = "Root-снимок не содержит DNS-политику",
        selectedRoute = "Таблица сокетов ядра",
        routeReason = "Одноразовый root-снимок; маршрут и содержимое пакета не анализировались",
        riskWarning = null,
        recommendation = "Сопоставьте этот прямой сокет с одновременным событием VPN или правилом root-файрвола.",
        status = state,
        technicalDetails = "local=$localAddress:$localPort remote=$remoteAddress:$remotePort uid=$uid state=$state",
        sourceLabel = "Root /proc socket snapshot",
        routeCheck = "Маршрут не вычислялся",
        appPackages = packages,
        lifecycle = FlowLifecycle.Snapshot,
        isBlocked = false,
        ipVersion = if (remoteAddress.contains(':')) FlowIpVersion.Ipv6 else FlowIpVersion.Ipv4,
        observedAt = observedAt,
    )
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
    var filterQuery by rememberSaveable { mutableStateOf("") }
    var protocolFilterName by rememberSaveable { mutableStateOf(FlowProtocolFilter.All.name) }
    var lifecycleFilterName by rememberSaveable { mutableStateOf(FlowLifecycleFilter.All.name) }
    var actionFilterName by rememberSaveable { mutableStateOf(FlowActionFilter.All.name) }
    var ipVersionFilterName by rememberSaveable { mutableStateOf(FlowIpVersionFilter.All.name) }
    var timeFilterName by rememberSaveable { mutableStateOf(FlowTimeFilter.All.name) }
    var filterClockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var appPickerOpen by rememberSaveable { mutableStateOf(false) }
    var liveDetailsOpen by rememberSaveable { mutableStateOf(false) }
    var runtimeDetailsOpen by rememberSaveable { mutableStateOf(false) }
    var pendingCsvExport by remember { mutableStateOf("") }
    var exportMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var rootSockets by remember { mutableStateOf<List<RootSocketSnapshot>>(emptyList()) }
    var rootSnapshotAt by remember { mutableLongStateOf(0L) }
    var rootScanBusy by remember { mutableStateOf(false) }
    var rootScanMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val rootSocketScanner = remember(context) { RootSocketSnapshotScanner(context.applicationContext) }
    val installedApps = remember(context) { context.loadInstalledAppsForRouting() }
    val allEvents = remember(vpnState.connectionFlows, vpnState.packetSummaries, rootSockets, rootSnapshotAt, config, context) {
        val previewer = LiveRouteDecisionPreviewer(config)
        vpnState.connectionFlows.map { flow -> flow.toFlowEvent(context, config) } +
            vpnState.packetSummaries.map { packet -> packet.toFlowEvent(previewer.preview(packet)) } +
            rootSockets.map { socket -> socket.toFlowEvent(context, rootSnapshotAt) }
    }
    val protocolFilter = FlowProtocolFilter.entries
        .firstOrNull { it.name == protocolFilterName }
        ?: FlowProtocolFilter.All
    val lifecycleFilter = FlowLifecycleFilter.entries
        .firstOrNull { it.name == lifecycleFilterName }
        ?: FlowLifecycleFilter.All
    val actionFilter = FlowActionFilter.entries
        .firstOrNull { it.name == actionFilterName }
        ?: FlowActionFilter.All
    val ipVersionFilter = FlowIpVersionFilter.entries
        .firstOrNull { it.name == ipVersionFilterName }
        ?: FlowIpVersionFilter.All
    val timeFilter = FlowTimeFilter.entries
        .firstOrNull { it.name == timeFilterName }
        ?: FlowTimeFilter.All
    LaunchedEffect(timeFilter) {
        filterClockMillis = System.currentTimeMillis()
        while (timeFilter.maxAgeMillis != null) {
            delay(30_000L)
            filterClockMillis = System.currentTimeMillis()
        }
    }
    val events = remember(
        allEvents,
        selectedAppPackage,
        filterQuery,
        protocolFilter,
        lifecycleFilter,
        actionFilter,
        ipVersionFilter,
        timeFilter,
        filterClockMillis,
    ) {
        filterFlowEvents(
            events = allEvents,
            appPackage = selectedAppPackage,
            query = filterQuery,
            protocol = protocolFilter,
            lifecycle = lifecycleFilter,
            action = actionFilter,
            ipVersion = ipVersionFilter,
            time = timeFilter,
            nowMillis = filterClockMillis,
        ).sortedByDescending(FlowEventUi::observedAt)
    }
    val activeFilterCount = listOf(
        selectedAppPackage != null,
        filterQuery.isNotBlank(),
        protocolFilter != FlowProtocolFilter.All,
        lifecycleFilter != FlowLifecycleFilter.All,
        actionFilter != FlowActionFilter.All,
        ipVersionFilter != FlowIpVersionFilter.All,
        timeFilter != FlowTimeFilter.All,
    ).count { it }
    val appFilters = remember(vpnState.connectionFlows, selectedAppPackage, context) {
        (vpnState.connectionFlows
            .flatMap { it.appPackages }
            .plus(listOfNotNull(selectedAppPackage)))
            .distinct()
            .sortedBy { context.applicationLabel(it).lowercase() }
    }
    val selectedEvent = selectedEventIndex?.let { events.getOrNull(it) }
    val showLiveTestRoute = vpnState.tunTestRouteActive || vpnState.packetSummaries.isNotEmpty()
    val showRuntime = vpnState.status == VpnServiceStatus.RuntimeActive
    val csvExporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null && pendingCsvExport.isNotBlank()) {
            val exportText = pendingCsvExport
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                            it.write(exportText)
                        } ?: error("Android не открыл выбранный файл.")
                    }
                }.onSuccess {
                    exportMessage = "CSV сохранён. В нём только метаданные выбранных соединений."
                }.onFailure { error ->
                    exportMessage = "Не удалось сохранить CSV: ${error.localizedMessage ?: "неизвестная ошибка"}"
                }
            }
        }
    }

    BackHandler(enabled = appPickerOpen || runtimeDetailsOpen || liveDetailsOpen || selectedEvent != null) {
        when {
            appPickerOpen -> appPickerOpen = false
            runtimeDetailsOpen -> runtimeDetailsOpen = false
            liveDetailsOpen -> liveDetailsOpen = false
            selectedEvent != null -> selectedEventIndex = null
        }
    }

    when {
        appPickerOpen -> FlowAppPickerScreen(
            padding = padding,
            apps = installedApps,
            selectedPackage = selectedAppPackage,
            onBack = { appPickerOpen = false },
            onSelect = { packageName ->
                selectedAppPackage = packageName
                selectedEventIndex = null
                appPickerOpen = false
            },
        )
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
            filterQuery = filterQuery,
            protocolFilter = protocolFilter,
            lifecycleFilter = lifecycleFilter,
            actionFilter = actionFilter,
            ipVersionFilter = ipVersionFilter,
            timeFilter = timeFilter,
            totalEventCount = allEvents.size,
            activeFilterCount = activeFilterCount,
            onOpenAppPicker = { appPickerOpen = true },
            onFilterQuery = {
                filterQuery = it
                selectedEventIndex = null
            },
            onProtocolFilter = {
                protocolFilterName = it.name
                selectedEventIndex = null
            },
            onLifecycleFilter = {
                lifecycleFilterName = it.name
                selectedEventIndex = null
            },
            onActionFilter = {
                actionFilterName = it.name
                selectedEventIndex = null
            },
            onIpVersionFilter = {
                ipVersionFilterName = it.name
                selectedEventIndex = null
            },
            onTimeFilter = {
                timeFilterName = it.name
                selectedEventIndex = null
            },
            onResetFilters = {
                selectedAppPackage = null
                filterQuery = ""
                protocolFilterName = FlowProtocolFilter.All.name
                lifecycleFilterName = FlowLifecycleFilter.All.name
                actionFilterName = FlowActionFilter.All.name
                ipVersionFilterName = FlowIpVersionFilter.All.name
                timeFilterName = FlowTimeFilter.All.name
                selectedEventIndex = null
            },
            exportMessage = exportMessage,
            onExport = {
                pendingCsvExport = exportFlowEventsCsv(events)
                exportMessage = null
                csvExporter.launch("ViRouteFS-flow-metadata.csv")
            },
            onAppFilter = {
                selectedAppPackage = it
                selectedEventIndex = null
            },
            onClear = {
                selectedEventIndex = null
                rootSockets = emptyList()
                rootSnapshotAt = 0L
                rootScanMessage = null
                onClear()
            },
            onPause = onPause,
            onRuntimeEvent = { runtimeDetailsOpen = true },
            onLiveEvent = { liveDetailsOpen = true },
            onEvent = { selectedEventIndex = it },
            rootScanBusy = rootScanBusy,
            rootScanMessage = rootScanMessage,
            onRootSnapshot = {
                rootScanBusy = true
                rootScanMessage = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { rootSocketScanner.scan() }
                    rootScanMessage = result.message
                    if (result.successful) {
                        rootSockets = result.sockets
                        rootSnapshotAt = System.currentTimeMillis()
                        selectedEventIndex = null
                    }
                    rootScanBusy = false
                }
            },
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
    filterQuery: String,
    protocolFilter: FlowProtocolFilter,
    lifecycleFilter: FlowLifecycleFilter,
    actionFilter: FlowActionFilter,
    ipVersionFilter: FlowIpVersionFilter,
    timeFilter: FlowTimeFilter,
    totalEventCount: Int,
    activeFilterCount: Int,
    onOpenAppPicker: () -> Unit,
    onFilterQuery: (String) -> Unit,
    onProtocolFilter: (FlowProtocolFilter) -> Unit,
    onLifecycleFilter: (FlowLifecycleFilter) -> Unit,
    onActionFilter: (FlowActionFilter) -> Unit,
    onIpVersionFilter: (FlowIpVersionFilter) -> Unit,
    onTimeFilter: (FlowTimeFilter) -> Unit,
    onResetFilters: () -> Unit,
    exportMessage: String?,
    onExport: () -> Unit,
    onAppFilter: (String?) -> Unit,
    onClear: () -> Unit,
    onPause: (Boolean) -> Unit,
    onRuntimeEvent: () -> Unit,
    onLiveEvent: () -> Unit,
    onEvent: (Int) -> Unit,
    rootScanBusy: Boolean,
    rootScanMessage: String?,
    onRootSnapshot: () -> Unit,
) = ScreenList(padding) {
    item { FlowControlCard(text, vpnState, onClear, onPause) }
    item {
        CardBlock {
            Text("Root-снимок прямых сокетов", fontWeight = FontWeight.SemiBold)
            Text(
                "По отдельной кнопке читает ограниченные таблицы /proc/net/tcp*,udp* и сопоставляет UID с локальными пакетами. Это дополняет события VPN прямыми соединениями; содержимое пакетов и TLS не читается.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onRootSnapshot,
                enabled = !rootScanBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (rootScanBusy) "Ожидаем root-менеджер…" else "Снять root-снимок сокетов")
            }
            rootScanMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
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
                    Text("Отслеживать приложение", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        selectedAppPackage?.let { LocalContext.current.applicationLabel(it) }
                            ?: "Показываются все приложения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onOpenAppPicker) { Text("Выбрать") }
            }
            if (appFilters.isNotEmpty()) {
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
                            leadingIcon = {
                                InstalledApplicationIcon(
                                    packageName = packageName,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            label = { Text(LocalContext.current.applicationLabel(packageName), maxLines = 1) },
                        )
                    }
                }
            }
        }
    }
    item {
        FlowFiltersCard(
            events = events,
            totalEventCount = totalEventCount,
            activeFilterCount = activeFilterCount,
            filterQuery = filterQuery,
            protocolFilter = protocolFilter,
            lifecycleFilter = lifecycleFilter,
            actionFilter = actionFilter,
            ipVersionFilter = ipVersionFilter,
            timeFilter = timeFilter,
            onFilterQuery = onFilterQuery,
            onProtocolFilter = onProtocolFilter,
            onLifecycleFilter = onLifecycleFilter,
            onActionFilter = onActionFilter,
            onIpVersionFilter = onIpVersionFilter,
            onTimeFilter = onTimeFilter,
            onResetFilters = onResetFilters,
            exportMessage = exportMessage,
            onExport = onExport,
        )
    }
    if (showRuntime) {
        item { FlowRuntimeRow(vpnState = vpnState, onClick = onRuntimeEvent) }
    }
    if (vpnState.profileGroupEvents.isNotEmpty()) {
        item { ProfileGroupJournalCard(text, vpnState.profileGroupEvents) }
    }
    if (vpnState.dnsFallbackEvents.isNotEmpty()) {
        item { DnsFallbackJournalCard(text, vpnState.dnsFallbackEvents) }
    }
    if (showLiveTestRoute) {
        item { FlowTunTestRouteRow(text = text, vpnState = vpnState, onClick = onLiveEvent) }
    }
    if (events.isEmpty()) {
        item {
            CardBlock {
                Text(
                    if (totalEventCount > 0 && activeFilterCount > 0) {
                        "Соединения есть, но ни одно не подходит под выбранные фильтры. Сбросьте часть условий или нажмите «Сбросить»."
                    } else if (selectedAppPackage == null) {
                        text.flowEmptyState
                    } else {
                        "У выбранного приложения пока нет соединений. Откройте его и выполните нужное действие — новые подключения появятся здесь."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    } else {
        items(events.size) { index ->
            FlowEventRow(text = text, event = events[index], onClick = { onEvent(index) })
        }
    }
}

@Composable
private fun FlowFiltersCard(
    events: List<FlowEventUi>,
    totalEventCount: Int,
    activeFilterCount: Int,
    filterQuery: String,
    protocolFilter: FlowProtocolFilter,
    lifecycleFilter: FlowLifecycleFilter,
    actionFilter: FlowActionFilter,
    ipVersionFilter: FlowIpVersionFilter,
    timeFilter: FlowTimeFilter,
    onFilterQuery: (String) -> Unit,
    onProtocolFilter: (FlowProtocolFilter) -> Unit,
    onLifecycleFilter: (FlowLifecycleFilter) -> Unit,
    onActionFilter: (FlowActionFilter) -> Unit,
    onIpVersionFilter: (FlowIpVersionFilter) -> Unit,
    onTimeFilter: (FlowTimeFilter) -> Unit,
    onResetFilters: () -> Unit,
    exportMessage: String?,
    onExport: () -> Unit,
) = CardBlock {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Фильтры соединений", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(
                "Показано ${events.size} из $totalEventCount • активно фильтров: $activeFilterCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onResetFilters, enabled = activeFilterCount > 0) {
            Text("Сбросить")
        }
    }
    OutlinedTextField(
        value = filterQuery,
        onValueChange = onFilterQuery,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Адрес, домен, пакет или маршрут") },
        placeholder = { Text("Например: 443, example.com, com.browser") },
        singleLine = true,
    )
    FlowFilterRow("Протокол") {
        FlowProtocolFilter.entries.forEach { option ->
            FilterChip(
                selected = protocolFilter == option,
                onClick = { onProtocolFilter(option) },
                label = { Text(option.label) },
            )
        }
    }
    FlowFilterRow("Состояние") {
        FlowLifecycleFilter.entries.forEach { option ->
            FilterChip(
                selected = lifecycleFilter == option,
                onClick = { onLifecycleFilter(option) },
                label = { Text(option.label) },
            )
        }
    }
    FlowFilterRow("Результат") {
        FlowActionFilter.entries.forEach { option ->
            FilterChip(
                selected = actionFilter == option,
                onClick = { onActionFilter(option) },
                label = { Text(option.label) },
            )
        }
    }
    FlowFilterRow("IP-версия") {
        FlowIpVersionFilter.entries.forEach { option ->
            FilterChip(
                selected = ipVersionFilter == option,
                onClick = { onIpVersionFilter(option) },
                label = { Text(option.label) },
            )
        }
    }
    FlowFilterRow("Начало") {
        FlowTimeFilter.entries.forEach { option ->
            FilterChip(
                selected = timeFilter == option,
                onClick = { onTimeFilter(option) },
                label = { Text(option.label) },
            )
        }
    }
    OutlinedButton(
        onClick = onExport,
        enabled = events.isNotEmpty(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Сохранить показанные метаданные в CSV")
    }
    Text(
        "Экспорт выполняется только по нажатию и не содержит payload, HTTPS-содержимое или секреты.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    exportMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun FlowFilterRow(label: String, content: @Composable () -> Unit) {
    Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        content()
    }
}

internal fun filterFlowEvents(
    events: List<FlowEventUi>,
    appPackage: String?,
    query: String,
    protocol: FlowProtocolFilter,
    lifecycle: FlowLifecycleFilter = FlowLifecycleFilter.All,
    action: FlowActionFilter = FlowActionFilter.All,
    ipVersion: FlowIpVersionFilter = FlowIpVersionFilter.All,
    time: FlowTimeFilter = FlowTimeFilter.All,
    nowMillis: Long = System.currentTimeMillis(),
): List<FlowEventUi> {
    val normalizedQuery = query.trim()
    val oldestAllowed = time.maxAgeMillis?.let { maxAge -> nowMillis - maxAge }
    return events.filter { event ->
        val appMatches = appPackage == null || appPackage in event.appPackages
        val eventProtocol = event.portProtocol
            .substringAfter("/", "")
            .trim()
            .uppercase()
        val protocolMatches = when (protocol) {
            FlowProtocolFilter.All -> true
            FlowProtocolFilter.Tcp -> eventProtocol == "TCP"
            FlowProtocolFilter.Udp -> eventProtocol == "UDP"
            FlowProtocolFilter.Icmp -> eventProtocol == "ICMP"
            FlowProtocolFilter.Other -> eventProtocol !in setOf("TCP", "UDP", "ICMP")
        }
        val lifecycleMatches = when (lifecycle) {
            FlowLifecycleFilter.All -> true
            FlowLifecycleFilter.Active -> event.lifecycle == FlowLifecycle.Active
            FlowLifecycleFilter.Closed -> event.lifecycle == FlowLifecycle.Closed
        }
        val actionMatches = when (action) {
            FlowActionFilter.All -> true
            FlowActionFilter.Allowed -> !event.isBlocked
            FlowActionFilter.Blocked -> event.isBlocked
        }
        val ipVersionMatches = when (ipVersion) {
            FlowIpVersionFilter.All -> true
            FlowIpVersionFilter.Ipv4 -> event.ipVersion == FlowIpVersion.Ipv4
            FlowIpVersionFilter.Ipv6 -> event.ipVersion == FlowIpVersion.Ipv6
        }
        val timeMatches = oldestAllowed == null ||
            (event.observedAt > 0L && event.observedAt in oldestAllowed..nowMillis)
        val queryMatches = normalizedQuery.isBlank() || listOf(
            event.appName,
            event.domain,
            event.resolvedIp.orEmpty(),
            event.portProtocol,
            event.selectedRoute,
            event.routeReason,
            event.routeCheck,
            event.appPackages.joinToString(" "),
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
        appMatches &&
            protocolMatches &&
            lifecycleMatches &&
            actionMatches &&
            ipVersionMatches &&
            timeMatches &&
            queryMatches
    }
}

internal fun exportFlowEventsCsv(events: List<FlowEventUi>): String = buildString {
    appendLine(
        listOf(
            "application",
            "packages",
            "destination",
            "resolved_ip",
            "port_protocol",
            "dns_policy",
            "route",
            "reason",
            "route_check",
            "status",
            "lifecycle",
            "action",
            "ip_version",
            "observed_at_epoch_ms",
            "finished_at_epoch_ms",
            "duration_ms",
            "close_reason",
            "source",
        ).joinToString(","),
    )
    events.forEach { event ->
        appendLine(
            listOf(
                event.appName,
                event.appPackages.joinToString(" "),
                event.domain,
                event.resolvedIp.orEmpty(),
                event.portProtocol,
                event.dnsPolicy,
                event.selectedRoute,
                event.routeReason,
                event.routeCheck,
                event.status,
                event.lifecycle.name,
                if (event.isBlocked) "blocked" else "allowed",
                event.ipVersion.name,
                event.observedAt.toString(),
                event.finishedAt?.toString().orEmpty(),
                event.durationMillis?.toString().orEmpty(),
                event.closeReason.orEmpty(),
                event.sourceLabel,
            ).joinToString(",") { value -> value.toCsvCell() },
        )
    }
}

private fun String.toCsvCell(): String =
    "\"${replace("\"", "\"\"")}\""

@Composable
private fun FlowAppPickerScreen(
    padding: PaddingValues,
    apps: List<InstalledAppUi>,
    selectedPackage: String?,
    onBack: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    var search by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, search) {
        val query = search.trim()
        if (query.isBlank()) {
            apps
        } else {
            apps.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }
    ScreenList(padding) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBack) { Text("Назад") }
                Header("Выбор приложения", "Можно начать наблюдение до первого соединения")
            }
        }
        item {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Название или пакет") },
                placeholder = { Text("Например: браузер или com.example.app") },
                singleLine = true,
            )
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Все приложения", fontWeight = FontWeight.SemiBold)
                        Text("Не ограничивать список соединений", style = MaterialTheme.typography.bodySmall)
                    }
                    if (selectedPackage == null) StatusChip("Выбрано")
                }
            }
        }
        items(filtered.size) { index ->
            val app = filtered[index]
            CardBlock {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(app.packageName) },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    InstalledApplicationIcon(
                        packageName = app.packageName,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    when {
                        selectedPackage == app.packageName -> StatusChip("Выбрано")
                        app.isSystem -> StatusChip("Системное")
                    }
                }
            }
        }
        if (filtered.isEmpty()) {
            item { CardBlock { Text("Приложения по этому запросу не найдены.") } }
        }
    }
}

@Composable
private fun FlowControlCard(
    text: UiText,
    vpnState: VpnServiceUiState,
    onClear: () -> Unit,
    onPause: (Boolean) -> Unit,
) = CardBlock {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Соединения приложений", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Адрес, приложение, правило и выбранный маршрут.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(if (vpnState.packetInspectorPaused) "Пауза" else "В эфире")
        }
        Text(
            "Содержимое сайтов, сообщения, пароли и файлы не записываются.",
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
        InstalledApplicationIcon(
            packageName = event.appPackages.firstOrNull(),
            contentDescription = null,
            modifier = Modifier.size(40.dp),
        )
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
    val decision = explainFlowRoute(
        config = config,
        appPackages = appPackages,
        domain = domain,
        destinationIp = destinationHost,
        destinationPort = destinationPort,
        network = network,
    )
    val routeName = when (outboundTag) {
        SING_BOX_BLOCK_TAG -> "Block / Блокировать"
        SING_BOX_DIRECT_TAG -> "System / Система"
        else -> config.profiles.firstOrNull { runtimeProfileTag(it.id) == outboundTag }?.name
            ?: config.profileGroups.firstOrNull { runtimeProfileTag(it.id) == outboundTag }?.name
            ?: outboundTag.ifBlank { decision.tunnelProfile.name }
    }
    val blocked = outboundTag == SING_BOX_BLOCK_TAG || outboundType.equals("block", true)
    val direct = outboundTag == SING_BOX_DIRECT_TAG || outboundType.equals("direct", true)
    val expectedTags = expectedRuntimeTags(config, decision)
    val expectedRouteName = config.profileGroups.firstOrNull { it.id == decision.targetId }?.name
        ?: decision.tunnelProfile.name
    val routeMatches = outboundTag in expectedTags
    val routeCheck = if (routeMatches) {
        if (decision.targetId in config.profileGroups.map { it.id }) {
            "Совпадает: правило «${decision.matchedRule.name}» выбрало группу «$expectedRouteName», её активный участник — «$routeName»."
        } else {
            "Совпадает: правило «${decision.matchedRule.name}» рассчитало тот же маршрут."
        }
    } else {
        "Требует проверки: правило «${decision.matchedRule.name}» ожидало «$expectedRouteName», фактически выбран «$routeName»."
    }
    val routeMismatchWarning = routeCheck.takeUnless { routeMatches }
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
        routeReason = buildString {
            matchedRule.takeIf(String::isNotBlank)?.let {
                append("Живой движок: ").append(it).append(". ")
            }
            append("Локальный расчёт: правило «${decision.matchedRule.name}» → «${decision.tunnelProfile.name}».")
        },
        riskWarning = listOfNotNull(
            routeMismatchWarning,
            when {
                blocked -> "Соединение заблокировано выбранным правилом."
                direct -> "Это соединение идёт через System — обычный мобильный интернет или Wi‑Fi телефона."
                destinationPort == 80 -> "Порт 80 обычно означает незашифрованный HTTP. Не вводите пароли без HTTPS."
                else -> null
            },
        ).joinToString(" ").takeIf(String::isNotBlank),
        recommendation = when {
            !routeMatches -> "Проверьте порядок правил, приложение, домен, порт и транспорт. Фактический outbound выше является источником истины."
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
            appendLine("Допустимые outbound: ${expectedTags.joinToString()}")
            appendLine("Проверка: $routeCheck")
            appendLine("Отправлено: $uplinkBytes байт")
            appendLine("Получено: $downlinkBytes байт")
            append("Создано: ${DateFormat.getDateTimeInstance().format(Date(createdAt))}")
        },
        sourceLabel = "Живой поток",
        routeCheck = routeCheck,
        appPackages = appPackages,
        lifecycle = if (isActive) FlowLifecycle.Active else FlowLifecycle.Closed,
        isBlocked = blocked,
        ipVersion = detectIpVersion(destinationHost),
        observedAt = createdAt,
        finishedAt = closedAt,
        durationMillis = closedAt?.let { finished -> (finished - createdAt).coerceAtLeast(0L) },
    )
}

@Composable
private fun ProfileGroupJournalCard(
    text: UiText,
    events: List<ProfileGroupRuntimeEvent>,
) = CardBlock {
    Text(
        "Переключения групп",
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Почему резерв или round-robin выбрал именно этот маршрут. Журнал хранится только в памяти до остановки или очистки сканера.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    events.take(8).forEach { event ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(event.groupName, fontWeight = FontWeight.SemiBold)
                Text(
                    event.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    event.timestamp.formatPacketTime(text),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(event.reason.displayName())
        }
    }
    if (events.size > 8) {
        Text(
            "Ещё событий: ${events.size - 8}. Очистка сканера удаляет и этот журнал.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun ProfileGroupRuntimeReason.displayName(): String = when (this) {
    ProfileGroupRuntimeReason.InitialSelection -> "Старт"
    ProfileGroupRuntimeReason.PrimaryRecovered -> "Основной"
    ProfileGroupRuntimeReason.Failover -> "Резерв"
    ProfileGroupRuntimeReason.RoundRobin -> "По кругу"
    ProfileGroupRuntimeReason.AvailabilityRecovered -> "Восстановлен"
    ProfileGroupRuntimeReason.AllUnavailable -> "Недоступно"
}

@Composable
private fun DnsFallbackJournalCard(
    text: UiText,
    events: List<DnsFallbackRuntimeEvent>,
) = CardBlock {
    Text(
        "Резервные DNS-серверы",
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Здесь видно, почему запрос перешёл на следующий DNS. Доменные имена и ответы не сохраняются; журнал живёт только в памяти.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    events.take(8).forEach { event ->
        Column(Modifier.fillMaxWidth()) {
            Text(
                event.policyNames.joinToString().ifBlank { "Активная DNS-политика" },
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                event.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                event.timestamp.formatPacketTime(text),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (events.size > 8) {
        Text(
            "Ещё событий: ${events.size - 8}. Очистка сканера удаляет и этот журнал.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun expectedRuntimeTags(config: RoutingConfig, decision: RouteDecision): Set<String> {
    val group = config.profileGroups.firstOrNull { it.id == decision.targetId }
        ?: return setOf(runtimeProfileTag(decision.targetId))
    return buildSet {
        add(runtimeProfileTag(group.id))
        group.memberProfileIds.forEach { add(runtimeProfileTag(it)) }
    }
}

internal fun explainFlowRoute(
    config: RoutingConfig,
    appPackages: List<String>,
    domain: String,
    destinationIp: String,
    destinationPort: Int?,
    network: String,
): RouteDecision {
    val transport = when (network.lowercase()) {
        "tcp" -> RouteTransport.Tcp
        "udp" -> RouteTransport.Udp
        else -> RouteTransport.Any
    }
    val inputs = buildList {
        addAll(appPackages)
        domain.takeIf(String::isNotBlank)?.let(::add)
        destinationIp.takeIf(String::isNotBlank)?.let(::add)
    }.distinct()
    val engine = RouteEngine(config)
    val decisions = inputs.map { input ->
        engine.simulate(RouteQuery(input, destinationPort, transport))
    }
    return decisions
        .filter { it.matchedRule.type != RouteRuleType.DEFAULT }
        .minWithOrNull(
            compareBy<RouteDecision> { it.matchedRule.priority }
                .thenBy { it.matchedRule.name }
                .thenBy { it.matchedRule.id },
        )
        ?: engine.simulate(RouteQuery("default", destinationPort, transport))
}

private fun endpointHost(endpoint: String): String = when {
    endpoint.startsWith("[") -> endpoint.substringAfter('[').substringBefore(']')
    endpoint.count { it == ':' } == 1 -> endpoint.substringBeforeLast(':')
    else -> endpoint
}

internal fun detectIpVersion(host: String): FlowIpVersion {
    val normalized = host.trim().removePrefix("[").removeSuffix("]")
    if (':' in normalized) return FlowIpVersion.Ipv6
    val octets = normalized.split('.')
    return if (octets.size == 4 &&
        octets.all { octet -> octet.isNotEmpty() && octet.all(Char::isDigit) && octet.toIntOrNull() in 0..255 }
    ) {
        FlowIpVersion.Ipv4
    } else {
        FlowIpVersion.Unknown
    }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstalledApplicationIcon(
                    packageName = event.appPackages.firstOrNull(),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Column {
                    Text(event.appName, fontWeight = FontWeight.SemiBold)
                    event.appPackages.firstOrNull()?.let { packageName ->
                        Text(
                            packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            FlowField(text.flowSource, event.sourceLabel)
            FlowField(text.flowApp, event.appName)
            FlowField(text.flowDomain, event.domain)
            FlowField(text.flowResolvedIp, event.resolvedIp ?: text.none)
            FlowField(text.flowPortProtocol, event.portProtocol)
            FlowField("Версия IP", event.ipVersion.userLabel)
            FlowField("Состояние", event.lifecycle.userLabel)
            FlowField("Результат", if (event.isBlocked) "Заблокировано" else "Не заблокировано")
            if (event.observedAt > 0L) {
                FlowField("Начало", DateFormat.getDateTimeInstance().format(Date(event.observedAt)))
            }
            event.finishedAt?.let { finishedAt ->
                FlowField("Окончание", DateFormat.getDateTimeInstance().format(Date(finishedAt)))
                FlowField("Длительность", event.durationMillis.toReadableDuration())
                FlowField("Причина закрытия", event.closeReason ?: "Движок не сообщил")
            }
            FlowField(text.flowDnsPolicy, event.dnsPolicy)
            FlowField(text.flowSelectedRoute, event.selectedRoute)
            FlowField("Проверка маршрута", event.routeCheck)
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

private val FlowIpVersion.userLabel: String
    get() = when (this) {
        FlowIpVersion.Ipv4 -> "IPv4"
        FlowIpVersion.Ipv6 -> "IPv6"
        FlowIpVersion.Unknown -> "Не определена"
    }

private val FlowLifecycle.userLabel: String
    get() = when (this) {
        FlowLifecycle.Active -> "Активно"
        FlowLifecycle.Closed -> "Завершено"
        FlowLifecycle.Snapshot -> "Предварительный расчёт"
    }

private fun Long?.toReadableDuration(): String {
    val millis = this ?: return "не определена"
    if (millis < 1_000L) return "$millis мс"
    val totalSeconds = millis / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return buildList {
        if (hours > 0L) add("$hours ч")
        if (minutes > 0L) add("$minutes мин")
        if (seconds > 0L || isEmpty()) add("$seconds с")
    }.joinToString(" ")
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
        routeCheck = "Предварительный расчёт: это тестовая сводка TUN, а не подтверждение живого outbound.",
        lifecycle = FlowLifecycle.Snapshot,
        isBlocked = blocked,
        ipVersion = FlowIpVersion.Ipv4,
        observedAt = timestamp,
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
