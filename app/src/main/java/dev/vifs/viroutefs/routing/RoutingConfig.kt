package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import dev.vifs.viroutefs.vless.VLESS_RUNTIME_LIMITATION
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.validateVlessProfile
import java.util.Locale

const val CURRENT_ROUTING_CONFIG_VERSION = 2
const val MOCK_PROFILE_LIMITATION = "Профиль пока не подключает реальный тоннель. Он используется для симуляции маршрутов."
const val SOCKS5_RUNTIME_LIMITATION = "Selected profile: SOCKS5. Runtime forwarding is not enabled yet."
const val VLESS_ROUTE_DECISION_LIMITATION = "Selected profile is VLESS. Runtime forwarding is not enabled yet."
const val DNS_POLICY_LIMITATION = "DNS-политика пока используется для объяснения и проверки риска утечки. Реальное DNS-маршрутизирование будет добавлено позже."

data class RoutingConfig(
    val version: Int = CURRENT_ROUTING_CONFIG_VERSION,
    val profiles: List<TunnelProfile>,
    val dnsPolicies: List<DnsPolicy>,
    val rules: List<RouteRule>,
    val defaultProfileId: String? = null,
    val hostOverrides: List<DnsHostOverride> = emptyList(),
)

data class TunnelProfile(
    val id: String,
    val name: String,
    val type: TunnelType,
    val description: String,
    val enabled: Boolean = true,
    val mockOnly: Boolean = type.isMockOnly,
    val platformNotes: String? = null,
    val dnsPolicyId: String? = null,
    val socks5: Socks5ProfileConfig? = null,
    val vless: VlessProfileConfig? = null,
) {
    val warningText: String?
        get() = when (type) {
            TunnelType.Pptp -> "Устаревший и небезопасный протокол. Используйте только для старых сетей, если другого варианта нет."
            TunnelType.L2tp -> "Устаревший и небезопасный режим."
            TunnelType.L2tpIpSec, TunnelType.Sstp -> "Legacy/corporate compatibility: используйте только при необходимости совместимости."
            else -> null
        }
}

data class DnsHostOverride(
    val id: String,
    val hostname: String,
    val ipAddress: String,
    val enabled: Boolean = true,
    val note: String? = null,
)

enum class TunnelType(val label: String, val isMockOnly: Boolean) {
    Direct("System", false),
    Block("Block", false),
    WireGuard("WireGuard", true),
    OpenVpn("OpenVPN", true),
    OpenConnectAnyConnect("OpenConnect / AnyConnect", true),
    Ikev2IpSec("IKEv2 / IPSec", true),
    SoftEther("SoftEther", true),
    ZeroTier("ZeroTier", true),
    TailscaleCompatible("Tailscale-compatible", true),
    HeadscaleCompatible("Headscale-compatible", true),
    XrayVlessReality("Xray / VLESS / Reality", true),
    VMess("VMess", true),
    Trojan("Trojan", true),
    Shadowsocks("Shadowsocks", true),
    Shadowsocks2022("Shadowsocks 2022", true),
    Hysteria2("Hysteria2", true),
    Tuic("TUIC", true),
    NaiveProxy("NaiveProxy", true),
    Brook("Brook", true),
    ShadowTls("ShadowTLS", true),
    Socks5("SOCKS5", true),
    VLESS("VLESS", true),
    HttpProxy("HTTP proxy", true),
    HttpsProxy("HTTPS proxy", true),
    SshTunnel("SSH tunnel", true),
    L2tpIpSec("L2TP/IPSec", true),
    L2tp("L2TP", true),
    Pptp("PPTP", true),
    Sstp("SSTP", true),
    IpSecXAuth("IPSec XAuth", true),
    IpSecPsk("IPSec PSK", true),

    // Backward-compatible names used by exported 0.4.0-alpha configs.
    XrayMock("Xray mock", true),
    Hysteria2Mock("Hysteria2 mock", true),
    OpenVpnMock("OpenVPN mock", true),
    WireGuardMock("WireGuard mock", true),
    Socks5Mock("Socks5 mock", true),
}

data class DnsPolicy(
    val id: String,
    val name: String,
    val type: DnsPolicyType,
    val serverText: String? = null,
    val resolveThroughProfileId: String? = null,
    val description: String,
    val enabled: Boolean = true,
)

enum class DnsPolicyType(val label: String) {
    System("System DNS"),
    Direct("System DNS (legacy direct)"),
    WorkMock("Work DNS mock"),
    TunnelMock("Tunnel DNS mock"),
    Custom("Custom DNS"),
}

data class AppMatcher(
    val platform: AppMatcherPlatform,
    val value: String,
    val displayName: String? = null,
)

enum class AppMatcherPlatform(val wireName: String) {
    Android("android"),
    Linux("linux"),
    Windows("windows"),
    Macos("macos"),
    Any("any"),
}

data class RouteRule(
    val id: String,
    val name: String,
    val type: RouteRuleType,
    val targetProfileId: String,
    val dnsPolicyId: String? = null,
    val priority: Int,
    val matchers: List<String>,
    val appMatchers: List<AppMatcher> = emptyList(),
    val enabled: Boolean = true,
    val reason: String,
    val technicalDetails: String,
    val recommendedAction: String,
)

enum class RouteRuleType {
    APP_GROUP,
    APP,
    DOMAIN,
    CIDR,
    DEFAULT,
}

data class RouteDecision(
    val input: String,
    val tunnelProfile: TunnelProfile,
    val dnsPolicy: DnsPolicy?,
    val matchedRule: RouteRule,
    val plainReason: String,
    val technicalDetails: String,
    val recommendedAction: String,
    val warnings: List<String>,
) {
    val dnsPolicySummary: String = dnsPolicy?.name ?: "Не выбрана"
    val profileMockSummary: String = if (tunnelProfile.type == TunnelType.Socks5) {
        SOCKS5_RUNTIME_LIMITATION
    } else if (tunnelProfile.type == TunnelType.VLESS) {
        VLESS_ROUTE_DECISION_LIMITATION
    } else if (tunnelProfile.mockOnly) {
        MOCK_PROFILE_LIMITATION
    } else {
        "Профиль не является mock-тоннелем."
    }
    val dnsLeakSummary: String = if (dnsPolicy == null || dnsPolicy.type == DnsPolicyType.System) {
        "Uses Android system DNS through the ViRouteFS policy model; real DNS enforcement is not implemented yet."
    } else {
        "DNS-политика описывает желаемое поведение, но реальное DNS-маршрутизирование пока не реализовано."
    }
}

class RouteEngine(
    private val config: RoutingConfig,
) {
    private val profilesById = config.profiles.associateBy { it.id }
    private val dnsPoliciesById = config.dnsPolicies.associateBy { it.id }
    private val enabledRules = config.rules
        .filter { it.enabled }
        .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name })

    fun simulate(rawInput: String): RouteDecision {
        val normalizedInput = rawInput.trim().ifBlank { "default" }
        val comparableInput = normalizedInput.lowercase(Locale.ROOT)
        val matchedRule = enabledRules.firstOrNull { rule ->
            rule.type != RouteRuleType.DEFAULT && rule.matches(comparableInput)
        } ?: enabledRules.firstOrNull { it.type == RouteRuleType.DEFAULT }
        ?: config.rules.first { it.type == RouteRuleType.DEFAULT }

        val targetProfile = profilesById[matchedRule.targetProfileId]
        val blockProfile = profilesById[RoutingConfigDefaults.BLOCK_PROFILE_ID]
            ?: config.profiles.firstOrNull { it.type == TunnelType.Block }
        val fallbackProfile = blockProfile?.takeIf { matchedRule.type != RouteRuleType.DEFAULT }
            ?: config.defaultProfileId?.let { profilesById[it] }?.takeIf { it.enabled }
            ?: config.profiles.firstOrNull { it.enabled && it.type == TunnelType.Direct }
            ?: config.profiles.first()
        val selectedProfile = targetProfile?.takeIf { it.enabled } ?: fallbackProfile
        val dnsPolicy = matchedRule.dnsPolicyId?.let { dnsPoliciesById[it] }?.takeIf { it.enabled }
        val warnings = buildWarnings(matchedRule, targetProfile, selectedProfile, dnsPolicy)

        return RouteDecision(
            input = normalizedInput,
            tunnelProfile = selectedProfile,
            dnsPolicy = dnsPolicy,
            matchedRule = matchedRule,
            plainReason = matchedRule.reason,
            technicalDetails = buildTechnicalDetails(matchedRule, selectedProfile, dnsPolicy, warnings),
            recommendedAction = matchedRule.recommendedAction,
            warnings = warnings,
        )
    }

    private fun RouteRule.matches(input: String): Boolean = when (type) {
        RouteRuleType.APP_GROUP,
        RouteRuleType.APP,
        RouteRuleType.DOMAIN -> textMatchers().any { matcher -> wildcardContains(input, matcher) }
        RouteRuleType.CIDR -> matchers.any { cidr -> ipv4InCidr(input, cidr) }
        RouteRuleType.DEFAULT -> true
    }

    private fun RouteRule.textMatchers(): List<String> = buildList {
        addAll(matchers)
        addAll(appMatchers.map { it.value })
        addAll(appMatchers.mapNotNull { it.displayName })
    }.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() && it != "*" }

    private fun wildcardContains(input: String, matcher: String): Boolean {
        val normalizedMatcher = matcher.removePrefix("*.")
        return input.contains(normalizedMatcher)
    }

    private fun buildWarnings(
        matchedRule: RouteRule,
        targetProfile: TunnelProfile?,
        selectedProfile: TunnelProfile,
        dnsPolicy: DnsPolicy?,
    ): List<String> = buildList {
        if (targetProfile == null) {
            add("Выбранное правилом направление не найдено. Модель безопасного поведения: Block / fail closed; без тихого fallback на другой профиль.")
        } else if (!targetProfile.enabled) {
            add("Профиль правила отключён. Модель безопасного поведения: Block / fail closed; без тихого fallback на другой профиль.")
        }
        if (selectedProfile.type == TunnelType.Socks5) {
            add(SOCKS5_RUNTIME_LIMITATION)
        } else if (selectedProfile.type == TunnelType.VLESS) {
            add(VLESS_ROUTE_DECISION_LIMITATION)
            add(VLESS_RUNTIME_LIMITATION)
        } else if (selectedProfile.mockOnly) {
            add(MOCK_PROFILE_LIMITATION)
        }
        if (dnsPolicy == null && matchedRule.dnsPolicyId != null) {
            add("DNS-политика правила не найдена или отключена. Возможна утечка DNS через системные настройки.")
        }
        dnsPolicy?.resolveThroughProfileId?.let { expectedProfileId ->
            if (expectedProfileId != selectedProfile.id) {
                val expectedName = profilesById[expectedProfileId]?.name ?: expectedProfileId
                add("Маршрут выбран через ${selectedProfile.name}, но DNS-политика ожидает резолвинг через $expectedName. В текущей версии это только предупреждение о риске утечки.")
            }
        }
        if (dnsPolicy != null && dnsPolicy.type != DnsPolicyType.System) {
            add("Маршрут выбран через ${selectedProfile.name}, но DNS-политика пока не применяется реально. В будущей версии DNS должен резолвиться через выбранную политику.")
        }
        add("Реальное VPN-маршрутизирование пока не реализовано; решение используется для симуляции и диагностики конфигурации.")
    }

    private fun buildTechnicalDetails(
        rule: RouteRule,
        profile: TunnelProfile,
        dnsPolicy: DnsPolicy?,
        warnings: List<String>,
    ): String = buildString {
        appendLine("Тип правила: ${rule.type}")
        appendLine("Приоритет: ${rule.priority} (меньше — важнее)")
        appendLine("Матчеры: ${rule.matchers.joinToString().ifBlank { "не требуются" }}")
        appendLine("Профиль: ${profile.name} (${profile.type.label})")
        if (profile.type == TunnelType.Socks5) appendLine(SOCKS5_RUNTIME_LIMITATION)
        if (profile.type == TunnelType.VLESS) appendLine(VLESS_ROUTE_DECISION_LIMITATION)
        appendLine("DNS-политика: ${dnsPolicy?.name ?: "не выбрана"}")
        appendLine("DNS default: ${if (dnsPolicy == null || dnsPolicy.type == DnsPolicyType.System) "Android system DNS" else "configured policy, not enforced yet"}")
        if (warnings.isNotEmpty()) {
            appendLine("Предупреждения:")
            warnings.forEach { appendLine("- $it") }
        }
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

fun validateRoutingConfig(config: RoutingConfig): List<String> = buildList {
    val profileIds = config.profiles.map { it.id }.toSet()
    val dnsPolicyIds = config.dnsPolicies.map { it.id }.toSet()
    config.defaultProfileId?.takeIf { it !in profileIds }?.let { add("Основной профиль $it не найден.") }
    if (config.profiles.isEmpty()) add("Нужен хотя бы один профиль маршрута.")
    if (config.rules.isEmpty()) add("Нужно хотя бы одно правило маршрутизации.")
    config.profiles.forEach { profile ->
        if (profile.id.isBlank()) add("Профиль без id: ${profile.name}")
        if (profile.name.isBlank()) add("Профиль ${profile.id} без имени.")
        if (profile.type == TunnelType.Socks5) {
            val socks5 = profile.socks5
            if (socks5 == null) {
                add("SOCKS5 profile ${profile.name} has no SOCKS5 configuration.")
            } else {
                validateSocks5Profile(
                    candidate = socks5,
                    existingProfiles = config.profiles.mapNotNull { it.socks5 },
                    originalName = socks5.name,
                ).forEach { add("Профиль ${profile.name}: $it") }
            }
        }
        if (profile.type == TunnelType.VLESS) {
            val vless = profile.vless
            if (vless == null) {
                add("VLESS profile ${profile.name} has no VLESS configuration.")
            } else {
                validateVlessProfile(vless).forEach { add("Профиль ${profile.name}: $it") }
            }
        }
        profile.dnsPolicyId?.takeIf { it !in dnsPolicyIds }?.let { add("Профиль ${profile.name}: DNS-политика $it не найдена.") }
    }
    config.dnsPolicies.forEach { policy ->
        if (policy.id.isBlank()) add("DNS-политика без id: ${policy.name}")
        if (policy.name.isBlank()) add("DNS-политика ${policy.id} без имени.")
        policy.resolveThroughProfileId?.takeIf { it !in profileIds }?.let {
            add("DNS-политика ${policy.name} ссылается на отсутствующий профиль $it.")
        }
    }
    config.profiles.mapNotNull { it.socks5 }.groupBy { it.name.trim().lowercase(Locale.ROOT) }.filterKeys { it.isNotBlank() }.forEach { (name, profiles) ->
        if (profiles.size > 1) add("Duplicate SOCKS5 profile name: $name")
    }
    config.hostOverrides.forEach { override ->
        if (override.id.isBlank()) add("DNS override без id: ${override.hostname}")
        if (override.hostname.isBlank()) add("DNS override ${override.id} без hostname.")
        if (override.ipAddress.isBlank()) add("DNS override ${override.hostname} без IP.")
    }
    config.rules.forEach { rule ->
        if (rule.name.isBlank()) add("Правило ${rule.id} без имени.")
        if (rule.priority < 0) add("Правило ${rule.name}: приоритет должен быть неотрицательным.")
        if (rule.type != RouteRuleType.DEFAULT && rule.matchers.isEmpty() && rule.appMatchers.isEmpty()) {
            add("Правило ${rule.name}: нужны матчеры.")
        }
        if (rule.targetProfileId !in profileIds) add("Правило ${rule.name}: профиль ${rule.targetProfileId} не найден.")
        rule.dnsPolicyId?.takeIf { it !in dnsPolicyIds }?.let { add("Правило ${rule.name}: DNS-политика $it не найдена.") }
        if (rule.type == RouteRuleType.CIDR) {
            rule.matchers
                .filterNot { isValidIpOrCidr(it) }
                .forEach { add("Правило ${rule.name}: некорректный IP/CIDR $it.") }
        }
    }
    findExactRouteConflicts(config.rules).forEach { add(it.message) }
    if (config.rules.count { it.enabled && it.type == RouteRuleType.DEFAULT } != 1) {
        add("Должно быть активно ровно одно правило DEFAULT.")
    }
}
