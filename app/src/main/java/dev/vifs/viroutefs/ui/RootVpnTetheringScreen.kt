// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.StatusChip
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.root.RootNetworkInterface
import dev.vifs.viroutefs.root.RootVpnTetheringController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun RootVpnTetheringScreen(
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(context) { RootVpnTetheringController(context.applicationContext) }
    var running by remember(controller) { mutableStateOf(controller.isRunning()) }
    var interfaces by remember { mutableStateOf<List<RootNetworkInterface>>(emptyList()) }
    var downstreamName by rememberSaveable { mutableStateOf<String?>(null) }
    var tunnelName by rememberSaveable { mutableStateOf<String?>(null) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    val downstreamCandidates = interfaces.filter(RootNetworkInterface::isLikelyDownstream)
    val tunnelCandidates = interfaces.filter(RootNetworkInterface::isTunnel)
    val downstream = downstreamCandidates.firstOrNull { it.name == downstreamName }
    val tunnel = tunnelCandidates.firstOrNull { it.name == tunnelName }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header(
                    "Раздача текущего VPN",
                    "Маршрутизация IPv4-клиентов hotspot, USB или Bluetooth через активный ViRouteFS",
                )
                OutlinedButton(onClick = onBack) { Text("← Назад в root-центр") }
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Состояние", fontWeight = FontWeight.SemiBold)
                    StatusChip(if (running) "Раздача включена" else "Выключена")
                }
                WarningText(
                    "Сначала вручную включите точку доступа Android и обычный VPN ViRouteFS. Этот модуль не меняет пароль hotspot, DHCP и настройки мобильной сети.",
                )
                Text(
                    "Клиенты используют маршрут по умолчанию внутри текущей VPN-конфигурации. Правила «по приложению» к приложениям на других устройствах неприменимы. При падении VPN прямой IPv4 и весь IPv6 клиентов блокируются.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            CardBlock {
                Button(
                    enabled = !busy && !running,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.discoverInterfaces() }
                            interfaces = result.interfaces
                            downstreamName = result.interfaces.firstOrNull { it.isLikelyDownstream }?.name
                            tunnelName = result.interfaces.firstOrNull { it.isTunnel }?.name
                            message = result.message
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Читаем интерфейсы…" else "Найти hotspot и VPN-интерфейс")
                }
                Text(
                    "ViRouteFS показывает только реально найденные приватные IPv4-интерфейсы и не принимает имя интерфейса вручную.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (downstreamCandidates.isNotEmpty()) {
            item {
                InterfaceChooser(
                    title = "Интерфейс клиентов",
                    candidates = downstreamCandidates,
                    selectedName = downstreamName,
                    enabled = !busy && !running,
                    onSelected = { downstreamName = it },
                )
            }
        }
        if (tunnelCandidates.isNotEmpty()) {
            item {
                InterfaceChooser(
                    title = "VPN-интерфейс",
                    candidates = tunnelCandidates,
                    selectedName = tunnelName,
                    enabled = !busy && !running,
                    onSelected = { tunnelName = it },
                )
            }
        }
        item {
            CardBlock {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        enabled = !busy && !running,
                    )
                    Text(
                        "Понимаю, что это экспериментальная IPv4-раздача и подключённые устройства временно потеряют сеть при остановке VPN",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Button(
                    enabled = acknowledged && downstream != null && tunnel != null && !busy && !running,
                    onClick = {
                        val chosenDownstream = downstream ?: return@Button
                        val chosenTunnel = tunnel ?: return@Button
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                controller.start(chosenDownstream, chosenTunnel)
                            }
                            message = result.message
                            running = controller.isRunning()
                            busy = false
                            if (result.successful) acknowledged = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (busy) "Применяем маршрут с откатом…" else "Раздавать текущий VPN")
                }
                OutlinedButton(
                    enabled = running && !busy,
                    onClick = {
                        busy = true
                        message = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) { controller.stop() }
                            message = result.message
                            running = controller.isRunning()
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Остановить раздачу и восстановить сеть") }
                Text(
                    "Правила не устанавливаются в загрузочный root-модуль и исчезнут после перезагрузки. Перед удалением ViRouteFS остановите раздачу.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InterfaceChooser(
    title: String,
    candidates: List<RootNetworkInterface>,
    selectedName: String?,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    CardBlock {
        Text(title, fontWeight = FontWeight.SemiBold)
        candidates.forEach { candidate ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(
                    selected = candidate.name == selectedName,
                    onClick = { onSelected(candidate.name) },
                    enabled = enabled,
                )
                Column(Modifier.weight(1f)) {
                    Text(candidate.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${candidate.ipv4Cidr} • сеть ${candidate.networkCidr}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
