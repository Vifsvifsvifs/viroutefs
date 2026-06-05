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
import java.io.FileInputStream
import java.io.InterruptedIOException

/**
 * Safe ViRouteFS TUN preview for 0.6.5-alpha.
 *
 * The default mode creates a minimal route-less Android TUN interface. The
 * optional test-route preview adds only 203.0.113.0/24 (TEST-NET-3) so users
 * can validate the read/drop lifecycle without routing normal internet traffic.
 * No DNS servers, default routes, packet payload logging, forwarding, proxying,
 * or real VPN engines are enabled.
 */
class ViRouteVpnService : VpnService() {
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var packetLoopThread: Thread? = null
    @Volatile private var packetLoopStopping: Boolean = false
    private var testRoutePreviewActive: Boolean = false
    private var packetsRead: Long = 0L
    private var bytesRead: Long = 0L
    private var lastPacketAt: Long? = null

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

        val requestedTestRoute = intent?.getBooleanExtra(
            VpnServiceController.EXTRA_TEST_ROUTE_ENABLED,
            false,
        ) ?: false

        if (tunDescriptor != null && testRoutePreviewActive == requestedTestRoute) {
            publishActiveState()
            updateNotification(activeStatus())
            return START_STICKY
        }

        if (tunDescriptor != null) closeTunDescriptor()
        establishTunPreview(requestedTestRoute)
        return START_STICKY
    }

    override fun onRevoke() {
        stopPreview()
        super.onRevoke()
    }

    override fun onDestroy() {
        closeTunDescriptor()
        isRunning = false
        if (lastState.status != VpnServiceStatus.Error) publishStoppedState()
        super.onDestroy()
    }

    private fun establishTunPreview(enableTestRoute: Boolean) {
        testRoutePreviewActive = enableTestRoute
        resetCounters()
        publishState(VpnServiceStatus.Starting)
        val descriptor = runCatching {
            val builder = Builder()
                .setSession(TUN_SESSION_NAME)
                .setMtu(TUN_MTU)
                .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX_LENGTH)
            if (enableTestRoute) {
                builder.addRoute(TEST_ROUTE_IPV4_NETWORK, TEST_ROUTE_IPV4_PREFIX_LENGTH)
            }
            builder.establish()
        }.onFailure { error ->
            val detail = error.localizedMessage ?: "Could not establish safe TUN preview."
            publishState(VpnServiceStatus.Error, detail)
            updateNotification(VpnServiceStatus.ServiceActiveNoTun)
            isRunning = false
            stopSelf()
        }.getOrNull()

        if (descriptor == null) {
            if (lastState.status != VpnServiceStatus.Error) {
                publishState(VpnServiceStatus.Error, "Android returned no TUN descriptor for the safe preview.")
                updateNotification(VpnServiceStatus.ServiceActiveNoTun)
                isRunning = false
                stopSelf()
            }
            return
        }

        tunDescriptor = descriptor
        if (enableTestRoute) startPacketLoop(descriptor)
        publishActiveState()
        updateNotification(activeStatus())
    }

    private fun startPacketLoop(descriptor: ParcelFileDescriptor) {
        stopPacketLoop()
        packetLoopStopping = false
        packetLoopThread = Thread({ readPacketsUntilClosed(descriptor) }, "ViRouteFS-TunReadDropPreview").apply {
            isDaemon = true
            start()
        }
    }

    private fun readPacketsUntilClosed(descriptor: ParcelFileDescriptor) {
        val buffer = ByteArray(TUN_MTU)
        runCatching {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                while (!packetLoopStopping) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    packetsRead += 1L
                    bytesRead += read.toLong()
                    lastPacketAt = System.currentTimeMillis()
                    publishActiveState()
                }
            }
        }.onFailure { error ->
            if (!packetLoopStopping && error !is InterruptedIOException) {
                val detail = error.localizedMessage ?: "TUN read failed; packet preview stopped."
                closeTunDescriptor()
                isRunning = false
                publishState(VpnServiceStatus.Error, detail)
                updateNotification(VpnServiceStatus.ServiceActiveNoTun)
                stopSelf()
            }
        }
    }

    private fun stopPreview() {
        closeTunDescriptor()
        isRunning = false
        publishStoppedState()
        stopSelf()
    }

    private fun closeTunDescriptor() {
        packetLoopStopping = true
        tunDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
        }
        tunDescriptor = null
        stopPacketLoop()
        testRoutePreviewActive = false
    }

    private fun stopPacketLoop() {
        packetLoopStopping = true
        val thread = packetLoopThread
        thread?.interrupt()
        if (thread != null && thread != Thread.currentThread()) {
            runCatching { thread.join(PACKET_LOOP_JOIN_TIMEOUT_MS) }
        }
        packetLoopThread = null
    }

    private fun resetCounters() {
        packetsRead = 0L
        bytesRead = 0L
        lastPacketAt = null
    }

    private fun activeStatus(): VpnServiceStatus =
        if (testRoutePreviewActive) VpnServiceStatus.TunTestRouteActive else VpnServiceStatus.TunPreviewActive

    private fun publishActiveState() {
        publishState(
            status = activeStatus(),
            tunTestRouteActive = testRoutePreviewActive,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            lastPacketAt = lastPacketAt,
        )
    }

    private fun publishStoppedState() {
        publishState(
            status = VpnServiceStatus.Stopped,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            lastPacketAt = lastPacketAt,
        )
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
            VpnServiceStatus.TunTestRouteActive -> "TEST-NET route preview active — packets are counted and dropped"
            VpnServiceStatus.TunPreviewActive -> "TUN preview active — no traffic routes installed"
            else -> "Local VPN preview — no traffic routing yet"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_viroutefs_launcher_0614_monochrome)
            .setContentTitle("ViRouteFS local VPN preview")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "$text. No default route, DNS servers, payload logging, packet forwarding, proxying, or VPN engines are enabled.",
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

    private fun publishState(
        status: VpnServiceStatus,
        detail: String? = null,
        tunTestRouteActive: Boolean = false,
        packetsRead: Long = 0L,
        bytesRead: Long = 0L,
        lastPacketAt: Long? = null,
    ) {
        val state = VpnServiceUiState(status, detail, tunTestRouteActive, packetsRead, bytesRead, lastPacketAt)
        rememberState(state)
        val intent = Intent(VpnServiceController.ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(VpnServiceController.EXTRA_STATUS, status.name)
            .putExtra(VpnServiceController.EXTRA_DETAIL, detail)
            .putExtra(VpnServiceController.EXTRA_TEST_ROUTE_ACTIVE, tunTestRouteActive)
            .putExtra(VpnServiceController.EXTRA_PACKETS_READ, packetsRead)
            .putExtra(VpnServiceController.EXTRA_BYTES_READ, bytesRead)
            .putExtra(VpnServiceController.EXTRA_LAST_PACKET_AT, lastPacketAt ?: VpnServiceController.NO_PACKET_TIME)
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
        private const val TEST_ROUTE_IPV4_NETWORK = "203.0.113.0"
        private const val TEST_ROUTE_IPV4_PREFIX_LENGTH = 24
        private const val TUN_MTU = 1500
        private const val PACKET_LOOP_JOIN_TIMEOUT_MS = 500L
    }
}
