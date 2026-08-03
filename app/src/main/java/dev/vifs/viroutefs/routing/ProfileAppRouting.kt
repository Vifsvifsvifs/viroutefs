// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

/**
 * Applies v2rayNG-style per-profile application routing on top of the shared
 * Android VPN. The generated rule is persisted for compatibility with older
 * routing code, but remains hidden from the manual route editor.
 */
fun RoutingConfig.withProfileAppRouting(
    profileId: String,
    mode: ProfileAppRoutingMode,
    packageNames: Collection<String>,
    networks: Collection<String> = emptyList(),
): RoutingConfig {
    require(
        profileId !in setOf(
            RoutingConfigDefaults.SYSTEM_PROFILE_ID,
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
            RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        ),
    ) { "Application routing can only be configured for a VPN profile." }
    require(profiles.any { it.id == profileId }) { "VPN profile '$profileId' was not found." }

    val normalizedPackages = packageNames
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
    val normalizedNetworks = networks
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .sorted()
    require(normalizedNetworks.all(::isValidIpOrCidr)) {
        "Every profile network must be a valid IPv4/IPv6 address or CIDR."
    }
    var next = copy(
        profiles = profiles.map { profile ->
            if (profile.id == profileId) {
                profile.copy(
                    appRoutingMode = mode,
                    appRoutingPackages = normalizedPackages,
                    appRoutingNetworks = normalizedNetworks,
                )
            } else {
                profile.copy(
                    appRoutingPackages = profile.appRoutingPackages.filterNot(normalizedPackages::contains),
                )
            }
        },
    )

    next = when {
        mode == ProfileAppRoutingMode.BypassSelected -> next.withDefaultRoute(profileId)
        next.defaultProfileId == profileId ->
            next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
        else -> next.withSyncedProfileAppRoutingRules()
    }
    return next
}

fun RoutingConfig.withSyncedProfileAppRoutingRules(): RoutingConfig {
    val manualRules = rules.filterNot(RouteRule::isManagedProfileAppRoutingRule)
    val generatedAppRules = profiles.mapNotNull { profile ->
        val packages = profile.appRoutingPackages
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        if (packages.isEmpty()) return@mapNotNull null

        val targetProfileId = when (profile.appRoutingMode) {
            ProfileAppRoutingMode.SelectedApps -> profile.id
            ProfileAppRoutingMode.BypassSelected -> {
                if (defaultProfileId != profile.id) return@mapNotNull null
                RoutingConfigDefaults.SYSTEM_PROFILE_ID
            }
        }
        RouteRule(
            id = managedProfileAppRoutingRuleId(profile.id, "apps"),
            name = when (profile.appRoutingMode) {
                ProfileAppRoutingMode.SelectedApps -> "Приложения через ${profile.name}"
                ProfileAppRoutingMode.BypassSelected -> "Обход VPN ${profile.name}"
            },
            type = RouteRuleType.APP_GROUP,
            targetProfileId = targetProfileId,
            dnsPolicyId = if (targetProfileId == RoutingConfigDefaults.SYSTEM_PROFILE_ID) {
                RoutingConfigDefaults.SYSTEM_DNS_ID
            } else {
                profile.dnsPolicyId
            },
            priority = PROFILE_APP_ROUTING_PRIORITY,
            matchers = emptyList(),
            appMatchers = packages.map { packageName ->
                AppMatcher(
                    platform = AppMatcherPlatform.Android,
                    value = packageName,
                    displayName = packageName,
                )
            },
            enabled = true,
            reason = when (profile.appRoutingMode) {
                ProfileAppRoutingMode.SelectedApps ->
                    "Пользователь выбрал приложения, которые должны использовать этот VPN-профиль."
                ProfileAppRoutingMode.BypassSelected ->
                    "Пользователь включил режим обхода: выбранные приложения используют System."
            },
            technicalDetails =
                "Автоматическое правило профиля. Ручные правила с меньшим приоритетом выполняются раньше.",
            recommendedAction =
                "Изменяйте этот список в окне VPN-профиля, а не в редакторе ручных маршрутов.",
        )
    }
    val generatedNetworkRules = profiles.mapNotNull { profile ->
        val networks = profile.appRoutingNetworks
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
        if (networks.isEmpty()) return@mapNotNull null
        RouteRule(
            id = managedProfileAppRoutingRuleId(profile.id, "networks"),
            name = "Сети через ${profile.name}",
            type = RouteRuleType.CIDR,
            targetProfileId = profile.id,
            dnsPolicyId = profile.dnsPolicyId,
            priority = PROFILE_NETWORK_ROUTING_PRIORITY,
            matchers = networks,
            enabled = true,
            reason = "Пользователь закрепил IP-сети за этим VPN-профилем.",
            technicalDetails =
                "Автоматическое сетевое правило профиля выполняется раньше его списка приложений и режима обхода.",
            recommendedAction =
                "Изменяйте сети в окне VPN-профиля, а не в редакторе ручных маршрутов.",
        )
    }
    return copy(rules = manualRules + generatedNetworkRules + generatedAppRules)
}

fun RouteRule.isManagedProfileAppRoutingRule(): Boolean = id.startsWith(PROFILE_APP_ROUTING_RULE_PREFIX)

private fun managedProfileAppRoutingRuleId(profileId: String, kind: String): String =
    "$PROFILE_APP_ROUTING_RULE_PREFIX$kind:$profileId"

private const val PROFILE_APP_ROUTING_RULE_PREFIX = "profile-app-routing:"
private const val PROFILE_NETWORK_ROUTING_PRIORITY = 19_000
private const val PROFILE_APP_ROUTING_PRIORITY = 20_000
