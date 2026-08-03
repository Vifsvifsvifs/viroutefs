// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.vifs.viroutefs.CardBlock
import dev.vifs.viroutefs.Header
import dev.vifs.viroutefs.ScreenList
import dev.vifs.viroutefs.WarningText
import dev.vifs.viroutefs.routing.ImportDuplicateResolution
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.VPN_GATE_VOLUNTEER_WARNING
import dev.vifs.viroutefs.routing.VpnGateCatalogClient
import dev.vifs.viroutefs.routing.VpnGateCatalogSnapshot
import dev.vifs.viroutefs.routing.VpnGateServer
import dev.vifs.viroutefs.routing.applyProfileImport
import dev.vifs.viroutefs.routing.previewVpnGateProfile
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VpnGateScreen(
    padding: PaddingValues,
    config: RoutingConfig,
    onBack: () -> Unit,
    onConfig: (RoutingConfig, String?) -> Unit,
) {
    val context = LocalContext.current
    val client = remember(context) { VpnGateCatalogClient(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<VpnGateCatalogSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var importKey by remember { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var country by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(VPN_GATE_SORT_PING) }

    LaunchedEffect(client) {
        snapshot = withContext(Dispatchers.IO) { client.loadCached() }
    }

    fun refresh() {
        if (loading) return
        loading = true
        message = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.fetch() } }
                .onSuccess { loaded ->
                    snapshot = loaded
                    message = "Каталог VPNGate обновлён: ${loaded.servers.size} серверов. Ничего не подключено автоматически."
                }
                .onFailure { error ->
                    message = if (snapshot != null) {
                        "Не удалось обновить VPNGate: ${error.localizedMessage ?: "ошибка сети"}. Оставлен локальный кэш."
                    } else {
                        "Не удалось загрузить VPNGate: ${error.localizedMessage ?: "ошибка сети"}."
                    }
                }
            loading = false
        }
    }

    val countries = remember(snapshot) {
        snapshot?.servers.orEmpty()
            .groupingBy { it.countryCode.ifBlank { it.countryName } }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map(Map.Entry<String, Int>::key)
            .take(12)
    }
    val visibleServers = remember(snapshot, query, country, sort) {
        val normalizedQuery = query.trim()
        snapshot?.servers.orEmpty()
            .asSequence()
            .filter { server ->
                country.isBlank() || server.countryCode == country || server.countryName == country
            }
            .filter { server ->
                normalizedQuery.isBlank() || listOf(
                    server.countryName,
                    server.countryCode,
                    server.hostName,
                    server.ipAddress,
                    server.operator,
                ).any { it.contains(normalizedQuery, ignoreCase = true) }
            }
            .sortedWith(
                if (sort == VPN_GATE_SORT_SPEED) {
                    compareByDescending<VpnGateServer> { it.speedBitsPerSecond }
                        .thenBy { it.pingMillis ?: Int.MAX_VALUE }
                } else {
                    compareBy<VpnGateServer> { it.pingMillis ?: Int.MAX_VALUE }
                        .thenByDescending { it.score }
                },
            )
            .take(MAX_VISIBLE_VPN_GATE_SERVERS)
            .toList()
    }

    ScreenList(padding) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Header("Бесплатные VPNGate", "Добровольческие OpenVPN-серверы, добавляемые только по вашему выбору")
                OutlinedButton(onClick = onBack) { Text("← Назад к VPN") }
            }
        }
        item {
            CardBlock {
                Text("Перед загрузкой", fontWeight = FontWeight.SemiBold)
                WarningText(VPN_GATE_VOLUNTEER_WARNING)
                Text(
                    "Список загружается напрямую с официального API VPNGate только после нажатия кнопки. ViRouteFS не проверяет владельца каждого сервера, не обещает его доступность и не отправляет каталог в свой облачный сервис.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Добавленный профиль остаётся выключенным. Вы сами включаете его и назначаете приложения, сети или основной маршрут.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = ::refresh,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            loading -> "Загружаем…"
                            snapshot == null -> "Понимаю риск — загрузить список"
                            else -> "Обновить список вручную"
                        },
                    )
                }
                snapshot?.let { loaded ->
                    val date = remember(loaded.fetchedAtEpochMillis) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(loaded.fetchedAtEpochMillis))
                    }
                    Text(
                        "${if (loaded.fromCache) "Локальный кэш" else "Загружено"}: $date • ${loaded.servers.size} серверов",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (snapshot != null) {
            item {
                CardBlock {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Страна, сервер или IP") },
                        singleLine = true,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = country.isBlank(),
                            onClick = { country = "" },
                            label = { Text("Все страны") },
                        )
                        countries.forEach { code ->
                            FilterChip(
                                selected = country == code,
                                onClick = { country = code },
                                label = { Text(code) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sort == VPN_GATE_SORT_PING,
                            onClick = { sort = VPN_GATE_SORT_PING },
                            label = { Text("По задержке") },
                        )
                        FilterChip(
                            selected = sort == VPN_GATE_SORT_SPEED,
                            onClick = { sort = VPN_GATE_SORT_SPEED },
                            label = { Text("По скорости") },
                        )
                    }
                    Text(
                        "Показано ${visibleServers.size} из ${snapshot?.servers?.size ?: 0}. Для точного поиска введите страну или адрес.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(visibleServers, key = VpnGateServer::stableKey) { server ->
                VpnGateServerCard(
                    server = server,
                    busy = importKey != null,
                    onAdd = {
                        importKey = server.stableKey
                        message = null
                        scope.launch {
                            runCatching {
                                withContext(Dispatchers.Default) { previewVpnGateProfile(server) }
                            }.onSuccess { preview ->
                                val result = applyProfileImport(config, preview, ImportDuplicateResolution.Skip)
                                if (result.added > 0) {
                                    onConfig(
                                        result.config,
                                        "Профиль «${preview.candidates.single().profile.name}» добавлен выключенным. Проверьте его и назначьте нужный маршрут.",
                                    )
                                    message = "OpenVPN-профиль добавлен выключенным. Автоматического подключения не было."
                                } else {
                                    message = "Этот VPNGate-профиль уже добавлен; дубликат не создан."
                                }
                            }.onFailure { error ->
                                message = "Профиль не добавлен: ${error.localizedMessage ?: "повреждённая конфигурация"}."
                            }
                            importKey = null
                        }
                    },
                )
            }
            if (visibleServers.isEmpty()) {
                item {
                    CardBlock {
                        Text("По этому фильтру серверов нет.")
                        Text("Сбросьте страну или измените поиск.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun VpnGateServerCard(
    server: VpnGateServer,
    busy: Boolean,
    onAdd: () -> Unit,
) {
    CardBlock {
        Text(
            "${server.countryCode.ifBlank { "—" }} • ${server.countryName.ifBlank { "Страна не указана" }}",
            fontWeight = FontWeight.SemiBold,
        )
        Text("${server.hostName} • ${server.ipAddress}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Задержка: ${server.pingMillis?.let { "$it мс" } ?: "нет данных"} • " +
                "скорость: ${formatVpnGateSpeed(server.speedBitsPerSecond)} • сессий: ${server.activeSessions}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Время работы: ${server.uptimeMillis / 86_400_000L} дн. • журнал: ${server.logType.ifBlank { "не указан" }}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (server.operator.isNotBlank()) {
            Text("Оператор: ${server.operator}", style = MaterialTheme.typography.labelSmall)
        }
        Button(
            onClick = onAdd,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (busy) "Подождите…" else "Добавить OpenVPN-профиль")
        }
    }
}

private fun formatVpnGateSpeed(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000_000L -> "%.1f Гбит/с".format(bitsPerSecond / 1_000_000_000.0)
    bitsPerSecond >= 1_000_000L -> "%.1f Мбит/с".format(bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000L -> "%.0f Кбит/с".format(bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond бит/с"
}

private const val VPN_GATE_SORT_PING = "ping"
private const val VPN_GATE_SORT_SPEED = "speed"
private const val MAX_VISIBLE_VPN_GATE_SERVERS = 100
