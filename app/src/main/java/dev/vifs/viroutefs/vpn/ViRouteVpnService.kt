// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.R

/**
 * Safe ViRouteFS VpnService preview for 0.5.1-alpha.
 *
 * This service intentionally does not call Builder.establish(), does not create
 * a TUN interface, and does not capture, inspect, route, or tunnel packets yet.
 * It only proves the Android VPN permission and local foreground service
 * lifecycle so device connectivity is not broken by an unfinished tunnel.
 */
class ViRouteVpnService : VpnService() {
    override fun onCreate() {
        super.onCreate()
        publishState(VpnServiceStatus.Starting)
        runCatching {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Foreground service notification setup failed."
            publishState(VpnServiceStatus.Error, detail)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnServiceController.ACTION_STOP) {
            isRunning = false
            publishState(VpnServiceStatus.Stopped)
            stopSelf()
            return START_NOT_STICKY
        }

        if (lastState.status == VpnServiceStatus.Error) return START_NOT_STICKY

        isRunning = true
        publishState(VpnServiceStatus.Active)
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        if (lastState.status != VpnServiceStatus.Error) publishState(VpnServiceStatus.Stopped)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ViRouteFS local VPN preview")
            .setContentText("No traffic routing or packet capture yet")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "ViRouteFS local VPN preview is active. No traffic routing, packet capture, TUN interface, or hidden interception is enabled yet.",
                ),
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ViRouteFS VPN preview",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Foreground notification for the local VPN service preview."
        }
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun publishState(status: VpnServiceStatus, detail: String? = null) {
        rememberState(VpnServiceUiState(status, detail))
        val intent = Intent(VpnServiceController.ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(VpnServiceController.EXTRA_STATUS, status.name)
            .putExtra(VpnServiceController.EXTRA_DETAIL, detail)
        sendBroadcast(intent)
    }

    companion object {
        @Volatile
        internal var isRunning: Boolean = false
            private set

        @Volatile
        internal var lastState: VpnServiceUiState = VpnServiceUiState(VpnServiceStatus.Off)
            private set

        internal fun rememberState(state: VpnServiceUiState) {
            lastState = state
        }

        private const val CHANNEL_ID = "viroutefs_vpn_preview"
        private const val NOTIFICATION_ID = 500
    }
}
