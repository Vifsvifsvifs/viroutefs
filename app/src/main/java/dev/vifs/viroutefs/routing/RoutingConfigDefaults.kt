package dev.vifs.viroutefs.routing

object RoutingConfigDefaults {
    const val DIRECT_PROFILE_ID = "direct"
    const val BLOCK_PROFILE_ID = "block"

    const val SYSTEM_DNS_ID = "system_dns"
    const val DIRECT_DNS_ID = "direct_dns"

    fun defaultConfig(): RoutingConfig = RoutingConfig(
        profiles = defaultProfiles(),
        dnsPolicies = defaultDnsPolicies(),
        rules = defaultRules(),
        defaultProfileId = DIRECT_PROFILE_ID,
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
            id = DIRECT_PROFILE_ID,
            name = "Direct",
            type = TunnelType.Direct,
            description = "Traffic remains on the device current network without a tunnel.",
            mockOnly = false,
            platformNotes = "Built-in local profile.",
            dnsPolicyId = DIRECT_DNS_ID,
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
            name = "System DNS",
            type = DnsPolicyType.System,
            description = "Use Android system DNS. This is local policy metadata until DNS routing is implemented.",
        ),
        DnsPolicy(
            id = DIRECT_DNS_ID,
            name = "Direct DNS",
            type = DnsPolicyType.Direct,
            serverText = "Provider / local network",
            resolveThroughProfileId = DIRECT_PROFILE_ID,
            description = "DNS should remain direct for local network policy planning. Real DNS enforcement will be added later.",
        ),
    )

    private fun defaultRules(): List<RouteRule> = listOf(
        RouteRule(
            id = "default_direct",
            name = "Default Direct",
            type = RouteRuleType.DEFAULT,
            targetProfileId = DIRECT_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 1000,
            matchers = emptyList(),
            reason = "If no explicit rule matches, the planned route remains Direct.",
            technicalDetails = "DEFAULT, priority 1000. App/domain/IP rules must be exclusive; if a chosen profile is unavailable, the safe behavior is Block / fail closed and never silent fallback to another VPN profile.",
            recommendedAction = "Add a specific rule when a destination needs a separate profile, block action, or DNS policy.",
        ),
    )
}
