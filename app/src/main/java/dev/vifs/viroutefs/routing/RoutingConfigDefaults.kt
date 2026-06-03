package dev.vifs.viroutefs.routing

object RoutingConfigDefaults {
    // The persisted id remains "direct" for backward compatibility with older local configs,
    // but the user-facing built-in default route is System / Система.
    const val SYSTEM_PROFILE_ID = "direct"
    const val DIRECT_PROFILE_ID = SYSTEM_PROFILE_ID
    const val BLOCK_PROFILE_ID = "block"

    const val SYSTEM_DNS_ID = "system_dns"

    fun defaultConfig(): RoutingConfig = RoutingConfig(
        profiles = defaultProfiles(),
        dnsPolicies = defaultDnsPolicies(),
        rules = defaultRules(),
        defaultProfileId = SYSTEM_PROFILE_ID,
        hostOverrides = emptyList(),
    )

    fun workPersonalConfig(): RoutingConfig = defaultConfig()

    fun mediaFastTunnelConfig(): RoutingConfig = defaultConfig()

    fun banksDirectConfig(): RoutingConfig = defaultConfig()

    fun safeDefaultConfig(): RoutingConfig = defaultConfig().copy(
        defaultProfileId = BLOCK_PROFILE_ID,
        rules = defaultRules().map { rule ->
            if (rule.type == RouteRuleType.DEFAULT) {
                rule.copy(
                    targetProfileId = BLOCK_PROFILE_ID,
                    name = "Fail-closed default",
                    reason = "Unknown traffic is blocked until an explicit rule is configured.",
                    recommendedAction = "Add explicit app, domain, or CIDR rules for allowed destinations.",
                )
            } else {
                rule
            }
        },
    )

    private fun defaultProfiles(): List<TunnelProfile> = listOf(
        TunnelProfile(
            id = SYSTEM_PROFILE_ID,
            name = "System / Система",
            type = TunnelType.Direct,
            description = "Default Android/system network path, controlled by ViRouteFS when network control is active.",
            mockOnly = false,
            platformNotes = "Built-in internal default route. Legacy id/type may still say direct for compatibility; this is not a bypass.",
            dnsPolicyId = SYSTEM_DNS_ID,
        ),
        TunnelProfile(
            id = BLOCK_PROFILE_ID,
            name = "Block",
            type = TunnelType.Block,
            description = "Traffic matching this profile must fail closed.",
            mockOnly = false,
            dnsPolicyId = SYSTEM_DNS_ID,
        ),
    )

    private fun defaultDnsPolicies(): List<DnsPolicy> = listOf(
        DnsPolicy(
            id = SYSTEM_DNS_ID,
            name = "Android system DNS",
            type = DnsPolicyType.System,
            resolveThroughProfileId = SYSTEM_PROFILE_ID,
            description = "Uses Android system DNS through the ViRouteFS policy model unless the user explicitly configures DNS.",
        ),
    )

    private fun defaultRules(): List<RouteRule> = listOf(
        RouteRule(
            id = "default_system",
            name = "Default System",
            type = RouteRuleType.DEFAULT,
            targetProfileId = SYSTEM_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 1000,
            matchers = emptyList(),
            reason = "Apps without explicit rules use the built-in System route inside ViRouteFS.",
            technicalDetails = "DEFAULT, priority 1000. When network control is active, unmatched apps use System inside ViRouteFS; matched app/domain/IP rules are exclusive. If a chosen profile is unavailable, the safe behavior is Block / fail closed and never silent fallback to another VPN profile.",
            recommendedAction = "Add a specific rule when a destination needs a separate profile, block action, or DNS policy.",
        ),
    )
}
