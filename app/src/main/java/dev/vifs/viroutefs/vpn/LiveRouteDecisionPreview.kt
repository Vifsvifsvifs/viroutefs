// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import dev.vifs.viroutefs.routing.RouteEngine
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.runtime.tcp.DEV_TCP_BRIDGE_EVENT_OPEN

internal const val VLESS_ROUTE_PREVIEW_RESPONSE_UNTESTED =
    "Latest manual VLESS response classification: not tested."
internal const val REMOTE_RUNTIME_FORWARDING_NOT_ENABLED =
    "Selected profile requires runtime forwarding, which is not enabled yet."
internal const val OBSERVATION_ONLY_NO_FORWARDING =
    "Observation only. Packets are not forwarded."
internal const val WOULD_CREATE_TCP_SESSION = "Would create TCP session"

internal data class LiveRouteDecisionPreview(
    val matchedRuleName: String?,
    val selectedProfileName: String,
    val selectedProfileType: String,
    val warnings: List<String>,
    val tcpSessionObservationLine: String?,
    val devSessionObservationLine: String? = null,
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
        tcpSessionObservationLine?.let(::add)
        devSessionObservationLine?.let(::add)
        addAll(warnings)
    }
}

internal class LiveRouteDecisionPreviewer(config: RoutingConfig) {
    private val routeEngine = RouteEngine(config)

    fun preview(summary: PacketSummary, devSessionOpen: Boolean = false): LiveRouteDecisionPreview {
        val decision = routeEngine.simulate(summary.dstIp)
        val selectedProfile = decision.tunnelProfile
        return LiveRouteDecisionPreview(
            matchedRuleName = decision.matchedRule.takeUnless { it.type == RouteRuleType.DEFAULT }?.name
                ?: decision.matchedRule.name,
            selectedProfileName = selectedProfile.name,
            selectedProfileType = selectedProfile.type.label,
            warnings = selectedProfile.runtimeForwardingWarnings(),
            tcpSessionObservationLine = summary.tcpSessionObservationLine(),
            devSessionObservationLine = if (devSessionOpen) DEV_TCP_BRIDGE_EVENT_OPEN else null,
        )
    }

    private fun PacketSummary.tcpSessionObservationLine(): String? =
        if (protocol == Ipv4Protocol.Tcp && srcPort != null && dstPort != null) WOULD_CREATE_TCP_SESSION else null

    private fun TunnelProfile.runtimeForwardingWarnings(): List<String> = when (type) {
        TunnelType.Socks5 -> emptyList()
        TunnelType.VLESS -> listOf(vless?.status.toVlessRoutePreviewClassificationLine())
        TunnelType.Socks5Mock -> listOf(REMOTE_RUNTIME_FORWARDING_NOT_ENABLED)
        TunnelType.Direct,
        TunnelType.Block -> emptyList()
        else -> if (singBox != null) {
            emptyList()
        } else if (mockOnly || type.remoteRuntimeForwardingRequired()) {
            listOf(REMOTE_RUNTIME_FORWARDING_NOT_ENABLED)
        } else {
            emptyList()
        }
    }

    private fun dev.vifs.viroutefs.vless.VlessProfileStatus?.toVlessRoutePreviewClassificationLine(): String = when (this) {
        dev.vifs.viroutefs.vless.VlessProfileStatus.TcpReachable -> "Latest manual VLESS response classification: response received or server reachable."
        dev.vifs.viroutefs.vless.VlessProfileStatus.LastTestFailed -> "Latest manual VLESS response classification: needs attention."
        dev.vifs.viroutefs.vless.VlessProfileStatus.Invalid -> "Latest manual VLESS response classification: validation error."
        dev.vifs.viroutefs.vless.VlessProfileStatus.Testing -> "Latest manual VLESS response classification: testing."
        dev.vifs.viroutefs.vless.VlessProfileStatus.ConfigReady -> "Latest manual VLESS response classification: config ready, not probed."
        dev.vifs.viroutefs.vless.VlessProfileStatus.NotTested,
        null -> VLESS_ROUTE_PREVIEW_RESPONSE_UNTESTED
    }

    private fun TunnelType.remoteRuntimeForwardingRequired(): Boolean = when (this) {
        TunnelType.XrayVlessReality,
        TunnelType.VLESS,
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
