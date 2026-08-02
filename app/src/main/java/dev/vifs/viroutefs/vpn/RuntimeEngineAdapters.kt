// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import dev.vifs.viroutefs.engine.CompiledEngineConfig
import dev.vifs.viroutefs.engine.EngineAdapter
import dev.vifs.viroutefs.engine.EngineBackend
import dev.vifs.viroutefs.engine.EngineCapabilities
import dev.vifs.viroutefs.engine.EngineConnectionTest
import dev.vifs.viroutefs.engine.EngineError
import dev.vifs.viroutefs.engine.EngineErrorStage
import dev.vifs.viroutefs.engine.EngineRuntimeContext
import dev.vifs.viroutefs.engine.EngineState
import dev.vifs.viroutefs.engine.EngineStatistics
import dev.vifs.viroutefs.engine.LocalEngineEndpoint
import dev.vifs.viroutefs.engine.SingBoxRoutingConfigCompiler
import dev.vifs.viroutefs.engine.XrayLocalProfile
import dev.vifs.viroutefs.engine.compileXrayRuntime
import dev.vifs.viroutefs.engine.routedProfileIds
import dev.vifs.viroutefs.engine.runtimeProfileGroupHealthTag
import dev.vifs.viroutefs.engine.validateXrayProfile
import dev.vifs.viroutefs.routing.ProfileGroupMode
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.orderedServers
import dev.vifs.viroutefs.routing.validateRoutingConfig
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

internal class ByeDpiEngineAdapter(
    context: Context,
) : EngineAdapter {
    override val id: String = ID
    override val backend: EngineBackend = EngineBackend.ByeDpi
    override val supportedProtocols: Set<TunnelType> = setOf(TunnelType.ByeDpi)
    override val dependencies: Set<String> = emptySet()
    override val alwaysRequired: Boolean = false
    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsMultipleInstances = false,
        supportsTcp = true,
        supportsUdp = false,
        supportsIpv6 = false,
        supportsDnsThroughProfile = false,
    )

    private val manager = ByeDpiProcessManager(context)
    private var activeProfiles: Int = 0
    override var state: EngineState = EngineState.Stopped
        private set
    override var lastError: EngineError? = null
        private set

    override fun validateProfile(profile: TunnelProfile): List<EngineError> =
        if (profile.type == TunnelType.ByeDpi) emptyList() else listOf(
            error(
                EngineErrorStage.Validation,
                "Движок совместимости получил профиль другого типа.",
                "Expected ByeDpi, received ${profile.type.name}.",
                "Пересоздайте профиль совместимости.",
            ),
        )

    override fun compile(
        config: RoutingConfig,
        profileIds: Set<String>,
    ): Result<CompiledEngineConfig> = Result.success(
        CompiledEngineConfig(
            adapterId = id,
            profileIds = profileIds,
            payload = null,
        ),
    )

    override fun start(
        compiled: CompiledEngineConfig,
        runtimeContext: EngineRuntimeContext,
    ): Result<Unit> {
        lastError = null
        activeProfiles = compiled.profileIds.size
        if (compiled.profileIds.isEmpty()) {
            state = EngineState.Connected
            return Result.success(Unit)
        }
        state = EngineState.Starting
        return manager.start()
            .map { port ->
                runtimeContext.publishEndpoint(id, LocalEngineEndpoint("127.0.0.1", port))
                state = EngineState.Connected
            }
            .onFailure { cause ->
                state = EngineState.Error
                lastError = error(
                    EngineErrorStage.Start,
                    "Не запустился локальный режим совместимости TCP/TLS.",
                    cause.message ?: cause::class.java.simpleName,
                    "Отключите связанные правила или переустановите APK той же архитектуры.",
                    cause,
                )
            }
    }

    override fun stop() {
        state = EngineState.Stopping
        manager.stop()
        activeProfiles = 0
        state = EngineState.Stopped
    }

    override fun isHealthy(): Boolean =
        state == EngineState.Connected && (activeProfiles == 0 || manager.isRunning())

    override fun statistics(): EngineStatistics =
        EngineStatistics(activeProfiles = activeProfiles)

    override fun testConnection(profileId: String): Result<EngineConnectionTest> =
        Result.success(
            EngineConnectionTest(
                successful = isHealthy(),
                summary = manager.lastMessage ?: "Локальный процесс ещё не запускался.",
            ),
        )

    override fun maskSecrets(message: String): String = message.take(500)

    override fun cleanup() {
        manager.stop()
    }

    private fun error(
        stage: EngineErrorStage,
        summary: String,
        details: String,
        action: String,
        cause: Throwable? = null,
    ) = EngineError(id, stage, summary, details, action, cause)

    companion object {
        const val ID = "byedpi"
    }
}

private data class XrayEnginePayload(
    val profiles: List<TunnelProfile>,
)

/**
 * Runs Xray only as a collection of app-private loopback SOCKS endpoints.
 *
 * It never creates an Android VPN or owns a TUN. The single sing-box router
 * remains responsible for device routes and sends only explicitly selected
 * flows to these endpoints.
 */
internal class XrayEngineAdapter(
    context: Context,
) : EngineAdapter {
    override val id: String = ID
    override val backend: EngineBackend = EngineBackend.Xray
    override val supportedProtocols: Set<TunnelType> = setOf(TunnelType.XrayVlessReality)
    override val dependencies: Set<String> = emptySet()
    override val alwaysRequired: Boolean = false
    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsMultipleInstances = true,
        supportsTcp = true,
        supportsUdp = true,
        supportsIpv6 = true,
        supportsDnsThroughProfile = true,
    )

    private val applicationContext = context.applicationContext
    private val processManager = XrayProcessManager(applicationContext)
    private var activePorts: Map<String, Int> = emptyMap()
    private var sensitiveValues: Set<String> = emptySet()
    override var state: EngineState = EngineState.Stopped
        private set
    override var lastError: EngineError? = null
        private set

    override fun validateProfile(profile: TunnelProfile): List<EngineError> =
        validateXrayProfile(profile).map { details ->
            error(
                EngineErrorStage.Validation,
                "Профиль «${profile.name}» не подходит для локального ядра Xray.",
                details,
                "Исправьте параметры VLESS/XHTTP либо отключите связанные с профилем правила.",
            )
        }

    override fun compile(
        config: RoutingConfig,
        profileIds: Set<String>,
    ): Result<CompiledEngineConfig> = runCatching {
        state = EngineState.Validating
        val profiles = config.profiles.filter { it.id in profileIds }
        require(profiles.size == profileIds.size) {
            "One or more routed Xray profiles are missing."
        }
        val problems = profiles.flatMap(::validateXrayProfile)
        require(problems.isEmpty()) { problems.joinToString(" ") }
        CompiledEngineConfig(
            adapterId = id,
            profileIds = profileIds,
            payload = XrayEnginePayload(profiles),
        )
    }.onFailure { cause ->
        state = EngineState.Error
        lastError = error(
            EngineErrorStage.Compilation,
            "Не удалось подготовить локальный профиль Xray.",
            maskSecrets(cause.message ?: cause::class.java.simpleName),
            "Проверьте параметры импортированного VLESS/XHTTP-профиля.",
            cause,
        )
    }

    override fun start(
        compiled: CompiledEngineConfig,
        runtimeContext: EngineRuntimeContext,
    ): Result<Unit> {
        stop()
        lastError = null
        val payload = compiled.payload as? XrayEnginePayload
            ?: return Result.failure(IllegalStateException("Compiled Xray payload is missing."))
        if (payload.profiles.isEmpty()) {
            state = EngineState.Connected
            return Result.success(Unit)
        }

        state = EngineState.Starting
        sensitiveValues = payload.profiles.flatMap { profile ->
            profile.vless?.let { vless ->
                listOfNotNull(
                    vless.uuid,
                    vless.host,
                    vless.sni,
                    vless.publicKey,
                    vless.shortId,
                    vless.path,
                    vless.hostHeader,
                    vless.xhttpExtra,
                )
            }.orEmpty()
        }.filter { it.length >= 3 }.toSet()

        return runCatching {
            val ports = reserveLoopbackPorts(payload.profiles.size)
            val runtime = compileXrayRuntime(
                payload.profiles.zip(ports).map { (profile, port) ->
                    XrayLocalProfile(
                        profileId = profile.id,
                        localSocksPort = port,
                        profile = requireNotNull(profile.vless),
                    )
                },
            )
            state = EngineState.Connecting
            processManager.start(
                configJson = runtime.json,
                ports = runtime.profilePorts.values,
                xudpBaseKey = xudpBaseKey(),
                maskLog = ::maskSecrets,
            ).getOrThrow()
            runtime.profilePorts.forEach { (profileId, port) ->
                runtimeContext.publishProfileEndpoint(
                    adapterId = id,
                    profileId = profileId,
                    endpoint = LocalEngineEndpoint("127.0.0.1", port),
                )
            }
            activePorts = runtime.profilePorts
            state = EngineState.Connected
        }.onFailure { cause ->
            processManager.stop()
            activePorts = emptyMap()
            state = EngineState.Error
            lastError = error(
                EngineErrorStage.Start,
                "Локальное ядро Xray не запустило профиль VLESS/XHTTP.",
                maskSecrets(cause.message ?: cause::class.java.simpleName),
                "Проверьте импортированный профиль; его трафик оставлен заблокированным.",
                cause,
            )
        }
    }

    override fun stop() {
        if (state != EngineState.Stopped) state = EngineState.Stopping
        processManager.stop()
        activePorts = emptyMap()
        sensitiveValues = emptySet()
        state = EngineState.Stopped
    }

    override fun isHealthy(): Boolean =
        state == EngineState.Connected &&
            (activePorts.isEmpty() || processManager.isRunning())

    override fun statistics(): EngineStatistics =
        EngineStatistics(activeProfiles = activePorts.size)

    override fun testConnection(profileId: String): Result<EngineConnectionTest> = runCatching {
        val port = requireNotNull(activePorts[profileId]) {
            "The selected Xray profile is not active."
        }
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), port), PORT_CHECK_TIMEOUT_MS)
        }
        EngineConnectionTest(
            successful = true,
            summary = "Локальный вход профиля Xray готов принимать маршрутизируемый трафик.",
        )
    }

    override fun maskSecrets(message: String): String {
        var masked = message
            .replace(Regex("(?i)vless://[^\\s]+"), "vless://<redacted>")
            .replace(
                Regex("(?i)(uuid|id|password|publicKey|shortId|extra)\\s*[=:]\\s*[^\\s,;]+"),
                "$1=<redacted>",
            )
            .replace(
                Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
                "<redacted-uuid>",
            )
        sensitiveValues.sortedByDescending(String::length).forEach { value ->
            masked = masked.replace(value, "<redacted>")
        }
        return masked.take(500)
    }

    override fun cleanup() {
        stop()
    }

    private fun xudpBaseKey(): String {
        val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return preferences.getString(XUDP_BASE_KEY, null)
            ?: UUID.randomUUID().toString().also { generated ->
                preferences.edit { putString(XUDP_BASE_KEY, generated) }
            }
    }

    private fun reserveLoopbackPorts(count: Int): List<Int> {
        val reservations = mutableListOf<ServerSocket>()
        return try {
            repeat(count) {
                reservations += ServerSocket(0, 1, InetAddress.getLoopbackAddress())
            }
            reservations.map(ServerSocket::getLocalPort)
        } finally {
            reservations.forEach { runCatching { it.close() } }
        }
    }

    private fun error(
        stage: EngineErrorStage,
        summary: String,
        details: String,
        action: String,
        cause: Throwable? = null,
    ) = EngineError(id, stage, summary, details, action, cause)

    companion object {
        const val ID = "xray"
        private const val PREFERENCES_NAME = "xray_runtime"
        private const val XUDP_BASE_KEY = "xudp_base_key"
        private const val PORT_CHECK_TIMEOUT_MS = 250
    }
}

internal class SingBoxEngineAdapter(
    private val service: ViRouteVpnService,
    private val onTunEstablished: (ParcelFileDescriptor) -> Unit,
    private val onLog: (String) -> Unit,
    private val onConnections: (List<VpnConnectionFlow>) -> Unit,
    private val onProfileGroupAction: (ProfileGroupRuntimeAction) -> Unit = {},
    private val onDnsFallback: (List<String>) -> Unit = {},
) : EngineAdapter {
    override val id: String = ID
    override val backend: EngineBackend = EngineBackend.SingBox
    override val supportedProtocols: Set<TunnelType> =
        dev.vifs.viroutefs.engine.EngineCatalog.protocols
            .filter {
                it.backend == EngineBackend.SingBox ||
                    it.type == TunnelType.Direct ||
                    it.type == TunnelType.Block ||
                    it.type == TunnelType.ByeDpi
            }
            .mapTo(linkedSetOf()) { it.type }
    override val dependencies: Set<String> = setOf(
        ByeDpiEngineAdapter.ID,
        XrayEngineAdapter.ID,
    )
    override val alwaysRequired: Boolean = true
    override val capabilities: EngineCapabilities = EngineCapabilities(
        supportsMultipleInstances = true,
        supportsTcp = true,
        supportsUdp = true,
        supportsIpv6 = true,
        supportsDnsThroughProfile = true,
    )

    private var runner: SingBoxEngineRunner? = null
    private var activeProfiles: Int = 0
    private var compiledWarnings: List<String> = emptyList()
    override var state: EngineState = EngineState.Stopped
        private set
    override var lastError: EngineError? = null
        private set

    override fun validateProfile(profile: TunnelProfile): List<EngineError> =
        validateRoutingConfig(
            RoutingConfig(
                profiles = listOf(profile),
                dnsPolicies = emptyList(),
                rules = emptyList(),
            ),
        )
            .filterNot {
                it.startsWith("Нужно хотя бы") ||
                    it.startsWith("Нужен хотя бы") ||
                    it.contains("DNS-политика")
            }
            .map { details ->
                error(
                    EngineErrorStage.Validation,
                    "Профиль «${profile.name}» не прошёл структурную проверку.",
                    details,
                    "Исправьте отмеченные поля профиля.",
                )
            }

    override fun compile(
        config: RoutingConfig,
        profileIds: Set<String>,
    ): Result<CompiledEngineConfig> = runCatching {
        state = EngineState.Validating
        defaultRouteActivationError(config)?.let { problem ->
            if (!config.emergencyBlockEnabled) error(problem)
        }
        val xrayProfileIds = config.profiles
            .filter { it.type == TunnelType.XrayVlessReality && it.id in config.routedProfileIds() }
            .mapTo(linkedSetOf()) { it.id }
        val keptIds = profileIds + xrayProfileIds + setOf(
            RoutingConfigDefaults.SYSTEM_PROFILE_ID,
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
        )
        val filtered = config.copy(
            profiles = config.profiles.filter { it.id in keptIds },
        )
        CompiledEngineConfig(
            adapterId = id,
            profileIds = profileIds,
            payload = filtered,
        )
    }.onFailure { cause ->
        state = EngineState.Error
        lastError = error(
            EngineErrorStage.Compilation,
            "Не удалось подготовить общую конфигурацию маршрутизатора.",
            cause.message ?: cause::class.java.simpleName,
            "Проверьте основной маршрут и активные правила.",
            cause,
        )
    }

    override fun start(
        compiled: CompiledEngineConfig,
        runtimeContext: EngineRuntimeContext,
    ): Result<Unit> {
        lastError = null
        state = EngineState.Starting
        return runCatching {
            val config = compiled.payload as? RoutingConfig
                ?: error("Compiled sing-box payload is missing.")
            val compatibilityPort = runtimeContext.endpoint(ByeDpiEngineAdapter.ID)?.port
            val xrayEndpoints = config.profiles
                .filter { it.type == TunnelType.XrayVlessReality }
                .mapNotNull { profile ->
                    runtimeContext.profileEndpoint(XrayEngineAdapter.ID, profile.id)
                        ?.let { profile.id to it }
                }
                .toMap()
            val nativeConfig = SingBoxRoutingConfigCompiler(
                byeDpiPort = compatibilityPort,
                xrayEndpoints = xrayEndpoints,
            )
                .compile(config)
            if (!config.emergencyBlockEnabled &&
                config.defaultProfileId !in nativeConfig.runtimeProfileIds
            ) {
                error("Основной маршрут не прошёл нативную проверку движка.")
            }
            compiledWarnings = nativeConfig.warnings
            activeProfiles = nativeConfig.runtimeProfileIds.size
            val managedGroups = config.profileGroups
                .filter {
                    it.enabled &&
                        (
                            it.mode == ProfileGroupMode.Failover ||
                                it.mode == ProfileGroupMode.RoundRobin
                            )
                }
                .mapNotNull { group ->
                    val members = group.memberProfileIds
                        .distinct()
                        .mapNotNull { profileId ->
                            val profile = config.profiles.firstOrNull { it.id == profileId }
                                ?: return@mapNotNull null
                            val tag = nativeConfig.profileTags[profileId] ?: return@mapNotNull null
                            RuntimeGroupMember(
                                profileId = profile.id,
                                profileName = profile.name,
                                outboundTag = tag,
                            )
                        }
                        .distinctBy(RuntimeGroupMember::outboundTag)
                    members.takeIf(List<*>::isNotEmpty)?.let {
                        ManagedProfileGroup(
                            groupId = group.id,
                            groupName = group.name,
                            groupTag = nativeConfig.profileTags.getValue(group.id),
                            healthGroupTag = runtimeProfileGroupHealthTag(group.id),
                            mode = group.mode,
                            members = members,
                            testIntervalSeconds = group.testIntervalSeconds,
                        )
                    }
                }
            val nextRunner = SingBoxEngineRunner(
                service = service,
                onTunEstablished = onTunEstablished,
                onLog = onLog,
                onConnections = onConnections,
                managedProfileGroups = managedGroups,
                onProfileGroupAction = onProfileGroupAction,
                dnsFallbackPolicyNames = config.dnsPolicies
                    .filter {
                        it.enabled &&
                            it.fallbackEnabled &&
                            it.orderedServers().size > 1
                    }
                    .map { it.name },
                onDnsFallback = onDnsFallback,
            )
            runner = nextRunner
            state = EngineState.Connecting
            nextRunner.start(nativeConfig.json).getOrThrow()
            check(nextRunner.isRunning()) {
                "sing-box did not confirm a running service and established TUN."
            }
            state = EngineState.Connected
        }.onFailure { cause ->
            runner?.stop()
            runner = null
            state = EngineState.Error
            lastError = error(
                EngineErrorStage.Start,
                "Локальный VPN-маршрутизатор не запустился.",
                maskSecrets(cause.message ?: cause::class.java.simpleName),
                "Проверьте профиль, DNS и основной маршрут; связанный трафик оставлен заблокированным.",
                cause,
            )
        }
    }

    override fun stop() {
        state = EngineState.Stopping
        runner?.stop()
        runner = null
        activeProfiles = 0
        compiledWarnings = emptyList()
        state = EngineState.Stopped
    }

    override fun isHealthy(): Boolean =
        state == EngineState.Connected && runner?.isRunning() == true

    override fun statistics(): EngineStatistics =
        EngineStatistics(activeProfiles = activeProfiles)

    override fun testConnection(profileId: String): Result<EngineConnectionTest> =
        Result.failure(
            UnsupportedOperationException(
                "Проверка через конкретный outbound будет добавлена в модуле routed diagnostics.",
            ),
        )

    override fun maskSecrets(message: String): String = message
        .replace(Regex("(?i)(password|passphrase|private_key|psk|uuid|auth_key|cookie)\\s*[=:]\\s*[^\\s,;]+"), "$1=<redacted>")
        .take(500)

    override fun cleanup() {
        runner?.stop()
        runner = null
    }

    fun clearConnectionHistory() {
        runner?.clearConnectionHistory()
    }

    fun warnings(): List<String> = compiledWarnings

    private fun error(
        stage: EngineErrorStage,
        summary: String,
        details: String,
        action: String,
        cause: Throwable? = null,
    ) = EngineError(id, stage, summary, details, action, cause)

    companion object {
        const val ID = "sing-box"
    }
}

