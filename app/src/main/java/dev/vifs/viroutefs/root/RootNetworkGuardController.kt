// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

data class RootNetworkGuardConfig(
    val vpnLock: Boolean = false,
    val blockDirectDns: Boolean = false,
    val blockDirectIpv6: Boolean = false,
) {
    val isEmpty: Boolean
        get() = !vpnLock && !blockDirectDns && !blockDirectIpv6
}

data class RootNetworkGuardResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

class RootNetworkGuardController(context: Context) {
    private val appContext = context.applicationContext
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()
    private val state = RootRuntimeStateRepository(appContext)
    private val recovery = RootNetworkRecoveryController(appContext)
    private val configRepository = RootNetworkGuardConfigRepository(appContext)

    fun loadConfig(): RootNetworkGuardConfig = configRepository.load()

    fun apply(config: RootNetworkGuardConfig): RootNetworkGuardResult {
        if (config.isEmpty) {
            return RootNetworkGuardResult(false, false, "Не выбрано ни одного защитного правила.")
        }
        val probe = access.requestAndProbe()
        val capabilities = probe.snapshot
        if (!probe.granted || capabilities == null) {
            return RootNetworkGuardResult(false, false, probe.message)
        }
        if (!capabilities.hasIptables || !capabilities.hasIp6tables) {
            return RootNetworkGuardResult(
                false,
                false,
                "Для защиты от утечек нужны одновременно iptables и ip6tables. Правила не менялись.",
            )
        }
        val modules = buildSet {
            if (config.vpnLock) add(RootManagedModule.EmergencyNetworkLock)
            if (config.blockDirectDns || config.blockDirectIpv6) add(RootManagedModule.LeakProtection)
        }
        val marked = runCatching {
            modules.forEach { module -> state.markPending(module, "iptables_owner") }
        }.isSuccess
        if (!marked) {
            return RootNetworkGuardResult(
                false,
                false,
                "Не удалось сохранить состояние аварийного восстановления. Сетевые правила не менялись.",
            )
        }
        val cleanup = networkGuardCleanupScript(state.pidFile().absolutePath)
        val script = rootNetworkGuardStartScript(
            config = config,
            appUid = appContext.applicationInfo.uid,
            cleanup = cleanup,
        )
        val execution = runCatching { executor.execute(script, ROOT_GUARD_APPLY_TIMEOUT_MILLIS) }.getOrNull()
        if (execution == null || !execution.completed || execution.exitCode != 0) {
            val recovered = recovery.recoverNetworkGuard()
            return RootNetworkGuardResult(
                false,
                !recovered.successful,
                if (recovered.successful) {
                    "Ядро не приняло полный набор защитных правил; изменения автоматически отменены."
                } else {
                    "Применение прервано, а откат не подтверждён. Используйте аварийную очистку root-центра или перезагрузите телефон."
                },
            )
        }
        if (runCatching { configRepository.save(config) }.isFailure) {
            recovery.recoverNetworkGuard()
            return RootNetworkGuardResult(
                false,
                false,
                "Правила применились, но конфигурация не сохранилась; защита автоматически остановлена.",
            )
        }
        return RootNetworkGuardResult(
            true,
            true,
            buildString {
                append("Ядерная защита включена для IPv4/IPv6.")
                if (config.vpnLock) append(" Прямой выход пользовательских UID вне tun/wg запрещён.")
                if (config.blockDirectDns) append(" Прямые DNS/DoT/DoQ порты 53 и 853 запрещены.")
                if (config.blockDirectIpv6) append(" Прямой IPv6 пользовательских UID запрещён.")
            },
        )
    }

    fun stop(): RootNetworkGuardResult {
        val result = recovery.recoverNetworkGuard()
        return RootNetworkGuardResult(result.successful, !result.successful, result.message)
    }
}

internal fun rootNetworkGuardStartScript(
    config: RootNetworkGuardConfig,
    appUid: Int,
    cleanup: String,
): String {
    require(!config.isEmpty) { "Network guard requires at least one rule." }
    require(appUid >= ROOT_GUARD_MIN_APP_UID) { "Unsafe ViRouteFS UID." }
    val commonRules = buildString {
        appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -o lo -j RETURN")
        appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -o 'tun+' -j RETURN")
        appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -o 'wg+' -j RETURN")
        appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -m owner --uid-owner $appUid -j RETURN")
    }.trimEnd()
    val ipv4Rules = buildString {
        if (config.vpnLock) {
            appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -m owner --uid-owner $ROOT_GUARD_USER_UID_RANGE -j REJECT")
        } else if (config.blockDirectDns) {
            appendDirectDnsRules()
        }
    }.trimEnd()
    val ipv6Rules = buildString {
        if (config.vpnLock || config.blockDirectIpv6) {
            appendLine("\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -m owner --uid-owner $ROOT_GUARD_USER_UID_RANGE -j REJECT")
        } else if (config.blockDirectDns) {
            appendDirectDnsRules()
        }
    }.trimEnd()
    return """
        $cleanup
        set -eu
        committed=0
        rollback_guard() {
          if [ "${'$'}committed" != 1 ]; then
            $cleanup
          fi
        }
        trap rollback_guard EXIT
        trap 'rollback_guard; exit 1' HUP INT TERM
        tool=iptables
        "${'$'}tool" -t filter -N VIROUTEFS_LOCK_OUT
        $commonRules
        $ipv4Rules
        "${'$'}tool" -t filter -I OUTPUT 1 -j VIROUTEFS_LOCK_OUT
        "${'$'}tool" -t filter -C OUTPUT -j VIROUTEFS_LOCK_OUT
        tool=ip6tables
        "${'$'}tool" -t filter -N VIROUTEFS_LOCK_OUT
        $commonRules
        $ipv6Rules
        "${'$'}tool" -t filter -I OUTPUT 1 -j VIROUTEFS_LOCK_OUT
        "${'$'}tool" -t filter -C OUTPUT -j VIROUTEFS_LOCK_OUT
        committed=1
        trap - EXIT HUP INT TERM
        printf 'viroutefs_network_guard=running\n'
    """.trimIndent()
}

private fun StringBuilder.appendDirectDnsRules() {
    listOf("udp" to 53, "tcp" to 53, "udp" to 853, "tcp" to 853).forEach { (protocol, port) ->
        appendLine(
            "\"${'$'}tool\" -t filter -A VIROUTEFS_LOCK_OUT -p $protocol --dport $port " +
                "-m owner --uid-owner $ROOT_GUARD_USER_UID_RANGE -j REJECT",
        )
    }
}

private class RootNetworkGuardConfigRepository(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, ROOT_GUARD_DIRECTORY)
    private val file = File(directory, ROOT_GUARD_FILE)

    fun load(): RootNetworkGuardConfig = runCatching {
        if (!file.isFile || file.length() !in 1..ROOT_GUARD_MAX_BYTES.toLong()) return@runCatching RootNetworkGuardConfig()
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.optInt("version") == ROOT_GUARD_VERSION)
        RootNetworkGuardConfig(
            vpnLock = root.optBoolean("vpnLock"),
            blockDirectDns = root.optBoolean("blockDirectDns"),
            blockDirectIpv6 = root.optBoolean("blockDirectIpv6"),
        )
    }.getOrElse { RootNetworkGuardConfig() }

    fun save(config: RootNetworkGuardConfig) {
        require(directory.exists() || directory.mkdirs()) { "Could not create root guard directory." }
        val bytes = JSONObject()
            .put("version", ROOT_GUARD_VERSION)
            .put("vpnLock", config.vpnLock)
            .put("blockDirectDns", config.blockDirectDns)
            .put("blockDirectIpv6", config.blockDirectIpv6)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
        val temporary = File(directory, "$ROOT_GUARD_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) error("Could not replace root guard configuration.")
        if (!temporary.renameTo(file)) error("Could not commit root guard configuration.")
    }
}

private const val ROOT_GUARD_USER_UID_RANGE = "10000-99999999"
private const val ROOT_GUARD_MIN_APP_UID = 10_000
private const val ROOT_GUARD_APPLY_TIMEOUT_MILLIS = 30_000L
private const val ROOT_GUARD_VERSION = 1
private const val ROOT_GUARD_MAX_BYTES = 16 * 1024
private const val ROOT_GUARD_DIRECTORY = "root-network-guard"
private const val ROOT_GUARD_FILE = "guard.json"
