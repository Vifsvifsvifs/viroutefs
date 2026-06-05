// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val VLESS_HISTORY_LIMIT_PER_PROFILE = 20

data class VlessTcpReachabilityHistoryItem(
    val profileId: String,
    val profileNameSnapshot: String,
    val host: String,
    val port: Int,
    val timestamp: Long,
    val state: VlessTcpReachabilityState,
    val message: String,
    val elapsedMs: Long? = null,
)

class VlessTcpReachabilityHistoryStore(
    private val historyFile: File,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILENAME))

    suspend fun recentForProfile(profileId: String): List<VlessTcpReachabilityHistoryItem> = withContext(Dispatchers.IO) {
        loadAll().filter { it.profileId == profileId }.sortedByDescending { it.timestamp }.take(VLESS_HISTORY_LIMIT_PER_PROFILE)
    }

    suspend fun add(item: VlessTcpReachabilityHistoryItem) = withContext(Dispatchers.IO) {
        val sanitized = item.copy(host = item.host.trim(), message = item.message.sanitizeVlessReachabilityMessage())
        val next = (listOf(sanitized) + loadAll())
            .groupBy { it.profileId }
            .flatMap { (_, items) -> items.sortedByDescending { it.timestamp }.take(VLESS_HISTORY_LIMIT_PER_PROFILE) }
            .sortedByDescending { it.timestamp }
        saveAll(next)
    }

    suspend fun clearProfile(profileId: String) = withContext(Dispatchers.IO) {
        saveAll(loadAll().filterNot { it.profileId == profileId })
    }

    fun loadAll(): List<VlessTcpReachabilityHistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(historyFile.readText())
            (0 until root.length()).mapNotNull { index -> root.optJSONObject(index)?.toHistoryItem() }
        }.getOrDefault(emptyList())
    }

    fun encode(items: List<VlessTcpReachabilityHistoryItem>): String = JSONArray(items.map { it.toJson() }).toString(2)

    private fun saveAll(items: List<VlessTcpReachabilityHistoryItem>) {
        historyFile.parentFile?.mkdirs()
        historyFile.writeText(encode(items))
    }

    private fun VlessTcpReachabilityHistoryItem.toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("profileNameSnapshot", profileNameSnapshot)
        put("host", host.trim())
        put("port", port)
        put("timestamp", timestamp)
        put("state", state.wireName())
        put("message", message.sanitizeVlessReachabilityMessage())
        put("elapsedMs", elapsedMs)
    }

    private fun JSONObject.toHistoryItem(): VlessTcpReachabilityHistoryItem? = runCatching {
        VlessTcpReachabilityHistoryItem(
            profileId = getString("profileId"),
            profileNameSnapshot = optString("profileNameSnapshot"),
            host = optString("host"),
            port = optInt("port"),
            timestamp = optLong("timestamp"),
            state = optString("state").toVlessTcpReachabilityState(),
            message = optString("message").sanitizeVlessReachabilityMessage(),
            elapsedMs = optLong("elapsedMs").takeIf { has("elapsedMs") && !isNull("elapsedMs") },
        )
    }.getOrNull()

    companion object {
        const val FILENAME = "vless_tcp_reachability_history.json"
        const val LIMIT_PER_PROFILE = VLESS_HISTORY_LIMIT_PER_PROFILE
    }
}

fun VlessTcpReachabilityState.wireName(): String = when (this) {
    VlessTcpReachabilityState.Reachable -> "reachable"
    VlessTcpReachabilityState.Timeout -> "timeout"
    VlessTcpReachabilityState.Refused -> "refused"
    VlessTcpReachabilityState.DnsOrHostError -> "dns_host_error"
    VlessTcpReachabilityState.ValidationError -> "validation_error"
}

fun String.toVlessTcpReachabilityState(): VlessTcpReachabilityState = when (lowercase()) {
    "reachable" -> VlessTcpReachabilityState.Reachable
    "timeout" -> VlessTcpReachabilityState.Timeout
    "refused" -> VlessTcpReachabilityState.Refused
    "dns_host_error" -> VlessTcpReachabilityState.DnsOrHostError
    "validation_error" -> VlessTcpReachabilityState.ValidationError
    else -> VlessTcpReachabilityState.DnsOrHostError
}
