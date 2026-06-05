// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType

internal const val SOCKS5_RUNTIME_FORWARDING_NOT_ENABLED =
    "Selected profile is SOCKS5. Runtime forwarding is not enabled yet."
internal const val REMOTE_RUNTIME_FORWARDING_NOT_ENABLED =
    "Selected profile requires runtime forwarding, which is not enabled yet."
internal const val OBSERVATION_ONLY_NO_FORWARDING =
    "Observation only. Packets are not forwarded."

internal data class LiveRouteDecisionPreview(
    val matchedRuleName: String?,
    val selectedProfileName: String,
    val selectedProfileType: String,
    val warnings: List<String>,
) {
    val decisionLine: String = buildString {
        append("Route preview: ")
        append(matchedRuleName?.let { "rule '$it'" } ?: "no matched rule")
        append(" → ")
        append(selectedProfileName)
        append(" (")
        append(selectedProfileType)
        append(')')
    }

    val safetyLine: String = OBSERVATION_ONLY_NO_FORWARDING

    val displayLines: List<String> = buildList {
        add(decisionLine)
        add(safetyLine)
        addAll(warnings)
    }
}

internal class LiveRouteDecisionPreviewer(config: RoutingConfig) {
    private val routeEngine = RouteEngine(config)

    fun preview(summary: PacketSummary): LiveRouteDecisionPreview {
        val decision = routeEngine.simulate(summary.dstIp)
        val selectedProfile = decision.tunnelProfile
        return LiveRouteDecisionPreview(
            matchedRuleName = decision.matchedRule.takeUnless { it.type == RouteRuleType.DEFAULT }?.name
                ?: decision.matchedRule.name,
            selectedProfileName = selectedProfile.name,
            selectedProfileType = selectedProfile.type.label,
            warnings = selectedProfile.runtimeForwardingWarnings(),
        )
    }

    private fun TunnelProfile.runtimeForwardingWarnings(): List<String> = when (type) {
        TunnelType.Socks5,
        TunnelType.Socks5Mock -> listOf(SOCKS5_RUNTIME_FORWARDING_NOT_ENABLED)
        TunnelType.Direct,
        TunnelType.Block -> emptyList()
        else -> if (mockOnly || type.remoteRuntimeForwardingRequired()) {
            listOf(REMOTE_RUNTIME_FORWARDING_NOT_ENABLED)
        } else {
            emptyList()
        }
    }

    private fun TunnelType.remoteRuntimeForwardingRequired(): Boolean = when (this) {
        TunnelType.XrayVlessReality,
        TunnelType.XrayMock,
        TunnelType.VMess,
        TunnelType.Trojan,
        TunnelType.Shadowsocks,
        TunnelType.Shadowsocks2022,
        TunnelType.Hysteria2,
        TunnelType.Hysteria2Mock,
        TunnelType.Tuic,
        TunnelType.NaiveProxy,
        TunnelType.Brook,
        TunnelType.ShadowTls,
        TunnelType.HttpProxy,
        TunnelType.HttpsProxy,
        TunnelType.SshTunnel -> true
        else -> false
    }
}
