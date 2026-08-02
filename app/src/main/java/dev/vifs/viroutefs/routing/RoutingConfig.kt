package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import dev.vifs.viroutefs.vless.VLESS_RUNTIME_LIMITATION
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.validateVlessProfile
import java.util.Locale

const val CURRENT_ROUTING_CONFIG_VERSION = 13
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
    val profileGroups: List<ProfileGroup> = emptyList(),
    val subscriptions: List<ProfileSubscription> = emptyList(),
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

fun RoutingConfig.withoutProfile(profileId: String): RoutingConfig {
    val updatedGroups = profileGroups.map { group ->
        val members = group.memberProfileIds.filterNot { it == profileId }
        group.copy(
            memberProfileIds = members,
            selectedProfileId = group.selectedProfileId
                ?.takeIf { it in members }
                ?: members.firstOrNull(),
        )
    }
    val removedGroupIds = updatedGroups
        .filter { it.memberProfileIds.distinct().size < 2 }
        .mapTo(linkedSetOf()) { it.id }
    val removedTargetIds = removedGroupIds + profileId
    var next = copy(
        profiles = profiles.filterNot { it.id == profileId },
        profileGroups = updatedGroups.filterNot { it.id in removedGroupIds },
        rules = rules.map { rule ->
            if (rule.targetProfileId in removedTargetIds) {
                rule.copy(targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID)
            } else {
                rule
            }
        },
        dnsPolicies = dnsPolicies.map { policy ->
            if (policy.resolveThroughProfileId in removedTargetIds) {
                policy.copy(
                    enabled = false,
                    resolveThroughProfileId = null,
                    description = "${policy.description} Disabled because its route target was removed.",
                )
            } else {
                policy
            }
        },
    )
    if (defaultProfileId in removedTargetIds) {
        next = next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
    }
    return next
}

fun defaultRouteActivationError(config: RoutingConfig): String? {
    val targetId = config.defaultProfileId
        ?: return "Основной маршрут не задан. Выберите обычный интернет телефона System или другой полностью настроенный маршрут."
    val group = config.profileGroups.firstOrNull { it.id == targetId }
    if (group != null) {
        if (!group.enabled) {
            return "Основная группа «${group.name}» выключена. Включите её или верните System."
        }
        val members = group.memberProfileIds
            .distinct()
            .mapNotNull { memberId -> config.profiles.firstOrNull { it.id == memberId } }
        if (members.size != group.memberProfileIds.distinct().size || members.size < 2) {
            return "Основная группа «${group.name}» содержит отсутствующие профили или меньше двух участников."
        }
        if (group.mode == ProfileGroupMode.Manual &&
            group.selectedProfileId !in group.memberProfileIds
        ) {
            return "В основной группе «${group.name}» не выбран активный профиль."
        }
        val availableMembers = members.filter { it.enabled && it.hasRuntimeConfiguration() }
        if (group.mode == ProfileGroupMode.Manual) {
            val selected = members.firstOrNull { it.id == group.selectedProfileId }
            if (selected == null || !selected.enabled || !selected.hasRuntimeConfiguration()) {
                return "Выбранный профиль основной группы «${group.name}» выключен или настроен не полностью."
            }
        }
        if (group.mode == ProfileGroupMode.Latency && availableMembers.size < 2) {
            return "В основной группе «${group.name}» осталось меньше двух доступных профилей."
        }
        if (group.mode in setOf(ProfileGroupMode.Failover, ProfileGroupMode.RoundRobin) &&
            availableMembers.isEmpty()
        ) {
            return "В основной группе «${group.name}» нет ни одного доступного профиля."
        }
        return null
    }
    val profile = config.profiles.firstOrNull { it.id == targetId }
        ?: return "Выбранный основной маршрут не найден. Верните System или выберите существующий профиль или группу."
    if (!profile.enabled) {
        return "Основной маршрут «${profile.name}» выключен. Включите его или верните System."
    }
    if (profile.type in setOf(TunnelType.Block, TunnelType.ByeDpi)) {
        return "Маршрут «${profile.name}» нельзя использовать как обычный интернет телефона. Назначьте его отдельному правилу."
    }
    if (!profile.hasRuntimeConfiguration()) {
        return "Для профиля «${profile.name}» ещё нет рабочего движка или полной конфигурации. Выберите готовый туннель."
    }
    return null
}

internal fun TunnelProfile.hasRuntimeConfiguration(): Boolean = when (type) {
    TunnelType.Direct,
    TunnelType.ByeDpi -> true
    TunnelType.Socks5 -> socks5 != null
    TunnelType.VLESS,
    TunnelType.XrayVlessReality -> vless != null
    else -> singBox != null
}

data class ProfileGroup(
    val id: String,
    val name: String,
    val mode: ProfileGroupMode,
    val memberProfileIds: List<String>,
    val selectedProfileId: String? = null,
    val testUrl: String = "https://www.gstatic.com/generate_204",
    val testIntervalSeconds: Int = 180,
    val toleranceMs: Int = 50,
    val enabled: Boolean = true,
)

data class ProfileSubscription(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean = true,
    val lastUpdatedAtEpochMs: Long? = null,
    val lastProfileCount: Int = 0,
) {
    override fun toString(): String =
        "ProfileSubscription(id=$id, name=$name, url=<redacted>, enabled=$enabled, " +
            "lastUpdatedAtEpochMs=$lastUpdatedAtEpochMs, lastProfileCount=$lastProfileCount)"
}

enum class ProfileGroupMode {
    Manual,
    Latency,
    Failover,
    RoundRobin,
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
    val sourceSubscriptionId: String? = null,
    val sourceEntryKey: String? = null,
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
    /**
     * Existing configurations keep their former primary-only behavior. New
     * custom policies enable this explicitly from the DNS editor.
     */
    val fallbackEnabled: Boolean = false,
    val queryTimeoutSeconds: Int = 5,
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

enum class DomainMatcherMode {
    Exact,
    Suffix,
    Keyword,
    Regex,
}

data class ParsedDomainMatcher(
    val mode: DomainMatcherMode,
    val value: String,
)

fun parseDomainMatcher(raw: String): ParsedDomainMatcher {
    val trimmed = raw.trim()
    val prefixProbe = trimmed.lowercase(Locale.ROOT)
    val (mode, value) = when {
        prefixProbe.startsWith("full:") -> DomainMatcherMode.Exact to trimmed.substring(5)
        prefixProbe.startsWith("keyword:") -> DomainMatcherMode.Keyword to trimmed.substring(8)
        prefixProbe.startsWith("regexp:") -> DomainMatcherMode.Regex to trimmed.substring(7)
        prefixProbe.startsWith("domain:") ->
            DomainMatcherMode.Suffix to trimmed.substring(7).removePrefix("*.")
        prefixProbe.startsWith("*.") -> DomainMatcherMode.Suffix to trimmed.substring(2)
        else -> DomainMatcherMode.Suffix to trimmed
    }
    val normalizedValue = when (mode) {
        DomainMatcherMode.Exact,
        DomainMatcherMode.Suffix -> value.trim().trimEnd('.').lowercase(Locale.ROOT)
        DomainMatcherMode.Keyword -> value.trim().lowercase(Locale.ROOT)
        DomainMatcherMode.Regex -> value.trim()
    }
    return ParsedDomainMatcher(mode, normalizedValue)
}

fun encodeDomainMatcher(mode: DomainMatcherMode, rawValue: String): String {
    val trimmed = rawValue.trim()
    val value = if (mode == DomainMatcherMode.Exact || mode == DomainMatcherMode.Suffix) {
        trimmed.trimEnd('.').lowercase(Locale.ROOT)
    } else if (mode == DomainMatcherMode.Keyword) {
        trimmed.lowercase(Locale.ROOT)
    } else {
        trimmed
    }
    return when (mode) {
        DomainMatcherMode.Exact -> "full:$value"
        DomainMatcherMode.Suffix -> "domain:${value.removePrefix("*.")}"
        DomainMatcherMode.Keyword -> "keyword:$value"
        DomainMatcherMode.Regex -> "regexp:$value"
    }
}

fun validateDomainMatcher(mode: DomainMatcherMode, rawValue: String): String? {
    val value = if (mode == DomainMatcherMode.Exact || mode == DomainMatcherMode.Suffix) {
        rawValue.trim().trimEnd('.')
    } else {
        rawValue.trim()
    }
    if (value.isBlank()) return "Укажите домен или шаблон."
    return when (mode) {
        DomainMatcherMode.Exact,
        DomainMatcherMode.Suffix -> {
            val normalized = value.removePrefix("*.")
            when {
                normalized.length > 253 -> "Домен не должен быть длиннее 253 символов."
                normalized.any(Char::isWhitespace) || normalized.any { it in "/:?#" } ->
                    "Введите только имя домена без схемы, пути, порта и пробелов."
                normalized.split('.').any { label ->
                    label.isBlank() || label.length > 63 || label.startsWith('-') || label.endsWith('-')
                } -> "Проверьте части домена: каждая должна содержать от 1 до 63 символов и не начинаться с дефиса."
                else -> null
            }
        }
        DomainMatcherMode.Keyword -> when {
            value.length > 253 -> "Ключевое слово не должно быть длиннее 253 символов."
            value.any(Char::isWhitespace) -> "Ключевое слово домена не должно содержать пробелы."
            else -> null
        }
        DomainMatcherMode.Regex -> when {
            value.length > 512 -> "Регулярное выражение не должно быть длиннее 512 символов."
            else -> runCatching { Regex(value) }.exceptionOrNull()
                ?.let { "Некорректное регулярное выражение: ${it.message ?: "ошибка синтаксиса"}" }
        }
    }
}

data class RouteDecision(
    val input: String,
    val targetId: String,
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
    private val groupsById = config.profileGroups.associateBy { it.id }
    private val dnsPoliciesById = config.dnsPolicies.associateBy { it.id }
    private val enabledRules = config.rules
        .filter { it.enabled }
        .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name }.thenBy { it.id })

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

        val targetGroup = groupsById[effectiveRule.targetProfileId]?.takeIf { it.enabled }
        val targetProfile = profilesById[effectiveRule.targetProfileId]
            ?: targetGroup?.let { group ->
                val memberId = when (group.mode) {
                    ProfileGroupMode.Manual -> group.selectedProfileId
                    ProfileGroupMode.Latency,
                    ProfileGroupMode.Failover,
                    ProfileGroupMode.RoundRobin -> group.memberProfileIds.firstOrNull { memberId ->
                        profilesById[memberId]?.let { it.enabled && it.hasRuntimeConfiguration() } == true
                    }
                }
                memberId?.let(profilesById::get)
            }
        val blockProfile = profilesById[RoutingConfigDefaults.BLOCK_PROFILE_ID]
            ?: config.profiles.firstOrNull { it.type == TunnelType.Block }
        val fallbackProfile = blockProfile
            ?: config.profiles.first()
        val selectedProfile = targetProfile?.takeIf { it.enabled } ?: fallbackProfile
        val dnsPolicy = effectiveRule.dnsPolicyId?.let { dnsPoliciesById[it] }?.takeIf { it.enabled }
        val warnings = buildWarnings(effectiveRule, targetProfile, targetGroup, selectedProfile, dnsPolicy)

        return RouteDecision(
            input = normalizedInput,
            targetId = effectiveRule.targetProfileId,
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
        val parsed = parseDomainMatcher(matcher)
        return when (parsed.mode) {
            DomainMatcherMode.Exact -> host == parsed.value
            DomainMatcherMode.Keyword -> host.contains(parsed.value)
            DomainMatcherMode.Regex -> runCatching { Regex(parsed.value).matches(host) }.getOrDefault(false)
            DomainMatcherMode.Suffix -> host == parsed.value || host.endsWith(".${parsed.value}")
        }
    }

    private fun buildWarnings(
        matchedRule: RouteRule,
        targetProfile: TunnelProfile?,
        targetGroup: ProfileGroup?,
        selectedProfile: TunnelProfile,
        dnsPolicy: DnsPolicy?,
    ): List<String> = buildList {
        if (targetProfile == null && targetGroup == null) {
            add("Выбранное правилом направление не найдено. Модель безопасного поведения: Block / fail closed; без тихого fallback на другой профиль.")
        } else if (targetProfile != null && !targetProfile.enabled) {
            add("Профиль правила отключён. Модель безопасного поведения: Block / fail closed; без тихого fallback на другой профиль.")
        }
        if (targetGroup?.mode == ProfileGroupMode.Latency) {
            add("Фактического участника latency-группы выбирает sing-box по HTTPS-проверке; локальный предпросмотр показывает первого участника, а не угадывает runtime-результат.")
        }
        if (targetGroup?.mode == ProfileGroupMode.Failover) {
            add("Фактического участника резервной группы выбирает runtime-контроллер по порядку и HTTPS-проверке; локальный предпросмотр показывает первый настроенный маршрут.")
        }
        if (targetGroup?.mode == ProfileGroupMode.RoundRobin) {
            add("Round-robin распределяет новые соединения между доступными участниками; локальный предпросмотр показывает первый настроенный маршрут.")
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
    val groupIds = config.profileGroups.map { it.id }.toSet()
    val subscriptionIds = config.subscriptions.map { it.id }.toSet()
    val targetIds = profileIds + groupIds
    val dnsPolicyIds = config.dnsPolicies.map { it.id }.toSet()
    config.defaultProfileId?.takeIf { it !in targetIds }?.let { add("Основной профиль или группа $it не найдены.") }
    if (config.profiles.isEmpty()) add("Нужен хотя бы один профиль маршрута.")
    if (config.rules.isEmpty()) add("Нужно хотя бы одно правило маршрутизации.")
    if ((profileIds intersect groupIds).isNotEmpty()) {
        add("Идентификаторы профилей и групп не должны совпадать.")
    }
    if (config.profileGroups.size != groupIds.size) {
        add("Идентификаторы групп маршрутов должны быть уникальными.")
    }
    if (config.subscriptions.size != subscriptionIds.size) {
        add("Идентификаторы подписок должны быть уникальными.")
    }
    if (config.subscriptions.size > 50) {
        add("Можно сохранить не более 50 подписок.")
    }
    config.subscriptions.forEach { subscription ->
        if (subscription.id.isBlank()) add("Подписка без id: ${subscription.name}")
        if (subscription.name.isBlank()) add("Подписка ${subscription.id} без имени.")
        if (subscription.name.length > 120 || subscription.name.any(::isUnsafeSubscriptionDisplayCharacter)) {
            add("Подписка ${subscription.id}: имя слишком длинное или содержит служебные символы.")
        }
        validateSubscriptionUrlSyntax(subscription.url)?.let {
            add("Подписка ${subscription.name}: $it")
        }
        if (subscription.lastProfileCount < 0) {
            add("Подписка ${subscription.name}: число профилей не может быть отрицательным.")
        }
    }
    config.profileGroups.forEach { group ->
        val members = group.memberProfileIds.distinct()
        if (group.id.isBlank()) add("Группа маршрутов без id: ${group.name}")
        if (group.name.isBlank()) add("Группа ${group.id} без имени.")
        if (members.size < 2) add("Группа ${group.name}: выберите минимум два разных профиля.")
        members.filterNot { it in profileIds }.forEach {
            add("Группа ${group.name}: профиль $it не найден.")
        }
        if (RoutingConfigDefaults.BLOCK_PROFILE_ID in members) {
            add("Группа ${group.name}: Block нельзя использовать как участника автоматической группы.")
        }
        if (group.mode == ProfileGroupMode.Manual && group.selectedProfileId !in members) {
            add("Группа ${group.name}: выбранный профиль не входит в группу.")
        }
        if (group.mode != ProfileGroupMode.Manual) {
            if (!group.testUrl.startsWith("https://", ignoreCase = true)) {
                add("Группа ${group.name}: проверка доступности должна использовать HTTPS.")
            }
            if (group.testIntervalSeconds !in 30..3600) {
                add("Группа ${group.name}: интервал проверки должен быть от 30 до 3600 секунд.")
            }
            if (group.mode == ProfileGroupMode.Latency && group.toleranceMs !in 0..2000) {
                add("Группа ${group.name}: допуск задержки должен быть от 0 до 2000 мс.")
            }
        }
    }
    config.profileGroups
        .filter { it.mode != ProfileGroupMode.Manual }
        .forEachIndexed { index, group ->
            config.profileGroups
                .filter { it.mode != ProfileGroupMode.Manual }
                .drop(index + 1)
                .filter { other ->
                    group.memberProfileIds.any { it in other.memberProfileIds } &&
                        group.testUrl.trim() != other.testUrl.trim()
                }
                .forEach { other ->
                    add(
                        "Группы ${group.name} и ${other.name} используют общий профиль, " +
                            "поэтому для точной проверки у них должен быть одинаковый HTTPS-адрес.",
                    )
                }
        }
    config.profiles.forEach { profile ->
        if (profile.id.isBlank()) add("Профиль без id: ${profile.name}")
        if (profile.name.isBlank()) add("Профиль ${profile.id} без имени.")
        if (profile.sourceSubscriptionId == null && profile.sourceEntryKey != null) {
            add("Профиль ${profile.name}: ключ подписки задан без самой подписки.")
        }
        profile.sourceSubscriptionId?.let { subscriptionId ->
            if (subscriptionId !in subscriptionIds) {
                add("Профиль ${profile.name}: подписка $subscriptionId не найдена.")
            }
            if (profile.sourceEntryKey.isNullOrBlank()) {
                add("Профиль ${profile.name}: отсутствует устойчивый ключ записи подписки.")
            }
        }
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
        policy.resolveThroughProfileId?.takeIf { it !in targetIds }?.let {
            add("DNS-политика ${policy.name} ссылается на отсутствующий профиль или группу $it.")
        }
        if (policy.queryTimeoutSeconds !in 1..30) {
            add("DNS-политика ${policy.name}: тайм-аут запроса должен быть от 1 до 30 секунд.")
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
        if (rule.targetProfileId !in targetIds) add("Правило ${rule.name}: профиль или группа ${rule.targetProfileId} не найдены.")
        rule.dnsPolicyId?.takeIf { it !in dnsPolicyIds }?.let { add("Правило ${rule.name}: DNS-политика $it не найдена.") }
        if (rule.type == RouteRuleType.CIDR) {
            rule.matchers
                .filterNot { isValidIpOrCidr(it) }
                .forEach { add("Правило ${rule.name}: некорректный IP/CIDR $it.") }
        }
        if (rule.type == RouteRuleType.DOMAIN) {
            rule.matchers.forEach { raw ->
                val parsed = parseDomainMatcher(raw)
                validateDomainMatcher(parsed.mode, parsed.value)?.let {
                    add("Правило ${rule.name}: $it")
                }
            }
        }
    }
    config.profiles
        .filter { it.sourceSubscriptionId != null }
        .groupBy { it.sourceSubscriptionId to it.sourceEntryKey }
        .filterValues { it.size > 1 }
        .forEach { (source, _) ->
            add("Подписка ${source.first}: ключ записи ${source.second} используется несколько раз.")
        }
    findExactRouteConflicts(config.rules).forEach { add(it.message) }
    if (config.rules.count { it.enabled && it.type == RouteRuleType.DEFAULT } != 1) {
        add("Должно быть активно ровно одно правило DEFAULT.")
    }
}

private fun isUnsafeSubscriptionDisplayCharacter(value: Char): Boolean =
    value.isISOControl() || Character.getType(value) == Character.FORMAT.toInt()
