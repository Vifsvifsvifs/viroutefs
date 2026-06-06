// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Dev-only Xray/libv2ray smoke runner.
 *
 * SAFETY BOUNDARIES:
 * - This class is experimental diagnostics code, not a user traffic tunnel.
 * - It never attaches to ViRouteFS VpnService, never receives a TUN file descriptor,
 *   and never routes device traffic.
 * - The smoke config only starts a localhost SOCKS inbound and a placeholder VLESS/TCP
 *   outbound. No outbound connection is made unless a developer manually connects to
 *   127.0.0.1:10808 while this smoke test is running.
 * - Logs are delivered through the local callback supplied by the UI. There is no
 *   telemetry, analytics, cloud upload, or automatic PCAP/log export.
 */
class XrayEngine(
    context: Context,
    private val onLog: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val callbackHandler = SmokeCallbackHandler()
    private val coreController: CoreController = Libv2ray.newCoreController(callbackHandler)

    init {
        initializeCoreEnvironment()
    }

    /**
     * Starts the minimal smoke config on a worker dispatcher.
     *
     * This is intentionally a suspend function so the Compose UI can call it from a
     * coroutine without blocking the main thread while libv2ray validates and starts
     * the core loop.
     */
    suspend fun startSmokeTest(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (coreController.isRunning) {
                val message = "Xray smoke test is already running. Status=${getStatusLocked()}"
                log(message)
                return@synchronized Result.success(Unit)
            }

            log("DEV-ONLY: starting Xray smoke test. No VPN/TUN traffic is connected.")
            log("Local test inbound: SOCKS 127.0.0.1:10808. Outbound: placeholder VLESS/TCP to 127.0.0.1:1.")

            runCatching {
                coreController.startLoop(minimalVlessTcpSmokeConfig(), NO_TUN_FD)
                check(coreController.isRunning) { "libv2ray did not report a running core after startLoop()." }
                Unit
            }.onSuccess {
                log("Xray smoke test started successfully. This only proves libv2ray can initialize and run a minimal config.")
            }.onFailure { error ->
                if (coreController.isRunning) {
                    runCatching { coreController.stopLoop() }
                }
                log("Xray smoke test failed: ${error.safeMessage()}")
            }
        }
    }

    /** Stops the smoke runner if it is active. */
    suspend fun stop(): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(lock) {
            if (!coreController.isRunning) {
                log("Xray smoke test is already stopped.")
                return@synchronized Result.success(Unit)
            }

            runCatching {
                coreController.stopLoop()
                Unit
            }.onSuccess {
                log("Xray smoke test stopped.")
            }.onFailure { error ->
                log("Xray smoke test stop failed: ${error.safeMessage()}")
            }
        }
    }

    /** Returns a short local status string for the UI. */
    suspend fun getStatus(): String = withContext(Dispatchers.IO) {
        synchronized(lock) { getStatusLocked() }
    }

    private fun initializeCoreEnvironment() {
        val assetPath = appContext.filesDir.absolutePath
        runCatching {
            Seq.setContext(appContext)
            Libv2ray.initCoreEnv(assetPath, xudpBaseKey(appContext))
        }.onSuccess {
            log("Xray core environment initialized locally at $assetPath.")
            runCatching { Libv2ray.checkVersionX() }
                .onSuccess { version -> log("Xray core version: $version") }
                .onFailure { error -> log("Xray version check failed: ${error.safeMessage()}") }
        }.onFailure { error ->
            log("Xray core environment initialization failed: ${error.safeMessage()}")
        }
    }

    private fun getStatusLocked(): String = if (coreController.isRunning) "running" else "stopped"

    private fun log(message: String) {
        onLog(message)
    }

    private inner class SmokeCallbackHandler : CoreCallbackHandler {
        override fun startup(): Long {
            log("libv2ray callback: startup.")
            return CALLBACK_SUCCESS
        }

        override fun shutdown(): Long {
            log("libv2ray callback: shutdown.")
            return CALLBACK_SUCCESS
        }

        override fun onEmitStatus(status: Long, message: String?): Long {
            val safeMessage = message?.takeIf { it.isNotBlank() } ?: "status update"
            log("libv2ray status $status: $safeMessage")
            return CALLBACK_SUCCESS
        }
    }

    @SuppressLint("HardwareIds")
    private fun xudpBaseKey(context: Context): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        )?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val key = androidId.copyOf(32)
        return android.util.Base64.encodeToString(
            key,
            android.util.Base64.NO_PADDING or
                android.util.Base64.URL_SAFE or
                android.util.Base64.NO_WRAP,
        )
    }

    private fun minimalVlessTcpSmokeConfig(): String = """
        {
          "log": {
            "loglevel": "warning"
          },
          "inbounds": [
            {
              "tag": "dev-only-local-socks-smoke",
              "listen": "127.0.0.1",
              "port": 10808,
              "protocol": "socks",
              "settings": {
                "auth": "noauth",
                "udp": false
              }
            }
          ],
          "outbounds": [
            {
              "tag": "dev-only-placeholder-vless-tcp",
              "protocol": "vless",
              "settings": {
                "vnext": [
                  {
                    "address": "127.0.0.1",
                    "port": 1,
                    "users": [
                      {
                        "id": "00000000-0000-0000-0000-000000000001",
                        "encryption": "none"
                      }
                    ]
                  }
                ]
              },
              "streamSettings": {
                "network": "tcp"
              }
            }
          ]
        }
    """.trimIndent()

    private fun Throwable.safeMessage(): String = message ?: this::class.java.simpleName

    private companion object {
        private const val NO_TUN_FD = -1
        private const val CALLBACK_SUCCESS = 0L
    }
}
