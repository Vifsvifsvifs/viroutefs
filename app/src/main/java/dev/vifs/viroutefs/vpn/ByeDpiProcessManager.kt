// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import android.os.Build
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Runs the pinned MIT-licensed ByeDPI executable as an app-private loopback
 * SOCKS5 proxy. ViRouteFS is excluded from its own TUN, so the child process
 * shares the app UID and its outbound sockets cannot loop back into the VPN.
 */
internal class ByeDpiProcessManager(context: Context) {
    private val applicationContext = context.applicationContext

    @Volatile
    private var process: Process? = null

    @Volatile
    private var outputThread: Thread? = null

    @Volatile
    var lastMessage: String? = null
        private set

    @Synchronized
    fun start(): Result<Int> = runCatching {
        stop()
        check(Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }) {
            "ByeDPI in this build requires an arm64-v8a device."
        }
        val executable = File(applicationContext.applicationInfo.nativeLibraryDir, BINARY_NAME)
        check(executable.isFile) {
            "The pinned ByeDPI executable is missing from the installed APK."
        }

        val port = findAvailableLoopbackPort()
        val child = ProcessBuilder(
            executable.absolutePath,
            "--ip",
            LOOPBACK_HOST,
            "--port",
            port.toString(),
            "--auto=torst",
            "--timeout",
            "3",
            "--tlsrec",
            "1+s",
        )
            .redirectErrorStream(true)
            .start()
        process = child
        outputThread = Thread(
            {
                runCatching {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            line.trim().takeIf(String::isNotEmpty)?.let { lastMessage = it.take(240) }
                        }
                    }
                }
            },
            "ViRouteFS-ByeDPI-Output",
        ).apply {
            isDaemon = true
            start()
        }

        waitUntilListening(child, port)
        lastMessage = "ByeDPI is listening on the app-private loopback proxy."
        port
    }.onFailure {
        lastMessage = it.localizedMessage ?: "ByeDPI could not be started."
        stop()
    }

    fun isRunning(): Boolean = process?.isAlive == true

    @Synchronized
    fun stop() {
        val child = process
        process = null
        if (child != null && child.isAlive) {
            child.destroy()
            for (attempt in 0 until 10) {
                if (!child.isAlive) break
                try {
                    Thread.sleep(25)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            if (child.isAlive) child.destroyForcibly()
        }
        outputThread?.interrupt()
        outputThread = null
    }

    private fun findAvailableLoopbackPort(): Int =
        ServerSocket(0, 1, InetAddress.getByName(LOOPBACK_HOST)).use { it.localPort }

    private fun waitUntilListening(child: Process, port: Int) {
        val deadline = System.nanoTime() + START_TIMEOUT_NANOS
        var lastError: Throwable? = null
        while (System.nanoTime() < deadline) {
            check(child.isAlive) {
                lastMessage ?: "ByeDPI exited before opening its local proxy."
            }
            val connected = runCatching {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(LOOPBACK_HOST, port), CONNECT_TIMEOUT_MILLIS)
                }
            }.onFailure { lastError = it }.isSuccess
            if (connected) return
            try {
                Thread.sleep(50)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                error("Interrupted while waiting for ByeDPI to start.")
            }
        }
        throw IllegalStateException(
            "ByeDPI did not open its local proxy in time.",
            lastError,
        )
    }

    companion object {
        const val BINARY_NAME = "libbyedpi.so"
        private const val LOOPBACK_HOST = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MILLIS = 100
        private const val START_TIMEOUT_NANOS = 3_000_000_000L
    }
}
