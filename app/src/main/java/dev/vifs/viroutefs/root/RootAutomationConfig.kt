// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

enum class RootAutomationTarget(val displayName: String) {
    ConnectionAdaptation("Адаптация соединений"),
    AppFirewall("Файрвол приложений"),
    NetworkGuard("Защита от утечек"),
}

enum class RootAutomationNetwork(val displayName: String) {
    Any("Любая сеть"),
    Wifi("Wi‑Fi"),
    Cellular("Мобильная сеть"),
}

enum class RootAutomationScreen(val displayName: String) {
    Any("Экран не важен"),
    On("Экран включён"),
    Off("Экран выключен"),
}

data class RootAutomationConfig(
    val target: RootAutomationTarget = RootAutomationTarget.ConnectionAdaptation,
    val network: RootAutomationNetwork = RootAutomationNetwork.Any,
    val screen: RootAutomationScreen = RootAutomationScreen.Any,
    val startHour: Int = 0,
    val endHour: Int = 0,
) {
    init {
        require(startHour in 0..23 && endHour in 0..23)
    }

    val usesWholeDay: Boolean
        get() = startHour == endHour
}

internal class RootAutomationConfigRepository(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, ROOT_AUTOMATION_DIRECTORY)
    private val file = File(directory, ROOT_AUTOMATION_FILE)

    fun load(): RootAutomationConfig = runCatching {
        if (!file.isFile || file.length() !in 1..ROOT_AUTOMATION_MAX_BYTES.toLong()) {
            return@runCatching RootAutomationConfig()
        }
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.optInt("version") == ROOT_AUTOMATION_VERSION)
        RootAutomationConfig(
            target = enumValueOrDefault(root.optString("target"), RootAutomationTarget.ConnectionAdaptation),
            network = enumValueOrDefault(root.optString("network"), RootAutomationNetwork.Any),
            screen = enumValueOrDefault(root.optString("screen"), RootAutomationScreen.Any),
            startHour = root.optInt("startHour").coerceIn(0, 23),
            endHour = root.optInt("endHour").coerceIn(0, 23),
        )
    }.getOrDefault(RootAutomationConfig())

    fun save(config: RootAutomationConfig) {
        require(directory.exists() || directory.mkdirs()) { "Could not create root automation directory." }
        val bytes = JSONObject()
            .put("version", ROOT_AUTOMATION_VERSION)
            .put("target", config.target.name)
            .put("network", config.network.name)
            .put("screen", config.screen.name)
            .put("startHour", config.startHour)
            .put("endHour", config.endHour)
            .toString(2)
            .toByteArray(Charsets.UTF_8)
        require(bytes.size <= ROOT_AUTOMATION_MAX_BYTES)
        val temporary = File(directory, "$ROOT_AUTOMATION_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) error("Could not replace root automation configuration.")
        if (!temporary.renameTo(file)) error("Could not commit root automation configuration.")
    }
}

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == value } ?: default

private const val ROOT_AUTOMATION_VERSION = 1
private const val ROOT_AUTOMATION_MAX_BYTES = 8 * 1024
private const val ROOT_AUTOMATION_DIRECTORY = "root-automation"
private const val ROOT_AUTOMATION_FILE = "automation.json"
