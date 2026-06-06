// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.content.Context
import go.Seq
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

class XrayEngineRunner(
    context: Context,
    private val onLog: (String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val callbackHandler = CallbackHandler()
    private val coreController: CoreController = Libv2ray.newCoreController(callbackHandler)

    init {
        val assetPath = appContext.filesDir.absolutePath
        runCatching {
            Seq.setContext(appContext)
            Libv2ray.initCoreEnv(assetPath, XUDP_BASE_KEY)
        }.onSuccess {
            onLog("Xray core environment initialized at $assetPath.")
            runCatching { Libv2ray.checkVersionX() }
                .onSuccess { version -> onLog("Xray core version: $version") }
        }.onFailure { error ->
            onLog("Xray core environment initialization failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    fun start(configJson: String): Result<Unit> = synchronized(lock) {
        if (isRunning()) {
            onLog("Xray smoke engine is already running.")
            return@synchronized Result.success(Unit)
        }

        onLog("Starting Xray smoke engine on 127.0.0.1:10808. No VPN/TUN traffic is attached.")
        runCatching {
            coreController.startLoop(configJson, NO_TUN_FD)
            check(isRunning()) { "Xray core did not report a running state after start." }
            Unit
        }.onSuccess {
            onLog("Xray smoke engine started.")
        }.onFailure { error ->
            if (isRunning()) runCatching { coreController.stopLoop() }
            onLog("Xray smoke engine failed to start: ${error.message ?: error::class.java.simpleName}")
        }
    }

    fun stop() {
        synchronized(lock) {
            if (!isRunning()) {
                onLog("Xray smoke engine is already stopped.")
                return
            }

            runCatching { coreController.stopLoop() }
                .onSuccess { onLog("Xray smoke engine stopped.") }
                .onFailure { error ->
                    onLog("Xray smoke engine stop failed: ${error.message ?: error::class.java.simpleName}")
                }
        }
    }

    fun isRunning(): Boolean = coreController.isRunning

    private inner class CallbackHandler : CoreCallbackHandler {
        override fun startup(): Long {
            onLog("Xray callback: startup.")
            return CALLBACK_SUCCESS
        }

        override fun shutdown(): Long {
            onLog("Xray callback: shutdown.")
            return CALLBACK_SUCCESS
        }

        override fun onEmitStatus(status: Long, message: String?): Long {
            val safeMessage = message?.takeIf { it.isNotBlank() } ?: "status update"
            onLog("Xray status $status: $safeMessage")
            return CALLBACK_SUCCESS
        }
    }

    private companion object {
        private const val NO_TUN_FD = -1
        private const val CALLBACK_SUCCESS = 0L
        private const val XUDP_BASE_KEY = "viroutefs-dev-xray-smoke"
    }
}
