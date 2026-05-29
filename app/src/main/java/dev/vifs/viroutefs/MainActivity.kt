package dev.vifs.viroutefs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AltRoute
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.BuildConfig
import dev.vifs.viroutefs.diagnostics.DiagnosticResult
import dev.vifs.viroutefs.diagnostics.DiagnosticStatus
import dev.vifs.viroutefs.diagnostics.DnsDiagnostic
import dev.vifs.viroutefs.diagnostics.HttpDiagnostic
import dev.vifs.viroutefs.diagnostics.TcpDiagnostic
import dev.vifs.viroutefs.diagnostics.TlsDiagnostic
import dev.vifs.viroutefs.routing.RouteDecision
import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ViRouteFsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ViRouteFsApp()
                }
            }
        }
    }
}

private enum class AppScreen(
    @StringRes val labelResId: Int,
    val icon: ImageVector,
) {
    Dashboard(R.string.screen_dashboard, Icons.Outlined.Home),
    Vpn(R.string.screen_vpn, Icons.Outlined.Shield),
    Routes(R.string.screen_routes, Icons.Outlined.AltRoute),
    Dns(R.string.screen_dns, Icons.Outlined.Dns),
    Tools(R.string.screen_tools, Icons.Outlined.Build),
    Logs(R.string.screen_logs, Icons.Outlined.Article),
    Settings(R.string.screen_settings, Icons.Outlined.Settings),
}

private data class InfoCardContent(
    val title: String,
    val simpleExplanation: String,
    val technicalDetails: String,
    val recommendedAction: String,
)

private data class RouteSampleData(
    val tunnelProfiles: List<TunnelProfile>,
    val routeRules: List<RouteRule>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViRouteFsApp() {
    var selectedScreen by rememberSaveable { mutableStateOf(AppScreen.Dashboard) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = stringResource(R.string.app_name))
                        Text(
                            text = stringResource(R.string.app_tagline),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.labelResId)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        when (selectedScreen) {
            AppScreen.Dashboard -> DashboardScreen(innerPadding)
            AppScreen.Vpn -> VpnScreen(innerPadding)
            AppScreen.Routes -> RoutesScreen(innerPadding)
            AppScreen.Dns -> DnsScreen(innerPadding)
            AppScreen.Tools -> ToolsScreen(innerPadding)
            AppScreen.Logs -> LogsScreen(innerPadding)
            AppScreen.Settings -> SettingsScreen(innerPadding)
        }
    }
}

@Composable
private fun DashboardScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.dashboard_header_title),
                subtitle = stringResource(R.string.dashboard_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_vpn_status_title),
                    simpleExplanation = stringResource(R.string.dashboard_vpn_status_simple),
                    technicalDetails = stringResource(R.string.dashboard_vpn_status_details),
                    recommendedAction = stringResource(R.string.dashboard_vpn_status_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_flow_title),
                    simpleExplanation = stringResource(R.string.dashboard_flow_simple),
                    technicalDetails = stringResource(R.string.dashboard_flow_details),
                    recommendedAction = stringResource(R.string.dashboard_flow_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dashboard_privacy_title),
                    simpleExplanation = stringResource(R.string.dashboard_privacy_simple),
                    technicalDetails = stringResource(R.string.dashboard_privacy_details),
                    recommendedAction = stringResource(R.string.dashboard_privacy_action),
                ),
            )
        }
    }
}

@Composable
private fun VpnScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_vpn),
                subtitle = stringResource(R.string.vpn_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.vpn_service_title),
                    simpleExplanation = stringResource(R.string.vpn_service_simple),
                    technicalDetails = stringResource(R.string.vpn_service_details),
                    recommendedAction = stringResource(R.string.vpn_service_action),
                ),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.vpn_future_rules_title),
                body = stringResource(R.string.vpn_future_rules_body),
            )
        }
    }
}

@Composable
private fun RoutesScreen(contentPadding: PaddingValues) {
    val sampleData = rememberRouteSampleData()
    val routeEngine = RouteEngine(sampleData.tunnelProfiles, sampleData.routeRules)
    val defaultInput = stringResource(R.string.routes_default_input)
    var simulatorInput by rememberSaveable { mutableStateOf(defaultInput) }
    var routeDecision by remember { mutableStateOf(routeEngine.simulate(defaultInput)) }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_routes),
                subtitle = stringResource(R.string.routes_header_subtitle),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.routes_mock_title),
                body = stringResource(R.string.routes_mock_body),
            )
        }
        item {
            Text(
                text = stringResource(R.string.routes_tunnels_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(sampleData.tunnelProfiles) { tunnelProfile ->
            TunnelProfileCard(tunnelProfile)
        }
        item {
            Text(
                text = stringResource(R.string.routes_rules_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(sampleData.routeRules) { routeRule ->
            RouteRuleCard(routeRule, sampleData.tunnelProfiles)
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.routes_simulator_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = simulatorInput,
                        onValueChange = { simulatorInput = it },
                        label = { Text(stringResource(R.string.routes_simulator_input_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            routeDecision = routeEngine.simulate(simulatorInput)
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.routes_simulator_button))
                    }
                }
            }
        }
        item {
            RouteDecisionCard(routeDecision)
        }
    }
}

@Composable
private fun rememberRouteSampleData(): RouteSampleData {
    val tunnelProfiles = listOf(
        TunnelProfile(
            id = "direct",
            name = stringResource(R.string.routes_tunnel_direct_name),
            type = TunnelType.Direct,
            description = stringResource(R.string.routes_tunnel_direct_description),
        ),
        TunnelProfile(
            id = "block",
            name = stringResource(R.string.routes_tunnel_block_name),
            type = TunnelType.Block,
            description = stringResource(R.string.routes_tunnel_block_description),
        ),
        TunnelProfile(
            id = "xray_de",
            name = stringResource(R.string.routes_tunnel_xray_germany_name),
            type = TunnelType.Xray,
            description = stringResource(R.string.routes_tunnel_xray_germany_description),
        ),
        TunnelProfile(
            id = "hysteria2_nl",
            name = stringResource(R.string.routes_tunnel_hysteria2_nl_name),
            type = TunnelType.Hysteria2,
            description = stringResource(R.string.routes_tunnel_hysteria2_nl_description),
        ),
        TunnelProfile(
            id = "openvpn_work",
            name = stringResource(R.string.routes_tunnel_openvpn_work_name),
            type = TunnelType.OpenVpn,
            description = stringResource(R.string.routes_tunnel_openvpn_work_description),
        ),
    )
    val routeRules = listOf(
        RouteRule(
            id = "banking_direct",
            name = stringResource(R.string.routes_rule_banking_name),
            type = RouteRuleType.APP_GROUP,
            targetTunnelId = "direct",
            priority = 10,
            matchers = listOf("sber", "tinkoff", "bank"),
            reason = stringResource(R.string.routes_rule_banking_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.APP_GROUP.name, 10, "sber, tinkoff, bank"),
            recommendedAction = stringResource(R.string.routes_rule_banking_action),
        ),
        RouteRule(
            id = "telegram_xray",
            name = stringResource(R.string.routes_rule_telegram_name),
            type = RouteRuleType.APP_GROUP,
            targetTunnelId = "xray_de",
            priority = 20,
            matchers = listOf("telegram", "tg"),
            reason = stringResource(R.string.routes_rule_telegram_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.APP_GROUP.name, 20, "telegram, tg"),
            recommendedAction = stringResource(R.string.routes_rule_telegram_action),
        ),
        RouteRule(
            id = "youtube_hysteria2",
            name = stringResource(R.string.routes_rule_youtube_name),
            type = RouteRuleType.DOMAIN,
            targetTunnelId = "hysteria2_nl",
            priority = 30,
            matchers = listOf("youtube", "youtu.be", "googlevideo"),
            reason = stringResource(R.string.routes_rule_youtube_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.DOMAIN.name, 30, "youtube, youtu.be, googlevideo"),
            recommendedAction = stringResource(R.string.routes_rule_youtube_action),
        ),
        RouteRule(
            id = "work_10",
            name = stringResource(R.string.routes_rule_work_10_name),
            type = RouteRuleType.CIDR,
            targetTunnelId = "openvpn_work",
            priority = 40,
            matchers = listOf("10.0.0.0/8"),
            reason = stringResource(R.string.routes_rule_work_10_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.CIDR.name, 40, "10.0.0.0/8"),
            recommendedAction = stringResource(R.string.routes_rule_work_action),
        ),
        RouteRule(
            id = "work_172",
            name = stringResource(R.string.routes_rule_work_172_name),
            type = RouteRuleType.CIDR,
            targetTunnelId = "openvpn_work",
            priority = 50,
            matchers = listOf("172.16.1.0/22"),
            reason = stringResource(R.string.routes_rule_work_172_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.CIDR.name, 50, "172.16.1.0/22"),
            recommendedAction = stringResource(R.string.routes_rule_work_action),
        ),
        RouteRule(
            id = "blocked_domain",
            name = stringResource(R.string.routes_rule_blocked_name),
            type = RouteRuleType.DOMAIN,
            targetTunnelId = "block",
            priority = 60,
            matchers = listOf("blocked.example", "suspicious.example", "malware.test"),
            reason = stringResource(R.string.routes_rule_blocked_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.DOMAIN.name, 60, "blocked.example, suspicious.example, malware.test"),
            recommendedAction = stringResource(R.string.routes_rule_blocked_action),
        ),
        RouteRule(
            id = "default_direct",
            name = stringResource(R.string.routes_rule_default_name),
            type = RouteRuleType.DEFAULT,
            targetTunnelId = "direct",
            priority = 1000,
            matchers = listOf("*"),
            reason = stringResource(R.string.routes_rule_default_reason),
            technicalDetails = stringResource(R.string.routes_rule_technical, RouteRuleType.DEFAULT.name, 1000, "*"),
            recommendedAction = stringResource(R.string.routes_rule_default_action),
        ),
    )

    return RouteSampleData(tunnelProfiles, routeRules)
}

@Composable
private fun TunnelProfileCard(tunnelProfile: TunnelProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AltRoute, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tunnelProfile.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            AssistChip(onClick = {}, label = { Text(tunnelProfile.type.name) })
            Text(text = tunnelProfile.description)
        }
    }
}

@Composable
private fun RouteRuleCard(
    routeRule: RouteRule,
    tunnelProfiles: List<TunnelProfile>,
) {
    val targetTunnelName = tunnelProfiles.first { it.id == routeRule.targetTunnelId }.name

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = routeRule.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = stringResource(R.string.routes_rule_target, targetTunnelName))
            Text(text = stringResource(R.string.routes_rule_matchers, routeRule.matchers.joinToString()))
            Text(text = stringResource(R.string.routes_rule_priority, routeRule.type.name, routeRule.priority))
        }
    }
}

@Composable
private fun RouteDecisionCard(routeDecision: RouteDecision) {
    InfoCard(
        InfoCardContent(
            title = stringResource(R.string.routes_result_title),
            simpleExplanation = stringResource(
                R.string.routes_result_simple,
                routeDecision.input,
                routeDecision.tunnelProfile.name,
            ),
            technicalDetails = stringResource(
                R.string.routes_result_details,
                routeDecision.matchedRule.name,
                routeDecision.technicalDetails,
            ),
            recommendedAction = routeDecision.recommendedAction,
        ),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LabeledText(
                label = stringResource(R.string.routes_result_matched_rule),
                body = routeDecision.matchedRule.name,
            )
            LabeledText(
                label = stringResource(R.string.routes_result_reason),
                body = routeDecision.plainReason,
            )
        }
    }
}

@Composable
private fun DnsScreen(contentPadding: PaddingValues) {
    val defaultDomain = stringResource(R.string.dns_default_domain)
    val defaultDnsServer = stringResource(R.string.dns_default_server)
    val defaultRecordType = stringResource(R.string.dns_default_record_type)
    val scope = rememberCoroutineScope()
    val diagnostic = remember { DnsDiagnostic() }

    var domain by rememberSaveable { mutableStateOf(defaultDomain) }
    var dnsServer by rememberSaveable { mutableStateOf(defaultDnsServer) }
    var recordType by rememberSaveable { mutableStateOf(defaultRecordType) }
    var isRunning by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<DiagnosticResult?>(null) }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.dns_header_title),
                subtitle = stringResource(R.string.dns_header_subtitle),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.dns_system_resolver_title),
                body = stringResource(R.string.dns_system_resolver_body),
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = domain,
                        onValueChange = { domain = it },
                        label = { Text(stringResource(R.string.dns_field_domain)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { dnsServer = it },
                        label = { Text(stringResource(R.string.dns_field_server)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = recordType,
                        onValueChange = { recordType = it.uppercase() },
                        label = { Text(stringResource(R.string.dns_field_record_type)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            isRunning = true
                            scope.launch {
                                result = diagnostic.lookup(domain, dnsServer, recordType)
                                isRunning = false
                            }
                        },
                        enabled = !isRunning,
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(if (isRunning) stringResource(R.string.action_checking) else stringResource(R.string.action_check))
                    }
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.dns_result_title),
                result = result,
                notRunText = stringResource(R.string.dns_result_not_run),
            )
        }
    }
}

@Composable
private fun ToolsScreen(contentPadding: PaddingValues) {
    val scope = rememberCoroutineScope()
    val tcpDiagnostic = remember { TcpDiagnostic() }
    val tlsDiagnostic = remember { TlsDiagnostic() }
    val httpDiagnostic = remember { HttpDiagnostic() }

    var tcpHost by rememberSaveable { mutableStateOf("example.com") }
    var tcpPort by rememberSaveable { mutableStateOf("443") }
    var tcpTimeout by rememberSaveable { mutableStateOf("5") }
    var tcpRunning by rememberSaveable { mutableStateOf(false) }
    var tcpResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    var tlsHost by rememberSaveable { mutableStateOf("example.com") }
    var tlsPort by rememberSaveable { mutableStateOf("443") }
    var tlsSni by rememberSaveable { mutableStateOf("example.com") }
    var tlsRunning by rememberSaveable { mutableStateOf(false) }
    var tlsResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    var httpUrl by rememberSaveable { mutableStateOf("https://example.com") }
    var httpRunning by rememberSaveable { mutableStateOf(false) }
    var httpResult by remember { mutableStateOf<DiagnosticResult?>(null) }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_tools),
                subtitle = stringResource(R.string.tools_header_subtitle),
            )
        }
        item {
            PlaceholderCard(
                title = stringResource(R.string.tools_safety_title),
                body = stringResource(R.string.tools_safety_body),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_tcp_title)) {
                OutlinedTextField(
                    value = tcpHost,
                    onValueChange = { tcpHost = it },
                    label = { Text(stringResource(R.string.tools_field_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = tcpPort,
                        onValueChange = { tcpPort = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.tools_field_port)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = tcpTimeout,
                        onValueChange = { tcpTimeout = it.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.tools_field_timeout)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(
                    onClick = {
                        tcpRunning = true
                        scope.launch {
                            tcpResult = tcpDiagnostic.check(tcpHost, tcpPort, tcpTimeout)
                            tcpRunning = false
                        }
                    },
                    enabled = !tcpRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (tcpRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_tcp_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_tcp_result_title),
                result = tcpResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_tls_title)) {
                OutlinedTextField(
                    value = tlsHost,
                    onValueChange = { tlsHost = it },
                    label = { Text(stringResource(R.string.tools_field_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tlsPort,
                    onValueChange = { tlsPort = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.tools_field_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = tlsSni,
                    onValueChange = { tlsSni = it },
                    label = { Text(stringResource(R.string.tools_field_sni)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        tlsRunning = true
                        scope.launch {
                            tlsResult = tlsDiagnostic.check(tlsHost, tlsPort, tlsSni)
                            tlsRunning = false
                        }
                    },
                    enabled = !tlsRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (tlsRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_tls_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_tls_result_title),
                result = tlsResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
        item {
            DiagnosticInputCard(title = stringResource(R.string.tools_http_title)) {
                OutlinedTextField(
                    value = httpUrl,
                    onValueChange = { httpUrl = it },
                    label = { Text(stringResource(R.string.tools_field_url)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        httpRunning = true
                        scope.launch {
                            httpResult = httpDiagnostic.check(httpUrl)
                            httpRunning = false
                        }
                    },
                    enabled = !httpRunning,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(if (httpRunning) stringResource(R.string.action_checking) else stringResource(R.string.tools_http_button))
                }
            }
        }
        item {
            DiagnosticResultCard(
                title = stringResource(R.string.tools_http_result_title),
                result = httpResult,
                notRunText = stringResource(R.string.tools_result_not_run),
            )
        }
    }
}

@Composable
private fun LogsScreen(contentPadding: PaddingValues) {
    val events = listOf(
        stringResource(R.string.logs_event_telegram),
        stringResource(R.string.logs_event_browser_dns),
        stringResource(R.string.logs_event_banking),
        stringResource(R.string.logs_event_mtu),
        stringResource(R.string.logs_event_wps),
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_logs),
                subtitle = stringResource(R.string.logs_header_subtitle),
            )
        }
        items(events) { event ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = event,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.logs_event_details_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(contentPadding: PaddingValues) {
    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_settings),
                subtitle = stringResource(R.string.settings_header_subtitle),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_local_first_title),
                    simpleExplanation = stringResource(R.string.settings_local_first_simple),
                    technicalDetails = stringResource(R.string.settings_local_first_details),
                    recommendedAction = stringResource(R.string.settings_local_first_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_safety_title),
                    simpleExplanation = stringResource(R.string.settings_safety_simple),
                    technicalDetails = stringResource(R.string.settings_safety_details),
                    recommendedAction = stringResource(R.string.settings_safety_action),
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.settings_version_title),
                    simpleExplanation = stringResource(R.string.settings_version_simple, BuildConfig.VERSION_NAME),
                    technicalDetails = stringResource(R.string.settings_version_details, BuildConfig.VERSION_CODE),
                    recommendedAction = stringResource(R.string.settings_version_action),
                ),
            )
        }
    }
}


@Composable
private fun DiagnosticInputCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun DiagnosticResultCard(
    title: String,
    result: DiagnosticResult?,
    notRunText: String,
) {
    val content = if (result == null) {
        InfoCardContent(
            title = title,
            simpleExplanation = notRunText,
            technicalDetails = stringResource(R.string.diagnostic_not_run_details),
            recommendedAction = stringResource(R.string.diagnostic_not_run_action),
        )
    } else {
        InfoCardContent(
            title = title,
            simpleExplanation = result.simpleExplanation,
            technicalDetails = result.technicalDetailsWithElapsed(),
            recommendedAction = result.recommendedAction,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        result?.let { diagnosticResult ->
            AssistChip(
                onClick = {},
                label = { Text(diagnosticResult.status.toRussianLabel()) },
            )
        }
        InfoCard(content = content)
    }
}

private fun DiagnosticStatus.toRussianLabel(): String = when (this) {
    DiagnosticStatus.SUCCESS -> "Успех"
    DiagnosticStatus.WARNING -> "Предупреждение"
    DiagnosticStatus.ERROR -> "Ошибка"
}

@Composable
private fun ScreenList(
    contentPadding: PaddingValues,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoCard(content: InfoCardContent) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Security, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = content.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            LabeledText(
                label = stringResource(R.string.card_label_simple_explanation),
                body = content.simpleExplanation,
            )
            LabeledText(
                label = stringResource(R.string.card_label_technical_details),
                body = content.technicalDetails,
            )
            LabeledText(
                label = stringResource(R.string.card_label_recommended_action),
                body = content.recommendedAction,
            )
        }
    }
}

@Composable
private fun PlaceholderCard(
    title: String,
    body: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = {}, label = { Text(stringResource(R.string.label_placeholder)) })
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(text = body)
        }
    }
}

@Composable
private fun LabeledText(
    label: String,
    body: String,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
