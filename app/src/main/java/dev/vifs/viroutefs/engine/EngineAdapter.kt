// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import java.util.concurrent.ConcurrentHashMap

internal enum class EngineState {
    Disabled,
    Validating,
    WaitingForNetwork,
    Starting,
    Connecting,
    Connected,
    IdleOnDemand,
    Reconnecting,
    Stopping,
    Stopped,
    Blocked,
    Error,
}

internal enum class EngineErrorStage {
    Validation,
    Compilation,
    Start,
    HealthCheck,
    ConnectionTest,
    Stop,
}

internal data class EngineError(
    val adapterId: String,
    val stage: EngineErrorStage,
    val summary: String,
    val technicalDetails: String,
    val recommendedAction: String,
    val cause: Throwable? = null,
)

internal data class EngineCapabilities(
    val supportsMultipleInstances: Boolean,
    val supportsTcp: Boolean,
    val supportsUdp: Boolean,
    val supportsIpv6: Boolean,
    val supportsDnsThroughProfile: Boolean,
)

internal data class EngineStatistics(
    val activeProfiles: Int = 0,
    val activeConnections: Int = 0,
    val uplinkBytes: Long = 0L,
    val downlinkBytes: Long = 0L,
)

internal data class EngineConnectionTest(
    val successful: Boolean,
    val summary: String,
    val latencyMillis: Long? = null,
)

internal data class CompiledEngineConfig(
    val adapterId: String,
    val profileIds: Set<String>,
    val payload: Any?,
    val warnings: List<String> = emptyList(),
)

internal data class LocalEngineEndpoint(
    val host: String,
    val port: Int,
)

internal class EngineRuntimeContext {
    private val endpoints = ConcurrentHashMap<String, LocalEngineEndpoint>()
    private val profileEndpoints = ConcurrentHashMap<String, LocalEngineEndpoint>()

    fun publishEndpoint(adapterId: String, endpoint: LocalEngineEndpoint) {
        endpoints[adapterId] = endpoint
    }

    fun endpoint(adapterId: String): LocalEngineEndpoint? = endpoints[adapterId]

    fun publishProfileEndpoint(
        adapterId: String,
        profileId: String,
        endpoint: LocalEngineEndpoint,
    ) {
        profileEndpoints["$adapterId\u0000$profileId"] = endpoint
    }

    fun profileEndpoint(adapterId: String, profileId: String): LocalEngineEndpoint? =
        profileEndpoints["$adapterId\u0000$profileId"]

    fun clearEndpoint(adapterId: String) {
        endpoints.remove(adapterId)
        profileEndpoints.keys
            .filter { it.startsWith("$adapterId\u0000") }
            .forEach(profileEndpoints::remove)
    }

    fun clear() {
        endpoints.clear()
        profileEndpoints.clear()
    }
}

/**
 * Common contract for every embedded or external userspace network engine.
 *
 * `Connected` is reserved for an adapter that has completed its own readiness
 * check; merely spawning a process is not sufficient.
 */
internal interface EngineAdapter {
    val id: String
    val backend: EngineBackend
    val supportedProtocols: Set<TunnelType>
    val dependencies: Set<String>
    val alwaysRequired: Boolean
    val capabilities: EngineCapabilities
    val state: EngineState
    val lastError: EngineError?

    fun validateProfile(profile: TunnelProfile): List<EngineError>
    fun compile(config: RoutingConfig, profileIds: Set<String>): Result<CompiledEngineConfig>
    fun start(compiled: CompiledEngineConfig, runtimeContext: EngineRuntimeContext): Result<Unit>
    fun stop()
    fun restart(compiled: CompiledEngineConfig, runtimeContext: EngineRuntimeContext): Result<Unit> {
        stop()
        return start(compiled, runtimeContext)
    }

    fun isHealthy(): Boolean
    fun statistics(): EngineStatistics
    fun testConnection(profileId: String): Result<EngineConnectionTest>
    fun maskSecrets(message: String): String
    fun cleanup()
}

