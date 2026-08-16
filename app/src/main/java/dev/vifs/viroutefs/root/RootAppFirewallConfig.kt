// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class RootAppFirewallConfig(
    val blockAllPackages: Set<String> = emptySet(),
    val blockWifiPackages: Set<String> = emptySet(),
    val blockCellularPackages: Set<String> = emptySet(),
    val blockVpnPackages: Set<String> = emptySet(),
) {
    val allPackages: Set<String>
        get() = blockAllPackages + blockWifiPackages + blockCellularPackages + blockVpnPackages

    val isEmpty: Boolean
        get() = allPackages.isEmpty()

    fun normalized(): RootAppFirewallConfig {
        val blockAll = blockAllPackages.validPackageNames()
        return copy(
            blockAllPackages = blockAll,
            blockWifiPackages = blockWifiPackages.validPackageNames() - blockAll,
            blockCellularPackages = blockCellularPackages.validPackageNames() - blockAll,
            blockVpnPackages = blockVpnPackages.validPackageNames() - blockAll,
        )
    }
}

internal class RootAppFirewallConfigRepository(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, ROOT_FIREWALL_DIRECTORY)
    private val file = File(directory, ROOT_FIREWALL_FILE)

    fun load(): RootAppFirewallConfig = runCatching {
        if (!file.isFile || file.length() !in 1..ROOT_FIREWALL_MAX_BYTES.toLong()) {
            return@runCatching RootAppFirewallConfig()
        }
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.optInt("version") == ROOT_FIREWALL_VERSION)
        RootAppFirewallConfig(
            blockAllPackages = root.stringSet("blockAll"),
            blockWifiPackages = root.stringSet("blockWifi"),
            blockCellularPackages = root.stringSet("blockCellular"),
            blockVpnPackages = root.stringSet("blockVpn"),
        ).normalized()
    }.getOrElse { RootAppFirewallConfig() }

    fun save(config: RootAppFirewallConfig) {
        val normalized = config.normalized()
        require(directory.exists() || directory.mkdirs()) { "Could not create root firewall directory." }
        val root = JSONObject()
            .put("version", ROOT_FIREWALL_VERSION)
            .put("blockAll", JSONArray(normalized.blockAllPackages.sorted()))
            .put("blockWifi", JSONArray(normalized.blockWifiPackages.sorted()))
            .put("blockCellular", JSONArray(normalized.blockCellularPackages.sorted()))
            .put("blockVpn", JSONArray(normalized.blockVpnPackages.sorted()))
        val bytes = root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size <= ROOT_FIREWALL_MAX_BYTES) { "Root firewall configuration is too large." }
        val temporary = File(directory, "$ROOT_FIREWALL_FILE.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        if (file.exists() && !file.delete()) error("Could not replace root firewall configuration.")
        if (!temporary.renameTo(file)) error("Could not commit root firewall configuration.")
    }
}

private fun Set<String>.validPackageNames(): Set<String> = asSequence()
    .map(String::trim)
    .filter { it.length in 3..255 && it.matches(PACKAGE_NAME_PATTERN) }
    .take(ROOT_FIREWALL_MAX_PACKAGES)
    .toCollection(linkedSetOf())

private fun JSONObject.stringSet(name: String): Set<String> {
    val array = optJSONArray(name) ?: return emptySet()
    return buildSet {
        repeat(minOf(array.length(), ROOT_FIREWALL_MAX_PACKAGES)) { index ->
            array.optString(index).trim().takeIf { it.matches(PACKAGE_NAME_PATTERN) }?.let(::add)
        }
    }
}

private val PACKAGE_NAME_PATTERN = Regex("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")
private const val ROOT_FIREWALL_VERSION = 1
private const val ROOT_FIREWALL_MAX_PACKAGES = 2_048
private const val ROOT_FIREWALL_MAX_BYTES = 256 * 1024
private const val ROOT_FIREWALL_DIRECTORY = "root-firewall"
private const val ROOT_FIREWALL_FILE = "policies.json"
