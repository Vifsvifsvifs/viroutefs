// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.ProfileGroupMode
import io.nekohasekai.libbox.CommandClient
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

internal data class RuntimeGroupMember(
    val profileId: String,
    val profileName: String,
    val outboundTag: String,
)

internal data class ManagedProfileGroup(
    val groupId: String,
    val groupName: String,
    val groupTag: String,
    val healthGroupTag: String,
    val mode: ProfileGroupMode,
    val members: List<RuntimeGroupMember>,
    val testIntervalSeconds: Int,
)

internal enum class ProfileGroupRuntimeReason {
    InitialSelection,
    PrimaryRecovered,
    Failover,
    RoundRobin,
    AvailabilityRecovered,
    AllUnavailable,
}

internal data class ProfileGroupRuntimeAction(
    val groupId: String,
    val groupName: String,
    val groupTag: String,
    val selectedProfileId: String?,
    val selectedProfileName: String?,
    val selectedOutboundTag: String?,
    val reason: ProfileGroupRuntimeReason,
    val message: String,
)

/**
 * Deterministic policy kept separate from libbox so failover decisions can be
 * tested without Android or a running VPN.
 */
internal class ProfileGroupRuntimePolicy(
    groups: List<ManagedProfileGroup>,
) {
    private val groupsByTag = groups.associateBy { it.groupTag }
    private val selectedByGroup = mutableMapOf<String, String>()
    private val availableByGroup = mutableMapOf<String, List<String>>()
    private val receivedHealthByGroup = mutableSetOf<String>()

    fun initialActions(): List<ProfileGroupRuntimeAction> = groupsByTag.values.mapNotNull { group ->
        val first = group.members.firstOrNull() ?: return@mapNotNull null
        selectedByGroup[group.groupTag] = first.outboundTag
        action(
            group = group,
            member = first,
            reason = ProfileGroupRuntimeReason.InitialSelection,
            message = "Группа «${group.groupName}»: выбран первый явный участник «${first.profileName}»; скрытого перехода на System нет.",
        )
    }

    fun updateHealth(
        groupTag: String,
        delayByOutboundTag: Map<String, Int>,
    ): List<ProfileGroupRuntimeAction> {
        val group = groupsByTag[groupTag] ?: return emptyList()
        val previousAvailable = availableByGroup[groupTag].orEmpty()
        val available = group.members
            .filter { (delayByOutboundTag[it.outboundTag] ?: 0) > 0 }
            .map { it.outboundTag }
        availableByGroup[groupTag] = available
        val hadHealth = groupTag in receivedHealthByGroup
        receivedHealthByGroup += groupTag

        if (available.isEmpty()) {
            if (!hadHealth || previousAvailable.isNotEmpty()) {
                return listOf(
                    action(
                        group = group,
                        member = null,
                        reason = ProfileGroupRuntimeReason.AllUnavailable,
                        message = "Группа «${group.groupName}»: проверка не нашла доступных участников. Текущий маршрут не заменён на System.",
                    ),
                )
            }
            return emptyList()
        }

        val currentTag = selectedByGroup[groupTag]
        return when (group.mode) {
            ProfileGroupMode.Failover -> {
                val targetTag = available.first()
                val target = group.member(targetTag)
                if (targetTag != currentTag) {
                    selectedByGroup[groupTag] = targetTag
                    val primaryTag = group.members.firstOrNull()?.outboundTag
                    listOf(
                        action(
                            group = group,
                            member = target,
                            reason = if (targetTag == primaryTag) {
                                ProfileGroupRuntimeReason.PrimaryRecovered
                            } else {
                                ProfileGroupRuntimeReason.Failover
                            },
                            message = if (targetTag == primaryTag) {
                                "Группа «${group.groupName}»: основной маршрут «${target.profileName}» снова доступен и выбран."
                            } else {
                                "Группа «${group.groupName}»: выбран резерв «${target.profileName}», потому что более приоритетные участники недоступны."
                            },
                        ),
                    )
                } else if (hadHealth && previousAvailable.isEmpty()) {
                    listOf(
                        action(
                            group = group,
                            member = target,
                            reason = ProfileGroupRuntimeReason.AvailabilityRecovered,
                            message = "Группа «${group.groupName}»: маршрут «${target.profileName}» снова доступен.",
                            switchOutbound = false,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            ProfileGroupMode.RoundRobin -> {
                if (currentTag !in available) {
                    val target = group.member(available.first())
                    selectedByGroup[groupTag] = target.outboundTag
                    listOf(
                        action(
                            group = group,
                            member = target,
                            reason = ProfileGroupRuntimeReason.AvailabilityRecovered,
                            message = "Группа «${group.groupName}»: активен доступный участник «${target.profileName}».",
                        ),
                    )
                } else if (hadHealth && previousAvailable.isEmpty()) {
                    val current = group.member(requireNotNull(currentTag))
                    listOf(
                        action(
                            group = group,
                            member = current,
                            reason = ProfileGroupRuntimeReason.AvailabilityRecovered,
                            message = "Группа «${group.groupName}»: доступность участников восстановлена.",
                            switchOutbound = false,
                        ),
                    )
                } else {
                    emptyList()
                }
            }
            ProfileGroupMode.Manual,
            ProfileGroupMode.Latency -> emptyList()
        }
    }

    fun onNewConnection(chain: List<String>): ProfileGroupRuntimeAction? {
        val group = chain.firstNotNullOfOrNull { groupsByTag[it] }
            ?.takeIf { it.mode == ProfileGroupMode.RoundRobin }
            ?: return null
        val available = availableByGroup[group.groupTag].orEmpty()
        if (available.size < 2) return null
        val current = selectedByGroup[group.groupTag]
        val currentIndex = available.indexOf(current).takeIf { it >= 0 } ?: 0
        val target = group.member(available[(currentIndex + 1) % available.size])
        if (target.outboundTag == current) return null
        selectedByGroup[group.groupTag] = target.outboundTag
        return action(
            group = group,
            member = target,
            reason = ProfileGroupRuntimeReason.RoundRobin,
            message = "Группа «${group.groupName}»: следующее новое соединение направлено к «${target.profileName}» по кругу.",
        )
    }

    private fun ManagedProfileGroup.member(tag: String): RuntimeGroupMember =
        members.first { it.outboundTag == tag }

    private fun action(
        group: ManagedProfileGroup,
        member: RuntimeGroupMember?,
        reason: ProfileGroupRuntimeReason,
        message: String,
        switchOutbound: Boolean = true,
    ) = ProfileGroupRuntimeAction(
        groupId = group.groupId,
        groupName = group.groupName,
        groupTag = group.groupTag,
        selectedProfileId = member?.profileId,
        selectedProfileName = member?.profileName,
        selectedOutboundTag = member?.outboundTag.takeIf { switchOutbound },
        reason = reason,
        message = message,
    )
}

/**
 * Connects the tested policy to the local-only libbox command socket.
 * Health requests and selector changes never leave the app except for the
 * explicit HTTPS probe configured by the user.
 */
internal class ProfileGroupRuntimeController(
    private val client: CommandClient,
    groups: List<ManagedProfileGroup>,
    private val onAction: (ProfileGroupRuntimeAction) -> Unit,
    private val onLog: (String) -> Unit,
) {
    private val managedGroups = groups.filter {
        it.mode == ProfileGroupMode.Failover || it.mode == ProfileGroupMode.RoundRobin
    }
    private val policy = ProfileGroupRuntimePolicy(managedGroups)
    private val stateLock = Any()
    private val latestHealth = mutableMapOf<String, Map<String, Int>>()
    private val scheduler: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { task ->
            Thread(task, "ViRouteFS-GroupHealth").apply { isDaemon = true }
        }

    fun start() {
        synchronized(stateLock) { policy.initialActions() }.forEach(::apply)
        managedGroups.forEach { group ->
            scheduler.scheduleWithFixedDelay(
                { runHealthCheck(group) },
                0L,
                group.testIntervalSeconds.toLong(),
                TimeUnit.SECONDS,
            )
        }
    }

    fun stop() {
        scheduler.shutdownNow()
    }

    fun updateOutbounds(delays: Map<String, Int>) {
        managedGroups.forEach { group ->
            val groupDelays = group.members.associate { member ->
                member.outboundTag to (delays[member.outboundTag] ?: 0)
            }
            synchronized(stateLock) {
                latestHealth[group.groupTag] = groupDelays
            }
        }
    }

    fun onNewConnection(chain: List<String>) {
        synchronized(stateLock) { policy.onNewConnection(chain) }?.let(::apply)
    }

    private fun runHealthCheck(group: ManagedProfileGroup) {
        runCatching { client.urlTest(group.healthGroupTag) }
            .onSuccess {
                scheduler.schedule(
                    {
                        val actions = synchronized(stateLock) {
                            latestHealth[group.groupTag]?.let { delays ->
                                policy.updateHealth(group.groupTag, delays)
                            }.orEmpty()
                        }
                        actions.forEach(::apply)
                    },
                    HEALTH_RESULT_SETTLE_SECONDS,
                    TimeUnit.SECONDS,
                )
            }
            .onFailure { error ->
                onLog(
                    "Группа «${group.groupName}»: проверка доступности не завершилась: " +
                        (error.message ?: error::class.java.simpleName).take(240),
                )
            }
    }

    private fun apply(action: ProfileGroupRuntimeAction) {
        val targetTag = action.selectedOutboundTag
        if (targetTag != null) {
            runCatching { client.selectOutbound(action.groupTag, targetTag) }
                .onFailure { error ->
                    onLog(
                        "Группа «${action.groupName}»: не удалось переключить маршрут: " +
                            (error.message ?: error::class.java.simpleName).take(240),
                    )
                    return
                }
        }
        onAction(action)
        onLog(action.message)
    }

    private companion object {
        const val HEALTH_RESULT_SETTLE_SECONDS = 12L
    }
}
