// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.AppMatcherPlatform
import dev.vifs.viroutefs.routing.DnsPolicy
import dev.vifs.viroutefs.routing.DnsPolicyType
import dev.vifs.viroutefs.routing.RouteRule
import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.SingBoxProfileKind
import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.normalizedSingBoxProfileObject
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.validateVlessProfile
import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal const val SING_BOX_TUN_TAG = "viroutefs-tun"
internal const val SING_BOX_BLOCK_TAG = "profile_block"
internal const val SING_BOX_DIRECT_TAG = "profile_direct"

internal data class SingBoxCompiledRuntime(
    val json: String,
    val warnings: List<String>,
    val runtimeProfileIds: Set<String>,
    val profileTags: Map<String, String>,
)

/**
 * Compiles the user-facing ViRouteFS model to the pinned sing-box 1.14 alpha schema.
 *
 * The compiler is deliberately fail-closed: an incomplete, disabled or not-yet
 * integrated profile never falls back to Direct. Rules that target it are sent
 * to Block, including the default rule.
 */
internal class SingBoxRoutingConfigCompiler(
    private val byeDpiPort: Int? = null,
) {
    fun compile(config: RoutingConfig): SingBoxCompiledRuntime {
        val warnings = mutableListOf<String>()
        val runtimeProfileIds = linkedSetOf<String>()
        val profileTags = linkedMapOf<String, String>()
        val outbounds = mutableListOf(
            JSONObject().put("type", "block").put("tag", SING_BOX_BLOCK_TAG),
            JSONObject().put("type", "direct").put("tag", SING_BOX_DIRECT_TAG),
        )
        val endpoints = mutableListOf<JSONObject>()

        config.profiles.forEach { profile ->
            when (profile.type) {
                TunnelType.Block -> {
                    profileTags[profile.id] = SING_BOX_BLOCK_TAG
                    runtimeProfileIds += profile.id
                }
                TunnelType.Direct -> {
                    if (profile.enabled) {
                        profileTags[profile.id] = SING_BOX_DIRECT_TAG
                        runtimeProfileIds += profile.id
                    }
                }
                else -> {
                    val tag = runtimeProfileTag(profile.id)
                    profile.toRuntimeObject(tag, warnings)?.let { runtimeObject ->
                        if (profile.singBox?.kind == SingBoxProfileKind.Endpoint) {
                            endpoints += runtimeObject
                        } else {
                            outbounds += runtimeObject
                        }
                        profileTags[profile.id] = tag
                        runtimeProfileIds += profile.id
                    }
                }
            }
        }

        val routeRules = JSONArray()
        if (config.emergencyBlockEnabled) {
            warnings += "Emergency network block is enabled; all supported device flows are routed to Block."
            routeRules.put(
                JSONObject()
                    .put("inbound", JSONArray().put(SING_BOX_TUN_TAG))
                    .put("action", "route")
                    .put("outbound", SING_BOX_BLOCK_TAG),
            )
        } else {
            routeRules.put(JSONObject().put("action", "sniff"))
            routeRules.put(
                JSONObject()
                    .put("protocol", "dns")
                    .put("action", "hijack-dns"),
            )

            config.rules
                .filter { it.enabled && it.type != RouteRuleType.DEFAULT }
                .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name })
                .forEach { rule ->
                    val targetTag = profileTags[rule.targetProfileId] ?: SING_BOX_BLOCK_TAG
                    if (targetTag == SING_BOX_BLOCK_TAG && profileTags[rule.targetProfileId] == null) {
                        warnings += "Rule '${rule.name}' targets an unavailable profile and will fail closed."
                    }
                    rule.toSingBoxRouteRule(targetTag, warnings)?.let(routeRules::put)
                }
        }

        val defaultProfileId = config.defaultProfileId
        val defaultTag = if (config.emergencyBlockEnabled) {
            SING_BOX_BLOCK_TAG
        } else {
            defaultProfileId?.let(profileTags::get) ?: SING_BOX_BLOCK_TAG
        }
        if (defaultTag == SING_BOX_BLOCK_TAG &&
            defaultProfileId != null &&
            profileTags[defaultProfileId] == null
        ) {
            warnings += "Default profile '$defaultProfileId' is unavailable; unmatched traffic will be blocked."
        }

        val dnsResult = compileDns(config, profileTags, defaultTag, warnings)
        val root = JSONObject()
            .put(
                "log",
                JSONObject()
                    .put("level", "warn")
                    .put("timestamp", true),
            )
            .put("dns", dnsResult.options)
            .put(
                "inbounds",
                JSONArray().put(
                    JSONObject()
                        .put("type", "tun")
                        .put("tag", SING_BOX_TUN_TAG)
                        .put(
                            "address",
                            JSONArray()
                                .put("172.19.0.1/30")
                                .put("fdfe:dcba:9876::1/126"),
                        )
                        .put("mtu", 1500)
                        .put("auto_route", true)
                        .put("strict_route", true)
                        .put("stack", "mixed"),
                ),
            )
            .put("endpoints", JSONArray(endpoints))
            .put("outbounds", JSONArray(outbounds))
            .put(
                "route",
                JSONObject()
                    .put("rules", routeRules)
                    .put("final", defaultTag)
                    .put("find_process", config.hasEnabledAppRules())
                    .put("auto_detect_interface", true)
                    .put("default_domain_resolver", dnsResult.defaultServerTag),
            )

        return SingBoxCompiledRuntime(
            json = root.toString(2),
            warnings = warnings.distinct(),
            runtimeProfileIds = runtimeProfileIds,
            profileTags = profileTags,
        )
    }

    private fun TunnelProfile.toRuntimeObject(
        tag: String,
        warnings: MutableList<String>,
    ): JSONObject? {
        if (!enabled) return null
        return when (type) {
            TunnelType.Socks5 -> {
                val candidate = socks5
                val errors = candidate?.let(::validateSocks5Profile).orEmpty()
                if (candidate == null || !candidate.enabled || errors.isNotEmpty()) {
                    warnings += "SOCKS5 profile '$name' is incomplete and will not be started."
                    null
                } else {
                    JSONObject()
                        .put("type", "socks")
                        .put("tag", tag)
                        .put("server", candidate.host.trim())
                        .put("server_port", candidate.port)
                        .put("version", "5")
                        .apply {
                            candidate.username?.takeIf(String::isNotBlank)?.let { put("username", it) }
                            candidate.password?.takeIf(String::isNotBlank)?.let { put("password", it) }
                        }
                }
            }
            TunnelType.VLESS,
            TunnelType.XrayVlessReality -> {
                val candidate = vless
                val errors = candidate?.let(::validateVlessRuntime).orEmpty()
                if (candidate == null || !candidate.enabled || errors.isNotEmpty()) {
                    warnings += "VLESS profile '$name' is incomplete and will not be started: ${errors.joinToString()}"
                    null
                } else {
                    candidate.toSingBoxVlessOutbound(tag)
                }
            }
            TunnelType.ByeDpi -> {
                val localPort = byeDpiPort
                if (localPort == null) {
                    warnings += "ByeDPI profile '$name' is enabled, but the local process is unavailable; matching traffic will fail closed."
                    null
                } else {
                    JSONObject()
                        .put("type", "socks")
                        .put("tag", tag)
                        .put("server", "127.0.0.1")
                        .put("server_port", localPort)
                        .put("version", "5")
                }
            }
            else -> {
                val candidate = singBox
                if (candidate == null) {
                    warnings += "Profile '$name' uses ${type.label}, which is not connected to the stable runtime yet."
                    null
                } else {
                    runCatching { normalizedSingBoxProfileObject(type, candidate, tag) }
                        .onFailure {
                            warnings += "Profile '$name' is invalid and will fail closed: ${it.message.orEmpty()}"
                        }
                        .getOrNull()
                }
            }
        }
    }

    private fun RouteRule.toSingBoxRouteRule(
        targetTag: String,
        warnings: MutableList<String>,
    ): JSONObject? {
        val output = JSONObject()
            .put("action", "route")
            .put("outbound", targetTag)
        return when (type) {
            RouteRuleType.DOMAIN -> {
                val match = domainMatchFields(matchers)
                if (match.isEmpty) {
                    warnings += "Domain rule '$name' has no usable domains and was skipped."
                    null
                } else {
                    match.copyInto(output)
                }
            }
            RouteRuleType.CIDR -> {
                val cidrs = matchers.map(String::trim).filter(String::isNotBlank).distinct()
                if (cidrs.isEmpty()) {
                    warnings += "CIDR rule '$name' has no usable networks and was skipped."
                    null
                } else {
                    output.put("ip_cidr", JSONArray(cidrs))
                }
            }
            RouteRuleType.APP,
            RouteRuleType.APP_GROUP -> {
                val packages = androidPackages()
                if (packages.isEmpty()) {
                    warnings += "App rule '$name' has no Android package names and was skipped."
                    null
                } else {
                    output.put("package_name", JSONArray(packages))
                }
            }
            RouteRuleType.DEFAULT -> null
        }
    }

    private fun compileDns(
        config: RoutingConfig,
        profileTags: Map<String, String>,
        defaultRouteTag: String,
        warnings: MutableList<String>,
    ): CompiledDns {
        val servers = JSONArray()
        val rules = JSONArray()
        val systemTag = "dns_system"
        servers.put(
            JSONObject()
                .put("type", "local")
                .put("tag", systemTag)
                .apply {
                    if (defaultRouteTag != SING_BOX_BLOCK_TAG) {
                        put("detour", defaultRouteTag)
                    }
                },
        )

        val usablePolicyTags = linkedMapOf<String, String>()
        config.dnsPolicies.filter { it.enabled }.forEach { policy ->
            if (policy.type != DnsPolicyType.Custom || policy.serverText.isNullOrBlank()) {
                usablePolicyTags[policy.id] = systemTag
                return@forEach
            }
            val policyTag = dnsTag(policy.id)
            val detour = policy.resolveThroughProfileId?.let(profileTags::get)
            if (policy.resolveThroughProfileId != null && detour == null) {
                warnings += "DNS policy '${policy.name}' requires an unavailable profile and will reject matching DNS requests."
                addDnsRejectRulesForPolicy(config, policy, rules)
                return@forEach
            }
            if (detour == SING_BOX_BLOCK_TAG) {
                warnings += "DNS policy '${policy.name}' is assigned to Block and will reject matching DNS requests."
                addDnsRejectRulesForPolicy(config, policy, rules)
                return@forEach
            }

            val policyServer = parseDnsServer(policy, policyTag, detour, warnings)
            if (policyServer == null) {
                warnings += "DNS policy '${policy.name}' has no valid server and will reject matching DNS requests."
                addDnsRejectRulesForPolicy(config, policy, rules)
                return@forEach
            }
            servers.put(policyServer)
            usablePolicyTags[policy.id] = policyTag
            addDnsRouteRulesForPolicy(config, policy, policyTag, rules)
        }

        config.hostOverrides
            .filter { it.enabled && it.hostname.isNotBlank() && it.ipAddress.isNotBlank() }
            .forEachIndexed { index, override ->
                val tag = "dns_hosts_$index"
                servers.put(
                    JSONObject()
                        .put("type", "hosts")
                        .put("tag", tag)
                        .put(
                            "predefined",
                            JSONObject().put(
                                override.hostname.trim().lowercase(Locale.ROOT),
                                JSONArray().put(override.ipAddress.trim()),
                            ),
                        ),
                )
                rules.put(
                    JSONObject()
                        .put("domain", JSONArray().put(override.hostname.trim().lowercase(Locale.ROOT)))
                        .put("action", "route")
                        .put("server", tag),
                )
            }

        val defaultPolicyId = config.defaultProfileId
            ?.let { id -> config.profiles.firstOrNull { it.id == id } }
            ?.dnsPolicyId
        val defaultServerTag = defaultPolicyId?.let(usablePolicyTags::get) ?: systemTag
        return CompiledDns(
            options = JSONObject()
                .put("servers", servers)
                .put("rules", rules)
                .put("final", defaultServerTag)
                .put("strategy", "prefer_ipv4")
                .put("independent_cache", true),
            defaultServerTag = defaultServerTag,
        )
    }

    private fun parseDnsServer(
        policy: DnsPolicy,
        baseTag: String,
        detour: String?,
        warnings: MutableList<String>,
    ): JSONObject? {
        val rawServers = policy.serverText
            .orEmpty()
            .split(Regex("[,;\\s]+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        if (rawServers.size > 1) {
            warnings += "DNS policy '${policy.name}' contains several servers; only the first server is used."
        }
        val raw = rawServers.firstOrNull() ?: return null
        return runCatching { dnsServer(raw, baseTag, detour) }
            .onFailure { warnings += "DNS server '$raw' in '${policy.name}' is invalid: ${it.message.orEmpty()}" }
            .getOrNull()
    }

    private fun dnsServer(raw: String, tag: String, detour: String?): JSONObject {
        if (raw.equals("local", ignoreCase = true) || raw.equals("localhost", ignoreCase = true)) {
            return JSONObject().put("type", "local").put("tag", tag)
        }
        val normalized = if ("://" in raw) raw else "udp://$raw"
        val uri = URI(normalized)
        val type = when (uri.scheme.lowercase(Locale.ROOT)) {
            "udp" -> "udp"
            "tcp" -> "tcp"
            "tls" -> "tls"
            "quic" -> "quic"
            "https" -> "https"
            "h3" -> "h3"
            else -> error("unsupported scheme '${uri.scheme}'")
        }
        val host = uri.host
            ?: uri.authority?.substringBeforeLast(':')?.trim('[', ']')
            ?: error("missing host")
        require(host.isNotBlank()) { "missing host" }
        val defaultPort = when (type) {
            "udp", "tcp" -> 53
            "tls", "quic" -> 853
            else -> 443
        }
        return JSONObject()
            .put("type", type)
            .put("tag", tag)
            .put("server", host)
            .apply {
                val port = uri.port.takeIf { it > 0 } ?: defaultPort
                if (port != defaultPort) put("server_port", port)
                if (type == "https" || type == "h3") {
                    uri.rawPath?.takeIf { it.isNotBlank() && it != "/dns-query" }?.let { put("path", it) }
                }
                if (type == "tls" || type == "quic" || type == "https" || type == "h3") {
                    put(
                        "tls",
                        JSONObject()
                            .put("enabled", true)
                            .put("server_name", host),
                    )
                }
                detour?.let { put("detour", it) }
            }
    }

    private fun addDnsRouteRulesForPolicy(
        config: RoutingConfig,
        policy: DnsPolicy,
        serverTag: String,
        output: JSONArray,
    ) {
        config.rules
            .filter { it.enabled && effectiveDnsPolicyId(config, it) == policy.id }
            .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name })
            .forEach { routeRule ->
                routeRule.toDnsMatchRule("route", serverTag)?.let(output::put)
            }
    }

    private fun addDnsRejectRulesForPolicy(
        config: RoutingConfig,
        policy: DnsPolicy,
        output: JSONArray,
    ) {
        config.rules
            .filter { it.enabled && effectiveDnsPolicyId(config, it) == policy.id }
            .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name })
            .forEach { routeRule ->
                routeRule.toDnsMatchRule("reject", null)?.let(output::put)
            }
    }

    private fun effectiveDnsPolicyId(config: RoutingConfig, rule: RouteRule): String? =
        rule.dnsPolicyId
            ?: config.profiles.firstOrNull { it.id == rule.targetProfileId }?.dnsPolicyId

    private fun RouteRule.toDnsMatchRule(action: String, serverTag: String?): JSONObject? {
        val output = JSONObject().put("action", action)
        if (serverTag != null) output.put("server", serverTag)
        return when (type) {
            RouteRuleType.DOMAIN -> domainMatchFields(matchers).takeUnless(DomainMatchFields::isEmpty)?.copyInto(output)
            RouteRuleType.APP,
            RouteRuleType.APP_GROUP -> androidPackages().takeIf(List<String>::isNotEmpty)
                ?.let { output.put("package_name", JSONArray(it)) }
            RouteRuleType.DEFAULT -> output
            RouteRuleType.CIDR -> null
        }
    }

    private fun RouteRule.androidPackages(): List<String> = buildList {
        addAll(matchers)
        addAll(
            appMatchers
                .filter { it.platform == AppMatcherPlatform.Android || it.platform == AppMatcherPlatform.Any }
                .map { it.value },
        )
    }.map(String::trim).filter(String::isNotBlank).distinct()

    private fun RoutingConfig.hasEnabledAppRules(): Boolean = rules.any {
        it.enabled && (it.type == RouteRuleType.APP || it.type == RouteRuleType.APP_GROUP)
    }

    private fun domainMatchFields(values: List<String>): DomainMatchFields {
        val exact = mutableListOf<String>()
        val suffix = mutableListOf<String>()
        val keyword = mutableListOf<String>()
        val regex = mutableListOf<String>()
        values.map(String::trim).filter(String::isNotBlank).forEach { raw ->
            val value = raw.lowercase(Locale.ROOT).trimEnd('.')
            when {
                value.startsWith("full:") -> exact += value.removePrefix("full:")
                value.startsWith("keyword:") -> keyword += value.removePrefix("keyword:")
                value.startsWith("regexp:") -> regex += value.removePrefix("regexp:")
                value.startsWith("domain:") -> suffix += value.removePrefix("domain:").removePrefix("*.")
                value.startsWith("*.") -> suffix += value.removePrefix("*.")
                else -> suffix += value
            }
        }
        return DomainMatchFields(
            exact = exact.distinct(),
            suffix = suffix.distinct(),
            keyword = keyword.distinct(),
            regex = regex.distinct(),
        )
    }

    private fun VlessProfileConfig.toSingBoxVlessOutbound(tag: String): JSONObject =
        JSONObject()
            .put("type", "vless")
            .put("tag", tag)
            .put("server", host.trim())
            .put("server_port", port)
            .put("uuid", uuid.trim())
            .apply {
                flow?.takeIf(String::isNotBlank)?.let { put("flow", it) }
                when (securityMode) {
                    VlessSecurityMode.NONE -> Unit
                    VlessSecurityMode.TLS,
                    VlessSecurityMode.REALITY -> put(
                        "tls",
                        JSONObject()
                            .put("enabled", true)
                            .put("server_name", sni?.takeIf(String::isNotBlank) ?: host.trim())
                            .apply {
                                fingerprint?.takeIf(String::isNotBlank)?.let { fingerprint ->
                                    put(
                                        "utls",
                                        JSONObject()
                                            .put("enabled", true)
                                            .put("fingerprint", fingerprint),
                                    )
                                }
                                if (securityMode == VlessSecurityMode.REALITY) {
                                    put(
                                        "reality",
                                        JSONObject()
                                            .put("enabled", true)
                                            .put("public_key", publicKey)
                                            .put("short_id", shortId.orEmpty()),
                                    )
                                }
                                alpn?.split(',')?.map(String::trim)?.filter(String::isNotBlank)
                                    ?.takeIf(List<String>::isNotEmpty)
                                    ?.let { put("alpn", JSONArray(it)) }
                            },
                    )
                }
                transportJson()?.let { put("transport", it) }
            }

    private fun VlessProfileConfig.transportJson(): JSONObject? = when (
        transportType?.lowercase(Locale.ROOT)?.ifBlank { "tcp" } ?: "tcp"
    ) {
        "tcp", "raw" -> null
        "ws", "websocket" -> JSONObject()
            .put("type", "ws")
            .put("path", path?.takeIf(String::isNotBlank) ?: "/")
            .apply {
                hostHeader?.takeIf(String::isNotBlank)?.let {
                    put("headers", JSONObject().put("Host", it))
                }
            }
        "grpc" -> JSONObject()
            .put("type", "grpc")
            .put("service_name", serviceName.orEmpty())
        else -> null
    }

    private fun validateVlessRuntime(profile: VlessProfileConfig): List<String> = buildList {
        addAll(validateVlessProfile(profile))
        if (profile.securityMode == VlessSecurityMode.REALITY) {
            if (profile.sni.isNullOrBlank()) add("REALITY SNI is required")
            if (profile.publicKey.isNullOrBlank()) add("REALITY public key is required")
            val shortId = profile.shortId.orEmpty()
            if (shortId.length > 16 ||
                shortId.length % 2 != 0 ||
                shortId.any { it !in "0123456789abcdefABCDEF" }
            ) {
                add("REALITY short ID must contain an even number of hex characters, at most 16")
            }
        }
    }

    private fun dnsTag(id: String): String =
        "dns_${safeTagPart(id)}_${id.hashCode().toUInt().toString(16)}"

    private fun safeTagPart(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9_-]"), "_")
        .take(40)
        .ifBlank { "route" }

    private data class CompiledDns(
        val options: JSONObject,
        val defaultServerTag: String,
    )

    private data class DomainMatchFields(
        val exact: List<String>,
        val suffix: List<String>,
        val keyword: List<String>,
        val regex: List<String>,
    ) {
        val isEmpty: Boolean
            get() = exact.isEmpty() && suffix.isEmpty() && keyword.isEmpty() && regex.isEmpty()

        fun copyInto(target: JSONObject): JSONObject = target.apply {
            if (exact.isNotEmpty()) put("domain", JSONArray(exact))
            if (suffix.isNotEmpty()) put("domain_suffix", JSONArray(suffix))
            if (keyword.isNotEmpty()) put("domain_keyword", JSONArray(keyword))
            if (regex.isNotEmpty()) put("domain_regex", JSONArray(regex))
        }
    }
}

internal fun runtimeProfileTag(id: String): String = when (id) {
    "block" -> SING_BOX_BLOCK_TAG
    "direct" -> SING_BOX_DIRECT_TAG
    else -> "profile_${safeRuntimeTagPart(id)}_${id.hashCode().toUInt().toString(16)}"
}

private fun safeRuntimeTagPart(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9_-]"), "_")
    .take(40)
    .ifBlank { "route" }
