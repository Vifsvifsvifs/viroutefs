// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DomainRouteModesTest {
    @Test
    fun allDomainModesUseTheSameCanonicalParserAndRouteEngine() {
        val cases = listOf(
            Triple(DomainMatcherMode.Exact, "example.test", listOf("example.test")),
            Triple(DomainMatcherMode.Suffix, "example.test", listOf("example.test", "api.example.test")),
            Triple(DomainMatcherMode.Keyword, "ample", listOf("example.test", "sample.invalid")),
            Triple(DomainMatcherMode.Regex, "(^|\\.)api\\.example\\.test$", listOf("api.example.test")),
        )

        cases.forEachIndexed { index, (mode, value, matches) ->
            val rule = domainRule(
                id = "mode-$index",
                matcher = encodeDomainMatcher(mode, value),
            )
            val defaults = RoutingConfigDefaults.defaultConfig()
            val engine = RouteEngine(defaults.copy(rules = listOf(rule) + defaults.rules))

            matches.forEach { host ->
                assertEquals(rule.id, engine.simulate(host).matchedRule.id, "$mode should match $host")
            }
        }

        val exact = RouteEngine(configWith(domainRule("exact", encodeDomainMatcher(DomainMatcherMode.Exact, "example.test"))))
        assertEquals("default_system", exact.simulate("api.example.test").matchedRule.id)
        val suffix = RouteEngine(configWith(domainRule("suffix", encodeDomainMatcher(DomainMatcherMode.Suffix, "example.test"))))
        assertEquals("default_system", suffix.simulate("fakeexample.test").matchedRule.id)
    }

    @Test
    fun regexTextKeepsCaseSensitiveOperatorsWhileDomainValuesAreCanonicalized() {
        assertEquals(
            "full:example.test",
            encodeDomainMatcher(DomainMatcherMode.Exact, "EXAMPLE.TEST."),
        )
        val regex = "^[^A-Z]+\\.example$"
        val encoded = encodeDomainMatcher(DomainMatcherMode.Regex, regex)

        assertEquals("regexp:$regex", encoded)
        assertEquals(ParsedDomainMatcher(DomainMatcherMode.Regex, regex), parseDomainMatcher(encoded))
    }

    @Test
    fun domainValidationRejectsPathsAndBrokenRegex() {
        assertNotNull(validateDomainMatcher(DomainMatcherMode.Exact, "https://example.test/path"))
        assertNotNull(validateDomainMatcher(DomainMatcherMode.Regex, "(["))
        assertNull(validateDomainMatcher(DomainMatcherMode.Suffix, "example.test"))
        assertNull(validateDomainMatcher(DomainMatcherMode.Keyword, "example"))
    }

    @Test
    fun movingRuleNormalizesExplicitPrioritiesAndLeavesDefaultUntouched() {
        val defaults = RoutingConfigDefaults.defaultConfig()
        val first = domainRule("first", "domain:first.test", priority = 50)
        val second = domainRule("second", "domain:second.test", priority = 50)
        val third = domainRule("third", "domain:third.test", priority = 5_000)
        val config = defaults.copy(rules = listOf(third, second, defaults.rules.single(), first))

        val moved = config.moveExplicitRule("third", -1)
        val ordered = moved.rules
            .filter { it.type != RouteRuleType.DEFAULT }
            .sortedBy { it.priority }

        assertEquals(listOf("first", "third", "second"), ordered.map { it.id })
        assertEquals(listOf(10, 20, 30), ordered.map { it.priority })
        assertEquals(1_000, moved.rules.single { it.type == RouteRuleType.DEFAULT }.priority)
    }

    @Test
    fun identicalPriorityAndNameUseStableIdTieBreakIndependentOfJsonOrder() {
        val a = domainRule("a-rule", "domain:same.test", name = "Same", priority = 10)
        val z = domainRule("z-rule", "domain:same.test", name = "Same", priority = 10)
        val defaults = RoutingConfigDefaults.defaultConfig()
        val firstOrder = RouteEngine(defaults.copy(rules = listOf(z, a) + defaults.rules))
        val reverseOrder = RouteEngine(defaults.copy(rules = listOf(a, z) + defaults.rules))

        assertEquals("a-rule", firstOrder.simulate("same.test").matchedRule.id)
        assertEquals("a-rule", reverseOrder.simulate("same.test").matchedRule.id)
    }

    private fun configWith(rule: RouteRule): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        return defaults.copy(rules = listOf(rule) + defaults.rules)
    }

    private fun domainRule(
        id: String,
        matcher: String,
        name: String = id,
        priority: Int = 10,
    ): RouteRule = RouteRule(
        id = id,
        name = name,
        type = RouteRuleType.DOMAIN,
        targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
        priority = priority,
        matchers = listOf(matcher),
        reason = "test",
        technicalDetails = "test",
        recommendedAction = "test",
    )
}
