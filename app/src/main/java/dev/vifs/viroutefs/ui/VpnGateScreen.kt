// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.ui

import android.content.Context
import android.telephony.TelephonyManager
import androidx.activity.compose.BackHandler
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
import dev.vifs.viroutefs.routing.VPN_GATE_AUTOMATIC_MEMBER_LIMIT
import dev.vifs.viroutefs.routing.VpnGateCatalogClient
import dev.vifs.viroutefs.routing.VpnGateCatalogSnapshot
import dev.vifs.viroutefs.routing.VpnGateServer
import dev.vifs.viroutefs.routing.applyProfileImport
import dev.vifs.viroutefs.routing.createAutomaticVpnGateRoute
import dev.vifs.viroutefs.routing.previewVpnGateProfile
import java.text.DateFormat
import java.util.Date
import java.util.Locale
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
    BackHandler(onBack = onBack)
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
    var selectionMode by rememberSaveable { mutableStateOf(VPN_GATE_MODE_MANUAL) }
    var homeCountryCode by rememberSaveable {
        mutableStateOf(detectDeviceCountryCode(context))
    }
    var preferredCountryCode by rememberSaveable {
        mutableStateOf(
            config.profileGroups
                .firstOrNull { it.id == dev.vifs.viroutefs.routing.VPN_GATE_AUTOMATIC_GROUP_ID }
                ?.preferredCountryCode
                .orEmpty(),
        )
    }

    LaunchedEffect(client) {
        snapshot = withContext(Dispatchers.IO) { client.loadCached() }
    }

    fun refresh() {
        if (loading) return
        loading = true
        message = null
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { client.fetch(config) } }
                .onSuccess { loaded ->
                    snapshot = loaded
                    message = buildString {
                        append("Каталог VPNGate обновлён: ${loaded.servers.size} серверов")
                        loaded.transportProfileName?.let { append(" через профиль «$it»") }
                        append(". Ничего не подключено автоматически.")
                    }
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
    val automaticCountryCodes = remember(snapshot, homeCountryCode) {
        val excluded = homeCountryCode.trim().uppercase()
        snapshot?.servers.orEmpty()
            .asSequence()
            .filter { it.countryCode.length == 2 && it.countryCode != excluded && (it.pingMillis ?: 0) > 0 }
            .groupBy(VpnGateServer::countryCode)
            .filterValues { it.size >= 2 }
            .keys
            .sorted()
    }
    val automaticCandidates = remember(snapshot, homeCountryCode, preferredCountryCode) {
        val excluded = homeCountryCode.trim().uppercase()
        snapshot?.servers.orEmpty()
            .asSequence()
            .filter { it.countryCode.length == 2 && it.countryCode != excluded }
            .filter { preferredCountryCode.isBlank() || it.countryCode == preferredCountryCode }
            .filter { (it.pingMillis ?: 0) > 0 }
            .sortedWith(compareBy<VpnGateServer> { it.pingMillis ?: Int.MAX_VALUE }.thenByDescending { it.score })
            .take(VPN_GATE_AUTOMATIC_MEMBER_LIMIT)
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
        item {
            CardBlock {
                Text("Как выбирать VPNGate", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectionMode == VPN_GATE_MODE_MANUAL,
                        onClick = { selectionMode = VPN_GATE_MODE_MANUAL },
                        label = { Text("Выбрать самому") },
                    )
                    FilterChip(
                        selected = selectionMode == VPN_GATE_MODE_AUTOMATIC,
                        onClick = { selectionMode = VPN_GATE_MODE_AUTOMATIC },
                        label = { Text("Автоматически") },
                    )
                }
                Text(
                    if (selectionMode == VPN_GATE_MODE_MANUAL) {
                        "Вы видите полный список и добавляете один выключенный профиль вручную."
                    } else {
                        "ViRouteFS исключит вашу страну, подготовит до шести серверов с наименьшим пингом и будет проверять их через реальное HTTPS-соединение каждые 60 секунд."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (selectionMode == VPN_GATE_MODE_AUTOMATIC) {
            item {
                CardBlock {
                    Text("Автоматический маршрут", fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = homeCountryCode,
                        onValueChange = { value ->
                            homeCountryCode = value.filter(Char::isLetter).take(2).uppercase()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Не выбирать серверы в моей стране") },
                        supportingText = { Text("Двухбуквенный код, например RU, KZ или DE") },
                        singleLine = true,
                    )
                    Text("Предпочтительная страна", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = preferredCountryCode.isBlank(),
                            onClick = { preferredCountryCode = "" },
                            label = { Text("Автоматически") },
                        )
                        automaticCountryCodes.forEach { countryCode ->
                            FilterChip(
                                selected = preferredCountryCode == countryCode,
                                onClick = { preferredCountryCode = countryCode },
                                label = { Text(countryCode) },
                            )
                        }
                    }
                    if (automaticCandidates.isNotEmpty()) {
                        Text(
                            "Предварительный выбор: " + automaticCandidates.joinToString { server ->
                                "${server.countryCode} ${server.pingMillis} мс"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            if (snapshot == null) {
                                "Сначала загрузите каталог VPNGate кнопкой выше."
                            } else {
                                "Для этого кода страны пока не найдено достаточно серверов с указанным пингом."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "После подтверждения появится один управляемый пункт VPNGate. Затем выберите приложения: без этого VPNGate не получит трафик и обычный интернет останется основным маршрутом.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(
                        enabled = snapshot != null && homeCountryCode.matches(Regex("[A-Z]{2}")) && importKey == null,
                        onClick = {
                            val loaded = snapshot ?: return@Button
                            importKey = VPN_GATE_AUTOMATIC_IMPORT_KEY
                            message = null
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.Default) {
                                        createAutomaticVpnGateRoute(
                                            config = config,
                                            servers = loaded.servers,
                                            excludedCountryCode = homeCountryCode,
                                            preferredCountryCode = preferredCountryCode.ifBlank { null },
                                        )
                                    }
                                }.onSuccess { result ->
                                    onConfig(
                                        result.config,
                                        "VPNGate подготовлен: ${result.selectedServers.size} сервера. Теперь выберите приложения; основной маршрут остаётся System.",
                                    )
                                    message = "Серверы подготовлены. Откройте «Приложения» у VPNGate: " + result.selectedServers.joinToString { server ->
                                        "${server.countryCode} ${server.pingMillis} мс"
                                    }
                                }.onFailure { error ->
                                    message = "Автоматический маршрут не создан: ${error.localizedMessage ?: "неподходящий каталог"}."
                                }
                                importKey = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (importKey == VPN_GATE_AUTOMATIC_IMPORT_KEY) "Подготавливаем…" else "Подготовить автоматический VPNGate")
                    }
                }
            }
        }
        if (snapshot != null && selectionMode == VPN_GATE_MODE_MANUAL) {
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

internal fun detectDeviceCountryCode(context: Context): String {
    val telephony = context.getSystemService(TelephonyManager::class.java)
    return listOf(
        runCatching { telephony?.networkCountryIso }.getOrNull(),
        runCatching { telephony?.simCountryIso }.getOrNull(),
        Locale.getDefault().country,
    ).firstOrNull { value -> value?.matches(Regex("[A-Za-z]{2}")) == true }
        ?.uppercase()
        .orEmpty()
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
private const val VPN_GATE_MODE_MANUAL = "manual"
private const val VPN_GATE_MODE_AUTOMATIC = "automatic"
private const val VPN_GATE_AUTOMATIC_IMPORT_KEY = "vpngate:auto"
private const val MAX_VISIBLE_VPN_GATE_SERVERS = 100
