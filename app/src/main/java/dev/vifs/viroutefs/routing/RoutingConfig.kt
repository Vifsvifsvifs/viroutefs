package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import dev.vifs.viroutefs.vless.VLESS_RUNTIME_LIMITATION
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.validateVlessProfile
import java.util.Locale

const val CURRENT_ROUTING_CONFIG_VERSION = 10
const val MOCK_PROFILE_LIMITATION = "Профиль пока не подключает реальный тоннель. Он используется для симуляции маршрутов."
const val SOCKS5_RUNTIME_STATUS = "SOCKS5 forwarding is available through the local sing-box TUN runtime."
const val VLESS_ROUTE_DECISION_STATUS = "VLESS forwarding is available through the local sing-box TUN runtime."
const val DNS_POLICY_LIMITATION =
    "Custom DNS is enforced for domain and Android app policies by the local sing-box runtime. Per-app matching requires Android 10 or newer."

data class RoutingConfig(
    val version: Int = CURRENT_ROUTING_CONFIG_VERSION,
    val profiles: List<TunnelProfile>,
    val dnsPolicies: List<DnsPolicy>,
    val rules: List<RouteRule>,
    val defaultProfileId: String? = null,
    val hostOverrides: List<DnsHostOverride> = emptyList(),
    val emergencyBlockEnabled: Boolean = false,
)

/**
 * Selects the default route used by every flow that has no more specific rule.
 *
 * Keeping the persisted DEFAULT rule in sync makes exported configurations
 * readable by older builds, while defaultProfileId remains authoritative.
 */
fun RoutingConfig.withDefaultRoute(profileId: String): RoutingConfig = copy(
    defaultProfileId = profileId,
    rules = rules.map { rule ->
        if (rule.type == RouteRuleType.DEFAULT) {
            rule.copy(
                targetProfileId = profileId,
                name = if (profileId == RoutingConfigDefaults.SYSTEM_PROFILE_ID) {
                    "Phone internet / System"
                } else {
                    "Custom default route"
                },
                reason = "Traffic without a more specific app, domain, IP, or CIDR rule uses the selected default route.",
                technicalDetails = "DEFAULT, priority ${rule.priority}. Explicit rules override this route. An unavailable custom route blocks activation.",
                recommendedAction = "Create rules only for traffic that must use a VPN, another tunnel, Block, or TCP/TLS compatibility mode.",
            )
        } else {
            rule
        }
    },
)

fun defaultRouteActivationError(config: RoutingConfig): String? {
    val profileId = config.defaultProfileId
        ?: return "Основной маршрут не задан. Выберите обычный интернет телефона System или другой полностью настроенный маршрут."
    val profile = config.profiles.firstOrNull { it.id == profileId }
        ?: return "Выбранный основной маршрут не найден. Верните System или выберите существующий профиль."
    if (!profile.enabled) {
        return "Основной маршрут «${profile.name}» выключен. Включите его или верните System."
    }
    if (profile.type in setOf(TunnelType.Block, TunnelType.ByeDpi)) {
        return "Маршрут «${profile.name}» нельзя использовать как обычный интернет телефона. Назначьте его отдельному правилу."
    }
    val hasRuntimeConfiguration = when (profile.type) {
        TunnelType.Direct -> true
        TunnelType.Socks5 -> profile.socks5 != null
        TunnelType.VLESS,
        TunnelType.XrayVlessReality -> profile.vless != null
        else -> profile.singBox != null
    }
    if (!hasRuntimeConfiguration) {
        return "Для профиля «${profile.name}» ещё нет рабочего движка или полной конфигурации. Выберите готовый туннель."
    }
    return null
}

data class TunnelProfile(
    val id: String,
    val name: String,
    val type: TunnelType,
    val description: String,
    val enabled: Boolean = true,
    val mockOnly: Boolean = type.isMockOnly,
    val platformNotes: String? = null,
    val dnsPolicyId: String? = null,
    val secretRef: String? = null,
    val socks5: Socks5ProfileConfig? = null,
    val vless: VlessProfileConfig? = null,
    val singBox: SingBoxProfileConfig? = null,
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
    ByeDpi("Совместимость TCP/TLS", true),
    Zapret2("Обработчик пакетов zapret2", true),
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
    Hysteria("Hysteria", true),
    Hysteria2("Hysteria2", true),
    Snell("Snell", true),
    Tuic("TUIC", true),
    AnyTls("AnyTLS", true),
    NaiveProxy("NaiveProxy", true),
    Brook("Brook", true),
    ShadowTls("ShadowTLS", true),
    Socks5("SOCKS5", true),
    VLESS("VLESS", true),
    HttpProxy("HTTP proxy", true),
    HttpsProxy("HTTPS proxy", true),
    SshTunnel("SSH tunnel", true),
    Tor("Tor", true),
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
    val servers: List<DnsServerConfig> = emptyList(),
)

data class DnsServerConfig(
    val id: String,
    val address: String,
    val priority: Int = 0,
    val enabled: Boolean = true,
)

fun DnsPolicy.orderedServers(): List<DnsServerConfig> {
    val configured = servers
        .filter { it.enabled && it.address.isNotBlank() }
        .sortedWith(compareBy<DnsServerConfig> { it.priority }.thenBy { it.id })
    if (configured.isNotEmpty()) return configured
    return serverText
        .orEmpty()
        .split(Regex("[,;\\s]+"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .mapIndexed { index, address ->
            DnsServerConfig(
                id = "legacy-$index",
                address = address,
                priority = index,
            )
        }
}

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
    val destinationPorts: List<DestinationPortRange> = emptyList(),
    val transport: RouteTransport = RouteTransport.Any,
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
        SOCKS5_RUNTIME_STATUS
    } else if (tunnelProfile.type == TunnelType.VLESS) {
        VLESS_ROUTE_DECISION_STATUS
    } else if (tunnelProfile.mockOnly) {
        MOCK_PROFILE_LIMITATION
    } else {
        "Профиль не является mock-тоннелем."
    }
    val dnsLeakSummary: String = if (dnsPolicy == null || dnsPolicy.type == DnsPolicyType.System) {
        "Uses the configured system DNS path through the active ViRouteFS TUN router."
    } else {
        "The runtime intercepts DNS and applies custom upstream rules to domains and Android packages."
    }
}

data class RouteQuery(
    val value: String,
    val destinationPort: Int? = null,
    val transport: RouteTransport = RouteTransport.Any,
)

class RouteEngine(
    private val config: RoutingConfig,
) {
    private val profilesById = config.profiles.associateBy { it.id }
    private val dnsPoliciesById = config.dnsPolicies.associateBy { it.id }
    private val enabledRules = config.rules
        .filter { it.enabled }
        .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name })

    fun simulate(rawInput: String): RouteDecision = simulate(RouteQuery(rawInput))

    fun simulate(query: RouteQuery): RouteDecision {
        val normalizedInput = query.value.trim().ifBlank { "default" }
        val comparableInput = normalizedInput.lowercase(Locale.ROOT)
        val matchedRule = enabledRules.firstOrNull { rule ->
            rule.type != RouteRuleType.DEFAULT &&
                rule.matches(query.copy(value = comparableInput))
        } ?: enabledRules.firstOrNull { it.type == RouteRuleType.DEFAULT }
        ?: config.rules.first { it.type == RouteRuleType.DEFAULT }
        val effectiveRule = if (matchedRule.type == RouteRuleType.DEFAULT) {
            matchedRule.copy(
                targetProfileId = config.defaultProfileId ?: RoutingConfigDefaults.BLOCK_PROFILE_ID,
            )
        } else {
            matchedRule
        }

        val targetProfile = profilesById[effectiveRule.targetProfileId]
        val blockProfile = profilesById[RoutingConfigDefaults.BLOCK_PROFILE_ID]
            ?: config.profiles.firstOrNull { it.type == TunnelType.Block }
        val fallbackProfile = blockProfile
            ?: config.profiles.first()
        val selectedProfile = targetProfile?.takeIf { it.enabled } ?: fallbackProfile
        val dnsPolicy = effectiveRule.dnsPolicyId?.let { dnsPoliciesById[it] }?.takeIf { it.enabled }
        val warnings = buildWarnings(effectiveRule, targetProfile, selectedProfile, dnsPolicy)

        return RouteDecision(
            input = normalizedInput,
            tunnelProfile = selectedProfile,
            dnsPolicy = dnsPolicy,
            matchedRule = effectiveRule,
            plainReason = effectiveRule.reason,
            technicalDetails = buildTechnicalDetails(effectiveRule, selectedProfile, dnsPolicy, warnings),
            recommendedAction = effectiveRule.recommendedAction,
            warnings = warnings,
        )
    }

    private fun RouteRule.matches(query: RouteQuery): Boolean {
        if (transport != RouteTransport.Any && query.transport != transport) return false
        if (destinationPorts.isNotEmpty()) {
            val port = query.destinationPort ?: return false
            if (destinationPorts.none { it.contains(port) }) return false
        }
        return when (type) {
            RouteRuleType.APP_GROUP,
            RouteRuleType.APP -> textMatchers().any { matcher -> query.value == matcher }
            RouteRuleType.DOMAIN -> textMatchers().any { matcher -> domainMatches(query.value, matcher) }
            RouteRuleType.CIDR -> matchers.any { matcher ->
                if ('/' in matcher) {
                    ipAddressInCidr(query.value, matcher)
                } else {
                    query.value == matcher.lowercase(Locale.ROOT)
                }
            }
            RouteRuleType.DEFAULT -> true
        }
    }

    private fun RouteRule.textMatchers(): List<String> = buildList {
        addAll(matchers)
        addAll(appMatchers.map { it.value })
        addAll(appMatchers.mapNotNull { it.displayName })
    }.map { it.lowercase(Locale.ROOT).trim() }.filter { it.isNotBlank() && it != "*" }

    private fun domainMatches(input: String, matcher: String): Boolean {
        val host = input
            .substringAfter("://", input)
            .substringBefore('/')
            .substringBefore(':')
            .trim()
            .trimEnd('.')
        return when {
            matcher.startsWith("full:") -> host == matcher.removePrefix("full:").trimEnd('.')
            matcher.startsWith("keyword:") -> host.contains(matcher.removePrefix("keyword:"))
            matcher.startsWith("regexp:") -> runCatching {
                Regex(matcher.removePrefix("regexp:")).matches(host)
            }.getOrDefault(false)
            else -> {
                val domain = matcher.removePrefix("domain:").removePrefix("*.").trimEnd('.')
                host == domain || host.endsWith(".$domain")
            }
        }
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
        if (selectedProfile.mockOnly &&
            selectedProfile.type != TunnelType.Socks5 &&
            selectedProfile.type != TunnelType.VLESS &&
            selectedProfile.singBox == null
        ) {
            add(MOCK_PROFILE_LIMITATION)
        }
        if (dnsPolicy == null && matchedRule.dnsPolicyId != null) {
            add("DNS-политика правила не найдена или отключена. Возможна утечка DNS через системные настройки.")
        }
        if (dnsPolicy != null && dnsPolicy.type != DnsPolicyType.System &&
            (matchedRule.type == RouteRuleType.APP || matchedRule.type == RouteRuleType.APP_GROUP)
        ) {
            add("Custom DNS will be matched to the Android package by the local sing-box runtime. Android 10 or newer is required.")
        }
        if (selectedProfile.type !in setOf(
                TunnelType.Direct,
                TunnelType.Block,
                TunnelType.Socks5,
                TunnelType.VLESS,
                TunnelType.XrayVlessReality,
            ) && selectedProfile.singBox == null
        ) {
            add("The selected protocol is not connected to the current stable runtime. Activation maps it to Block / fail closed.")
        }
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
        appendLine("Транспорт: ${rule.transport.name}")
        appendLine("Порты назначения: ${rule.destinationPorts.toDisplayText().ifBlank { "любые" }}")
        appendLine("Профиль: ${profile.name} (${profile.type.label})")
        if (profile.type == TunnelType.Socks5) appendLine(SOCKS5_RUNTIME_STATUS)
        if (profile.type == TunnelType.VLESS) appendLine(VLESS_ROUTE_DECISION_STATUS)
        if (profile.singBox != null) appendLine("Профиль подключён к локальному sing-box runtime.")
        appendLine("DNS-политика: ${dnsPolicy?.name ?: "не выбрана"}")
        appendLine("DNS default: ${if (dnsPolicy == null || dnsPolicy.type == DnsPolicyType.System) "Android system DNS through TUN" else "custom policy compiled into runtime"}")
        if (warnings.isNotEmpty()) {
            appendLine("Предупреждения:")
            warnings.forEach { appendLine("- $it") }
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
        if (profile.type == TunnelType.VLESS || profile.type == TunnelType.XrayVlessReality) {
            val vless = profile.vless
            if (vless == null) {
                add("${profile.type.label} profile ${profile.name} has no VLESS configuration.")
            } else {
                validateVlessProfile(vless).forEach { add("Профиль ${profile.name}: $it") }
            }
        }
        profile.singBox?.let { singBox ->
            validateSingBoxProfile(profile.type, singBox).forEach {
                add("Профиль ${profile.name}: $it")
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
        policy.orderedServers().forEach { server ->
            if (server.id.isBlank()) add("DNS-политика ${policy.name}: сервер без id.")
            if (server.priority < 0) {
                add("DNS-политика ${policy.name}: приоритет DNS-сервера должен быть неотрицательным.")
            }
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
        rule.destinationPorts.forEach { range ->
            if (range.first !in 1..65535 || range.last !in range.first..65535) {
                add("Правило ${rule.name}: неверный порт назначения ${range.toDisplayText()}.")
            }
        }
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
