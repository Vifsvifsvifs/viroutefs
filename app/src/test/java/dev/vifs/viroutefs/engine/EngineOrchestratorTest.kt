// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EngineOrchestratorTest {
    @Test
    fun startsDependenciesBeforeCoreAndStopsInReverseOrder() {
        val events = mutableListOf<String>()
        val dependency = FakeAdapter(
            id = "dependency",
            protocols = setOf(TunnelType.ByeDpi),
            events = events,
        )
        val core = FakeAdapter(
            id = "core",
            protocols = setOf(TunnelType.Direct, TunnelType.Block),
            dependencies = setOf(dependency.id),
            alwaysRequired = true,
            events = events,
        )
        val orchestrator = EngineOrchestrator(listOf(core, dependency))

        val plan = orchestrator.prepare(RoutingConfigDefaults.defaultConfig()).getOrThrow()
        orchestrator.start(plan).getOrThrow()
        orchestrator.stop()

        assertEquals(
            listOf(
                "compile:dependency",
                "compile:core",
                "start:dependency",
                "start:core",
                "stop:core",
                "cleanup:core",
                "stop:dependency",
                "cleanup:dependency",
            ),
            events,
        )
    }

    @Test
    fun failedStartTearsDownAlreadyStartedGeneration() {
        val events = mutableListOf<String>()
        val dependency = FakeAdapter(
            id = "dependency",
            protocols = setOf(TunnelType.ByeDpi),
            events = events,
        )
        val core = FakeAdapter(
            id = "core",
            protocols = setOf(TunnelType.Direct, TunnelType.Block),
            dependencies = setOf(dependency.id),
            alwaysRequired = true,
            failStart = true,
            events = events,
        )
        val orchestrator = EngineOrchestrator(listOf(core, dependency))

        val result = orchestrator.start(
            orchestrator.prepare(RoutingConfigDefaults.defaultConfig()).getOrThrow(),
        )

        assertTrue(result.isFailure)
        assertFalse(orchestrator.isHealthy())
        assertTrue("stop:dependency" in events)
        assertTrue("cleanup:dependency" in events)
    }

    @Test
    fun unavailableExplicitProtocolIsConvertedToBlockedProfile() {
        val events = mutableListOf<String>()
        val core = FakeAdapter(
            id = "core",
            protocols = setOf(TunnelType.Direct, TunnelType.Block),
            alwaysRequired = true,
            events = events,
        )
        val legacy = FakeAdapter(
            id = "legacy",
            protocols = setOf(TunnelType.L2tp),
            events = events,
        )
        val profile = TunnelProfile(
            id = "legacy-route",
            name = "Old L2TP",
            type = TunnelType.L2tp,
            description = "test",
        )
        val defaults = RoutingConfigDefaults.defaultConfig()
        val config = defaults.copy(
            profiles = defaults.profiles + profile,
            rules = defaults.rules + RouteRule(
                id = "legacy-rule",
                name = "Old subnet",
                type = RouteRuleType.CIDR,
                targetProfileId = profile.id,
                priority = 10,
                matchers = listOf("192.0.2.0/24"),
                reason = "test",
                technicalDetails = "test",
                recommendedAction = "test",
            ),
        )

        val result = EngineOrchestrator(listOf(core, legacy)).prepare(config)

        assertTrue(result.isSuccess)
        assertTrue(profile.id in result.getOrThrow().blockedProfileIds)
        assertEquals(listOf("compile:core"), events)
    }

    private class FakeAdapter(
        override val id: String,
        private val protocols: Set<TunnelType>,
        override val dependencies: Set<String> = emptySet(),
        override val alwaysRequired: Boolean = false,
        private val failStart: Boolean = false,
        private val events: MutableList<String>,
    ) : EngineAdapter {
        override val backend: EngineBackend = EngineBackend.BuiltIn
        override val supportedProtocols: Set<TunnelType> = protocols
        override val capabilities: EngineCapabilities = EngineCapabilities(
            supportsMultipleInstances = true,
            supportsTcp = true,
            supportsUdp = true,
            supportsIpv6 = true,
            supportsDnsThroughProfile = true,
        )
        override var state: EngineState = EngineState.Stopped
        override var lastError: EngineError? = null

        override fun validateProfile(profile: TunnelProfile): List<EngineError> = emptyList()

        override fun compile(
            config: RoutingConfig,
            profileIds: Set<String>,
        ): Result<CompiledEngineConfig> {
            events += "compile:$id"
            return Result.success(CompiledEngineConfig(id, profileIds, null))
        }

        override fun start(
            compiled: CompiledEngineConfig,
            runtimeContext: EngineRuntimeContext,
        ): Result<Unit> {
            events += "start:$id"
            if (failStart) {
                state = EngineState.Error
                return Result.failure(IllegalStateException("expected failure"))
            }
            state = EngineState.Connected
            return Result.success(Unit)
        }

        override fun stop() {
            events += "stop:$id"
            state = EngineState.Stopped
        }

        override fun isHealthy(): Boolean = state == EngineState.Connected

        override fun statistics(): EngineStatistics = EngineStatistics()

        override fun testConnection(profileId: String): Result<EngineConnectionTest> =
            Result.success(EngineConnectionTest(true, "ok"))

        override fun maskSecrets(message: String): String = message

        override fun cleanup() {
            events += "cleanup:$id"
        }
    }
}
