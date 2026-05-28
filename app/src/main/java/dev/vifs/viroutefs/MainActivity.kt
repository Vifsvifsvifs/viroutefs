package dev.vifs.viroutefs

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.ui.theme.ViRouteFsTheme

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
private fun DnsScreen(contentPadding: PaddingValues) {
    val defaultDomain = stringResource(R.string.dns_default_domain)
    val defaultDnsServer = stringResource(R.string.dns_default_server)
    val defaultRecordType = stringResource(R.string.dns_default_record_type)
    val initialResult = stringResource(R.string.dns_result_not_run)

    var domain by rememberSaveable { mutableStateOf(defaultDomain) }
    var dnsServer by rememberSaveable { mutableStateOf(defaultDnsServer) }
    var recordType by rememberSaveable { mutableStateOf(defaultRecordType) }
    var result by rememberSaveable { mutableStateOf(initialResult) }
    val preparedResult = stringResource(
        R.string.dns_result_prepared,
        recordType,
        domain,
        dnsServer,
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.dns_header_title),
                subtitle = stringResource(R.string.dns_header_subtitle),
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
                            result = preparedResult
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(stringResource(R.string.action_check))
                    }
                }
            }
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = stringResource(R.string.dns_result_title),
                    simpleExplanation = result,
                    technicalDetails = stringResource(R.string.dns_result_details),
                    recommendedAction = stringResource(R.string.dns_result_action),
                ),
            )
        }
    }
}

@Composable
private fun ToolsScreen(contentPadding: PaddingValues) {
    val tools = listOf(
        InfoCardContent(
            title = stringResource(R.string.tools_tcp_title),
            simpleExplanation = stringResource(R.string.tools_tcp_simple),
            technicalDetails = stringResource(R.string.tools_tcp_details),
            recommendedAction = stringResource(R.string.tools_tcp_action),
        ),
        InfoCardContent(
            title = stringResource(R.string.tools_tls_title),
            simpleExplanation = stringResource(R.string.tools_tls_simple),
            technicalDetails = stringResource(R.string.tools_tls_details),
            recommendedAction = stringResource(R.string.tools_tls_action),
        ),
        InfoCardContent(
            title = stringResource(R.string.tools_http_title),
            simpleExplanation = stringResource(R.string.tools_http_simple),
            technicalDetails = stringResource(R.string.tools_http_details),
            recommendedAction = stringResource(R.string.tools_http_action),
        ),
        InfoCardContent(
            title = stringResource(R.string.tools_mtu_title),
            simpleExplanation = stringResource(R.string.tools_mtu_simple),
            technicalDetails = stringResource(R.string.tools_mtu_details),
            recommendedAction = stringResource(R.string.tools_mtu_action),
        ),
        InfoCardContent(
            title = stringResource(R.string.tools_lan_title),
            simpleExplanation = stringResource(R.string.tools_lan_simple),
            technicalDetails = stringResource(R.string.tools_lan_details),
            recommendedAction = stringResource(R.string.tools_lan_action),
        ),
        InfoCardContent(
            title = stringResource(R.string.tools_audit_title),
            simpleExplanation = stringResource(R.string.tools_audit_simple),
            technicalDetails = stringResource(R.string.tools_audit_details),
            recommendedAction = stringResource(R.string.tools_audit_action),
        ),
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = stringResource(R.string.screen_tools),
                subtitle = stringResource(R.string.tools_header_subtitle),
            )
        }
        items(tools) { tool ->
            InfoCard(content = tool)
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
    }
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
