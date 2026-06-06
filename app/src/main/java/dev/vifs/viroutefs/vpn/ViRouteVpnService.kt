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
import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import dev.vifs.viroutefs.MainActivity
import dev.vifs.viroutefs.runtime.tcp.TcpSessionState
import dev.vifs.viroutefs.R
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import java.io.FileInputStream
import java.io.InterruptedIOException

/**
 * Safe ViRouteFS TUN runtime skeleton for 0.8.3-alpha.
 *
 * The default mode creates a minimal route-less Android TUN interface. The
 * optional test-route preview adds only 203.0.113.0/24 (TEST-NET-3) so users
 * can validate the read/drop lifecycle without routing normal internet traffic.
 * No DNS servers, default routes, packet payload logging, forwarding, proxying,
 * or real VPN engines are enabled. The packet loop parses only IPv4 header metadata for local TCP/UDP/ICMP counters and a local in-memory packet summary list, then drops every packet.
 */
class ViRouteVpnService : VpnService() {
    private var tunDescriptor: ParcelFileDescriptor? = null
    private var packetLoopThread: Thread? = null
    @Volatile private var packetLoopStopping: Boolean = false
    private var testRoutePreviewActive: Boolean = false
    private var xrayModeActive: Boolean = false
    private var xrayEngineRunner: XrayEngineRunner? = null
    private var packetsRead: Long = 0L
    private var bytesRead: Long = 0L
    private var ipv4PacketsRead: Long = 0L
    private var tcpPacketsRead: Long = 0L
    private var udpPacketsRead: Long = 0L
    private var icmpPacketsRead: Long = 0L
    private var lastPacketAt: Long? = null
    private val packetHistory = PacketSummaryHistory()
    private var lastUiPublishAt: Long = 0L

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
        when (intent?.action) {
            VpnServiceController.ACTION_STOP -> {
                stopPreview()
                return START_NOT_STICKY
            }
            ACTION_START_XRAY -> {
                isRunning = true
                if (tunDescriptor != null) closeTunDescriptor()
                startXrayMode(intent)
                return START_STICKY
            }
            VpnServiceController.ACTION_CLEAR_PACKET_SUMMARIES -> {
                packetHistory.clear()
                publishActiveState(force = true)
                return START_STICKY
            }
            VpnServiceController.ACTION_SET_PACKET_INSPECTOR_PAUSED -> {
                packetHistory.setPaused(intent.getBooleanExtra(VpnServiceController.EXTRA_PACKET_INSPECTOR_PAUSED, false))
                publishActiveState(force = true)
                return START_STICKY
            }
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

        if (tunDescriptor != null && !xrayModeActive && testRoutePreviewActive == requestedTestRoute) {
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

    private fun startXrayMode(intent: Intent) {
        val profile = intent.vlessProfileExtra() ?: run {
            failClosed("Missing VLESS REALITY profile for Xray mode.")
            return
        }
        xrayModeActive = true
        testRoutePreviewActive = false
        resetCounters()
        publishState(VpnServiceStatus.Starting, detail = "Starting VLESS REALITY engine.")
        val descriptor = runCatching {
            Builder()
                .setSession(XRAY_TUN_SESSION_NAME)
                .setMtu(TUN_MTU)
                .addAddress(TUN_IPV4_ADDRESS, TUN_IPV4_PREFIX_LENGTH)
                .addAddress(TUN_IPV6_ADDRESS, TUN_IPV6_PREFIX_LENGTH)
                .addDnsServer(XRAY_DNS_SERVER)
                .addRoute("0.0.0.0", 0)
                .addRoute("::", 0)
                .establish()
        }.getOrElse { error ->
            failClosed(error.localizedMessage ?: "Could not establish Xray VPN TUN.")
            return
        } ?: run {
            failClosed("Android returned no TUN descriptor for Xray mode.")
            return
        }

        tunDescriptor = descriptor
        val config = runCatching { buildXrayConfig(profile) }.getOrElse { error ->
            failClosed(error.localizedMessage ?: "Could not build Xray VLESS REALITY config.")
            return
        }
        val runner = XrayEngineRunner(tunFd = descriptor.fd)
        val result = runner.start(config)
        if (result.isFailure) {
            failClosed(result.exceptionOrNull()?.localizedMessage ?: "Xray engine failed to start.")
            return
        }
        xrayEngineRunner = runner
        publishActiveState(force = true)
        updateNotification(activeStatus())
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
        startPacketLoop(descriptor)
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
                    inspectPacket(buffer, read)
                    publishActiveStateThrottled()
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
        xrayEngineRunner?.stop()
        xrayEngineRunner = null
        tunDescriptor?.let { descriptor ->
            runCatching { descriptor.close() }
        }
        tunDescriptor = null
        stopPacketLoop()
        testRoutePreviewActive = false
        xrayModeActive = false
    }

    private fun failClosed(detail: String) {
        closeTunDescriptor()
        isRunning = false
        publishState(VpnServiceStatus.Error, detail)
        updateNotification(VpnServiceStatus.ServiceActiveNoTun)
        stopSelf()
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

    private fun inspectPacket(buffer: ByteArray, length: Int) {
        packetsRead += 1L
        bytesRead += length.toLong()
        val summary = Ipv4PacketParser.parseSummary(buffer, length)
        if (summary != null) {
            ipv4PacketsRead += 1L
            when (summary.protocol) {
                Ipv4Protocol.Tcp -> tcpPacketsRead += 1L
                Ipv4Protocol.Udp -> udpPacketsRead += 1L
                Ipv4Protocol.Icmp -> icmpPacketsRead += 1L
                Ipv4Protocol.Other -> Unit
            }
            packetHistory.add(summary)
            lastPacketAt = summary.timestamp
        } else {
            lastPacketAt = System.currentTimeMillis()
        }
    }

    private fun resetCounters() {
        packetsRead = 0L
        bytesRead = 0L
        ipv4PacketsRead = 0L
        tcpPacketsRead = 0L
        udpPacketsRead = 0L
        icmpPacketsRead = 0L
        lastPacketAt = null
        lastUiPublishAt = 0L
        packetHistory.clear()
    }

    private fun activeStatus(): VpnServiceStatus =
        if (testRoutePreviewActive || xrayModeActive) VpnServiceStatus.TunTestRouteActive else VpnServiceStatus.TunPreviewActive

    private fun publishActiveState(force: Boolean = false) {
        if (force) lastUiPublishAt = System.currentTimeMillis()
        publishState(
            status = activeStatus(),
            tunTestRouteActive = testRoutePreviewActive || xrayModeActive,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetHistory.lastUpdatedAt(),
            packetInspectorPaused = packetHistory.isPaused(),
            packetSummaries = packetHistory.newestFirst(),
        )
    }

    private fun publishActiveStateThrottled(now: Long = System.currentTimeMillis()) {
        if (now - lastUiPublishAt < UI_PUBLISH_INTERVAL_MS) return
        lastUiPublishAt = now
        publishActiveState()
    }

    private fun publishStoppedState() {
        publishState(
            status = VpnServiceStatus.Stopped,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetHistory.lastUpdatedAt(),
            packetInspectorPaused = packetHistory.isPaused(),
            packetSummaries = packetHistory.newestFirst(),
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
            VpnServiceStatus.TunTestRouteActive -> if (xrayModeActive) {
                "VLESS REALITY VPN active — traffic is routed through the local Xray engine"
            } else {
                "TEST-NET route preview active — packets are counted and dropped"
            }
            VpnServiceStatus.TunPreviewActive -> "TUN preview active — no traffic routes installed"
            else -> "Local VPN preview — no traffic routing yet"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_viroutefs_notification)
            .setContentTitle("ViRouteFS local VPN preview")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    if (xrayModeActive) {
                        "$text. No payload logging or telemetry is enabled."
                    } else {
                        "$text. No default route, DNS servers, payload logging, packet forwarding, proxying, or VPN engines are enabled."
                    },
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
        ipv4PacketsRead: Long = 0L,
        tcpPacketsRead: Long = 0L,
        udpPacketsRead: Long = 0L,
        icmpPacketsRead: Long = 0L,
        lastPacketAt: Long? = null,
        packetSummaryUpdatedAt: Long? = null,
        packetInspectorPaused: Boolean = false,
        packetSummaries: List<PacketSummary> = emptyList(),
        activeTcpSessions: Int = 0,
        tcpSessionStateStats: Map<TcpSessionState, Int> = emptyMap(),
    ) {
        val state = VpnServiceUiState(
            status = status,
            detail = detail,
            tunTestRouteActive = tunTestRouteActive,
            packetsRead = packetsRead,
            bytesRead = bytesRead,
            ipv4PacketsRead = ipv4PacketsRead,
            tcpPacketsRead = tcpPacketsRead,
            udpPacketsRead = udpPacketsRead,
            icmpPacketsRead = icmpPacketsRead,
            lastPacketAt = lastPacketAt,
            packetSummaryUpdatedAt = packetSummaryUpdatedAt,
            packetInspectorPaused = packetInspectorPaused,
            packetSummaries = packetSummaries,
            activeTcpSessions = activeTcpSessions,
            tcpSessionStateStats = tcpSessionStateStats,
        )
        rememberState(state)
        val intent = Intent(VpnServiceController.ACTION_STATE_CHANGED)
            .setPackage(packageName)
            .putExtra(VpnServiceController.EXTRA_STATUS, status.name)
            .putExtra(VpnServiceController.EXTRA_DETAIL, detail)
            .putExtra(VpnServiceController.EXTRA_TEST_ROUTE_ACTIVE, tunTestRouteActive)
            .putExtra(VpnServiceController.EXTRA_PACKETS_READ, packetsRead)
            .putExtra(VpnServiceController.EXTRA_BYTES_READ, bytesRead)
            .putExtra(VpnServiceController.EXTRA_IPV4_PACKETS_READ, ipv4PacketsRead)
            .putExtra(VpnServiceController.EXTRA_TCP_PACKETS_READ, tcpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_UDP_PACKETS_READ, udpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_ICMP_PACKETS_READ, icmpPacketsRead)
            .putExtra(VpnServiceController.EXTRA_LAST_PACKET_AT, lastPacketAt ?: VpnServiceController.NO_PACKET_TIME)
            .putExtra(VpnServiceController.EXTRA_PACKET_SUMMARY_UPDATED_AT, packetSummaryUpdatedAt ?: VpnServiceController.NO_PACKET_TIME)
            .putExtra(VpnServiceController.EXTRA_PACKET_INSPECTOR_PAUSED, packetInspectorPaused)
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_PACKET_SUMMARIES,
                ArrayList(packetSummaries.map(VpnServiceController::encodePacketSummary)),
            )
            .putExtra(VpnServiceController.EXTRA_ACTIVE_TCP_SESSIONS, activeTcpSessions)
            .putStringArrayListExtra(
                VpnServiceController.EXTRA_TCP_SESSION_STATE_STATS,
                ArrayList(VpnServiceController.encodeTcpSessionStateStats(tcpSessionStateStats)),
            )
        sendBroadcast(intent)
    }

    private fun Intent.vlessProfileExtra(): VlessProfileConfig? {
        getBundleExtra(EXTRA_VLESS_PROFILE_BUNDLE)?.toVlessProfileConfig()?.let { return it }
        val profileBundle = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_VLESS_PROFILE, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(EXTRA_VLESS_PROFILE) as? Bundle
        }
        profileBundle?.toVlessProfileConfig()?.let { return it }
        @Suppress("DEPRECATION")
        val serializableProfile: Any? = getSerializableExtra(EXTRA_VLESS_PROFILE)
        return serializableProfile as? VlessProfileConfig
    }

    private fun Bundle.toVlessProfileConfig(): VlessProfileConfig? {
        val host = getString(EXTRA_VLESS_HOST)?.trim().orEmpty()
        val uuid = getString(EXTRA_VLESS_UUID)?.trim().orEmpty()
        val name = getString(EXTRA_VLESS_NAME)?.trim().orEmpty().ifBlank { host }
        val port = getInt(EXTRA_VLESS_PORT, -1)
        if (host.isBlank() || uuid.isBlank() || port !in 1..65535) return null
        val security = getString(EXTRA_VLESS_SECURITY)?.trim()?.lowercase()
        return VlessProfileConfig(
            name = name,
            host = host,
            port = port,
            uuid = uuid,
            transportType = getString(EXTRA_VLESS_TRANSPORT),
            securityMode = if (security == VlessSecurityMode.REALITY.wireName) VlessSecurityMode.REALITY else VlessSecurityMode.NONE,
            encryption = getString(EXTRA_VLESS_ENCRYPTION),
            flow = getString(EXTRA_VLESS_FLOW),
            sni = getString(EXTRA_VLESS_SERVER_NAME),
            publicKey = getString(EXTRA_VLESS_PUBLIC_KEY),
            shortId = getString(EXTRA_VLESS_SHORT_ID),
            fingerprint = getString(EXTRA_VLESS_FINGERPRINT),
        )
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
        internal const val ACTION_START_XRAY = "dev.vifs.viroutefs.vpn.START_XRAY"
        internal const val EXTRA_VLESS_PROFILE = "vless_profile"
        internal const val EXTRA_VLESS_PROFILE_BUNDLE = "vless_profile_bundle"
        internal const val EXTRA_VLESS_NAME = "vless_name"
        internal const val EXTRA_VLESS_HOST = "vless_host"
        internal const val EXTRA_VLESS_PORT = "vless_port"
        internal const val EXTRA_VLESS_UUID = "vless_uuid"
        internal const val EXTRA_VLESS_TRANSPORT = "vless_transport"
        internal const val EXTRA_VLESS_SECURITY = "vless_security"
        internal const val EXTRA_VLESS_ENCRYPTION = "vless_encryption"
        internal const val EXTRA_VLESS_FLOW = "vless_flow"
        internal const val EXTRA_VLESS_SERVER_NAME = "vless_server_name"
        internal const val EXTRA_VLESS_PUBLIC_KEY = "vless_public_key"
        internal const val EXTRA_VLESS_SHORT_ID = "vless_short_id"
        internal const val EXTRA_VLESS_FINGERPRINT = "vless_fingerprint"
        private const val SAFE_TUN_PREVIEW_ENABLED = true
        private const val TUN_SESSION_NAME = "ViRouteFS TUN preview"
        private const val XRAY_TUN_SESSION_NAME = "ViRouteFS VLESS REALITY"
        private const val TUN_IPV4_ADDRESS = "10.250.0.2"
        private const val TUN_IPV4_PREFIX_LENGTH = 32
        private const val TUN_IPV6_ADDRESS = "fd00:250::2"
        private const val TUN_IPV6_PREFIX_LENGTH = 128
        private const val XRAY_DNS_SERVER = "1.1.1.1"
        private const val TEST_ROUTE_IPV4_NETWORK = "203.0.113.0"
        private const val TEST_ROUTE_IPV4_PREFIX_LENGTH = 24
        private const val TUN_MTU = 1500
        private const val PACKET_LOOP_JOIN_TIMEOUT_MS = 500L
        private const val UI_PUBLISH_INTERVAL_MS = 250L
    }
}
