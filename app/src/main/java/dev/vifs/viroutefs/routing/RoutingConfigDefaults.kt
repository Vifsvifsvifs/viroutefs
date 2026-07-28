package dev.vifs.viroutefs.routing

object RoutingConfigDefaults {
    // The persisted id remains "direct" for backward compatibility with older local configs,
    // but the user-facing built-in default route is System / Система.
    const val SYSTEM_PROFILE_ID = "direct"
    const val DIRECT_PROFILE_ID = SYSTEM_PROFILE_ID
    const val BLOCK_PROFILE_ID = "block"
    const val BYEDPI_PROFILE_ID = "byedpi"

    const val SYSTEM_DNS_ID = "system_dns"

    fun defaultConfig(): RoutingConfig = RoutingConfig(
        profiles = defaultProfiles(),
        dnsPolicies = defaultDnsPolicies(),
        rules = defaultRules(),
        defaultProfileId = null,
        hostOverrides = emptyList(),
    )

    fun workPersonalConfig(): RoutingConfig = defaultConfig()

    fun mediaFastTunnelConfig(): RoutingConfig = defaultConfig()

    fun banksDirectConfig(): RoutingConfig = defaultConfig()

    fun safeDefaultConfig(): RoutingConfig = defaultConfig()

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
        byeDpiProfile(),
    )

    fun byeDpiProfile(enabled: Boolean = false): TunnelProfile = TunnelProfile(
        id = BYEDPI_PROFILE_ID,
        name = "ByeDPI",
        type = TunnelType.ByeDpi,
        description = "Локальный SOCKS-прокси совместимости для сетей, где DPI мешает нормальной передаче TCP/TLS.",
        enabled = enabled,
        mockOnly = false,
        platformNotes = "Встроенный ByeDPI (MIT). Это не VPN: он не шифрует весь трафик и не скрывает IP-адрес.",
        dnsPolicyId = SYSTEM_DNS_ID,
    )

    fun ensureRequiredProfiles(config: RoutingConfig): RoutingConfig {
        val existingIds = config.profiles.mapTo(mutableSetOf()) { it.id }
        val required = defaultProfiles().filterNot { it.id in existingIds }
        val providerProfileId = config.defaultProfileId?.takeUnless {
            it == SYSTEM_PROFILE_ID || it == BLOCK_PROFILE_ID || it == BYEDPI_PROFILE_ID
        }
        return config.copy(
            version = CURRENT_ROUTING_CONFIG_VERSION,
            profiles = config.profiles + required,
            defaultProfileId = providerProfileId,
            rules = config.rules.map { rule ->
                if (rule.type == RouteRuleType.DEFAULT) {
                    rule.copy(
                        targetProfileId = providerProfileId ?: BLOCK_PROFILE_ID,
                        name = if (providerProfileId == null) "Provider tunnel not selected" else "Provider tunnel",
                        reason = if (providerProfileId == null) {
                            "Traffic stays blocked until a provider tunnel is selected."
                        } else {
                            "Traffic without a more specific rule uses the provider tunnel."
                        },
                        technicalDetails = "DEFAULT, priority ${rule.priority}. Explicit app, domain, IP, and CIDR rules override this route.",
                        recommendedAction = if (providerProfileId == null) {
                            "Add a working VPN profile and choose it as the provider tunnel."
                        } else {
                            "Add specific rules only for traffic that needs another route."
                        },
                    )
                } else {
                    rule
                }
            },
        )
    }

    private fun defaultDnsPolicies(): List<DnsPolicy> = listOf(
        DnsPolicy(
            id = SYSTEM_DNS_ID,
            name = "Android system DNS",
            type = DnsPolicyType.System,
            resolveThroughProfileId = null,
            description = "Uses Android system DNS through the selected provider tunnel unless the user explicitly configures another DNS server.",
        ),
    )

    private fun defaultRules(): List<RouteRule> = listOf(
        RouteRule(
            id = "default_system",
            name = "Provider tunnel not selected",
            type = RouteRuleType.DEFAULT,
            targetProfileId = BLOCK_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 1000,
            matchers = emptyList(),
            reason = "Traffic stays blocked until a provider tunnel is selected.",
            technicalDetails = "DEFAULT, priority 1000. When network control is active, every unmatched flow must use the selected provider tunnel. Explicit app/domain/IP/CIDR rules override it.",
            recommendedAction = "Add a working VPN profile and choose it as the provider tunnel.",
        ),
    )
}
