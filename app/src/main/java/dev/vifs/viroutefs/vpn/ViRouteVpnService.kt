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
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.R

/**
 * Safe ViRouteFS route-less TUN preview for 0.6.0-alpha.
 *
 * This service creates a minimal Android TUN interface only when the local VPN
 * preview is explicitly started. It deliberately does not add routes, DNS
 * servers, packet reading, packet inspection, packet forwarding, proxying, or
 * tunnel engines, so normal device internet should remain unchanged.
 */
class ViRouteVpnService : VpnService() {
    private var tunDescriptor: ParcelFileDescriptor? = null

    override fun onCreate() {
        super.onCreate()
        publishState(VpnServiceStatus.Starting)
        runCatching {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification(VpnServiceStatus.ServiceActiveNoTun))
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Foreground service notification setup failed."
            publishState(VpnServiceStatus.Error, detail)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == VpnServiceController.ACTION_STOP) {
            stopPreview()
            return START_NOT_STICKY
        }

        if (lastState.status == VpnServiceStatus.Error) return START_NOT_STICKY

        isRunning = true
        if (!SAFE_TUN_PREVIEW_ENABLED) {
            publishState(VpnServiceStatus.ServiceActiveNoTun, "TUN preview is disabled.")
            updateNotification(VpnServiceStatus.ServiceActiveNoTun)
            return START_STICKY
        }

        if (tunDescriptor != null) {
            publishState(VpnServiceStatus.TunPreviewActive)
            updateNotification(VpnServiceStatus.TunPreviewActive)
            return START_STICKY
        }

        establishTunPreview()
        return START_STICKY
    }

    override fun onRevoke() {
        stopPreview()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunDescriptor()
        isRunning = false
        if (lastState.status != VpnServiceStatus.Error) publishState(VpnServiceStatus.Stopped)
        super.onDestroy()
    }

    private fun establishTunPreview() {
        publishState(VpnServiceStatus.Starting)
        val descriptor = runCatching {
            Builder()
                .setSession(TUN_SESSION_NAME)
                .setMtu(TUN_MTU)
                .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX_LENGTH)
                .establish()
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Could not establish route-less TUN preview."
            publishState(VpnServiceStatus.Error, detail)
            updateNotification(VpnServiceStatus.ServiceActiveNoTun)
            isRunning = false
            stopSelf()
        }.getOrNull()

        if (descriptor == null) {
            if (lastState.status != VpnServiceStatus.Error) {
                publishState(VpnServiceStatus.Error, "Android returned no TUN descriptor for the route-less preview.")
                updateNotification(VpnServiceStatus.ServiceActiveNoTun)
                isRunning = false
                stopSelf()
            }
            return
        }

        tunDescriptor = descriptor
        publishState(VpnServiceStatus.TunPreviewActive)
        updateNotification(VpnServiceStatus.TunPreviewActive)
    }

    private fun stopPreview() {
        closeTunDescriptor()
        isRunning = false
        publishState(VpnServiceStatus.Stopped)
        stopSelf()
    }

    private fun closeTunDescriptor() {
        tunDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
        }
        tunDescriptor = null
    }

    private fun buildNotification(status: VpnServiceStatus): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = when (status) {
            VpnServiceStatus.TunPreviewActive -> "TUN preview active — no traffic routes installed"
            else -> "Local VPN preview — no traffic routing yet"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ViRouteFS local VPN preview")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text. No DNS servers, packet capture, packet forwarding, proxying, or VPN engines are enabled.",
                ),
            )
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(status: VpnServiceStatus) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status))
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
        private const val SAFE_TUN_PREVIEW_ENABLED = true
        private const val TUN_SESSION_NAME = "ViRouteFS TUN preview"
        private const val TUN_IPV4_ADDRESS = "10.250.0.2"
        private const val TUN_IPV4_PREFIX_LENGTH = 32
        private const val TUN_MTU = 1500
    }
}
