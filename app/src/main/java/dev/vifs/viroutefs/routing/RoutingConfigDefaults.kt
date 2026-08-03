package dev.vifs.viroutefs.routing

object RoutingConfigDefaults {
    // The persisted id remains "direct" for backward compatibility with older local configs,
    // but the user-facing built-in default route is System / Система.
    const val SYSTEM_PROFILE_ID = "direct"
    const val DIRECT_PROFILE_ID = SYSTEM_PROFILE_ID
    const val BLOCK_PROFILE_ID = "block"
    const val BYEDPI_PROFILE_ID = "byedpi"
    const val NETWORK_COMPATIBILITY_PROFILE_NAME = "Совместимость TCP/TLS"

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

    fun safeDefaultConfig(): RoutingConfig = defaultConfig()

    private fun defaultProfiles(): List<TunnelProfile> = listOf(
        systemProfile(),
        blockProfile(),
        byeDpiProfile(),
    )

    private fun systemProfile(dnsPolicyId: String? = SYSTEM_DNS_ID): TunnelProfile = TunnelProfile(
        id = SYSTEM_PROFILE_ID,
        name = "System / Система",
        type = TunnelType.Direct,
        description = "Default Android/system network path, controlled by ViRouteFS when network control is active.",
        enabled = true,
        mockOnly = false,
        platformNotes = "Built-in internal default route. Legacy id/type may still say direct for compatibility; this is not a bypass.",
        dnsPolicyId = dnsPolicyId,
    )

    private fun blockProfile(dnsPolicyId: String? = SYSTEM_DNS_ID): TunnelProfile = TunnelProfile(
        id = BLOCK_PROFILE_ID,
        name = "Block",
        type = TunnelType.Block,
        description = "Traffic matching this profile must fail closed.",
        enabled = true,
        mockOnly = false,
        dnsPolicyId = dnsPolicyId,
    )

    fun byeDpiProfile(enabled: Boolean = false): TunnelProfile = TunnelProfile(
        id = BYEDPI_PROFILE_ID,
        name = NETWORK_COMPATIBILITY_PROFILE_NAME,
        type = TunnelType.ByeDpi,
        description = "Локальный обработчик совместимости для сетей, где промежуточное оборудование нарушает стандартную передачу TCP/TLS.",
        enabled = enabled,
        mockOnly = false,
        platformNotes = "Техническая реализация: встроенный движок ByeDPI по лицензии MIT. Это не VPN, не шифрование и не средство сокрытия IP-адреса.",
        dnsPolicyId = SYSTEM_DNS_ID,
    )

    fun ensureRequiredProfiles(config: RoutingConfig): RoutingConfig {
        val normalizedProfiles = config.profiles.map { profile ->
            when (profile.id) {
                SYSTEM_PROFILE_ID -> systemProfile(
                    dnsPolicyId = profile.dnsPolicyId ?: SYSTEM_DNS_ID,
                )
                BLOCK_PROFILE_ID -> blockProfile(
                    dnsPolicyId = profile.dnsPolicyId ?: SYSTEM_DNS_ID,
                )
                BYEDPI_PROFILE_ID -> byeDpiProfile(enabled = profile.enabled).copy(
                    dnsPolicyId = profile.dnsPolicyId ?: SYSTEM_DNS_ID,
                )
                else -> profile
            }
        }
        val existingIds = normalizedProfiles.mapTo(mutableSetOf()) { it.id }
        val required = defaultProfiles().filterNot { it.id in existingIds }
        val profileIds = normalizedProfiles.mapTo(mutableSetOf()) { it.id }.apply {
            addAll(required.map { it.id })
        }
        val defaultRouteProfileId = config.defaultProfileId
            ?.takeIf { it in profileIds }
            ?.takeUnless { it == BLOCK_PROFILE_ID || it == BYEDPI_PROFILE_ID }
            ?: SYSTEM_PROFILE_ID
        return config.copy(
            version = CURRENT_ROUTING_CONFIG_VERSION,
            profiles = normalizedProfiles + required,
            defaultProfileId = defaultRouteProfileId,
            rules = config.rules.map { rule ->
                if (rule.type == RouteRuleType.DEFAULT) {
                    rule.copy(
                        targetProfileId = defaultRouteProfileId,
                        name = if (defaultRouteProfileId == SYSTEM_PROFILE_ID) {
                            "Phone internet / System"
                        } else {
                            "Custom default route"
                        },
                        reason = "Traffic without a more specific rule uses the selected default route.",
                        technicalDetails = "DEFAULT, priority ${rule.priority}. Explicit app, domain, IP, and CIDR rules override this route.",
                        recommendedAction = if (defaultRouteProfileId == SYSTEM_PROFILE_ID) {
                            "Add rules only for apps, domains, IPs, or networks that must use a VPN, Block, or TCP/TLS compatibility mode."
                        } else {
                            "Keep the custom default profile available or return the default route to System."
                        },
                    )
                } else {
                    rule
                }
            },
        ).withSyncedProfileAppRoutingRules()
    }

    private fun defaultDnsPolicies(): List<DnsPolicy> = listOf(
        DnsPolicy(
            id = SYSTEM_DNS_ID,
            name = "Android system DNS",
            type = DnsPolicyType.System,
            resolveThroughProfileId = null,
            description = "Uses Android system DNS through the phone's current connection unless the user explicitly configures another DNS server.",
        ),
    )

    private fun defaultRules(): List<RouteRule> = listOf(
        RouteRule(
            id = "default_system",
            name = "Phone internet / System",
            type = RouteRuleType.DEFAULT,
            targetProfileId = SYSTEM_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 1000,
            matchers = emptyList(),
            reason = "Traffic without a more specific rule uses the phone's normal mobile data or Wi-Fi connection.",
            technicalDetails = "DEFAULT, priority 1000. Network control remains active, while explicit app/domain/IP/CIDR rules can override this route.",
            recommendedAction = "Add rules only for traffic that must use a VPN, another tunnel, Block, or TCP/TLS compatibility mode.",
        ),
    )
}
