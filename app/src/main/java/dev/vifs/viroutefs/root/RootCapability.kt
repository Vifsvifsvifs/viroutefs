// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

data class RootCapabilitySnapshot(
    val rootGranted: Boolean,
    val uid: Int?,
    val identity: String,
    val selinuxMode: String,
    val kernelRelease: String,
    val effectiveCapabilitiesHex: String,
    val hasIp: Boolean,
    val hasIptables: Boolean,
    val hasIp6tables: Boolean,
    val hasNftables: Boolean,
    val hasNfQueue: Boolean,
    val hasTcpdump: Boolean,
    val hasTrafficControl: Boolean,
    val hasConntrack: Boolean,
    val hasWireGuardKernelModule: Boolean,
    val checkedAtEpochMillis: Long,
)

data class RootProbeOutcome(
    val snapshot: RootCapabilitySnapshot? = null,
    val message: String,
    val suCommandVisible: Boolean,
) {
    val granted: Boolean
        get() = snapshot?.rootGranted == true
}

internal fun parseRootCapabilityProbe(
    output: String,
    checkedAtEpochMillis: Long = System.currentTimeMillis(),
): RootCapabilitySnapshot {
    val values = output.lineSequence()
        .map(String::trim)
        .filter { '=' in it }
        .associate { line -> line.substringBefore('=') to line.substringAfter('=') }
    fun flag(name: String): Boolean = values[name] == "1"
    val uid = values["uid"]?.toIntOrNull()
    return RootCapabilitySnapshot(
        rootGranted = uid == 0,
        uid = uid,
        identity = values["identity"].orEmpty().take(ROOT_PROBE_VALUE_LIMIT),
        selinuxMode = values["selinux"].orEmpty().take(ROOT_PROBE_VALUE_LIMIT),
        kernelRelease = values["kernel"].orEmpty().take(ROOT_PROBE_VALUE_LIMIT),
        effectiveCapabilitiesHex = values["cap_eff"].orEmpty().take(32),
        hasIp = flag("ip"),
        hasIptables = flag("iptables"),
        hasIp6tables = flag("ip6tables"),
        hasNftables = flag("nft"),
        hasNfQueue = flag("nfqueue"),
        hasTcpdump = flag("tcpdump"),
        hasTrafficControl = flag("tc"),
        hasConntrack = flag("conntrack"),
        hasWireGuardKernelModule = flag("wireguard_kernel"),
        checkedAtEpochMillis = checkedAtEpochMillis,
    )
}

private const val ROOT_PROBE_VALUE_LIMIT = 256
