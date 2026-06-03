// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat

internal enum class VpnServiceStatus {
    Off,
    PermissionRequired,
    NotificationPermissionRequired,
    Starting,
    Active,
    Stopped,
    Error,
}

internal data class VpnServiceUiState(
    val status: VpnServiceStatus,
    val detail: String? = null,
)

internal class VpnServiceController(context: Context) {
    private val appContext = context.applicationContext

    fun prepareIntent(): Intent? = VpnService.prepare(appContext)

    fun notificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun currentState(): VpnServiceUiState = if (ViRouteVpnService.isRunning) {
        VpnServiceUiState(VpnServiceStatus.Active)
    } else {
        ViRouteVpnService.lastState
    }

    fun startLocalService() {
        if (ViRouteVpnService.isRunning) {
            publishState(VpnServiceStatus.Active)
            return
        }
        publishState(VpnServiceStatus.Starting)
        val intent = Intent(appContext, ViRouteVpnService::class.java).setAction(ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopLocalService() {
        if (!ViRouteVpnService.isRunning) {
            publishState(VpnServiceStatus.Stopped)
            return
        }
        val intent = Intent(appContext, ViRouteVpnService::class.java).setAction(ACTION_STOP)
        appContext.startService(intent)
    }

    fun registerStateReceiver(onState: (VpnServiceUiState) -> Unit): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_STATE_CHANGED) return
                val status = intent.getStringExtra(EXTRA_STATUS)?.let { value ->
                    runCatching { VpnServiceStatus.valueOf(value) }.getOrNull()
                } ?: return
                onState(VpnServiceUiState(status, intent.getStringExtra(EXTRA_DETAIL)))
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            receiver,
            IntentFilter(ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return receiver
    }

    fun unregisterStateReceiver(receiver: BroadcastReceiver) {
        runCatching { appContext.unregisterReceiver(receiver) }
    }

    private fun publishState(status: VpnServiceStatus, detail: String? = null) {
        ViRouteVpnService.rememberState(VpnServiceUiState(status, detail))
        val intent = Intent(ACTION_STATE_CHANGED)
            .setPackage(appContext.packageName)
            .putExtra(EXTRA_STATUS, status.name)
            .putExtra(EXTRA_DETAIL, detail)
        appContext.sendBroadcast(intent)
    }

    companion object {
        internal const val ACTION_START = "dev.vifs.viroutefs.vpn.START"
        internal const val ACTION_STOP = "dev.vifs.viroutefs.vpn.STOP"
        internal const val ACTION_STATE_CHANGED = "dev.vifs.viroutefs.vpn.STATE_CHANGED"
        internal const val EXTRA_STATUS = "status"
        internal const val EXTRA_DETAIL = "detail"
    }
}
