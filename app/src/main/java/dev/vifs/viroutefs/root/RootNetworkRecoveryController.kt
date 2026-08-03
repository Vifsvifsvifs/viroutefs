// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import android.os.Process

data class RootNetworkRecoveryResult(
    val successful: Boolean,
    val message: String,
)

class RootNetworkRecoveryController(context: Context) {
    private val appContext = context.applicationContext
    private val repository = RootRuntimeStateRepository(appContext)
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

    fun recoverAppFirewall(
        timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS,
    ): RootNetworkRecoveryResult {
        val result = executor.execute(
            appFirewallCleanupScript(repository.pidFile().absolutePath),
            timeoutMillis,
        )
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.removeModule(RootManagedModule.AppFirewall)
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Root-файрвол приложений остановлен. Его цепочки IPv4/IPv6 удалены; другие root-модули не изменялись.",
        )
    }

    fun recoverNetworkGuard(
        timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS,
    ): RootNetworkRecoveryResult {
        val result = executor.execute(
            networkGuardCleanupScript(repository.pidFile().absolutePath),
            timeoutMillis,
        )
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.removeModule(RootManagedModule.EmergencyNetworkLock)
        repository.removeModule(RootManagedModule.LeakProtection)
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Ядерный запрет и правила защиты от утечек остановлены. Другие root-модули не изменялись.",
        )
    }

    fun recoverPacketCapture(
        timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS,
    ): RootNetworkRecoveryResult {
        val result = executor.execute(
            packetCaptureCleanupScript(
                pidFile = repository.packetCapturePidFile().absolutePath,
                logFile = repository.packetCaptureLogFile().absolutePath,
                captureFile = repository.packetCaptureFile().absolutePath,
                appUid = Process.myUid(),
            ),
            timeoutMillis,
        )
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.removeModule(RootManagedModule.PacketCapture)
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Локальная запись PCAP остановлена. Другие root-модули не изменялись.",
        )
    }

    fun recoverTethering(
        timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS,
    ): RootNetworkRecoveryResult {
        val result = executor.execute(
            rootTetheringCleanupScript(repository.tetheringStateFile().absolutePath),
            timeoutMillis,
        )
        val failure = recoveryFailure(result)
        if (failure != null) return failure
        repository.removeModule(RootManagedModule.Tethering)
        return RootNetworkRecoveryResult(
            successful = true,
            message = "Раздача через VPN остановлена: собственные NAT/forward-правила и маршрут удалены, прежнее значение IPv4 forwarding восстановлено.",
        )
    }

    fun recoverAll(timeoutMillis: Long = ROOT_RECOVERY_TIMEOUT_MILLIS): RootNetworkRecoveryResult {
        RootAutomationService.requestStop(appContext)
        if (!RootAutomationService.awaitStopped(ROOT_AUTOMATION_RECOVERY_WAIT_MILLIS)) {
            return RootNetworkRecoveryResult(
                successful = false,
                message = "Общая очистка отложена: root-автоматизация ещё завершает принадлежащий ей модуль. Повторите очистку через несколько секунд.",
            )
        }
        val kernelWireGuard = RootKernelWireGuardController(appContext)
        if (kernelWireGuard.isRunning()) {
            val stopped = kernelWireGuard.stop()
            if (!stopped.successful) {
                return RootNetworkRecoveryResult(
                    successful = false,
                    message = "Общая очистка остановлена, чтобы не потерять состояние отката системного WireGuard. ${stopped.message}",
                )
            }
        }
        val script = rootCleanupScript(
            pidFile = repository.pidFile().absolutePath,
            logFile = repository.logFile().absolutePath,
            packetCapturePidFile = repository.packetCapturePidFile().absolutePath,
            packetCaptureLogFile = repository.packetCaptureLogFile().absolutePath,
            packetCaptureFile = repository.packetCaptureFile().absolutePath,
            appUid = Process.myUid(),
            tetheringStateFile = repository.tetheringStateFile().absolutePath,
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

internal fun rootCleanupScript(
    pidFile: String,
    logFile: String,
    packetCapturePidFile: String? = null,
    packetCaptureLogFile: String? = null,
    packetCaptureFile: String? = null,
    appUid: Int? = null,
    tetheringStateFile: String? = null,
): String {
    val log = shellQuote(logFile)
    val packetCaptureCleanup = if (
        packetCapturePidFile != null && packetCaptureLogFile != null &&
        packetCaptureFile != null && appUid != null
    ) {
        packetCaptureCleanupScript(packetCapturePidFile, packetCaptureLogFile, packetCaptureFile, appUid)
    } else {
        ""
    }
    val tetheringCleanup = tetheringStateFile?.let(::rootTetheringCleanupScript).orEmpty()
    return """
        ${rootCleanupFunctions(pidFile)}
        stop_managed_process
        $packetCaptureCleanup
        $tetheringCleanup
        if command -v iptables >/dev/null 2>&1; then
          remove_hook iptables mangle OUTPUT VIROUTEFS_Z2_OUT
          remove_hook iptables filter OUTPUT VIROUTEFS_FW_OUT
          remove_chain iptables filter VIROUTEFS_FW_WIFI
          remove_chain iptables filter VIROUTEFS_FW_CELL
          remove_chain iptables filter VIROUTEFS_FW_VPN
          remove_hook iptables filter OUTPUT VIROUTEFS_LOCK_OUT
          remove_hook iptables filter FORWARD VIROUTEFS_TETHER_FWD
          remove_hook iptables nat POSTROUTING VIROUTEFS_TETHER_NAT
          remove_hook iptables mangle FORWARD VIROUTEFS_TETHER_MSS
        fi
        if command -v ip6tables >/dev/null 2>&1; then
          remove_hook ip6tables mangle OUTPUT VIROUTEFS_Z2_OUT
          remove_hook ip6tables filter OUTPUT VIROUTEFS_FW_OUT
          remove_chain ip6tables filter VIROUTEFS_FW_WIFI
          remove_chain ip6tables filter VIROUTEFS_FW_CELL
          remove_chain ip6tables filter VIROUTEFS_FW_VPN
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

internal fun appFirewallCleanupScript(pidFile: String): String = """
    ${rootCleanupFunctions(pidFile)}
    if command -v iptables >/dev/null 2>&1; then
      remove_hook iptables filter OUTPUT VIROUTEFS_FW_OUT
      remove_chain iptables filter VIROUTEFS_FW_WIFI
      remove_chain iptables filter VIROUTEFS_FW_CELL
      remove_chain iptables filter VIROUTEFS_FW_VPN
    fi
    if command -v ip6tables >/dev/null 2>&1; then
      remove_hook ip6tables filter OUTPUT VIROUTEFS_FW_OUT
      remove_chain ip6tables filter VIROUTEFS_FW_WIFI
      remove_chain ip6tables filter VIROUTEFS_FW_CELL
      remove_chain ip6tables filter VIROUTEFS_FW_VPN
    fi
    printf 'viroutefs_app_firewall_cleanup=ok\n'
""".trimIndent()

internal fun networkGuardCleanupScript(pidFile: String): String = """
    ${rootCleanupFunctions(pidFile)}
    if command -v iptables >/dev/null 2>&1; then
      remove_hook iptables filter OUTPUT VIROUTEFS_LOCK_OUT
    fi
    if command -v ip6tables >/dev/null 2>&1; then
      remove_hook ip6tables filter OUTPUT VIROUTEFS_LOCK_OUT
    fi
    printf 'viroutefs_network_guard_cleanup=ok\n'
""".trimIndent()

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

internal fun packetCaptureCleanupScript(
    pidFile: String,
    logFile: String,
    captureFile: String,
    appUid: Int,
): String {
    require(appUid in 10_000..99_999_999) { "Unsafe application UID." }
    val pid = shellQuote(pidFile)
    val log = shellQuote(logFile)
    val capture = shellQuote(captureFile)
    return """
        stop_packet_capture() {
          if [ -r $pid ]; then
            capture_pid="${'$'}(tr -cd '0-9' < $pid | head -c 12)"
            if [ -n "${'$'}capture_pid" ] && [ -r "/proc/${'$'}capture_pid/cmdline" ]; then
              capture_cmd="${'$'}(tr '\000' ' ' < "/proc/${'$'}capture_pid/cmdline" 2>/dev/null)"
              case "${'$'}capture_cmd" in
                *libtcpdump.so*)
                  kill -2 "${'$'}capture_pid" 2>/dev/null || true
                  sleep 1
                  if kill -0 "${'$'}capture_pid" 2>/dev/null; then
                    kill -15 "${'$'}capture_pid" 2>/dev/null || true
                  fi
                  ;;
              esac
            fi
          fi
          if [ -f $capture ]; then
            chown $appUid:$appUid $capture 2>/dev/null || true
            chmod 600 $capture 2>/dev/null || true
          fi
          rm -f $pid $log
        }
        stop_packet_capture
        printf 'viroutefs_packet_capture_cleanup=ok\n'
    """.trimIndent()
}

internal fun rootTetheringCleanupScript(stateFile: String): String {
    val state = shellQuote(stateFile)
    return """
        remove_tether_hook() {
          tether_tool="${'$'}1"; tether_table="${'$'}2"; tether_parent="${'$'}3"; tether_child="${'$'}4"
          while "${'$'}tether_tool" -t "${'$'}tether_table" -C "${'$'}tether_parent" -j "${'$'}tether_child" >/dev/null 2>&1; do
            "${'$'}tether_tool" -t "${'$'}tether_table" -D "${'$'}tether_parent" -j "${'$'}tether_child" >/dev/null 2>&1 || break
          done
          "${'$'}tether_tool" -t "${'$'}tether_table" -F "${'$'}tether_child" >/dev/null 2>&1 || true
          "${'$'}tether_tool" -t "${'$'}tether_table" -X "${'$'}tether_child" >/dev/null 2>&1 || true
        }
        if command -v iptables >/dev/null 2>&1; then
          remove_tether_hook iptables filter FORWARD VIROUTEFS_TETHER_FWD
          remove_tether_hook iptables nat POSTROUTING VIROUTEFS_TETHER_NAT
          remove_tether_hook iptables mangle FORWARD VIROUTEFS_TETHER_MSS
        fi
        if command -v ip6tables >/dev/null 2>&1; then
          remove_tether_hook ip6tables filter FORWARD VIROUTEFS_TETHER_FWD
        fi
        if [ -r $state ]; then
          IFS='|' read -r tether_version tether_previous tether_down tether_tunnel < $state || true
          tether_valid=1
          [ "${'$'}tether_version" = v1 ] || tether_valid=0
          case "${'$'}tether_previous" in 0|1) ;; *) tether_valid=0 ;; esac
          case "${'$'}tether_down" in ''|*[!A-Za-z0-9_.-]*) tether_valid=0 ;; esac
          case "${'$'}tether_tunnel" in ''|*[!A-Za-z0-9_.-]*) tether_valid=0 ;; esac
          if [ "${'$'}tether_valid" = 1 ] && command -v ip >/dev/null 2>&1; then
            while ip -4 rule del priority 16220 iif "${'$'}tether_down" lookup 62241 >/dev/null 2>&1; do :; done
            ip -4 route del default dev "${'$'}tether_tunnel" table 62241 >/dev/null 2>&1 || true
            printf '%s' "${'$'}tether_previous" > /proc/sys/net/ipv4/ip_forward 2>/dev/null || true
          fi
        fi
        rm -f $state
        printf 'viroutefs_vpn_tethering_cleanup=ok\n'
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
        remove_chain() {
          tool="${'$'}1"; table="${'$'}2"; chain="${'$'}3"
          "${'$'}tool" -t "${'$'}table" -F "${'$'}chain" >/dev/null 2>&1 || true
          "${'$'}tool" -t "${'$'}table" -X "${'$'}chain" >/dev/null 2>&1 || true
        }
    """.trimIndent()
}

private const val ROOT_RECOVERY_TIMEOUT_MILLIS = 20_000L
private const val ROOT_AUTOMATION_RECOVERY_WAIT_MILLIS = 10_000L
