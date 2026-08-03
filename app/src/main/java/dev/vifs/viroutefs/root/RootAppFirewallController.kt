// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

data class RootAppFirewallResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
    val appliedPackageCount: Int = 0,
    val appliedUidCount: Int = 0,
    val skippedPackages: Set<String> = emptySet(),
)

internal data class RootFirewallUidRule(
    val uid: Int,
    val blockAll: Boolean,
    val blockWifi: Boolean,
    val blockCellular: Boolean,
    val blockVpn: Boolean,
)

class RootAppFirewallController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()
    private val runtimeState = RootRuntimeStateRepository(appContext)
    private val configRepository = RootAppFirewallConfigRepository(appContext)
    private val recovery = RootNetworkRecoveryController(appContext)

    fun loadConfig(): RootAppFirewallConfig = configRepository.load()

    fun apply(config: RootAppFirewallConfig): RootAppFirewallResult {
        val normalized = config.normalized()
        if (normalized.isEmpty) {
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "Не выбрано ни одного ограничения. Для полного выключения используйте отдельную кнопку остановки.",
            )
        }
        val probe = access.requestAndProbe()
        val capabilities = probe.snapshot
        if (!probe.granted || capabilities == null) {
            return RootAppFirewallResult(false, false, probe.message)
        }
        if (!capabilities.hasIptables || !capabilities.hasIp6tables) {
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "Нужны одновременно iptables и ip6tables. Файрвол не применён, чтобы IPv6 не остался без защиты.",
            )
        }
        val resolved = resolveRules(normalized)
        if (resolved.rules.isEmpty()) {
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "Выбранные пакеты не найдены или используют защищённые системные UID. Правила не менялись.",
                skippedPackages = resolved.skipped,
            )
        }
        if (resolved.rules.size > ROOT_FIREWALL_MAX_UIDS) {
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "За один безопасный набор можно ограничить не больше $ROOT_FIREWALL_MAX_UIDS UID. Сейчас выбрано ${resolved.rules.size}; уменьшите список.",
                skippedPackages = resolved.skipped,
            )
        }
        val marked = runCatching {
            runtimeState.markPending(RootManagedModule.AppFirewall, "iptables_owner")
        }.isSuccess
        if (!marked) {
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "Не удалось сохранить состояние аварийного восстановления. Сетевые правила не менялись.",
            )
        }
        val script = rootAppFirewallStartScript(
            rules = resolved.rules,
            cleanup = appFirewallCleanupScript(runtimeState.pidFile().absolutePath),
        )
        val execution = runCatching { executor.execute(script, ROOT_FIREWALL_APPLY_TIMEOUT_MILLIS) }.getOrNull()
        if (execution == null || !execution.completed || execution.exitCode != 0) {
            val recovered = recovery.recoverAppFirewall()
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = if (recovered.successful) {
                    "Ядро не приняло полный набор owner/interface-правил; изменения автоматически отменены."
                } else {
                    "Применение прервано, а автоматический откат не подтверждён. Используйте аварийную очистку в root-центре."
                },
                skippedPackages = resolved.skipped,
            )
        }
        val saved = runCatching { configRepository.save(normalized) }.isSuccess
        if (!saved) {
            recovery.recoverAppFirewall()
            return RootAppFirewallResult(
                successful = false,
                running = false,
                message = "Правила были созданы, но их конфигурация не сохранилась; файрвол автоматически остановлен.",
                skippedPackages = resolved.skipped,
            )
        }
        return RootAppFirewallResult(
            successful = true,
            running = true,
            message = buildString {
                append("Root-файрвол применён для ${resolved.appliedPackages.size} приложений (${resolved.rules.size} UID). ")
                append("Правила действуют и для IPv4, и для IPv6.")
                if (resolved.sharedUidCount > 0) {
                    append(" Объединено общих UID: ${resolved.sharedUidCount}; ограничения такого UID затрагивают все разделяющие его приложения.")
                }
                if (resolved.skipped.isNotEmpty()) append(" Пропущено пакетов: ${resolved.skipped.size}.")
            },
            appliedPackageCount = resolved.appliedPackages.size,
            appliedUidCount = resolved.rules.size,
            skippedPackages = resolved.skipped,
        )
    }

    fun stop(): RootAppFirewallResult {
        val result = recovery.recoverAppFirewall()
        return RootAppFirewallResult(
            successful = result.successful,
            running = !result.successful,
            message = result.message,
        )
    }

    @Suppress("DEPRECATION")
    private fun resolveRules(config: RootAppFirewallConfig): ResolvedFirewallRules {
        val byUid = linkedMapOf<Int, MutableUidRule>()
        val applied = linkedSetOf<String>()
        val skipped = linkedSetOf<String>()
        config.allPackages.sorted().forEach { packageName ->
            val info = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    appContext.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    appContext.packageManager.getApplicationInfo(packageName, 0)
                }
            }.getOrNull()
            val uid = info?.uid
            if (uid == null || uid < ROOT_FIREWALL_MIN_APP_UID || uid == appContext.applicationInfo.uid) {
                skipped += packageName
                return@forEach
            }
            val rule = byUid.getOrPut(uid) { MutableUidRule(uid) }
            rule.blockAll = rule.blockAll || packageName in config.blockAllPackages
            rule.blockWifi = rule.blockWifi || packageName in config.blockWifiPackages
            rule.blockCellular = rule.blockCellular || packageName in config.blockCellularPackages
            rule.blockVpn = rule.blockVpn || packageName in config.blockVpnPackages
            applied += packageName
        }
        val sharedUidCount = byUid.keys.count { uid ->
            appContext.packageManager.getPackagesForUid(uid).orEmpty().size > 1
        }
        return ResolvedFirewallRules(
            rules = byUid.values.map(MutableUidRule::freeze),
            appliedPackages = applied,
            skipped = skipped,
            sharedUidCount = sharedUidCount,
        )
    }
}

private data class MutableUidRule(
    val uid: Int,
    var blockAll: Boolean = false,
    var blockWifi: Boolean = false,
    var blockCellular: Boolean = false,
    var blockVpn: Boolean = false,
) {
    fun freeze(): RootFirewallUidRule = RootFirewallUidRule(
        uid = uid,
        blockAll = blockAll,
        blockWifi = !blockAll && blockWifi,
        blockCellular = !blockAll && blockCellular,
        blockVpn = !blockAll && blockVpn,
    )
}

private data class ResolvedFirewallRules(
    val rules: List<RootFirewallUidRule>,
    val appliedPackages: Set<String>,
    val skipped: Set<String>,
    val sharedUidCount: Int,
)

internal fun rootAppFirewallStartScript(
    rules: List<RootFirewallUidRule>,
    cleanup: String,
): String {
    require(rules.isNotEmpty()) { "Root firewall requires at least one UID rule." }
    require(rules.size <= ROOT_FIREWALL_MAX_UIDS) { "Too many root firewall UID rules." }
    require(rules.all { it.uid >= ROOT_FIREWALL_MIN_APP_UID }) { "Unsafe root firewall UID." }
    val ruleLines = buildString {
        rules.sortedBy(RootFirewallUidRule::uid).forEach { rule ->
            if (rule.blockAll) appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_FW_OUT -m owner --uid-owner ${rule.uid} -j REJECT")
            if (rule.blockWifi) appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_FW_WIFI -m owner --uid-owner ${rule.uid} -j REJECT")
            if (rule.blockCellular) appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_FW_CELL -m owner --uid-owner ${rule.uid} -j REJECT")
            if (rule.blockVpn) appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_FW_VPN -m owner --uid-owner ${rule.uid} -j REJECT")
        }
    }.trimEnd()
    return """
        $cleanup
        set -eu
        committed=0
        rollback_firewall() {
          if [ "${'$'}committed" != 1 ]; then
            $cleanup
          fi
        }
        trap rollback_firewall EXIT
        trap 'rollback_firewall; exit 1' HUP INT TERM
        for tool in iptables ip6tables; do
          "${'$'}tool" -t filter -N VIROUTEFS_FW_OUT
          "${'$'}tool" -t filter -N VIROUTEFS_FW_WIFI
          "${'$'}tool" -t filter -N VIROUTEFS_FW_CELL
          "${'$'}tool" -t filter -N VIROUTEFS_FW_VPN
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'tun+' -j VIROUTEFS_FW_VPN
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'wg+' -j VIROUTEFS_FW_VPN
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'wlan+' -j VIROUTEFS_FW_WIFI
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'swlan+' -j VIROUTEFS_FW_WIFI
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'rmnet+' -j VIROUTEFS_FW_CELL
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'ccmni+' -j VIROUTEFS_FW_CELL
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'pdp+' -j VIROUTEFS_FW_CELL
          "${'$'}tool" -t filter -A VIROUTEFS_FW_OUT -o 'wwan+' -j VIROUTEFS_FW_CELL
          $ruleLines
          "${'$'}tool" -t filter -I OUTPUT 1 -j VIROUTEFS_FW_OUT
          "${'$'}tool" -t filter -C OUTPUT -j VIROUTEFS_FW_OUT
        done
        committed=1
        trap - EXIT HUP INT TERM
        printf 'viroutefs_app_firewall=running\n'
    """.trimIndent()
}

internal const val ROOT_FIREWALL_MAX_UIDS = 64
private const val ROOT_FIREWALL_MIN_APP_UID = 10_000
private const val ROOT_FIREWALL_APPLY_TIMEOUT_MILLIS = 30_000L
