// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class RootManagedModule {
    ConnectionAdaptation,
    AppFirewall,
    EmergencyNetworkLock,
    LeakProtection,
    PacketCapture,
    Tethering,
    Automation,
    KernelWireGuard,
}

data class RootRuntimeState(
    val transactionId: String,
    val modules: Set<RootManagedModule>,
    val backend: String,
    val updatedAtEpochMillis: Long,
    val recoveryRequired: Boolean,
)

internal class RootRuntimeStateRepository(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, ROOT_STATE_DIRECTORY)
    private val stateFile = File(directory, ROOT_STATE_FILE)

    fun load(): RootRuntimeState? = runCatching {
        if (!stateFile.isFile || stateFile.length() !in 1..ROOT_STATE_MAX_BYTES.toLong()) return@runCatching null
        val root = JSONObject(stateFile.readText(Charsets.UTF_8))
        require(root.optInt("version") == ROOT_STATE_VERSION) { "Unsupported root state version." }
        val modules = root.optJSONArray("modules")?.let { array ->
            buildSet {
                repeat(array.length()) { index ->
                    RootManagedModule.entries.firstOrNull { it.name == array.optString(index) }?.let(::add)
                }
            }
        }.orEmpty()
        RootRuntimeState(
            transactionId = root.getString("transactionId").take(80),
            modules = modules,
            backend = root.optString("backend").take(40),
            updatedAtEpochMillis = root.optLong("updatedAtEpochMillis").coerceAtLeast(0L),
            recoveryRequired = root.optBoolean("recoveryRequired", true),
        )
    }.getOrNull()

    fun markPending(module: RootManagedModule, backend: String): RootRuntimeState {
        require(backend.matches(Regex("[a-z0-9_-]{1,40}"))) { "Invalid root backend." }
        val previous = load()
        val state = RootRuntimeState(
            transactionId = UUID.randomUUID().toString(),
            modules = previous?.modules.orEmpty() + module,
            backend = when (previous?.backend) {
                null, "", backend -> backend
                else -> "mixed"
            },
            updatedAtEpochMillis = System.currentTimeMillis(),
            recoveryRequired = true,
        )
        save(state)
        return state
    }

    fun removeModule(module: RootManagedModule) {
        val previous = load() ?: return
        val remaining = previous.modules - module
        if (remaining.isEmpty()) {
            clear()
        } else {
            save(
                previous.copy(
                    modules = remaining,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    recoveryRequired = true,
                ),
            )
        }
    }

    fun clear() {
        if (stateFile.exists() && !stateFile.delete()) error("Could not clear root recovery state.")
    }

    fun pidFile(): File {
        directory.mkdirs()
        return File(directory, ROOT_ZAPRET_PID_FILE)
    }

    fun logFile(): File {
        directory.mkdirs()
        return File(directory, ROOT_ZAPRET_LOG_FILE)
    }

    private fun save(state: RootRuntimeState) {
        require(directory.exists() || directory.mkdirs()) { "Could not create root state directory." }
        val root = JSONObject()
            .put("version", ROOT_STATE_VERSION)
            .put("transactionId", state.transactionId)
            .put("modules", JSONArray(state.modules.map { it.name }.sorted()))
            .put("backend", state.backend)
            .put("updatedAtEpochMillis", state.updatedAtEpochMillis)
            .put("recoveryRequired", state.recoveryRequired)
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= ROOT_STATE_MAX_BYTES) { "Root state is too large." }
        val temporary = File(directory, "$ROOT_STATE_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (stateFile.exists() && !stateFile.delete()) error("Could not replace root recovery state.")
        if (!temporary.renameTo(stateFile)) error("Could not commit root recovery state.")
    }
}

private const val ROOT_STATE_VERSION = 1
private const val ROOT_STATE_MAX_BYTES = 16 * 1024
private const val ROOT_STATE_DIRECTORY = "root-runtime"
private const val ROOT_STATE_FILE = "managed-state.json"
private const val ROOT_ZAPRET_PID_FILE = "connection-adaptation.pid"
private const val ROOT_ZAPRET_LOG_FILE = "connection-adaptation.log"
