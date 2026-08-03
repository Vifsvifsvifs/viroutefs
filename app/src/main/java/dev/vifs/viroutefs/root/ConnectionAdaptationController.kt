// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context

data class ConnectionAdaptationResult(
    val successful: Boolean,
    val running: Boolean,
    val message: String,
)

class ConnectionAdaptationController(context: Context) {
    private val appContext = context.applicationContext
    private val assets = Zapret2AssetInstaller(appContext)
    private val access = RootAccessController(appContext)
    private val executor = RootCommandExecutor()
    private val stateRepository = RootRuntimeStateRepository(appContext)
    private val recovery = RootNetworkRecoveryController(appContext)

    fun start(): ConnectionAdaptationResult {
        val probe = access.requestAndProbe()
        val capabilities = probe.snapshot
        if (!probe.granted || capabilities == null) {
            return ConnectionAdaptationResult(false, false, probe.message)
        }
        if (!capabilities.hasIptables || !capabilities.hasIp6tables || !capabilities.hasNfQueue) {
            return ConnectionAdaptationResult(
                successful = false,
                running = false,
                message = "Ядро не подтвердило полный набор iptables, ip6tables и NFQUEUE с безопасным queue-bypass. Режим не включён.",
            )
        }
        val runtime = runCatching { assets.prepare() }.getOrElse { error ->
            return ConnectionAdaptationResult(false, false, error.localizedMessage ?: "Не удалось проверить файлы root-движка.")
        }
        stateRepository.markPending(
            module = RootManagedModule.ConnectionAdaptation,
            backend = "iptables_nfqueue",
        )
        val script = connectionAdaptationStartScript(
            runtime = runtime,
            pidFile = stateRepository.pidFile().absolutePath,
            logFile = stateRepository.logFile().absolutePath,
        )
        val result = executor.execute(script, CONNECTION_ADAPTATION_START_TIMEOUT_MILLIS)
        if (!result.completed || result.exitCode != 0) {
            val recovered = recovery.recoverConnectionAdaptation()
            return ConnectionAdaptationResult(
                successful = false,
                running = false,
                message = if (recovered.successful) {
                    "Движок или NFQUEUE-правила не запустились; изменения автоматически отменены."
                } else {
                    "Запуск прерван, но автоматический откат не подтверждён. Нажмите «Удалить root-правила ViRouteFS» в root-центре."
                },
            )
        }
        return ConnectionAdaptationResult(
            successful = true,
            running = true,
            message = "Адаптация соединений запущена отдельно от ByeDPI. Обрабатываются веб-порты TCP 80/443 и QUIC UDP 443; это не VPN и не шифрование.",
        )
    }

    fun stop(): ConnectionAdaptationResult {
        val result = recovery.recoverConnectionAdaptation()
        return ConnectionAdaptationResult(
            successful = result.successful,
            running = !result.successful,
            message = result.message,
        )
    }
}

internal fun connectionAdaptationStartScript(
    runtime: Zapret2RuntimeFiles,
    pidFile: String,
    logFile: String,
    queueNumber: Int = CONNECTION_ADAPTATION_QUEUE_NUMBER,
): String {
    require(queueNumber in 1024..65535) { "Unsafe NFQUEUE number." }
    val binary = shellQuote(runtime.binary.absolutePath)
    val libraryArg = shellQuote("--lua-init=@${runtime.library.absolutePath}")
    val antiDpiArg = shellQuote("--lua-init=@${runtime.antiDpi.absolutePath}")
    val automaticArg = shellQuote("--lua-init=@${runtime.automatic.absolutePath}")
    val pidArg = shellQuote("--pidfile=$pidFile")
    val pid = shellQuote(pidFile)
    val log = shellQuote(logFile)
    val cleanup = connectionAdaptationCleanupScript(pidFile, logFile)
    return """
        $cleanup
        set -eu
        committed=0
        rollback_start() {
          if [ "${'$'}committed" != 1 ]; then
            remove_hook iptables mangle OUTPUT VIROUTEFS_Z2_OUT
            remove_hook ip6tables mangle OUTPUT VIROUTEFS_Z2_OUT
            stop_managed_process
          fi
        }
        trap rollback_start EXIT
        trap 'rollback_start; exit 1' HUP INT TERM
        $binary --daemon $pidArg --qnum=$queueNumber --fwmark=0x40000000 --debug=0 \
          $libraryArg $antiDpiArg $automaticArg \
          --filter-tcp=80 --filter-l7=http --payload=http_req \
          --lua-desync=fake:blob=fake_default_http:tcp_md5 \
          --lua-desync=multisplit:pos=method+2 --new \
          --filter-tcp=443 --filter-l7=tls --payload=tls_client_hello \
          --lua-desync=fake:blob=fake_default_tls:tcp_md5:tcp_seq=-10000 \
          --lua-desync=multidisorder:pos=1,midsld --new \
          --filter-udp=443 --filter-l7=quic --payload=quic_initial \
          --lua-desync=fake:blob=fake_default_quic:repeats=6 >$log 2>&1
        sleep 1
        managed_pid="${'$'}(tr -cd '0-9' < $pid | head -c 12)"
        [ -n "${'$'}managed_pid" ] && kill -0 "${'$'}managed_pid"
        for tool in iptables ip6tables; do
          "${'$'}tool" -t mangle -N VIROUTEFS_Z2_OUT
          "${'$'}tool" -t mangle -A VIROUTEFS_Z2_OUT -m mark --mark 0x40000000/0x40000000 -j RETURN
          "${'$'}tool" -t mangle -A VIROUTEFS_Z2_OUT -p tcp --dport 80 -j NFQUEUE --queue-num $queueNumber --queue-bypass
          "${'$'}tool" -t mangle -A VIROUTEFS_Z2_OUT -p tcp --dport 443 -j NFQUEUE --queue-num $queueNumber --queue-bypass
          "${'$'}tool" -t mangle -A VIROUTEFS_Z2_OUT -p udp --dport 443 -j NFQUEUE --queue-num $queueNumber --queue-bypass
          "${'$'}tool" -t mangle -I OUTPUT 1 -j VIROUTEFS_Z2_OUT
          "${'$'}tool" -t mangle -C OUTPUT -j VIROUTEFS_Z2_OUT
        done
        committed=1
        trap - EXIT HUP INT TERM
        printf 'viroutefs_connection_adaptation=running\n'
    """.trimIndent()
}

private const val CONNECTION_ADAPTATION_QUEUE_NUMBER = 62240
private const val CONNECTION_ADAPTATION_START_TIMEOUT_MILLIS = 30_000L
