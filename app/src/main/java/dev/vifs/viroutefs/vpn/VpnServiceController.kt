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
import dev.vifs.viroutefs.routing.LiveRouteDecisionPreview
import org.json.JSONObject

internal enum class VpnServiceStatus {
    Off,
    PermissionRequired,
    NotificationPermissionRequired,
    Starting,
    ServiceActiveNoTun,
    TunPreviewActive,
    TunTestRouteActive,
    Stopped,
    Error,
}

internal data class VpnServiceUiState(
    val status: VpnServiceStatus,
    val detail: String? = null,
    val tunTestRouteActive: Boolean = false,
    val packetsRead: Long = 0L,
    val bytesRead: Long = 0L,
    val ipv4PacketsRead: Long = 0L,
    val tcpPacketsRead: Long = 0L,
    val udpPacketsRead: Long = 0L,
    val icmpPacketsRead: Long = 0L,
    val lastPacketAt: Long? = null,
    val packetRoutePreviews: List<LiveRouteDecisionPreview> = emptyList(),
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

    fun currentState(): VpnServiceUiState = ViRouteVpnService.lastState

    fun startLocalService(testRoutePreviewEnabled: Boolean = false) {
        publishState(VpnServiceStatus.Starting, tunTestRouteActive = testRoutePreviewEnabled)
        val intent = Intent(appContext, ViRouteVpnService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_TEST_ROUTE_ENABLED, testRoutePreviewEnabled)
        if (ViRouteVpnService.isRunning) {
            appContext.startService(intent)
        } else {
            ContextCompat.startForegroundService(appContext, intent)
        }
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
                onState(
                    VpnServiceUiState(
                        status = status,
                        detail = intent.getStringExtra(EXTRA_DETAIL),
                        tunTestRouteActive = intent.getBooleanExtra(EXTRA_TEST_ROUTE_ACTIVE, false),
                        packetsRead = intent.getLongExtra(EXTRA_PACKETS_READ, 0L),
                        bytesRead = intent.getLongExtra(EXTRA_BYTES_READ, 0L),
                        ipv4PacketsRead = intent.getLongExtra(EXTRA_IPV4_PACKETS_READ, 0L),
                        tcpPacketsRead = intent.getLongExtra(EXTRA_TCP_PACKETS_READ, 0L),
                        udpPacketsRead = intent.getLongExtra(EXTRA_UDP_PACKETS_READ, 0L),
                        icmpPacketsRead = intent.getLongExtra(EXTRA_ICMP_PACKETS_READ, 0L),
                        lastPacketAt = intent.getLongExtra(EXTRA_LAST_PACKET_AT, NO_PACKET_TIME)
                            .takeUnless { it == NO_PACKET_TIME },
                        packetRoutePreviews = intent.getStringArrayListExtra(EXTRA_PACKET_ROUTE_PREVIEWS)
                            ?.mapNotNull { decodeRoutePreview(it) }
                            ?: emptyList(),
                    ),
                )
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
        packetRoutePreviews: List<LiveRouteDecisionPreview> = emptyList(),
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
            packetRoutePreviews = packetRoutePreviews,
        )
        ViRouteVpnService.rememberState(state)
        val intent = Intent(ACTION_STATE_CHANGED)
            .setPackage(appContext.packageName)
            .putExtra(EXTRA_STATUS, status.name)
            .putExtra(EXTRA_DETAIL, detail)
            .putExtra(EXTRA_TEST_ROUTE_ACTIVE, tunTestRouteActive)
            .putExtra(EXTRA_PACKETS_READ, packetsRead)
            .putExtra(EXTRA_BYTES_READ, bytesRead)
            .putExtra(EXTRA_IPV4_PACKETS_READ, ipv4PacketsRead)
            .putExtra(EXTRA_TCP_PACKETS_READ, tcpPacketsRead)
            .putExtra(EXTRA_UDP_PACKETS_READ, udpPacketsRead)
            .putExtra(EXTRA_ICMP_PACKETS_READ, icmpPacketsRead)
            .putExtra(EXTRA_LAST_PACKET_AT, lastPacketAt ?: NO_PACKET_TIME)
            .putStringArrayListExtra(
                EXTRA_PACKET_ROUTE_PREVIEWS,
                ArrayList(packetRoutePreviews.map { encodeRoutePreview(it) }),
            )
        appContext.sendBroadcast(intent)
    }

    companion object {
        internal const val ACTION_START = "dev.vifs.viroutefs.vpn.START"
        internal const val ACTION_STOP = "dev.vifs.viroutefs.vpn.STOP"
        internal const val ACTION_STATE_CHANGED = "dev.vifs.viroutefs.vpn.STATE_CHANGED"
        internal const val EXTRA_STATUS = "status"
        internal const val EXTRA_DETAIL = "detail"
        internal const val EXTRA_TEST_ROUTE_ENABLED = "test_route_enabled"
        internal const val EXTRA_TEST_ROUTE_ACTIVE = "test_route_active"
        internal const val EXTRA_PACKETS_READ = "packets_read"
        internal const val EXTRA_BYTES_READ = "bytes_read"
        internal const val EXTRA_IPV4_PACKETS_READ = "ipv4_packets_read"
        internal const val EXTRA_TCP_PACKETS_READ = "tcp_packets_read"
        internal const val EXTRA_UDP_PACKETS_READ = "udp_packets_read"
        internal const val EXTRA_ICMP_PACKETS_READ = "icmp_packets_read"
        internal const val EXTRA_LAST_PACKET_AT = "last_packet_at"
        internal const val EXTRA_PACKET_ROUTE_PREVIEWS = "packet_route_previews"
        internal const val NO_PACKET_TIME = -1L

        internal fun encodeRoutePreview(preview: LiveRouteDecisionPreview): String = JSONObject()
            .put("observedAt", preview.observedAt)
            .put("protocol", preview.protocol)
            .put("source", preview.source)
            .put("destination", preview.destination)
            .put("matchedRuleName", preview.matchedRuleName)
            .put("selectedProfileName", preview.selectedProfileName)
            .put("selectedProfileType", preview.selectedProfileType)
            .put("warning", preview.warning)
            .put("decisionText", preview.decisionText)
            .toString()

        internal fun decodeRoutePreview(encoded: String): LiveRouteDecisionPreview? = runCatching {
            val json = JSONObject(encoded)
            LiveRouteDecisionPreview(
                observedAt = json.optLong("observedAt"),
                protocol = json.optString("protocol"),
                source = json.optString("source"),
                destination = json.optString("destination"),
                matchedRuleName = json.optString("matchedRuleName").takeUnless { it.isBlank() || json.isNull("matchedRuleName") },
                selectedProfileName = json.optString("selectedProfileName"),
                selectedProfileType = json.optString("selectedProfileType"),
                warning = json.optString("warning").takeUnless { it.isBlank() || json.isNull("warning") },
                decisionText = json.optString("decisionText"),
            )
        }.getOrNull()
    }
}
