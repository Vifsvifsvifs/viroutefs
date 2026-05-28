package dev.vifs.viroutefs

import android.os.Bundle
import androidx.activity.ComponentActivity
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
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("Dashboard", Icons.Outlined.Home),
    Vpn("VPN", Icons.Outlined.Shield),
    Dns("DNS", Icons.Outlined.Dns),
    Tools("Tools", Icons.Outlined.Build),
    Logs("Logs", Icons.Outlined.Article),
    Settings("Settings", Icons.Outlined.Settings),
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
                        Text(text = "ViRouteFS")
                        Text(
                            text = "Visual Route & Flow Scanner",
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
                        label = { Text(screen.label) },
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
                title = "Local-first network overview",
                subtitle = "A safe starting point for VPN routing, flow visibility, and diagnostics.",
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "VPN router status",
                    simpleExplanation = "The VPN router is not running yet.",
                    technicalDetails = "A placeholder Android VpnService is declared, but no tunnel engines or routing rules are active.",
                    recommendedAction = "Use this screen later to start routing after explicit user approval.",
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "Flow scanner preview",
                    simpleExplanation = "Human-readable flow events will appear in Logs.",
                    technicalDetails = "Future events can summarize DNS, TCP, TLS, HTTP, UDP, route, and MTU observations.",
                    recommendedAction = "Open Logs to view sample events for the first milestone.",
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "Privacy boundary",
                    simpleExplanation = "No analytics, telemetry, ads, or tracking SDKs are included.",
                    technicalDetails = "Logs and future PCAP files should remain local unless the user explicitly exports them.",
                    recommendedAction = "Review Settings before enabling future capture or export features.",
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
                title = "VPN",
                subtitle = "Placeholder controls for a future single-VpnService router.",
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "ViRouteFsVpnService",
                    simpleExplanation = "The app can request Android VPN permission in a later milestone.",
                    technicalDetails = "The service is declared with android.permission.BIND_VPN_SERVICE and currently performs no routing.",
                    recommendedAction = "Do not rely on this build for traffic tunneling yet.",
                ),
            )
        }
        item {
            PlaceholderCard(
                title = "Future route rules",
                body = "Per-app, CIDR, domain, and DNS-based routing will be designed here after the skeleton is stable.",
            )
        }
    }
}

@Composable
private fun DnsScreen(contentPadding: PaddingValues) {
    var domain by rememberSaveable { mutableStateOf("example.com") }
    var dnsServer by rememberSaveable { mutableStateOf("1.1.1.1") }
    var recordType by rememberSaveable { mutableStateOf("A") }
    var result by rememberSaveable {
        mutableStateOf("No lookup has been run. This is a UI placeholder and does not send DNS traffic yet.")
    }

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = "DNS checker",
                subtitle = "Prepare safe DNS diagnostics without making network requests yet.",
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
                        label = { Text("Domain") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = dnsServer,
                        onValueChange = { dnsServer = it },
                        label = { Text("DNS server") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = recordType,
                        onValueChange = { recordType = it.uppercase() },
                        label = { Text("Record type") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            result = "Prepared $recordType lookup for $domain using $dnsServer. Real DNS checks are not implemented yet."
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Check")
                    }
                }
            }
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "Result",
                    simpleExplanation = result,
                    technicalDetails = "This placeholder only echoes the requested lookup fields and avoids network traffic.",
                    recommendedAction = "In a later milestone, compare resolver answers and explain likely DNS issues.",
                ),
            )
        }
    }
}

@Composable
private fun ToolsScreen(contentPadding: PaddingValues) {
    val tools = listOf(
        InfoCardContent(
            title = "TCP check",
            simpleExplanation = "Test whether a host and port can accept TCP connections.",
            technicalDetails = "Future checks can record connect latency, timeout, and route selection.",
            recommendedAction = "Use only for systems you own or are authorized to test.",
        ),
        InfoCardContent(
            title = "TLS/SNI check",
            simpleExplanation = "Verify that a TLS service presents an expected certificate.",
            technicalDetails = "Future checks can show SNI, certificate issuer, expiry, and handshake errors.",
            recommendedAction = "Use this to debug your own domains and privacy-safe connectivity issues.",
        ),
        InfoCardContent(
            title = "HTTP check",
            simpleExplanation = "Inspect basic HTTP availability and response status.",
            technicalDetails = "Future checks can show status code, redirects, timing, and selected route.",
            recommendedAction = "Avoid sending secrets in diagnostic URLs.",
        ),
        InfoCardContent(
            title = "MTU test",
            simpleExplanation = "Find packet size problems that can break VPN connections.",
            technicalDetails = "Future tests can estimate MTU/MSS and explain fragmentation symptoms.",
            recommendedAction = "Run this when websites stall or VPN traffic feels unreliable.",
        ),
        InfoCardContent(
            title = "LAN scanner",
            simpleExplanation = "Discover devices on your local network with safe checks.",
            technicalDetails = "Future discovery should be transparent, rate-limited, and limited to local networks.",
            recommendedAction = "Scan only networks where you have permission.",
        ),
        InfoCardContent(
            title = "Security audit",
            simpleExplanation = "Review defensive network posture without attack automation.",
            technicalDetails = "Allowed checks include Wi-Fi encryption, WPS detection, DNS audit, and exposed local services.",
            recommendedAction = "Treat findings as risks or misconfigurations, not proof of compromise.",
        ),
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = "Tools",
                subtitle = "Safe diagnostic placeholders for the first milestone.",
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
        "Telegram connected to 149.154.167.91:443. Rule: Telegram → Xray Germany. Status: sample success. Latency: 84 ms.",
        "Browser requested example.com A record. DNS server: 1.1.1.1. Status: sample answer from cache.",
        "Banking app route selected Direct. Reason: trusted app rule. Status: sample policy decision.",
        "MTU probe for VPN profile suggested 1380 bytes. Status: sample advisory, not measured.",
        "Security audit found WPS enabled on a saved Wi-Fi profile. Meaning: detected risk, not confirmed compromise.",
    )

    ScreenList(contentPadding = contentPadding) {
        item {
            SectionHeader(
                title = "Logs",
                subtitle = "Human-readable sample flow events. Nothing is captured in this skeleton.",
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
                        text = "Technical details will be expandable in a later milestone.",
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
                title = "Settings",
                subtitle = "Privacy and safety defaults for future features.",
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "Local-first by default",
                    simpleExplanation = "Diagnostics, logs, and future captures should stay on this device.",
                    technicalDetails = "This skeleton has no cloud upload paths, analytics SDKs, telemetry SDKs, ad SDKs, or tracking SDKs.",
                    recommendedAction = "Only export logs or PCAP files after a clear user action.",
                ),
            )
        }
        item {
            InfoCard(
                InfoCardContent(
                    title = "Safety boundary",
                    simpleExplanation = "ViRouteFS is for defensive diagnostics and VPN routing.",
                    technicalDetails = "Offensive features such as cracking, deauth, exploit automation, and brute force are out of scope.",
                    recommendedAction = "Keep future tools transparent, permission-based, and rate-limited.",
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
            LabeledText(label = "Simple explanation", body = content.simpleExplanation)
            LabeledText(label = "Technical details", body = content.technicalDetails)
            LabeledText(label = "Recommended action", body = content.recommendedAction)
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
            AssistChip(onClick = {}, label = { Text("Placeholder") })
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
