// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.TunnelType

internal data class EnginePlan(
    val configs: List<CompiledEngineConfig>,
    val routedProfileIds: Set<String>,
    val blockedProfileIds: Set<String>,
    val warnings: List<String>,
)

internal data class EngineOrchestratorSnapshot(
    val states: Map<String, EngineState>,
    val errors: Map<String, EngineError>,
)

/**
 * Starts only engines referenced by the active routing/DNS configuration.
 *
 * Dependencies are started first. A failure tears down the partial generation
 * in reverse order so the caller can keep the VPN fail-closed.
 */
internal class EngineOrchestrator(
    adapters: List<EngineAdapter>,
) {
    private val adapterById = adapters.associateBy(EngineAdapter::id)
    private val runtimeContext = EngineRuntimeContext()
    private var startedAdapters: List<EngineAdapter> = emptyList()

    init {
        require(adapterById.size == adapters.size) { "Engine adapter ids must be unique." }
        adapters.forEach { adapter ->
            val missing = adapter.dependencies - adapterById.keys
            require(missing.isEmpty()) {
                "Engine adapter ${adapter.id} has missing dependencies: ${missing.joinToString()}."
            }
        }
    }

    fun prepare(config: RoutingConfig): Result<EnginePlan> = runCatching {
        val routedProfileIds = config.routedProfileIds()
        val selectedProfiles = config.profiles.filter { it.id in routedProfileIds }
        val blockedProfileIds = linkedSetOf<String>()
        val warnings = mutableListOf<String>()
        (routedProfileIds - selectedProfiles.map { it.id }.toSet()).forEach { profileId ->
            blockedProfileIds += profileId
            warnings += "Маршрут ссылается на отсутствующий профиль $profileId и будет заблокирован."
        }
        selectedProfiles.filterNot { it.enabled }.forEach { profile ->
            blockedProfileIds += profile.id
            warnings += "Профиль «${profile.name}» выключен; связанные маршруты будут заблокированы."
        }
        val routedProfiles = selectedProfiles.filter { profile ->
            if (!profile.enabled) return@filter false
            val descriptor = EngineCatalog.descriptor(profile.type)
            val canStart = profile.type in setOf(TunnelType.Direct, TunnelType.Block) ||
                descriptor?.canStartRuntime == true
            if (!canStart) {
                blockedProfileIds += profile.id
                warnings += "Профиль «${profile.name}» имеет статус ${descriptor?.readiness?.name ?: "Unavailable"}; связанные маршруты будут заблокированы."
            }
            canStart
        }
        val required = adapterById.values.filter { adapter ->
            adapter.alwaysRequired ||
                routedProfiles.any { it.type in adapter.supportedProtocols }
        }
        val ordered = topologicalOrder(required)
        val compileErrors = mutableListOf<EngineError>()
        routedProfiles.forEach { profile ->
            val adapter = ordered.firstOrNull { profile.type in it.supportedProtocols }
            if (adapter == null && profile.type !in setOf(TunnelType.Direct, TunnelType.Block)) {
                blockedProfileIds += profile.id
                warnings += "Для профиля «${profile.name}» нет EngineAdapter; связанные маршруты будут заблокированы."
            } else if (profile.type !in setOf(TunnelType.Direct, TunnelType.Block)) {
                // System and Block are built-in routing targets, not external
                // connection profiles. Sending a migrated built-in profile to
                // protocol validation can incorrectly prevent the VPN router
                // from starting when no VPN profile is configured.
                adapter?.validateProfile(profile)?.let(compileErrors::addAll)
            }
        }
        check(compileErrors.isEmpty()) {
            compileErrors.joinToString(" ") { it.summary }
        }

        val configs = ordered.map { adapter ->
            val profileIds = routedProfiles
                .filter { it.type in adapter.supportedProtocols }
                .mapTo(linkedSetOf()) { it.id }
            adapter.compile(config, profileIds).getOrElse { error ->
                throw EngineOrchestratorException(
                    EngineError(
                        adapterId = adapter.id,
                        stage = EngineErrorStage.Compilation,
                        summary = "Не удалось подготовить движок ${adapter.backend.label}.",
                        technicalDetails = error.message ?: error::class.java.simpleName,
                        recommendedAction = "Проверьте отмеченные профили и повторите запуск.",
                        cause = error,
                    ),
                )
            }
        }
        EnginePlan(
            configs = configs,
            routedProfileIds = routedProfileIds,
            blockedProfileIds = blockedProfileIds,
            warnings = warnings,
        )
    }

    fun start(plan: EnginePlan): Result<EngineOrchestratorSnapshot> = runCatching {
        stop()
        val started = mutableListOf<EngineAdapter>()
        try {
            plan.configs.forEach { compiled ->
                val adapter = requireNotNull(adapterById[compiled.adapterId])
                adapter.start(compiled, runtimeContext).getOrElse { error ->
                    throw EngineOrchestratorException(
                        adapter.lastError ?: EngineError(
                            adapterId = adapter.id,
                            stage = EngineErrorStage.Start,
                            summary = "Движок ${adapter.backend.label} не запустился.",
                            technicalDetails = error.message ?: error::class.java.simpleName,
                            recommendedAction = "Исправьте профиль или остановите связанные маршруты.",
                            cause = error,
                        ),
                    )
                }
                check(adapter.isHealthy() && adapter.state == EngineState.Connected) {
                    "Engine ${adapter.id} did not confirm Connected."
                }
                started += adapter
            }
            startedAdapters = started.toList()
            snapshot()
        } catch (error: Throwable) {
            started.asReversed().forEach { adapter ->
                runCatching { adapter.stop() }
                runCatching { adapter.cleanup() }
                runtimeContext.clearEndpoint(adapter.id)
            }
            startedAdapters = emptyList()
            runtimeContext.clear()
            throw error
        }
    }

    fun stop() {
        startedAdapters.asReversed().forEach { adapter ->
            runCatching { adapter.stop() }
            runCatching { adapter.cleanup() }
            runtimeContext.clearEndpoint(adapter.id)
        }
        startedAdapters = emptyList()
        runtimeContext.clear()
    }

    fun isHealthy(): Boolean =
        startedAdapters.isNotEmpty() && startedAdapters.all(EngineAdapter::isHealthy)

    fun snapshot(): EngineOrchestratorSnapshot = EngineOrchestratorSnapshot(
        states = adapterById.mapValues { (_, adapter) -> adapter.state },
        errors = adapterById.mapNotNull { (id, adapter) ->
            adapter.lastError?.let { id to it }
        }.toMap(),
    )

    private fun topologicalOrder(required: List<EngineAdapter>): List<EngineAdapter> {
        val requiredIds = required.mapTo(linkedSetOf(), EngineAdapter::id)
        required.forEach { requiredIds += transitiveDependencies(it.id) }
        val result = mutableListOf<EngineAdapter>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()

        fun visit(id: String) {
            if (id in visited) return
            check(visiting.add(id)) { "Engine dependency cycle includes $id." }
            val adapter = requireNotNull(adapterById[id])
            adapter.dependencies.sorted().forEach(::visit)
            visiting.remove(id)
            visited += id
            if (id in requiredIds) result += adapter
        }
        requiredIds.sorted().forEach(::visit)
        return result
    }

    private fun transitiveDependencies(id: String): Set<String> {
        val result = linkedSetOf<String>()
        fun collect(current: String) {
            adapterById.getValue(current).dependencies.forEach { dependency ->
                if (result.add(dependency)) collect(dependency)
            }
        }
        collect(id)
        return result
    }
}

internal class EngineOrchestratorException(
    val engineError: EngineError,
) : IllegalStateException(engineError.summary, engineError.cause)

internal fun RoutingConfig.routedProfileIds(): Set<String> = buildSet {
    add(defaultProfileId ?: RoutingConfigDefaults.SYSTEM_PROFILE_ID)
    rules.filter { it.enabled }.forEach { rule ->
        add(rule.targetProfileId)
        rule.dnsPolicyId
            ?.let { policyId -> dnsPolicies.firstOrNull { it.id == policyId } }
            ?.resolveThroughProfileId
            ?.let(::add)
    }
    profiles.filter { it.enabled && it.dnsPolicyId != null }.forEach { profile ->
        dnsPolicies.firstOrNull { it.id == profile.dnsPolicyId }
            ?.resolveThroughProfileId
            ?.let(::add)
    }
}
