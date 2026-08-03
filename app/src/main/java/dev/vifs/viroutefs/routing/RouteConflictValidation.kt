package dev.vifs.viroutefs.routing

import java.util.Locale

data class RouteConflict(
    val matcherType: RouteRuleType,
    val matcher: String,
    val ruleIds: List<String>,
    val message: String,
)

fun RoutingConfig.moveExplicitRule(ruleId: String, offset: Int): RoutingConfig {
    if (offset == 0) return this
    val ordered = rules
        .filter { it.type != RouteRuleType.DEFAULT && !it.isManagedProfileAppRoutingRule() }
        .sortedWith(compareBy<RouteRule> { it.priority }.thenBy { it.name }.thenBy { it.id })
        .toMutableList()
    val currentIndex = ordered.indexOfFirst { it.id == ruleId }
    if (currentIndex < 0) return this
    val targetIndex = (currentIndex + offset).coerceIn(0, ordered.lastIndex)
    if (targetIndex == currentIndex) return this
    val moved = ordered.removeAt(currentIndex)
    ordered.add(targetIndex, moved)
    val normalizedPriorities = ordered
        .mapIndexed { index, rule -> rule.id to (index + 1) * 10 }
        .toMap()
    return copy(
        rules = rules.map { rule ->
            normalizedPriorities[rule.id]?.let { priority -> rule.copy(priority = priority) } ?: rule
        },
    )
}

fun findExactRouteConflicts(rules: List<RouteRule>): List<RouteConflict> {
    val explicitRules = rules.filter {
        it.enabled && it.type != RouteRuleType.DEFAULT && !it.isManagedProfileAppRoutingRule()
    }
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
            val matcher = candidate.matchers.firstOrNull()?.takeIf { it.isNotBlank() }
            if (matcher == null) {
                add("Domain / host matcher is required.")
            } else {
                val parsed = parseDomainMatcher(matcher)
                validateDomainMatcher(parsed.mode, parsed.value)?.let(::add)
            }
        }
        RouteRuleType.CIDR -> {
            val value = candidate.matchers.firstOrNull()?.trim().orEmpty()
            if (value.isBlank()) {
                add("IP / CIDR matcher is required.")
            } else if (!isValidIpOrCidr(value)) {
                add("Enter a valid IPv4/IPv6 address or CIDR, for example 192.0.2.10, 192.0.2.0/24 or 2001:db8::/32.")
            }
        }
        RouteRuleType.DEFAULT -> Unit
    }
    candidate.destinationPorts.forEach { range ->
        if (range.first !in 1..65535 || range.last !in range.first..65535) {
            add("Destination port must be between 1 and 65535.")
        }
    }
    findConflictsForCandidate(candidate, rules).forEach { add(it.message) }
}

fun isValidIpOrCidr(text: String): Boolean {
    val normalized = text.trim()
    return isValidIpAddress(normalized) || isValidCidr(normalized)
}

fun isValidIpv4(text: String): Boolean {
    val octets = text.split('.')
    if (octets.size != 4) return false
    return octets.all { octet ->
        octet.isNotBlank() && octet.toIntOrNull()?.let { value -> value in 0..255 } == true
    }
}

fun normalizedMatcherKey(type: RouteRuleType, raw: String): String = when (type) {
    RouteRuleType.DOMAIN -> parseDomainMatcher(raw).let { encodeDomainMatcher(it.mode, it.value) }
    RouteRuleType.CIDR -> raw.trim().lowercase(Locale.ROOT)
    RouteRuleType.APP, RouteRuleType.APP_GROUP -> raw.trim().lowercase(Locale.ROOT)
    RouteRuleType.DEFAULT -> ""
}

private fun findDuplicateAppConflicts(rules: List<RouteRule>): List<RouteConflict> = rules
    .flatMap { rule ->
        rule.appMatchers.map { matcher -> rule.conditionedMatcherKey(RouteRuleType.APP, matcher.value) to rule }
    }
    .groupBy({ it.first }, { it.second })
    .toRouteConflicts(RouteRuleType.APP)

private fun findDuplicateTextMatcherConflicts(rules: List<RouteRule>, type: RouteRuleType): List<RouteConflict> = rules
    .filter { it.type == type }
    .flatMap { rule -> rule.matchers.map { matcher -> rule.conditionedMatcherKey(type, matcher) to rule } }
    .groupBy({ it.first }, { it.second })
    .toRouteConflicts(type)

private fun Map<String, List<RouteRule>>.toRouteConflicts(type: RouteRuleType): List<RouteConflict> = entries
    .mapNotNull { (matcher, matchedRules) ->
        val ruleIds = matchedRules.map { it.id }.distinct()
        val visibleMatcher = matcher.substringBefore(CONDITION_SEPARATOR)
        if (visibleMatcher.isBlank() || ruleIds.size < 2) return@mapNotNull null
        val names = matchedRules.distinctBy { it.id }.joinToString { it.name }
        RouteConflict(
            matcherType = type,
            matcher = visibleMatcher,
            ruleIds = ruleIds,
            message = "Duplicate ${type.name.lowercase(Locale.ROOT)} matcher '$visibleMatcher' with the same transport and ports is already used by: $names.",
        )
    }

private fun RouteRule.conditionedMatcherKey(type: RouteRuleType, raw: String): String =
    buildString {
        append(normalizedMatcherKey(type, raw))
        append(CONDITION_SEPARATOR)
        append(transport.name)
        append(CONDITION_SEPARATOR)
        append(destinationPorts.toDisplayText())
    }

private const val CONDITION_SEPARATOR = "\u0000"
