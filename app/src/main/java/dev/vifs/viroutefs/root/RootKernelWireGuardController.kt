// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import com.wireguard.android.backend.Tunnel
import com.wireguard.android.backend.WgQuickBackend
import com.wireguard.android.util.RootShell
import com.wireguard.android.util.ToolsInstaller
import com.wireguard.config.Config
import dev.vifs.viroutefs.routing.AesGcmSecretCodec
import dev.vifs.viroutefs.routing.AndroidSecretKeyProvider
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.ProfileAppRoutingMode
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigRepository
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.orderedServers
import dev.vifs.viroutefs.routing.writeTextAtomically
import dev.vifs.viroutefs.vpn.ViRouteVpnService
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.InetAddress
import org.json.JSONObject

data class RootKernelWireGuardProfile(
    val id: String,
    val name: String,
    val compatible: Boolean,
    val summary: String,
)

data class RootKernelWireGuardResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

data class RootKernelWireGuardDetails(
    val successful: Boolean,
    val running: Boolean,
    val moduleVersion: String? = null,
    val receivedBytes: Long = 0L,
    val transmittedBytes: Long = 0L,
    val message: String,
)

class RootKernelWireGuardController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val runtime = RootRuntimeStateRepository(appContext)
    private val routingRepository = RoutingConfigRepository(appContext)
    private val recoveryStore = RootKernelWireGuardRecoveryStore(appContext)

    fun isRunning(): Boolean =
        RootManagedModule.KernelWireGuard in runtime.load()?.modules.orEmpty() || recoveryStore.exists()

    fun activeProfileId(): String? = recoveryStore.load().getOrNull()?.profileId

    suspend fun listProfiles(): List<RootKernelWireGuardProfile> {
        val config = routingRepository.load().config
        return config.profiles
            .filter { it.type == TunnelType.WireGuard }
            .map { profile ->
                val prepared = runCatching { prepareKernelConfig(profile, config) }
                RootKernelWireGuardProfile(
                    id = profile.id,
                    name = profile.name,
                    compatible = prepared.isSuccess,
                    summary = prepared.fold(
                        onSuccess = { it.summary },
                        onFailure = { it.message ?: "Профиль нельзя преобразовать в безопасную системную конфигурацию." },
                    ),
                )
            }
            .sortedWith(compareByDescending<RootKernelWireGuardProfile> { it.id == activeProfileId() }.thenBy { it.name.lowercase() })
    }

    suspend fun start(profileId: String): RootKernelWireGuardResult {
        if (isRunning()) {
            return RootKernelWireGuardResult(
                false,
                true,
                "Системный WireGuard уже отмечен как активный. Сначала остановите его или выполните общую очистку root-центра.",
            )
        }
        if (ViRouteVpnService.isRunning) {
            return RootKernelWireGuardResult(
                false,
                false,
                "Сначала остановите обычный сетевой контроль ViRouteFS: два VPN-маршрутизатора одновременно могут конфликтовать.",
            )
        }
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootKernelWireGuardResult(false, false, probe.message)
        if (probe.snapshot?.hasWireGuardKernelModule != true || !WgQuickBackend.hasKernelSupport()) {
            return RootKernelWireGuardResult(
                false,
                false,
                "В текущем ядре Android модуль WireGuard не найден. Обычный WireGuard ViRouteFS через VPN Android остаётся доступен без root.",
            )
        }
        val routing = routingRepository.load().config
        val profile = routing.profiles.firstOrNull { it.id == profileId && it.type == TunnelType.WireGuard }
            ?: return RootKernelWireGuardResult(false, false, "Выбранный WireGuard-профиль не найден.")
        val prepared = runCatching { prepareKernelConfig(profile, routing) }.getOrElse { error ->
            return RootKernelWireGuardResult(false, false, error.message ?: "Профиль не подходит для системного WireGuard.")
        }
        val officialConfig = runCatching { parseOfficialConfig(prepared.wgQuickText) }.getOrElse { error ->
            return RootKernelWireGuardResult(
                false,
                false,
                "Официальная библиотека WireGuard отклонила конфигурацию: ${error.localizedMessage.orEmpty().take(240)}",
            )
        }

        runCatching {
            recoveryStore.save(profile.id, profile.name, prepared.wgQuickText)
            runtime.markPending(RootManagedModule.KernelWireGuard, "wireguard_wgquick")
        }.getOrElse {
            recoveryStore.clear()
            return RootKernelWireGuardResult(false, false, "Не удалось сохранить зашифрованное состояние отката. Туннель не запускался.")
        }

        val backend = createBackend()
        return runCatching {
            check(backend.setState(KERNEL_WIREGUARD_TUNNEL, Tunnel.State.UP, officialConfig) == Tunnel.State.UP)
            check(backend.getState(KERNEL_WIREGUARD_TUNNEL) == Tunnel.State.UP)
            RootKernelWireGuardResult(
                true,
                true,
                "Системный WireGuard включён через модуль ядра. ${prepared.summary} Обычный режим ViRouteFS не изменён.",
            )
        }.getOrElse { error ->
            val rolledBack = runCatching {
                backend.setState(KERNEL_WIREGUARD_TUNNEL, Tunnel.State.DOWN, officialConfig)
                backend.getState(KERNEL_WIREGUARD_TUNNEL) == Tunnel.State.DOWN
            }.getOrDefault(false)
            if (rolledBack) {
                runtime.removeModule(RootManagedModule.KernelWireGuard)
                recoveryStore.clear()
            }
            RootKernelWireGuardResult(
                false,
                !rolledBack,
                if (rolledBack) {
                    "Системный WireGuard не запустился и был автоматически удалён без изменения обычного VPN. ${safeError(error)}"
                } else {
                    "Запуск прерван, но автоматический откат не подтверждён. Не перезагружайте постоянный root-модуль; выполните общую очистку root-центра. ${safeError(error)}"
                },
            )
        }
    }

    fun stop(): RootKernelWireGuardResult {
        val stored = recoveryStore.load().getOrElse { error ->
            return RootKernelWireGuardResult(
                false,
                true,
                "Не удалось прочитать зашифрованное состояние отката. Очистка не выполнялась: ${safeError(error)}",
            )
        } ?: run {
            runtime.removeModule(RootManagedModule.KernelWireGuard)
            return RootKernelWireGuardResult(true, false, "Сохранённая системная сессия WireGuard не найдена.")
        }
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootKernelWireGuardResult(false, true, probe.message)
        val officialConfig = runCatching { parseOfficialConfig(stored.wgQuickText) }.getOrElse { error ->
            return RootKernelWireGuardResult(false, true, "Состояние отката WireGuard повреждено: ${safeError(error)}")
        }
        val backend = createBackend()
        return runCatching {
            backend.setState(KERNEL_WIREGUARD_TUNNEL, Tunnel.State.DOWN, officialConfig)
            check(backend.getState(KERNEL_WIREGUARD_TUNNEL) == Tunnel.State.DOWN)
            runtime.removeModule(RootManagedModule.KernelWireGuard)
            recoveryStore.clear()
            RootKernelWireGuardResult(
                true,
                false,
                "Системный WireGuard остановлен; его интерфейс, Android-маршрут и DNS-состояние удалены адресно.",
            )
        }.getOrElse { error ->
            RootKernelWireGuardResult(
                false,
                true,
                "Остановка не подтверждена, поэтому зашифрованное состояние отката сохранено. ${safeError(error)}",
            )
        }
    }

    fun details(): RootKernelWireGuardDetails {
        val probe = access.requestAndProbe()
        if (!probe.granted) return RootKernelWireGuardDetails(false, isRunning(), message = probe.message)
        if (probe.snapshot?.hasWireGuardKernelModule != true || !WgQuickBackend.hasKernelSupport()) {
            return RootKernelWireGuardDetails(
                false,
                false,
                message = "Модуль WireGuard в ядре не найден. Для этого устройства используйте обычный режим без root.",
            )
        }
        return runCatching {
            val backend = createBackend()
            val running = backend.getState(KERNEL_WIREGUARD_TUNNEL) == Tunnel.State.UP
            val statistics = if (running) backend.getStatistics(KERNEL_WIREGUARD_TUNNEL) else null
            RootKernelWireGuardDetails(
                successful = true,
                running = running,
                moduleVersion = backend.version,
                receivedBytes = statistics?.totalRx() ?: 0L,
                transmittedBytes = statistics?.totalTx() ?: 0L,
                message = if (running) "Интерфейс vrwg0 найден в ядре." else "Интерфейс vrwg0 сейчас не поднят.",
            )
        }.getOrElse { error ->
            RootKernelWireGuardDetails(false, isRunning(), message = "Не удалось прочитать состояние WireGuard: ${safeError(error)}")
        }
    }

    private fun createBackend(): WgQuickBackend {
        val rootShell = ViRouteSafeWireGuardRootShell(appContext)
        return WgQuickBackend(appContext, rootShell, ToolsInstaller(appContext, rootShell))
    }
}

internal data class PreparedKernelWireGuardConfig(
    val wgQuickText: String,
    val summary: String,
)

internal fun prepareKernelConfig(
    profile: TunnelProfile,
    routing: RoutingConfig,
): PreparedKernelWireGuardConfig {
    require(profile.type == TunnelType.WireGuard) { "Нужен WireGuard-профиль." }
    require(profile.enabled) { "Профиль выключен в настройках." }
    val options = requireNotNull(profile.singBox) { "У профиля нет настроек WireGuard." }
    val root = JSONObject(options.optionsJson)
    require(root.optString("type") == "wireguard") { "Профиль содержит не WireGuard-конфигурацию." }
    val addresses = root.requiredStringArray("address", "Address")
    val privateKey = root.requiredSingleLine("private_key", "PrivateKey")
    val peers = root.optJSONArray("peers")
    require(peers != null && peers.length() in 1..32) { "WireGuard-профиль должен содержать от 1 до 32 peers." }

    val allowedIps = buildList {
        repeat(peers.length()) { index ->
            addAll(peers.getJSONObject(index).requiredStringArray("allowed_ips", "AllowedIPs peer #${index + 1}"))
        }
    }
    val fullTunnel = allowedIps.any { it == "0.0.0.0/0" || it == "::/0" }
    val dnsPolicy = profile.dnsPolicyId?.let { id -> routing.dnsPolicies.firstOrNull { it.id == id && it.enabled } }
    val dnsServers = dnsPolicy?.kernelDnsServers().orEmpty()
    if (fullTunnel) {
        require(dnsServers.isNotEmpty()) {
            "Для полного туннеля выберите у профиля включённую Custom DNS-политику с обычным IP-адресом. Это защищает от прямой DNS-утечки."
        }
    }
    if (dnsPolicy != null && dnsPolicy.type == DnsPolicyType.Custom) {
        require(dnsPolicy.resolveThroughProfileId == null || dnsPolicy.resolveThroughProfileId == profile.id) {
            "Системный режим не может отправить DNS через другой VPN-профиль."
        }
        require(dnsServers.isNotEmpty()) {
            "Custom DNS этого профиля не содержит обычного IPv4/IPv6-адреса; DoH/DoT в системном wg-quick не переносится."
        }
    }

    val text = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = $privateKey")
        appendLine("Address = ${addresses.joinToString(", ")}")
        root.optInt("listen_port", 0).takeIf { it in 1..65_535 }?.let { appendLine("ListenPort = $it") }
        root.optInt("mtu", 0).takeIf { it in 576..65_535 }?.let { appendLine("MTU = $it") }
        if (dnsServers.isNotEmpty()) appendLine("DNS = ${dnsServers.joinToString(", ")}")
        val packages = profile.appRoutingPackages
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .also { values -> require(values.size <= 128 && values.all(::isSafeAndroidPackageName)) { "Список приложений содержит некорректное имя пакета." } }
        if (packages.isNotEmpty()) {
            val key = if (profile.appRoutingMode == ProfileAppRoutingMode.BypassSelected) {
                "ExcludedApplications"
            } else {
                "IncludedApplications"
            }
            appendLine("$key = ${packages.joinToString(", ")}")
        }
        repeat(peers.length()) { index ->
            val peer = peers.getJSONObject(index)
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${peer.requiredSingleLine("public_key", "PublicKey peer #${index + 1}")}")
            peer.optString("pre_shared_key").takeIf(String::isNotBlank)?.let { key ->
                requireSafeSingleLine(key, "PresharedKey peer #${index + 1}")
                appendLine("PresharedKey = $key")
            }
            appendLine("AllowedIPs = ${peer.requiredStringArray("allowed_ips", "AllowedIPs peer #${index + 1}").joinToString(", ")}")
            val host = peer.requiredSingleLine("address", "Endpoint peer #${index + 1}")
            val port = peer.optInt("port", -1)
            require(port in 1..65_535) { "Некорректный порт WireGuard peer #${index + 1}." }
            appendLine("Endpoint = ${formatWireGuardEndpoint(host, port)}")
            peer.optInt("persistent_keepalive_interval", -1).takeIf { it in 0..65_535 }?.let {
                appendLine("PersistentKeepalive = $it")
            }
        }
    }
    require(text.toByteArray().size <= KERNEL_WIREGUARD_CONFIG_MAX_BYTES) { "WireGuard-конфигурация слишком велика." }
    parseOfficialConfig(text)
    val routeSummary = if (fullTunnel) {
        "Полный туннель использует DNS из выбранной политики."
    } else {
        "Раздельный туннель сохраняет системный DNS, если Custom DNS не выбран."
    }
    val appSummary = when {
        profile.appRoutingPackages.isEmpty() -> "Маршрут действует для всех приложений."
        profile.appRoutingMode == ProfileAppRoutingMode.BypassSelected -> "Выбранные приложения обходят туннель."
        else -> "Только выбранные приложения входят в туннель."
    }
    return PreparedKernelWireGuardConfig(text, "$routeSummary $appSummary")
}

private fun DnsPolicy.kernelDnsServers(): List<String> {
    if (type != DnsPolicyType.Custom || !enabled) return emptyList()
    return orderedServers()
        .map { it.address.trim().removePrefix("udp://") }
        .filter(::isNumericIpAddress)
        .distinct()
        .take(4)
}

private fun JSONObject.requiredSingleLine(name: String, label: String): String =
    getString(name).trim().also { requireSafeSingleLine(it, label) }

private fun JSONObject.requiredStringArray(name: String, label: String): List<String> {
    val array = optJSONArray(name)
    require(array != null && array.length() in 1..64) { "$label не заполнен." }
    return List(array.length()) { index ->
        array.getString(index).trim().also { requireSafeSingleLine(it, "$label #${index + 1}") }
    }
}

private fun requireSafeSingleLine(value: String, label: String) {
    require(value.isNotBlank() && value.length <= 1024 && value.none { it == '\n' || it == '\r' || it == '\u0000' }) {
        "$label содержит недопустимое значение."
    }
}

private fun formatWireGuardEndpoint(rawHost: String, port: Int): String {
    val host = rawHost.removePrefix("[").removeSuffix("]")
    require(host.length in 1..253 && host.matches(Regex("[A-Za-z0-9._:%-]+"))) { "Некорректный адрес WireGuard endpoint." }
    return if (':' in host) "[$host]:$port" else "$host:$port"
}

private fun isSafeAndroidPackageName(value: String): Boolean =
    value.length in 3..255 && value.matches(Regex("[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+"))

private fun isNumericIpAddress(value: String): Boolean {
    if (value.isBlank() || value.any { it.isWhitespace() } || '%' in value) return false
    if (value.matches(Regex("[0-9]{1,3}(?:\\.[0-9]{1,3}){3}"))) {
        return value.split('.').all { it.toIntOrNull() in 0..255 }
    }
    if (':' !in value || !value.matches(Regex("[0-9A-Fa-f:.]+"))) return false
    return runCatching { InetAddress.getByName(value).hostAddress?.contains(':') == true }.getOrDefault(false)
}

private fun parseOfficialConfig(text: String): Config =
    ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)).use(Config::parse)

private object KERNEL_WIREGUARD_TUNNEL : Tunnel {
    override fun getName(): String = KERNEL_WIREGUARD_INTERFACE
    override fun onStateChange(newState: Tunnel.State) = Unit
}

private class ViRouteSafeWireGuardRootShell(context: Context) : RootShell(context) {
    private val appContext = context.applicationContext
    private val executor = RootCommandExecutor()
    private val binaryDirectory = File(appContext.codeCacheDir, "bin").apply { mkdirs() }
    private val temporaryDirectory = File(appContext.cacheDir, "tmp").apply { mkdirs() }
    private val configPath = File(temporaryDirectory, "$KERNEL_WIREGUARD_INTERFACE.conf").absolutePath

    override fun run(output: MutableCollection<String>?, command: String): Int {
        val allowed = command == "wg show interfaces" ||
            command == "cat /sys/module/wireguard/version" ||
            command == "wg show '$KERNEL_WIREGUARD_INTERFACE' dump" ||
            command == "cat /sys/module/wireguard/version && wg-quick up '$configPath'" ||
            command == "wg-quick down '$configPath'"
        if (!allowed) throw SecurityException("WireGuard root command is outside the ViRouteFS allow-list.")
        val wrapped = "export CALLING_PACKAGE=${shellQuote(appContext.packageName)}; " +
            "export PATH=${shellQuote(binaryDirectory.absolutePath)}:\"${'$'}PATH\"; " +
            "export TMPDIR=${shellQuote(temporaryDirectory.absolutePath)}; $command"
        val result = executor.execute(wrapped, KERNEL_WIREGUARD_COMMAND_TIMEOUT_MILLIS)
        if (!result.suCommandVisible) throw IOException("Root command is unavailable.")
        if (!result.completed) throw IOException("WireGuard root command timed out.")
        output?.addAll(result.output.lineSequence().take(256).toList())
        return result.exitCode ?: 1
    }

    override fun stop() = Unit
}

private data class RootKernelWireGuardRecoveryState(
    val profileId: String,
    val profileName: String,
    val wgQuickText: String,
)

private class RootKernelWireGuardRecoveryStore(context: Context) {
    private val file = File(context.applicationContext.noBackupFilesDir, KERNEL_WIREGUARD_RECOVERY_FILE)
    private val keyProvider = AndroidSecretKeyProvider(KERNEL_WIREGUARD_KEY_ALIAS)

    fun exists(): Boolean = file.isFile

    fun load(): Result<RootKernelWireGuardRecoveryState?> = runCatching {
        if (!file.isFile) return@runCatching null
        require(file.length() in 1..KERNEL_WIREGUARD_RECOVERY_MAX_BYTES.toLong()) { "Recovery state is too large." }
        val plaintext = AesGcmSecretCodec.decrypt(file.readText(Charsets.UTF_8), keyProvider.getOrCreate())
        val root = JSONObject(plaintext)
        require(root.getInt("version") == 1) { "Unsupported WireGuard recovery version." }
        RootKernelWireGuardRecoveryState(
            profileId = root.getString("profileId").take(200),
            profileName = root.getString("profileName").take(300),
            wgQuickText = root.getString("wgQuickText").also {
                require(it.toByteArray().size <= KERNEL_WIREGUARD_CONFIG_MAX_BYTES)
            },
        )
    }

    fun save(profileId: String, profileName: String, wgQuickText: String) {
        require(profileId.isNotBlank() && profileId.length <= 200)
        require(wgQuickText.toByteArray().size <= KERNEL_WIREGUARD_CONFIG_MAX_BYTES)
        val plaintext = JSONObject()
            .put("version", 1)
            .put("profileId", profileId)
            .put("profileName", profileName.take(300))
            .put("wgQuickText", wgQuickText)
            .toString()
        val envelope = AesGcmSecretCodec.encrypt(plaintext, keyProvider.getOrCreate())
        require(envelope.toByteArray().size <= KERNEL_WIREGUARD_RECOVERY_MAX_BYTES)
        file.writeTextAtomically(envelope)
        file.setReadable(false, false)
        file.setWritable(false, false)
        file.setReadable(true, true)
        file.setWritable(true, true)
    }

    fun clear() {
        if (file.exists() && !file.delete()) error("Could not clear WireGuard recovery state.")
    }
}

private fun safeError(error: Throwable): String =
    (error.localizedMessage ?: error::class.java.simpleName).replace(Regex("(?i)(privatekey|presharedkey)\\s*=\\s*\\S+"), "\$1=<hidden>").take(300)

private const val KERNEL_WIREGUARD_INTERFACE = "vrwg0"
private const val KERNEL_WIREGUARD_CONFIG_MAX_BYTES = 64 * 1024
private const val KERNEL_WIREGUARD_RECOVERY_MAX_BYTES = 128 * 1024
private const val KERNEL_WIREGUARD_COMMAND_TIMEOUT_MILLIS = 45_000L
private const val KERNEL_WIREGUARD_RECOVERY_FILE = "root-wireguard-recovery.v1.json.aesgcm"
private const val KERNEL_WIREGUARD_KEY_ALIAS = "dev.vifs.viroutefs.root-wireguard-recovery.v1"
