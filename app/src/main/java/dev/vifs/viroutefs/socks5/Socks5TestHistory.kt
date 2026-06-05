// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val HISTORY_LIMIT_PER_PROFILE = 20

data class Socks5TestHistoryItem(
    val profileId: String,
    val profileNameSnapshot: String,
    val testType: Socks5DiagnosticTestType,
    val targetHost: String? = null,
    val targetPort: Int? = null,
    val timestamp: Long,
    val state: Socks5DiagnosticState,
    val message: String,
    val elapsedMs: Long? = null,
)

class Socks5TestHistoryStore(
    private val historyFile: File,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILENAME))

    suspend fun recentForProfile(profileId: String): List<Socks5TestHistoryItem> = withContext(Dispatchers.IO) {
        loadAll().filter { it.profileId == profileId }.sortedByDescending { it.timestamp }.take(HISTORY_LIMIT_PER_PROFILE)
    }

    suspend fun add(item: Socks5TestHistoryItem) = withContext(Dispatchers.IO) {
        val sanitized = item.copy(message = item.message.sanitizeSocks5Diagnostic(), targetHost = item.targetHost?.trim())
        val next = (listOf(sanitized) + loadAll())
            .groupBy { it.profileId }
            .flatMap { (_, items) -> items.sortedByDescending { it.timestamp }.take(HISTORY_LIMIT_PER_PROFILE) }
            .sortedByDescending { it.timestamp }
        saveAll(next)
    }

    suspend fun clearProfile(profileId: String) = withContext(Dispatchers.IO) {
        saveAll(loadAll().filterNot { it.profileId == profileId })
    }

    fun loadAll(): List<Socks5TestHistoryItem> {
        if (!historyFile.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(historyFile.readText())
            (0 until root.length()).mapNotNull { index -> root.optJSONObject(index)?.toHistoryItem() }
        }.getOrDefault(emptyList())
    }

    fun encode(items: List<Socks5TestHistoryItem>): String = JSONArray(items.map { it.toJson() }).toString(2)

    private fun saveAll(items: List<Socks5TestHistoryItem>) {
        historyFile.parentFile?.mkdirs()
        historyFile.writeText(encode(items))
    }

    private fun Socks5TestHistoryItem.toJson(): JSONObject = JSONObject().apply {
        put("profileId", profileId)
        put("profileNameSnapshot", profileNameSnapshot)
        put("testType", testType.name.lowercase())
        put("targetHost", targetHost)
        put("targetPort", targetPort)
        put("timestamp", timestamp)
        put("state", state.name)
        put("message", message.sanitizeSocks5Diagnostic())
        put("elapsedMs", elapsedMs)
    }

    private fun JSONObject.toHistoryItem(): Socks5TestHistoryItem? = runCatching {
        Socks5TestHistoryItem(
            profileId = getString("profileId"),
            profileNameSnapshot = optString("profileNameSnapshot"),
            testType = Socks5DiagnosticTestType.fromWireName(optString("testType")),
            targetHost = optNullableString("targetHost"),
            targetPort = optInt("targetPort").takeIf { it in 1..65535 },
            timestamp = optLong("timestamp"),
            state = Socks5DiagnosticState.valueOf(optString("state")),
            message = optString("message").sanitizeSocks5Diagnostic(),
            elapsedMs = optLong("elapsedMs").takeIf { has("elapsedMs") && !isNull("elapsedMs") },
        )
    }.getOrNull()

    companion object {
        const val FILENAME = "socks5_test_history.json"
        const val LIMIT_PER_PROFILE = HISTORY_LIMIT_PER_PROFILE
    }
}

private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
