// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootAutomationConditionsTest {
    @Test
    fun equalHoursMeanWholeDay() {
        val config = RootAutomationConfig(startHour = 7, endHour = 7)

        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Any, true, 0))
        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Wifi, false, 23))
    }

    @Test
    fun wifiScreenOffAndOvernightWindowMustAllMatch() {
        val config = RootAutomationConfig(
            network = RootAutomationNetwork.Wifi,
            screen = RootAutomationScreen.Off,
            startHour = 22,
            endHour = 6,
        )

        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Wifi, false, 23))
        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Wifi, false, 5))
        assertFalse(automationConditionsMatch(config, RootAutomationNetwork.Wifi, true, 23))
        assertFalse(automationConditionsMatch(config, RootAutomationNetwork.Cellular, false, 23))
        assertFalse(automationConditionsMatch(config, RootAutomationNetwork.Wifi, false, 12))
    }

    @Test
    fun daytimeEndHourIsExclusive() {
        val config = RootAutomationConfig(startHour = 8, endHour = 18)

        assertFalse(automationConditionsMatch(config, RootAutomationNetwork.Any, true, 7))
        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Any, true, 8))
        assertTrue(automationConditionsMatch(config, RootAutomationNetwork.Any, true, 17))
        assertFalse(automationConditionsMatch(config, RootAutomationNetwork.Any, true, 18))
    }
}
