// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

/**
 * Payload-free connection metadata emitted by the local sing-box runtime.
 *
 * No packet contents, HTTP bodies, passwords, messages, or files are stored.
 */
internal data class VpnConnectionFlow(
    val id: String,
    val createdAt: Long,
    val closedAt: Long?,
    val network: String,
    val source: String,
    val destination: String,
    val domain: String,
    val protocol: String,
    val appPackages: List<String>,
    val processPath: String,
    val outboundTag: String,
    val outboundType: String,
    val matchedRule: String,
    val uplinkBytes: Long,
    val downlinkBytes: Long,
) {
    val isActive: Boolean
        get() = closedAt == null

    val totalBytes: Long
        get() = uplinkBytes + downlinkBytes
}
