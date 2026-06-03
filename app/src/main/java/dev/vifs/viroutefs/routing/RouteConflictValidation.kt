package dev.vifs.viroutefs.routing

import java.util.Locale

data class RouteConflict(
    val matcherType: RouteRuleType,
    val matcher: String,
    val ruleIds: List<String>,
    val message: String,
)

fun findExactRouteConflicts(rules: List<RouteRule>): List<RouteConflict> {
    val explicitRules = rules.filter { it.enabled && it.type != RouteRuleType.DEFAULT }
    return buildList {
        addAll(findDuplicateAppConflicts(explicitRules))
        addAll(findDuplicateTextMatcherConflicts(explicitRules, RouteRuleType.DOMAIN))
        addAll(findDuplicateTextMatcherConflicts(explicitRules, RouteRuleType.CIDR))
    }
}

fun findConflictsForCandidate(candidate: RouteRule, rules: List<RouteRule>): List<RouteConflict> {
    if (!candidate.enabled || candidate.type == RouteRuleType.DEFAULT) return emptyList()
    return findExactRouteConflicts(rules.filterNot { it.id == candidate.id } + candidate)
        .filter { conflict -> candidate.id in conflict.ruleIds }
}

fun validateRouteEditorDraft(candidate: RouteRule, rules: List<RouteRule>): List<String> = buildList {
    if (candidate.name.isBlank()) add("Route name is required.")
    when (candidate.type) {
        RouteRuleType.APP, RouteRuleType.APP_GROUP -> {
            if (candidate.appMatchers.isEmpty()) add("Select at least one installed app.")
        }
        RouteRuleType.DOMAIN -> {
            if (candidate.matchers.none { it.trim().isNotBlank() }) add("Domain / host matcher is required.")
        }
        RouteRuleType.CIDR -> {
            val value = candidate.matchers.firstOrNull()?.trim().orEmpty()
            if (value.isBlank()) {
                add("IP / CIDR matcher is required.")
            } else if (!isValidIpOrCidr(value)) {
                add("Enter a valid IPv4 address or CIDR, for example 192.0.2.10 or 192.0.2.0/24.")
            }
        }
        RouteRuleType.DEFAULT -> Unit
    }
    findConflictsForCandidate(candidate, rules).forEach { add(it.message) }
}

fun isValidIpOrCidr(text: String): Boolean {
    val normalized = text.trim()
    return isValidIpv4(normalized) || isValidCidr(normalized)
}

fun isValidIpv4(text: String): Boolean {
    val octets = text.split('.')
    if (octets.size != 4) return false
    return octets.all { octet ->
        octet.isNotBlank() && octet.toIntOrNull()?.let { value -> value in 0..255 } == true
    }
}

fun normalizedMatcherKey(type: RouteRuleType, raw: String): String = when (type) {
    RouteRuleType.DOMAIN -> raw.trim().trimEnd('.').lowercase(Locale.ROOT)
    RouteRuleType.CIDR -> raw.trim().lowercase(Locale.ROOT)
    RouteRuleType.APP, RouteRuleType.APP_GROUP -> raw.trim().lowercase(Locale.ROOT)
    RouteRuleType.DEFAULT -> ""
}

private fun findDuplicateAppConflicts(rules: List<RouteRule>): List<RouteConflict> = rules
    .flatMap { rule ->
        rule.appMatchers.map { matcher -> normalizedMatcherKey(RouteRuleType.APP, matcher.value) to rule }
    }
    .groupBy({ it.first }, { it.second })
    .toRouteConflicts(RouteRuleType.APP)

private fun findDuplicateTextMatcherConflicts(rules: List<RouteRule>, type: RouteRuleType): List<RouteConflict> = rules
    .filter { it.type == type }
    .flatMap { rule -> rule.matchers.map { matcher -> normalizedMatcherKey(type, matcher) to rule } }
    .groupBy({ it.first }, { it.second })
    .toRouteConflicts(type)

private fun Map<String, List<RouteRule>>.toRouteConflicts(type: RouteRuleType): List<RouteConflict> = entries
    .mapNotNull { (matcher, matchedRules) ->
        val ruleIds = matchedRules.map { it.id }.distinct()
        if (matcher.isBlank() || ruleIds.size < 2) return@mapNotNull null
        val names = matchedRules.distinctBy { it.id }.joinToString { it.name }
        RouteConflict(
            matcherType = type,
            matcher = matcher,
            ruleIds = ruleIds,
            message = "Duplicate ${type.name.lowercase(Locale.ROOT)} matcher '$matcher' is already used by: $names.",
        )
    }
