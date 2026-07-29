// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.ProfileGroupMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileGroupRuntimePolicyTest {
    @Test
    fun failoverUsesPriorityAndReturnsToRecoveredPrimary() {
        val plan = plan(ProfileGroupMode.Failover)
        val policy = ProfileGroupRuntimePolicy(listOf(plan))

        val initial = policy.initialActions().single()
        assertEquals("out-primary", initial.selectedOutboundTag)

        assertTrue(
            policy.updateHealth(
                plan.groupTag,
                mapOf("out-primary" to 45, "out-reserve" to 80),
            ).isEmpty(),
        )
        val failover = policy.updateHealth(
            plan.groupTag,
            mapOf("out-primary" to 0, "out-reserve" to 82),
        ).single()
        assertEquals(ProfileGroupRuntimeReason.Failover, failover.reason)
        assertEquals("reserve", failover.selectedProfileId)

        val recovered = policy.updateHealth(
            plan.groupTag,
            mapOf("out-primary" to 48, "out-reserve" to 83),
        ).single()
        assertEquals(ProfileGroupRuntimeReason.PrimaryRecovered, recovered.reason)
        assertEquals("primary", recovered.selectedProfileId)
    }

    @Test
    fun allUnavailableNeverInventsSystemFallbackAndIsNotRepeated() {
        val plan = plan(ProfileGroupMode.Failover)
        val policy = ProfileGroupRuntimePolicy(listOf(plan))
        policy.initialActions()

        val unavailable = policy.updateHealth(
            plan.groupTag,
            mapOf("out-primary" to 0, "out-reserve" to 0),
        ).single()

        assertEquals(ProfileGroupRuntimeReason.AllUnavailable, unavailable.reason)
        assertNull(unavailable.selectedOutboundTag)
        assertTrue(unavailable.message.contains("не заменён на System"))
        assertTrue(
            policy.updateHealth(
                plan.groupTag,
                mapOf("out-primary" to 0, "out-reserve" to 0),
            ).isEmpty(),
        )
    }

    @Test
    fun roundRobinRotatesOnlyAfterHealthCheckAndOnlyForItsChain() {
        val plan = plan(ProfileGroupMode.RoundRobin)
        val policy = ProfileGroupRuntimePolicy(listOf(plan))
        policy.initialActions()

        assertNull(policy.onNewConnection(listOf("out-primary", plan.groupTag)))
        policy.updateHealth(
            plan.groupTag,
            mapOf("out-primary" to 30, "out-reserve" to 60),
        )
        assertNull(policy.onNewConnection(listOf("out-primary", "another-group")))

        val second = policy.onNewConnection(listOf("out-primary", plan.groupTag))
        val first = policy.onNewConnection(listOf("out-reserve", plan.groupTag))

        assertEquals("reserve", second?.selectedProfileId)
        assertEquals("primary", first?.selectedProfileId)
        assertEquals(ProfileGroupRuntimeReason.RoundRobin, first?.reason)
    }

    @Test
    fun roundRobinSkipsUnavailableMembers() {
        val third = RuntimeGroupMember("third", "Third", "out-third")
        val plan = plan(ProfileGroupMode.RoundRobin).copy(
            members = plan(ProfileGroupMode.RoundRobin).members + third,
        )
        val policy = ProfileGroupRuntimePolicy(listOf(plan))
        policy.initialActions()
        policy.updateHealth(
            plan.groupTag,
            mapOf("out-primary" to 20, "out-reserve" to 0, "out-third" to 70),
        )

        assertEquals("third", policy.onNewConnection(listOf(plan.groupTag))?.selectedProfileId)
        assertEquals("primary", policy.onNewConnection(listOf(plan.groupTag))?.selectedProfileId)
    }

    private fun plan(mode: ProfileGroupMode) = ManagedProfileGroup(
        groupId = "office",
        groupName = "Office",
        groupTag = "group-office",
        healthGroupTag = "group-office-health",
        mode = mode,
        members = listOf(
            RuntimeGroupMember("primary", "Primary", "out-primary"),
            RuntimeGroupMember("reserve", "Reserve", "out-reserve"),
        ),
        testIntervalSeconds = 60,
    )
}
