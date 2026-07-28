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
import dev.vifs.viroutefs.runtime.tcp.TcpSessionState
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal enum class VpnServiceStatus {
    Off,
    PermissionRequired,
    NotificationPermissionRequired,
    Starting,
    RuntimeActive,
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
    val packetSummaryUpdatedAt: Long? = null,
    val packetInspectorPaused: Boolean = false,
    val packetSummaries: List<PacketSummary> = emptyList(),
    val connectionFlows: List<VpnConnectionFlow> = emptyList(),
    val activeTcpSessions: Int = 0,
    val tcpSessionStateStats: Map<TcpSessionState, Int> = emptyMap(),
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

    fun reloadLocalService(testRoutePreviewEnabled: Boolean = false) {
        if (!ViRouteVpnService.isRunning) return
        appContext.startService(
            Intent(appContext, ViRouteVpnService::class.java)
                .setAction(ACTION_RELOAD)
                .putExtra(EXTRA_TEST_ROUTE_ENABLED, testRoutePreviewEnabled),
        )
    }

    fun clearPacketSummaries() {
        if (!ViRouteVpnService.isRunning) {
            val state = ViRouteVpnService.lastState.copy(
                packetSummaries = emptyList(),
                connectionFlows = emptyList(),
                packetSummaryUpdatedAt = System.currentTimeMillis(),
            )
            publishState(state)
            return
        }
        appContext.startService(Intent(appContext, ViRouteVpnService::class.java).setAction(ACTION_CLEAR_PACKET_SUMMARIES))
    }

    fun setPacketInspectorPaused(paused: Boolean) {
        if (!ViRouteVpnService.isRunning) {
            val state = ViRouteVpnService.lastState.copy(
                packetInspectorPaused = paused,
                packetSummaryUpdatedAt = System.currentTimeMillis(),
            )
            publishState(state)
            return
        }
        appContext.startService(
            Intent(appContext, ViRouteVpnService::class.java)
                .setAction(ACTION_SET_PACKET_INSPECTOR_PAUSED)
                .putExtra(EXTRA_PACKET_INSPECTOR_PAUSED, paused),
        )
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
                        packetSummaryUpdatedAt = intent.getLongExtra(EXTRA_PACKET_SUMMARY_UPDATED_AT, NO_PACKET_TIME)
                            .takeUnless { it == NO_PACKET_TIME },
                        packetInspectorPaused = intent.getBooleanExtra(EXTRA_PACKET_INSPECTOR_PAUSED, false),
                        packetSummaries = decodePacketSummaries(intent.getStringArrayListExtra(EXTRA_PACKET_SUMMARIES)),
                        connectionFlows = decodeConnectionFlows(
                            intent.getStringArrayListExtra(EXTRA_CONNECTION_FLOWS),
                        ),
                        activeTcpSessions = intent.getIntExtra(EXTRA_ACTIVE_TCP_SESSIONS, 0),
                        tcpSessionStateStats = decodeTcpSessionStateStats(
                            intent.getStringArrayListExtra(EXTRA_TCP_SESSION_STATE_STATS),
                        ),
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
        packetSummaryUpdatedAt: Long? = null,
        packetInspectorPaused: Boolean = false,
        packetSummaries: List<PacketSummary> = emptyList(),
        connectionFlows: List<VpnConnectionFlow> = emptyList(),
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
            connectionFlows = connectionFlows,
            activeTcpSessions = activeTcpSessions,
            tcpSessionStateStats = tcpSessionStateStats,
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
            .putExtra(EXTRA_PACKET_SUMMARY_UPDATED_AT, packetSummaryUpdatedAt ?: NO_PACKET_TIME)
            .putExtra(EXTRA_PACKET_INSPECTOR_PAUSED, packetInspectorPaused)
            .putStringArrayListExtra(EXTRA_PACKET_SUMMARIES, ArrayList(packetSummaries.map(::encodePacketSummary)))
            .putStringArrayListExtra(EXTRA_CONNECTION_FLOWS, ArrayList(connectionFlows.map(::encodeConnectionFlow)))
            .putExtra(EXTRA_ACTIVE_TCP_SESSIONS, activeTcpSessions)
            .putStringArrayListExtra(EXTRA_TCP_SESSION_STATE_STATS, ArrayList(encodeTcpSessionStateStats(tcpSessionStateStats)))
        appContext.sendBroadcast(intent)
    }

    private fun publishState(state: VpnServiceUiState) {
        publishState(
            status = state.status,
            detail = state.detail,
            tunTestRouteActive = state.tunTestRouteActive,
            packetsRead = state.packetsRead,
            bytesRead = state.bytesRead,
            ipv4PacketsRead = state.ipv4PacketsRead,
            tcpPacketsRead = state.tcpPacketsRead,
            udpPacketsRead = state.udpPacketsRead,
            icmpPacketsRead = state.icmpPacketsRead,
            lastPacketAt = state.lastPacketAt,
            packetSummaryUpdatedAt = state.packetSummaryUpdatedAt,
            packetInspectorPaused = state.packetInspectorPaused,
            packetSummaries = state.packetSummaries,
            connectionFlows = state.connectionFlows,
            activeTcpSessions = state.activeTcpSessions,
            tcpSessionStateStats = state.tcpSessionStateStats,
        )
    }

    companion object {
        internal const val ACTION_START = "dev.vifs.viroutefs.vpn.START"
        internal const val ACTION_RELOAD = "dev.vifs.viroutefs.vpn.RELOAD"
        internal const val ACTION_STOP = "dev.vifs.viroutefs.vpn.STOP"
        internal const val ACTION_CLEAR_PACKET_SUMMARIES = "dev.vifs.viroutefs.vpn.CLEAR_PACKET_SUMMARIES"
        internal const val ACTION_SET_PACKET_INSPECTOR_PAUSED = "dev.vifs.viroutefs.vpn.SET_PACKET_INSPECTOR_PAUSED"
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
        internal const val EXTRA_PACKET_SUMMARY_UPDATED_AT = "packet_summary_updated_at"
        internal const val EXTRA_PACKET_INSPECTOR_PAUSED = "packet_inspector_paused"
        internal const val EXTRA_PACKET_SUMMARIES = "packet_summaries"
        internal const val EXTRA_CONNECTION_FLOWS = "connection_flows"
        internal const val EXTRA_ACTIVE_TCP_SESSIONS = "active_tcp_sessions"
        internal const val EXTRA_TCP_SESSION_STATE_STATS = "tcp_session_state_stats"
        internal const val NO_PACKET_TIME = -1L

        internal fun encodePacketSummary(summary: PacketSummary): String = listOf(
            summary.timestamp.toString(),
            summary.protocol.name,
            summary.srcIp,
            summary.srcPort?.toString().orEmpty(),
            summary.dstIp,
            summary.dstPort?.toString().orEmpty(),
            summary.packetSize.toString(),
        ).joinToString(PACKET_SUMMARY_SEPARATOR)

        internal fun decodePacketSummaries(encoded: ArrayList<String>?): List<PacketSummary> = encoded
            .orEmpty()
            .mapNotNull(::decodePacketSummary)

        internal fun encodeConnectionFlow(flow: VpnConnectionFlow): String = listOf(
            flow.id,
            flow.createdAt.toString(),
            flow.closedAt?.toString().orEmpty(),
            flow.network,
            flow.source,
            flow.destination,
            flow.domain,
            flow.protocol,
            flow.appPackages.joinToString(FLOW_PACKAGE_SEPARATOR),
            flow.processPath,
            flow.outboundTag,
            flow.outboundType,
            flow.matchedRule,
            flow.uplinkBytes.toString(),
            flow.downlinkBytes.toString(),
        ).joinToString(CONNECTION_FLOW_SEPARATOR, transform = ::encodeFlowField)

        internal fun decodeConnectionFlows(encoded: ArrayList<String>?): List<VpnConnectionFlow> = encoded
            .orEmpty()
            .mapNotNull(::decodeConnectionFlow)

        private fun decodeConnectionFlow(encoded: String): VpnConnectionFlow? {
            val parts = encoded.split(CONNECTION_FLOW_SEPARATOR).map(::decodeFlowField)
            if (parts.size != CONNECTION_FLOW_FIELD_COUNT) return null
            return runCatching {
                VpnConnectionFlow(
                    id = parts[0],
                    createdAt = parts[1].toLong(),
                    closedAt = parts[2].takeIf(String::isNotBlank)?.toLong(),
                    network = parts[3],
                    source = parts[4],
                    destination = parts[5],
                    domain = parts[6],
                    protocol = parts[7],
                    appPackages = parts[8].split(FLOW_PACKAGE_SEPARATOR).filter(String::isNotBlank),
                    processPath = parts[9],
                    outboundTag = parts[10],
                    outboundType = parts[11],
                    matchedRule = parts[12],
                    uplinkBytes = parts[13].toLong(),
                    downlinkBytes = parts[14].toLong(),
                )
            }.getOrNull()
        }

        private fun decodePacketSummary(encoded: String): PacketSummary? {
            val parts = encoded.split(PACKET_SUMMARY_SEPARATOR)
            if (parts.size != PACKET_SUMMARY_FIELD_COUNT) return null
            return runCatching {
                PacketSummary(
                    timestamp = parts[0].toLong(),
                    protocol = Ipv4Protocol.valueOf(parts[1]),
                    srcIp = parts[2],
                    srcPort = parts[3].takeIf { it.isNotBlank() }?.toInt(),
                    dstIp = parts[4],
                    dstPort = parts[5].takeIf { it.isNotBlank() }?.toInt(),
                    packetSize = parts[6].toInt(),
                )
            }.getOrNull()
        }

        internal fun encodeTcpSessionStateStats(stats: Map<TcpSessionState, Int>): List<String> = stats.map { (state, count) ->
            listOf(state.name, count.toString()).joinToString(TCP_SESSION_STATE_SEPARATOR)
        }

        internal fun decodeTcpSessionStateStats(encoded: ArrayList<String>?): Map<TcpSessionState, Int> = encoded
            .orEmpty()
            .mapNotNull { item ->
                val parts = item.split(TCP_SESSION_STATE_SEPARATOR)
                if (parts.size != TCP_SESSION_STATE_FIELD_COUNT) return@mapNotNull null
                val state = runCatching { TcpSessionState.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
                val count = parts[1].toIntOrNull() ?: return@mapNotNull null
                state to count
            }
            .toMap()

        private const val PACKET_SUMMARY_SEPARATOR = "|"
        private const val PACKET_SUMMARY_FIELD_COUNT = 7
        private const val CONNECTION_FLOW_SEPARATOR = "|"
        private const val FLOW_PACKAGE_SEPARATOR = "\u001E"
        private const val CONNECTION_FLOW_FIELD_COUNT = 15
        private const val TCP_SESSION_STATE_SEPARATOR = ":"
        private const val TCP_SESSION_STATE_FIELD_COUNT = 2

        private fun encodeFlowField(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun decodeFlowField(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    }
}
