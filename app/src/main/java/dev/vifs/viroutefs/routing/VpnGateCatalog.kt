// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

const val VPN_GATE_CATALOG_URL = "https://www.vpngate.net/api/iphone/"

data class VpnGateServer(
    val hostName: String,
    val ipAddress: String,
    val score: Long,
    val pingMillis: Int?,
    val speedBitsPerSecond: Long,
    val countryName: String,
    val countryCode: String,
    val activeSessions: Int,
    val uptimeMillis: Long,
    val totalUsers: Long,
    val logType: String,
    val operator: String,
    val message: String,
    val openVpnConfigBase64: String,
) {
    val stableKey: String
        get() = "$hostName|$ipAddress"
}

data class VpnGateAutomaticRouteResult(
    val config: RoutingConfig,
    val selectedServers: List<VpnGateServer>,
    val rejectedProfiles: Int,
)

fun parseVpnGateCatalog(source: String, maxServers: Int = MAX_VPN_GATE_SERVERS): List<VpnGateServer> {
    require(source.toByteArray(StandardCharsets.UTF_8).size <= MAX_VPN_GATE_CATALOG_BYTES) {
        "Каталог VPNGate превышает безопасный размер."
    }
    require(maxServers in 1..MAX_VPN_GATE_SERVERS) { "Некорректный лимит серверов VPNGate." }

    var indexes: Map<String, Int>? = null
    val servers = mutableListOf<VpnGateServer>()
    source.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank() || line == "*vpn_servers" || line == "*") return@forEach
        if (line.startsWith("#HostName,")) {
            indexes = parseCsvRow(line.removePrefix("#"))
                .mapIndexed { index, name -> name.trim() to index }
                .toMap()
            return@forEach
        }
        val header = indexes ?: return@forEach
        if (servers.size >= maxServers) return@forEach
        val fields = parseCsvRow(line)
        fun field(name: String): String = header[name]
            ?.takeIf { it in fields.indices }
            ?.let(fields::get)
            .orEmpty()
            .trim()

        val hostName = field("HostName").take(MAX_VPN_GATE_TEXT_LENGTH)
        val ipAddress = field("IP").take(MAX_VPN_GATE_TEXT_LENGTH)
        val encodedConfig = field("OpenVPN_ConfigData_Base64")
        if (hostName.isBlank() || ipAddress.isBlank() || encodedConfig.isBlank()) return@forEach
        if (encodedConfig.length > MAX_VPN_GATE_CONFIG_BASE64_CHARS) return@forEach

        servers += VpnGateServer(
            hostName = hostName,
            ipAddress = ipAddress,
            score = field("Score").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            pingMillis = field("Ping").toIntOrNull()?.takeIf { it >= 0 },
            speedBitsPerSecond = field("Speed").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            countryName = field("CountryLong").take(MAX_VPN_GATE_TEXT_LENGTH),
            countryCode = field("CountryShort").uppercase().take(3),
            activeSessions = field("NumVpnSessions").toIntOrNull()?.coerceAtLeast(0) ?: 0,
            uptimeMillis = field("Uptime").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            totalUsers = field("TotalUsers").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            logType = field("LogType").take(MAX_VPN_GATE_TEXT_LENGTH),
            operator = field("Operator").take(MAX_VPN_GATE_LONG_TEXT_LENGTH),
            message = field("Message").take(MAX_VPN_GATE_LONG_TEXT_LENGTH),
            openVpnConfigBase64 = encodedConfig,
        )
    }
    require(indexes != null) { "VPNGate вернул каталог без заголовка." }
    require(servers.isNotEmpty()) { "VPNGate не вернул доступных OpenVPN-серверов." }
    return servers.distinctBy(VpnGateServer::stableKey)
}

fun decodeVpnGateOpenVpnConfig(server: VpnGateServer): String {
    require(server.openVpnConfigBase64.length <= MAX_VPN_GATE_CONFIG_BASE64_CHARS) {
        "Профиль VPNGate превышает безопасный размер."
    }
    val decoded = runCatching { Base64.getDecoder().decode(server.openVpnConfigBase64) }
        .getOrElse { error("VPNGate вернул повреждённый OpenVPN-профиль.") }
    require(decoded.size <= MAX_VPN_GATE_CONFIG_BYTES) { "OpenVPN-профиль VPNGate слишком большой." }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val source = runCatching { decoder.decode(ByteBuffer.wrap(decoded)).toString() }
        .getOrElse { error("OpenVPN-профиль VPNGate содержит некорректный текст.") }
    require('\u0000' !in source) { "OpenVPN-профиль VPNGate содержит недопустимые данные." }
    return source
}

/**
 * Converts the public VPNGate .ovpn file through the same conservative importer
 * as a local file. Unknown directives and external commands are never executed.
 */
fun previewVpnGateProfile(server: VpnGateServer): ProfileImportPreview {
    val source = decodeVpnGateOpenVpnConfig(server)
    val preview = previewProfileImport(source)
    val original = preview.candidates.singleOrNull()
        ?: error("VPNGate должен содержать ровно один OpenVPN-профиль.")
    require(original.profile.type == TunnelType.OpenVpn) { "VPNGate вернул не OpenVPN-профиль." }

    val options = JSONObject(requireNotNull(original.profile.singBox).optionsJson)
    val usesPublicCredentials = source.lineSequence().any { line ->
        line.trim().substringBefore(' ').equals("auth-user-pass", ignoreCase = true)
    }
    if (usesPublicCredentials) {
        options.put("username", "vpn").put("password", "vpn")
    }
    val country = server.countryName.ifBlank { server.countryCode.ifBlank { "Unknown country" } }
    val name = "VPNGate • $country • ${server.hostName}".take(96)
    val profile = original.profile.copy(
        name = name,
        description = "$country • ${server.hostName} • добровольческий OpenVPN-сервер VPNGate",
        enabled = false,
        mockOnly = false,
        platformNotes = buildString {
            append("Публичный добровольческий сервер VPNGate. Доступность и политика оператора могут меняться.")
            if (usesPublicCredentials) append(" Учётные данные vpn/vpn являются общедоступными.")
        },
        singBox = original.profile.singBox.copy(optionsJson = options.toString(2)),
    )
    val candidate = original.copy(
        profile = profile,
        fingerprint = profileFingerprint(profile),
        warnings = (original.warnings + VPN_GATE_VOLUNTEER_WARNING).distinct(),
    )
    return ProfileImportPreview(
        candidates = listOf(candidate),
        warnings = (preview.warnings + VPN_GATE_VOLUNTEER_WARNING).distinct(),
    )
}

fun createAutomaticVpnGateRoute(
    config: RoutingConfig,
    servers: List<VpnGateServer>,
    excludedCountryCode: String,
    memberLimit: Int = VPN_GATE_AUTOMATIC_MEMBER_LIMIT,
    makeDefault: Boolean = true,
): VpnGateAutomaticRouteResult {
    val countryCode = excludedCountryCode.trim().uppercase()
    require(countryCode.matches(Regex("[A-Z]{2}"))) {
        "Укажите двухбуквенный код своей страны, чтобы она не попала в автоматический выбор."
    }
    require(memberLimit in 2..VPN_GATE_AUTOMATIC_MEMBER_LIMIT) {
        "Для автоматического VPNGate нужно от 2 до $VPN_GATE_AUTOMATIC_MEMBER_LIMIT серверов."
    }

    val candidates = servers
        .asSequence()
        .filter { it.countryCode.length == 2 && it.countryCode != countryCode }
        .filter { (it.pingMillis ?: 0) > 0 }
        .sortedWith(
            compareBy<VpnGateServer> { it.pingMillis ?: Int.MAX_VALUE }
                .thenByDescending { it.score }
                .thenBy { it.stableKey },
        )
        .take(VPN_GATE_AUTOMATIC_IMPORT_ATTEMPTS)
        .toList()

    var rejected = 0
    val selected = mutableListOf<Pair<VpnGateServer, TunnelProfile>>()
    candidates.forEach { server ->
        if (selected.size >= memberLimit) return@forEach
        runCatching { previewVpnGateProfile(server).candidates.single().profile }
            .onSuccess { imported ->
                val profileId = automaticVpnGateProfileId(server)
                val existing = config.profiles.firstOrNull { it.id == profileId }
                selected += server to imported.copy(
                    id = profileId,
                    enabled = true,
                    sourceSubscriptionId = null,
                    sourceEntryKey = null,
                    dnsPolicyId = existing?.dnsPolicyId,
                    appRoutingMode = existing?.appRoutingMode ?: ProfileAppRoutingMode.SelectedApps,
                    appRoutingPackages = existing?.appRoutingPackages.orEmpty(),
                    appRoutingNetworks = existing?.appRoutingNetworks.orEmpty(),
                )
            }
            .onFailure { rejected += 1 }
    }
    require(selected.size >= 2) {
        "Не удалось подготовить хотя бы два исправных VPNGate-профиля из других стран. Обновите каталог или выберите сервер вручную."
    }

    val oldAutomaticIds = config.profiles
        .filter(TunnelProfile::isAutomaticVpnGateProfile)
        .mapTo(linkedSetOf(), TunnelProfile::id)
    val protectedOldIds = buildSet {
        config.rules
            .filter { it.type != RouteRuleType.DEFAULT && it.targetProfileId in oldAutomaticIds }
            .mapTo(this) { it.targetProfileId }
        config.dnsPolicies.mapNotNullTo(this) { it.resolveThroughProfileId?.takeIf(oldAutomaticIds::contains) }
        config.profileGroups
            .filterNot { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
            .flatMapTo(this) { it.memberProfileIds.filter(oldAutomaticIds::contains) }
    }
    val selectedIds = selected.mapTo(linkedSetOf()) { it.second.id }
    val retainedProfiles = config.profiles
        .filterNot { profile ->
            profile.id in selectedIds ||
                (profile.id in oldAutomaticIds && profile.id !in protectedOldIds)
        }
        .map { profile ->
            if (profile.isAutomaticVpnGateProfile()) {
                profile.copy(sourceSubscriptionId = null, sourceEntryKey = null)
            } else {
                profile
            }
        }
    val group = ProfileGroup(
        id = VPN_GATE_AUTOMATIC_GROUP_ID,
        name = "VPNGate • автоматический выбор",
        mode = ProfileGroupMode.Latency,
        memberProfileIds = selected.map { it.second.id },
        selectedProfileId = selected.first().second.id,
        testUrl = VPN_GATE_AUTOMATIC_TEST_URL,
        testIntervalSeconds = 60,
        toleranceMs = 35,
        enabled = true,
    )
    val prepared = config.copy(
        profiles = retainedProfiles + selected.map { it.second },
        profileGroups = config.profileGroups.filterNot { it.id == VPN_GATE_AUTOMATIC_GROUP_ID } + group,
    )
    val next = if (makeDefault || config.defaultProfileId == VPN_GATE_AUTOMATIC_GROUP_ID) {
        prepared.withDefaultRoute(group.id)
    } else {
        prepared.withDefaultRoute(config.defaultProfileId ?: RoutingConfigDefaults.SYSTEM_PROFILE_ID)
    }
    return VpnGateAutomaticRouteResult(
        config = next,
        selectedServers = selected.map { it.first },
        rejectedProfiles = rejected,
    )
}

fun TunnelProfile.isAutomaticVpnGateProfile(): Boolean =
    id.startsWith(VPN_GATE_AUTOMATIC_PROFILE_PREFIX) ||
        sourceSubscriptionId == VPN_GATE_LEGACY_AUTOMATIC_SOURCE_ID

fun RoutingConfig.hasAutomaticVpnGate(): Boolean =
    profileGroups.any { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }

fun RoutingConfig.isAutomaticVpnGateEnabled(): Boolean {
    val group = profileGroups.firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID } ?: return false
    val members = profiles.filter { it.id in group.memberProfileIds && it.isAutomaticVpnGateProfile() }
    return group.enabled && members.size >= 2 && members.any(TunnelProfile::enabled)
}

fun RoutingConfig.withAutomaticVpnGateEnabled(enabled: Boolean): RoutingConfig {
    val group = profileGroups.firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID } ?: return this
    val memberIds = group.memberProfileIds.toSet()
    val hasAppSelection = rules.any {
        it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID && it.appMatchers.isNotEmpty()
    }
    var next = copy(
        profiles = profiles.map { profile ->
            if (profile.id in memberIds && profile.isAutomaticVpnGateProfile()) {
                profile.copy(enabled = enabled, sourceSubscriptionId = null, sourceEntryKey = null)
            } else {
                profile
            }
        },
        profileGroups = profileGroups.map { current ->
            if (current.id == group.id) current.copy(enabled = enabled) else current
        },
        rules = rules.map { rule ->
            if (rule.id == VPN_GATE_AUTOMATIC_APP_RULE_ID) rule.copy(enabled = enabled) else rule
        },
    )
    next = when {
        !enabled && next.defaultProfileId == group.id ->
            next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
        enabled && !hasAppSelection -> next.withDefaultRoute(group.id)
        else -> next.withSyncedProfileAppRoutingRules()
    }
    return next
}

fun RoutingConfig.withAutomaticVpnGateApps(packageNames: Collection<String>): RoutingConfig {
    require(hasAutomaticVpnGate()) { "Сначала нужно подготовить автоматический VPNGate." }
    val packages = packageNames.map(String::trim).filter(String::isNotBlank).distinct().sorted()
    val withoutOldRule = rules.filterNot { it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID }
    val appRule = packages.takeIf { it.isNotEmpty() }?.let {
        RouteRule(
            id = VPN_GATE_AUTOMATIC_APP_RULE_ID,
            name = "Приложения через автоматический VPNGate",
            type = RouteRuleType.APP_GROUP,
            targetProfileId = VPN_GATE_AUTOMATIC_GROUP_ID,
            dnsPolicyId = RoutingConfigDefaults.SYSTEM_DNS_ID,
            priority = VPN_GATE_AUTOMATIC_APP_RULE_PRIORITY,
            matchers = emptyList(),
            appMatchers = packages.map { packageName ->
                AppMatcher(
                    platform = AppMatcherPlatform.Android,
                    value = packageName,
                    displayName = packageName,
                )
            },
            enabled = true,
            reason = "Пользователь выбрал приложения в мастере «Настрой всё за меня».",
            technicalDetails = "Управляемое правило VPNGate; остальные приложения используют основной маршрут System.",
            recommendedAction = "Измените выбор в мастере VPNGate или удалите автоматический VPNGate одной кнопкой.",
        )
    }
    return copy(rules = withoutOldRule + listOfNotNull(appRule))
        .withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
}

fun RoutingConfig.withoutAutomaticVpnGate(): RoutingConfig {
    val automaticIds = profiles
        .filter(TunnelProfile::isAutomaticVpnGateProfile)
        .mapTo(linkedSetOf(), TunnelProfile::id)
    val removedTargets = automaticIds + VPN_GATE_AUTOMATIC_GROUP_ID
    var next = copy(
        profiles = profiles.filterNot { it.id in automaticIds },
        profileGroups = profileGroups
            .filterNot { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
            .map { current ->
                val remainingMembers = current.memberProfileIds.filterNot(automaticIds::contains)
                current.copy(
                    memberProfileIds = remainingMembers,
                    selectedProfileId = current.selectedProfileId
                        ?.takeIf { it in remainingMembers }
                        ?: remainingMembers.firstOrNull(),
                )
            }
            .filter { it.memberProfileIds.distinct().size >= 2 },
        rules = rules.filterNot {
            it.id == VPN_GATE_AUTOMATIC_APP_RULE_ID || it.targetProfileId in removedTargets
        },
        dnsPolicies = dnsPolicies.map { policy ->
            if (policy.resolveThroughProfileId in removedTargets) {
                policy.copy(enabled = false, resolveThroughProfileId = null)
            } else {
                policy
            }
        },
    )
    if (next.defaultProfileId in removedTargets) {
        next = next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
    }
    return next.withSyncedProfileAppRoutingRules()
}

/** Repairs beta.11/beta.12 automatic profiles before full config validation. */
fun RoutingConfig.withMigratedVpnGateManagement(): RoutingConfig {
    val group = profileGroups.firstOrNull { it.id == VPN_GATE_AUTOMATIC_GROUP_ID }
    val hasLegacyProfiles = profiles.any { it.isAutomaticVpnGateProfile() }
    if (group == null && !hasLegacyProfiles) return this
    var next = copy(
        profiles = profiles.map { profile ->
            if (profile.isAutomaticVpnGateProfile()) {
                profile.copy(enabled = false, sourceSubscriptionId = null, sourceEntryKey = null)
            } else {
                profile
            }
        },
        profileGroups = profileGroups.map { current ->
            if (current.id == VPN_GATE_AUTOMATIC_GROUP_ID) current.copy(enabled = false) else current
        },
        rules = rules.map { rule ->
            if (rule.id == VPN_GATE_AUTOMATIC_APP_RULE_ID) rule.copy(enabled = false) else rule
        },
    )
    if (next.defaultProfileId == VPN_GATE_AUTOMATIC_GROUP_ID) {
        next = next.withDefaultRoute(RoutingConfigDefaults.SYSTEM_PROFILE_ID)
    }
    return next.withSyncedProfileAppRoutingRules()
}

private fun automaticVpnGateProfileId(server: VpnGateServer): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
        .digest(server.stableKey.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    return "$VPN_GATE_AUTOMATIC_PROFILE_PREFIX${digest.take(20)}"
}

private fun parseCsvRow(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index += 1
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                values += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index += 1
    }
    require(!quoted) { "VPNGate вернул повреждённую CSV-строку." }
    values += current.toString()
    return values
}

const val VPN_GATE_VOLUNTEER_WARNING =
    "VPNGate использует добровольческие сторонние серверы: не передавайте через них чувствительные данные без дополнительного сквозного шифрования."

private const val MAX_VPN_GATE_SERVERS = 512
private const val MAX_VPN_GATE_CATALOG_BYTES = 4 * 1024 * 1024
private const val MAX_VPN_GATE_CONFIG_BYTES = 256 * 1024
private const val MAX_VPN_GATE_CONFIG_BASE64_CHARS = 512 * 1024
private const val MAX_VPN_GATE_TEXT_LENGTH = 160
private const val MAX_VPN_GATE_LONG_TEXT_LENGTH = 512
const val VPN_GATE_AUTOMATIC_MEMBER_LIMIT = 6
private const val VPN_GATE_AUTOMATIC_IMPORT_ATTEMPTS = 32
private const val VPN_GATE_LEGACY_AUTOMATIC_SOURCE_ID = "vpngate:auto"
const val VPN_GATE_AUTOMATIC_PROFILE_PREFIX = "profile_vpngate_auto_"
const val VPN_GATE_AUTOMATIC_GROUP_ID = "group_vpngate_auto"
const val VPN_GATE_AUTOMATIC_APP_RULE_ID = "route_vpngate_auto_apps"
const val VPN_GATE_MANAGEMENT_MIGRATION_VERSION = 16
private const val VPN_GATE_AUTOMATIC_APP_RULE_PRIORITY = 15_000
private const val VPN_GATE_AUTOMATIC_TEST_URL = "https://www.gstatic.com/generate_204"
