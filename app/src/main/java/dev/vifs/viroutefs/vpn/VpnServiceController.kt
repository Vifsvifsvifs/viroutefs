package dev.vifs.viroutefs.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import androidx.core.content.ContextCompat

internal enum class VpnServiceStatus {
    Off,
    PermissionRequired,
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

    fun currentState(): VpnServiceUiState = if (ViRouteVpnService.isRunning) {
        VpnServiceUiState(VpnServiceStatus.Active)
    } else {
        VpnServiceUiState(VpnServiceStatus.Off)
    }

    fun startLocalService() {
        val intent = Intent(appContext, ViRouteVpnService::class.java).setAction(ACTION_START)
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun stopLocalService() {
        val intent = Intent(appContext, ViRouteVpnService::class.java)
        appContext.stopService(intent)
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

    companion object {
        internal const val ACTION_START = "dev.vifs.viroutefs.vpn.START"
        internal const val ACTION_STOP = "dev.vifs.viroutefs.vpn.STOP"
        internal const val ACTION_STATE_CHANGED = "dev.vifs.viroutefs.vpn.STATE_CHANGED"
        internal const val EXTRA_STATUS = "status"
        internal const val EXTRA_DETAIL = "detail"
    }
}
