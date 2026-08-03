// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootAppFirewallScriptTest {
    @Test
    fun normalizesAllNetworkPolicyAboveTransportPolicies() {
        val config = RootAppFirewallConfig(
            blockAllPackages = setOf("com.example.all"),
            blockWifiPackages = setOf("com.example.all", "com.example.wifi"),
            blockCellularPackages = setOf("com.example.all"),
        ).normalized()

        assertTrue("com.example.all" in config.blockAllPackages)
        assertFalse("com.example.all" in config.blockWifiPackages)
        assertFalse("com.example.all" in config.blockCellularPackages)
        assertTrue("com.example.wifi" in config.blockWifiPackages)
    }

    @Test
    fun scriptUsesNamespacedDualStackOwnerChainsWithoutGlobalFlush() {
        val cleanup = appFirewallCleanupScript("/data/user/0/app/root.pid")
        val script = rootAppFirewallStartScript(
            rules = listOf(
                RootFirewallUidRule(10_123, blockAll = true, blockWifi = false, blockCellular = false, blockVpn = false),
                RootFirewallUidRule(10_456, blockAll = false, blockWifi = true, blockCellular = true, blockVpn = true),
            ),
            cleanup = cleanup,
        )

        assertTrue(script.contains("for tool in iptables ip6tables"))
        assertTrue(script.contains("VIROUTEFS_FW_OUT"))
        assertTrue(script.contains("VIROUTEFS_FW_WIFI"))
        assertTrue(script.contains("VIROUTEFS_FW_CELL"))
        assertTrue(script.contains("VIROUTEFS_FW_VPN"))
        assertTrue(script.contains("--uid-owner 10123"))
        assertTrue(script.contains("--uid-owner 10456"))
        assertTrue(script.contains("-o 'wlan+'"))
        assertTrue(script.contains("trap rollback_firewall EXIT"))
        assertFalse(script.contains("iptables -F\n"))
        assertFalse(script.contains("nft flush ruleset"))
    }

    @Test
    fun maximumPolicyScriptStaysWithinRootExecutorLimit() {
        val rules = (0 until ROOT_FIREWALL_MAX_UIDS).map { offset ->
            RootFirewallUidRule(
                uid = 10_000 + offset,
                blockAll = false,
                blockWifi = true,
                blockCellular = true,
                blockVpn = true,
            )
        }
        val script = rootAppFirewallStartScript(
            rules = rules,
            cleanup = appFirewallCleanupScript("/data/user/0/app/root.pid"),
        )

        assertTrue(script.length < 64 * 1024, "script length=${script.length}")
    }
}
