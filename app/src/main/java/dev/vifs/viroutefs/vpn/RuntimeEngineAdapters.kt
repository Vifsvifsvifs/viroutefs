// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import android.os.ParcelFileDescriptor
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
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.validateRoutingConfig

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

internal class SingBoxEngineAdapter(
    private val service: ViRouteVpnService,
    private val onTunEstablished: (ParcelFileDescriptor) -> Unit,
    private val onLog: (String) -> Unit,
    private val onConnections: (List<VpnConnectionFlow>) -> Unit,
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
    override val dependencies: Set<String> = setOf(ByeDpiEngineAdapter.ID)
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
        val keptIds = profileIds + setOf(
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
            val nativeConfig = SingBoxRoutingConfigCompiler(byeDpiPort = compatibilityPort)
                .compile(config)
            if (!config.emergencyBlockEnabled &&
                config.defaultProfileId !in nativeConfig.runtimeProfileIds
            ) {
                error("Основной маршрут не прошёл нативную проверку движка.")
            }
            compiledWarnings = nativeConfig.warnings
            activeProfiles = nativeConfig.runtimeProfileIds.size
            val nextRunner = SingBoxEngineRunner(
                service = service,
                onTunEstablished = onTunEstablished,
                onLog = onLog,
                onConnections = onConnections,
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

