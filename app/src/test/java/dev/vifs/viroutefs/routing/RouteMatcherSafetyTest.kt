// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals

class RouteMatcherSafetyTest {
    @Test
    fun domainMatcherAcceptsOnlyDomainOrSubdomainNotSubstring() {
        val config = configWithRule(RouteRuleType.DOMAIN, listOf("bank.example"))
        val engine = RouteEngine(config)

        assertEquals("test-rule", engine.simulate("bank.example").matchedRule.id)
        assertEquals("test-rule", engine.simulate("login.bank.example").matchedRule.id)
        assertEquals("default_system", engine.simulate("fakebank.example").matchedRule.id)
        assertEquals("default_system", engine.simulate("bank.example.attacker.test").matchedRule.id)
    }

    @Test
    fun appMatcherRequiresExactPackageName() {
        val config = configWithRule(RouteRuleType.APP, listOf("com.example.bank"))
        val engine = RouteEngine(config)

        assertEquals("test-rule", engine.simulate("com.example.bank").matchedRule.id)
        assertEquals("default_system", engine.simulate("com.example.bank.fake").matchedRule.id)
        assertEquals("default_system", engine.simulate("prefix.com.example.bank").matchedRule.id)
    }

    private fun configWithRule(type: RouteRuleType, matchers: List<String>): RoutingConfig {
        val defaults = RoutingConfigDefaults.defaultConfig()
        return defaults.copy(
            rules = listOf(
                RouteRule(
                    id = "test-rule",
                    name = "Test",
                    type = type,
                    targetProfileId = RoutingConfigDefaults.BLOCK_PROFILE_ID,
                    priority = 10,
                    matchers = matchers,
                    reason = "test",
                    technicalDetails = "test",
                    recommendedAction = "test",
                ),
            ) + defaults.rules,
        )
    }
}
