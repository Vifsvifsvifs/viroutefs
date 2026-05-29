package dev.vifs.viroutefs.routing

import java.util.Locale

data class TunnelProfile(
    val id: String,
    val name: String,
    val type: TunnelType,
    val description: String,
)

enum class TunnelType {
    Direct,
    Block,
    Xray,
    Hysteria2,
    OpenVpn,
}

data class RouteRule(
    val id: String,
    val name: String,
    val type: RouteRuleType,
    val targetTunnelId: String,
    val priority: Int,
    val matchers: List<String>,
    val reason: String,
    val technicalDetails: String,
    val recommendedAction: String,
)

enum class RouteRuleType {
    APP_GROUP,
    DOMAIN,
    CIDR,
    DEFAULT,
}

data class RouteDecision(
    val input: String,
    val tunnelProfile: TunnelProfile,
    val matchedRule: RouteRule,
    val plainReason: String,
    val technicalDetails: String,
    val recommendedAction: String,
)

class RouteEngine(
    private val tunnelProfiles: List<TunnelProfile>,
    routeRules: List<RouteRule>,
) {
    private val rules = routeRules.sortedWith(compareBy<RouteRule> { it.priority })

    fun simulate(rawInput: String): RouteDecision {
        val normalizedInput = rawInput.trim()
        val comparableInput = normalizedInput.lowercase(Locale.ROOT)
        val matchedRule = rules.firstOrNull { rule -> rule.matches(comparableInput) }
            ?: rules.first { it.type == RouteRuleType.DEFAULT }
        val tunnelProfile = tunnelProfiles.first { it.id == matchedRule.targetTunnelId }

        return RouteDecision(
            input = normalizedInput,
            tunnelProfile = tunnelProfile,
            matchedRule = matchedRule,
            plainReason = matchedRule.reason,
            technicalDetails = matchedRule.technicalDetails,
            recommendedAction = matchedRule.recommendedAction,
        )
    }

    private fun RouteRule.matches(input: String): Boolean = when (type) {
        RouteRuleType.APP_GROUP,
        RouteRuleType.DOMAIN -> matchers.any { matcher -> input.contains(matcher.lowercase(Locale.ROOT)) }
        RouteRuleType.CIDR -> matchers.any { cidr -> ipv4InCidr(input, cidr) }
        RouteRuleType.DEFAULT -> true
    }

    private fun ipv4InCidr(input: String, cidr: String): Boolean {
        val inputAddress = input.toIpv4IntOrNull() ?: return false
        val cidrParts = cidr.split('/')
        if (cidrParts.size != 2) return false
        val networkAddress = cidrParts[0].toIpv4IntOrNull() ?: return false
        val prefixLength = cidrParts[1].toIntOrNull()?.takeIf { it in 0..32 } ?: return false
        val mask = if (prefixLength == 0) 0 else (-1 shl (32 - prefixLength))

        return (inputAddress and mask) == (networkAddress and mask)
    }

    private fun String.toIpv4IntOrNull(): Int? {
        val parts = split('.')
        if (parts.size != 4) return null

        return parts.fold(0) { accumulator, part ->
            val octet = part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
            (accumulator shl 8) or octet
        }
    }
}
