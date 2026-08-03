// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context

data class RootNetworkRecoveryResult(
    val successful: Boolean,
    val message: String,
)

class RootNetworkRecoveryController(context: Context) {
    private val repository = RootRuntimeStateRepository(context.applicationContext)
    private val executor = RootCommandExecutor()

    fun currentState(): RootRuntimeState? = repository.load()

    fun recoverConnectionAdaptation(
        timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS,
    ): RootNetworkRecoveryResult {
        val result = executor.execute(
            connectionAdaptationCleanupScript(
                pidFile = repository.pidFile().absolutePath,
                logFile = repository.logFile().absolutePath,
            ),
            timeoutMillis,
        )
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.removeModule(RootManagedModule.ConnectionAdaptation)
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Адаптация соединений остановлена, её процесс и NFQUEUE-цепочки удалены. Другие root-модули не изменялись.",
        )
    }

    fun recoverAll(timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS): RootNetworkRecoveryResult {
        val script = rootCleanupScript(
            pidFile = repository.pidFile().absolutePath,
            logFile = repository.logFile().absolutePath,
        )
        val result = executor.execute(script, timeoutMillis)
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.clear()
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Собственные root-процессы и цепочки ViRouteFS удалены. Чужие правила файрвола не изменялись.",
        )
    }

    private fun recoveryFailure(result: RootCommandResult): RootNetworkRecoveryResult? {
        if (!result.suCommandVisible) {
            return RootNetworkRecoveryResult(
                successful = false,
                message = "Команда su недоступна. Разрешите ViRouteFS в root-менеджере, затем повторите восстановление.",
            )
        }
        if (!result.completed || result.exitCode != 0) {
            return RootNetworkRecoveryResult(
                successful = false,
                message = "Не удалось подтвердить полное удаление правил ViRouteFS. Не перезагружайте постоянный root-модуль; повторите восстановление после проверки root.",
            )
        }
        return null
    }
}

internal fun rootCleanupScript(pidFile: String, logFile: String): String {
    val log = shellQuote(logFile)
    return """
        ${rootCleanupFunctions(pidFile)}
        stop_managed_process
        if command -v iptables >/dev/null 2>&1; then
          remove_hook iptables mangle OUTPUT VIROUTEFS_Z2_OUT
          remove_hook iptables filter OUTPUT VIROUTEFS_FW_OUT
          remove_hook iptables filter OUTPUT VIROUTEFS_LOCK_OUT
          remove_hook iptables filter FORWARD VIROUTEFS_TETHER_FWD
          remove_hook iptables nat POSTROUTING VIROUTEFS_TETHER_NAT
        fi
        if command -v ip6tables >/dev/null 2>&1; then
          remove_hook ip6tables mangle OUTPUT VIROUTEFS_Z2_OUT
          remove_hook ip6tables filter OUTPUT VIROUTEFS_FW_OUT
          remove_hook ip6tables filter OUTPUT VIROUTEFS_LOCK_OUT
          remove_hook ip6tables filter FORWARD VIROUTEFS_TETHER_FWD
        fi
        if command -v nft >/dev/null 2>&1; then
          nft delete table inet viroutefs >/dev/null 2>&1 || true
        fi
        rm -f $log
        printf 'viroutefs_root_cleanup=ok\n'
    """.trimIndent()
}

internal fun connectionAdaptationCleanupScript(pidFile: String, logFile: String): String {
    val log = shellQuote(logFile)
    return """
        ${rootCleanupFunctions(pidFile)}
        stop_managed_process
        if command -v iptables >/dev/null 2>&1; then
          remove_hook iptables mangle OUTPUT VIROUTEFS_Z2_OUT
        fi
        if command -v ip6tables >/dev/null 2>&1; then
          remove_hook ip6tables mangle OUTPUT VIROUTEFS_Z2_OUT
        fi
        rm -f $log
        printf 'viroutefs_connection_adaptation_cleanup=ok\n'
    """.trimIndent()
}

private fun rootCleanupFunctions(pidFile: String): String {
    val pid = shellQuote(pidFile)
    return """
        stop_managed_process() {
          if [ -r $pid ]; then
            managed_pid="${'$'}(tr -cd '0-9' < $pid | head -c 12)"
            if [ -n "${'$'}managed_pid" ] && [ -r "/proc/${'$'}managed_pid/cmdline" ]; then
              managed_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}managed_pid/cmdline" 2>/dev/null)"
              case "${'$'}managed_cmd" in
                *libzapret2.so*) kill "${'$'}managed_pid" 2>/dev/null || true ;;
              esac
            fi
            rm -f $pid
          fi
        }
        remove_hook() {
          tool="${'$'}1"; table="${'$'}2"; parent="${'$'}3"; child="${'$'}4"
          while "${'$'}tool" -t "${'$'}table" -C "${'$'}parent" -j "${'$'}child" >/dev/null 2>&1; do
            "${'$'}tool" -t "${'$'}table" -D "${'$'}parent" -j "${'$'}child" >/dev/null 2>&1 || break
          done
          "${'$'}tool" -t "${'$'}table" -F "${'$'}child" >/dev/null 2>&1 || true
          "${'$'}tool" -t "${'$'}table" -X "${'$'}child" >/dev/null 2>&1 || true
        }
    """.trimIndent()
}

private const val ROOT_RECOVERY_TIMEOUT_MILLIS = 20_000L
