// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import android.os.Build
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Runs the pinned MPL-2.0 Xray-core executable as an app-private child process.
 *
 * The runtime configuration exists only in no-backup internal storage while
 * the process is active and is removed on stop. The child shares the app UID,
 * which ViRouteFS excludes from its own TUN to prevent a routing loop.
 */
internal class XrayProcessManager(context: Context) {
    private val applicationContext = context.applicationContext

    @Volatile
    private var process: Process? = null

    @Volatile
    private var outputThread: Thread? = null

    @Volatile
    private var activeConfigFile: File? = null

    @Volatile
    var lastMessage: String? = null
        private set

    @Synchronized
    fun start(
        configJson: String,
        ports: Collection<Int>,
        xudpBaseKey: String,
        maskLog: (String) -> String,
    ): Result<Unit> = runCatching {
        stop()
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "The Xray engine in this build requires an arm64-v8a device."
        }
        require(ports.isNotEmpty()) { "At least one Xray local endpoint is required." }
        require(ports.all { it in 1..65535 }) { "Invalid Xray local endpoint port." }

        val executable = File(applicationContext.applicationInfo.nativeLibraryDir, BINARY_NAME)
        check(executable.isFile) {
            "The pinned Xray engine is missing from the installed APK."
        }
        val runtimeDirectory = File(applicationContext.noBackupFilesDir, RUNTIME_DIRECTORY_NAME)
        check(runtimeDirectory.exists() || runtimeDirectory.mkdirs()) {
            "Could not create the private Xray runtime directory."
        }
        runtimeDirectory.listFiles()
            ?.filter { it.isFile && it.name.startsWith(CONFIG_PREFIX) && it.name.endsWith(CONFIG_SUFFIX) }
            ?.forEach(File::delete)
        val configFile = File.createTempFile(CONFIG_PREFIX, CONFIG_SUFFIX, runtimeDirectory)
        configFile.writeText(configJson, Charsets.UTF_8)
        configFile.setReadable(false, false)
        configFile.setWritable(false, false)
        check(configFile.setReadable(true, true) && configFile.setWritable(true, true)) {
            "Could not restrict the private Xray runtime configuration."
        }
        activeConfigFile = configFile

        val child = ProcessBuilder(
            executable.absolutePath,
            "run",
            "-format",
            "json",
            "-config",
            configFile.absolutePath,
        )
            .directory(runtimeDirectory)
            .redirectErrorStream(true)
            .apply {
                environment()[XUDP_BASE_KEY_ENV] = xudpBaseKey
                environment()[ASSET_DIRECTORY_ENV] = runtimeDirectory.absolutePath
            }
            .start()
        process = child
        outputThread = Thread(
            {
                runCatching {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            line.trim().takeIf(String::isNotEmpty)?.let {
                                lastMessage = maskLog(it).take(MAX_LOG_LENGTH)
                            }
                        }
                    }
                }
            },
            "ViRouteFS-Xray-Output",
        ).apply {
            isDaemon = true
            start()
        }

        waitUntilListening(child, ports)
        lastMessage = "Xray-core is listening on ${ports.size} app-private route endpoint(s)."
    }.onFailure { error ->
        lastMessage = maskLog(error.localizedMessage ?: "Xray-core could not be started.")
        stop()
    }

    fun isRunning(): Boolean = process?.isAlive == true

    @Synchronized
    fun stop() {
        val child = process
        process = null
        if (child != null && child.isAlive) {
            child.destroy()
            var attempts = 0
            while (child.isAlive && attempts < STOP_ATTEMPTS) {
                try {
                    Thread.sleep(STOP_RETRY_DELAY_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
                attempts += 1
            }
            if (child.isAlive) child.destroyForcibly()
        }
        outputThread?.interrupt()
        outputThread = null
        activeConfigFile?.let { file ->
            runCatching {
                file.writeText("")
                file.delete()
            }
        }
        activeConfigFile = null
    }

    private fun waitUntilListening(child: Process, ports: Collection<Int>) {
        val pending = ports.toMutableSet()
        val deadline = System.nanoTime() + START_TIMEOUT_NANOS
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            check(child.isAlive) {
                lastMessage ?: "Xray-core exited before opening its local route endpoints."
            }
            val iterator = pending.iterator()
            while (iterator.hasNext()) {
                val port = iterator.next()
                val connected = runCatching {
                    Socket().use { socket ->
                        socket.connect(
                            InetSocketAddress(LOOPBACK_HOST, port),
                            CONNECT_TIMEOUT_MS,
                        )
                    }
                }.onFailure { lastError = it }.isSuccess
                if (connected) iterator.remove()
            }
            if (pending.isEmpty()) return
            try {
                Thread.sleep(START_RETRY_DELAY_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                error("Interrupted while waiting for Xray-core to start.")
            }
        }
        throw IllegalStateException(
            "Xray-core did not open all local route endpoints in time.",
            lastError,
        )
    }

    companion object {
        const val BINARY_NAME = "libxray.so"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val RUNTIME_DIRECTORY_NAME = "xray-runtime"
        private const val CONFIG_PREFIX = "runtime-"
        private const val CONFIG_SUFFIX = ".json"
        private const val XUDP_BASE_KEY_ENV = "xray.xudp.basekey"
        private const val ASSET_DIRECTORY_ENV = "xray.location.asset"
        private const val CONNECT_TIMEOUT_MS = 150
        private const val START_TIMEOUT_NANOS = 8_000_000_000L
        private const val START_RETRY_DELAY_MS = 50L
        private const val STOP_ATTEMPTS = 20
        private const val STOP_RETRY_DELAY_MS = 25L
        private const val MAX_LOG_LENGTH = 320
    }
}
