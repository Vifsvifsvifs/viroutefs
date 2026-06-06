// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val VLESS_PROTOCOL_PROBE_HISTORY_LIMIT_PER_PROFILE = 20

data class VlessProtocolProbeHistoryItem(
    val profileId: String,
    val profileNameSnapshot: String,
    val serverHost: String,
    val serverPort: Int,
    val targetHost: String,
    val targetPort: Int,
    val timestamp: Long,
    val state: VlessProtocolProbeState,
    val message: String,
    val elapsedMs: Long? = null,
    val securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
    val responseBytes: Int = 0,
)

class VlessProtocolProbeHistoryStore(
    private val historyFile: File,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILENAME))

    suspend fun recentForProfile(profileId: String): List<VlessProtocolProbeHistoryItem> = withContext(Dispatchers.IO) {
        loadAll().filter { it.profileId == profileId }.sortedByDescending { it.timestamp }.take(LIMIT_PER_PROFILE)
    }

    suspend fun add(item: VlessProtocolProbeHistoryItem) = withContext(Dispatchers.IO) {
        val sanitized = item.copy(
            profileNameSnapshot = item.profileNameSnapshot.sanitizeVlessReachabilityMessage(),
            serverHost = item.serverHost.trim().sanitizeVlessReachabilityMessage(),
            targetHost = item.targetHost.trim().sanitizeVlessReachabilityMessage(),
            securityMode = item.securityMode,
            message = item.message.sanitizeVlessReachabilityMessage(),
            responseBytes = item.responseBytes.coerceAtLeast(0),
        )
        val next = (loadAll() + sanitized)
            .groupBy { it.profileId }
            .flatMap { (_, items) -> items.sortedByDescending { it.timestamp }.take(LIMIT_PER_PROFILE) }
            .sortedByDescending { it.timestamp }
        saveAll(next)
    }

    fun loadAll(): List<VlessProtocolProbeHistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(historyFile.readText())
            List(array.length()) { index -> array.getJSONObject(index).toHistoryItem() }.filterNotNull()
        }.getOrDefault(emptyList())
    }

    fun encode(items: List<VlessProtocolProbeHistoryItem>): String = JSONArray(items.map { it.toJson() }).toString(2)

    private fun saveAll(items: List<VlessProtocolProbeHistoryItem>) {
        historyFile.parentFile?.mkdirs()
        historyFile.writeText(encode(items))
    }

    private fun VlessProtocolProbeHistoryItem.toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("profileNameSnapshot", profileNameSnapshot.sanitizeVlessReachabilityMessage())
        put("serverHost", serverHost.sanitizeVlessReachabilityMessage())
        put("serverPort", serverPort)
        put("targetHost", targetHost.sanitizeVlessReachabilityMessage())
        put("targetPort", targetPort)
        put("securityMode", securityMode.wireName)
        put("timestamp", timestamp)
        put("state", state.wireName())
        put("message", message.sanitizeVlessReachabilityMessage())
        put("responseBytes", responseBytes.coerceAtLeast(0))
        elapsedMs?.let { put("elapsedMs", it) }
    }

    private fun JSONObject.toHistoryItem(): VlessProtocolProbeHistoryItem? = runCatching {
        VlessProtocolProbeHistoryItem(
            profileId = getString("profileId"),
            profileNameSnapshot = optString("profileNameSnapshot").sanitizeVlessReachabilityMessage(),
            serverHost = optString("serverHost").sanitizeVlessReachabilityMessage(),
            serverPort = optInt("serverPort"),
            targetHost = optString("targetHost").sanitizeVlessReachabilityMessage(),
            targetPort = optInt("targetPort"),
            securityMode = optString("securityMode", VlessSecurityMode.NONE.wireName).toVlessSecurityMode(),
            timestamp = optLong("timestamp"),
            state = optString("state").toVlessProtocolProbeState(),
            message = optString("message").sanitizeVlessReachabilityMessage(),
            elapsedMs = if (has("elapsedMs")) optLong("elapsedMs") else null,
            responseBytes = optInt("responseBytes", 0).coerceAtLeast(0),
        )
    }.getOrNull()

    companion object {
        const val FILENAME = "vless_protocol_probe_history.json"
        const val LIMIT_PER_PROFILE = VLESS_PROTOCOL_PROBE_HISTORY_LIMIT_PER_PROFILE
    }
}

fun VlessProtocolProbeState.wireName(): String = when (this) {
    VlessProtocolProbeState.TcpConnected -> "tcp_connected"
    VlessProtocolProbeState.TlsHandshakeSuccess -> "tls_handshake_success"
    VlessProtocolProbeState.TlsHandshakeFailed -> "tls_handshake_failed"
    VlessProtocolProbeState.VlessRequestSent -> "vless_request_sent"
    VlessProtocolProbeState.RequestSentNoImmediateResponse -> "request_sent_no_immediate_response"
    VlessProtocolProbeState.ResponseReceived -> "response_received"
    VlessProtocolProbeState.ServerKeptConnectionBriefly -> "server_kept_connection_briefly"
    VlessProtocolProbeState.ServerClosedConnection -> "server_closed_connection"
    VlessProtocolProbeState.InvalidEmptyResponse -> "invalid_empty_response"
    VlessProtocolProbeState.Timeout -> "timeout"
    VlessProtocolProbeState.Refused -> "refused"
    VlessProtocolProbeState.HostDnsError -> "host_dns_error"
    VlessProtocolProbeState.ValidationError -> "validation_error"
    VlessProtocolProbeState.UnsupportedSecurityMode -> "unsupported_security_mode"
}

fun String.toVlessProtocolProbeState(): VlessProtocolProbeState = when (lowercase()) {
    "tcp_connected" -> VlessProtocolProbeState.TcpConnected
    "tls_handshake_success" -> VlessProtocolProbeState.TlsHandshakeSuccess
    "tls_handshake_failed" -> VlessProtocolProbeState.TlsHandshakeFailed
    "vless_request_sent" -> VlessProtocolProbeState.VlessRequestSent
    "request_sent_no_immediate_response" -> VlessProtocolProbeState.RequestSentNoImmediateResponse
    "response_received" -> VlessProtocolProbeState.ResponseReceived
    "server_kept_connection_briefly" -> VlessProtocolProbeState.ServerKeptConnectionBriefly
    "server_closed_connection" -> VlessProtocolProbeState.ServerClosedConnection
    "invalid_empty_response" -> VlessProtocolProbeState.InvalidEmptyResponse
    "timeout" -> VlessProtocolProbeState.Timeout
    "refused" -> VlessProtocolProbeState.Refused
    "host_dns_error" -> VlessProtocolProbeState.HostDnsError
    "validation_error" -> VlessProtocolProbeState.ValidationError
    "unsupported_security_mode" -> VlessProtocolProbeState.UnsupportedSecurityMode
    else -> VlessProtocolProbeState.HostDnsError
}

private fun String.toVlessSecurityMode(): VlessSecurityMode = VlessSecurityMode.entries.firstOrNull { it.wireName.equals(this, ignoreCase = true) } ?: VlessSecurityMode.NONE
