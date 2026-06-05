// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

const val VLESS_RUNTIME_LIMITATION = "Selected profile: VLESS-like. Runtime forwarding is not enabled yet."
const val OBSERVATION_ONLY_ROUTE_PREVIEW = "Observation only: packet was inspected locally and dropped; ViRouteFS did not forward it."

data class LivePacketMetadata(
    val protocol: String,
    val sourceIp: String,
    val destinationIp: String,
    val sourcePort: Int? = null,
    val destinationPort: Int? = null,
)

data class LiveRouteDecisionPreview(
    val observedAt: Long,
    val protocol: String,
    val source: String,
    val destination: String,
    val matchedRuleName: String?,
    val selectedProfileName: String,
    val selectedProfileType: String,
    val warning: String?,
    val decisionText: String,
)

class LiveRouteDecisionPreviewer(config: RoutingConfig) {
    private val routeEngine = RouteEngine(config)

    fun preview(metadata: LivePacketMetadata, observedAt: Long): LiveRouteDecisionPreview {
        val decision = routeEngine.simulate(metadata.destinationIp)
        val profile = decision.tunnelProfile
        val warning = when (profile.type) {
            TunnelType.Socks5 -> SOCKS5_RUNTIME_LIMITATION
            TunnelType.XrayVlessReality -> VLESS_RUNTIME_LIMITATION
            else -> null
        }
        val selectedProfileType = profile.type.label
        val matchedRuleName = decision.matchedRule.name.takeUnless { decision.matchedRule.type == RouteRuleType.DEFAULT }
        val decisionText = buildDecisionText(
            protocol = metadata.protocol,
            destination = metadata.destinationEndpoint,
            matchedRuleName = matchedRuleName,
            profileName = profile.name,
            profileType = selectedProfileType,
            warning = warning,
        )
        return LiveRouteDecisionPreview(
            observedAt = observedAt,
            protocol = metadata.protocol,
            source = metadata.sourceEndpoint,
            destination = metadata.destinationEndpoint,
            matchedRuleName = matchedRuleName,
            selectedProfileName = profile.name,
            selectedProfileType = selectedProfileType,
            warning = warning,
            decisionText = decisionText,
        )
    }

    private fun buildDecisionText(
        protocol: String,
        destination: String,
        matchedRuleName: String?,
        profileName: String,
        profileType: String,
        warning: String?,
    ): String = buildString {
        append("$protocol packet to $destination would use ")
        append(profileName)
        append(" ($profileType)")
        append(" via ")
        append(matchedRuleName ?: "default rule fallback")
        append(". ")
        append(OBSERVATION_ONLY_ROUTE_PREVIEW)
        warning?.let { append(" Warning: ").append(it) }
    }
}

private val LivePacketMetadata.sourceEndpoint: String
    get() = endpoint(sourceIp, sourcePort)

private val LivePacketMetadata.destinationEndpoint: String
    get() = endpoint(destinationIp, destinationPort)

private fun endpoint(ip: String, port: Int?): String = if (port == null) ip else "$ip:$port"
