// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootVpnTetheringScriptTest {
    @Test
    fun probeParserSeparatesDefaultHotspotAndTunnelWithoutUserNames() {
        val interfaces = parseRootTetheringProbe(
            """
                default=rmnet_data0
                addr=rmnet_data0|10.20.30.2/30
                addr=ap0|192.168.43.1/24
                addr=tun0|172.19.0.1/30
                addr=bad name|192.168.1.1/24
                addr=wlan1|203.0.113.1/24
            """.trimIndent(),
        )

        assertEquals(3, interfaces.size)
        assertTrue(interfaces.single { it.name == "rmnet_data0" }.isDefaultInternet)
        assertTrue(interfaces.single { it.name == "ap0" }.isLikelyDownstream)
        assertEquals("192.168.43.0/24", interfaces.single { it.name == "ap0" }.networkCidr)
        assertTrue(interfaces.single { it.name == "tun0" }.isTunnel)
    }

    @Test
    fun startScriptUsesNamespacedNatPolicyRouteLeakBlockAndRollback() {
        val script = rootVpnTetheringStartScript(
            downstreamInterface = "ap0",
            downstreamAddressCidr = "192.168.43.1/24",
            downstreamNetworkCidr = "192.168.43.0/24",
            tunnelInterface = "tun0",
            stateFile = "/data/user/0/app/tethering-state",
            appUid = 10_321,
        )

        assertTrue(script.contains("VIROUTEFS_TETHER_FWD"))
        assertTrue(script.contains("VIROUTEFS_TETHER_NAT"))
        assertTrue(script.contains("VIROUTEFS_TETHER_MSS"))
        assertTrue(script.contains("iif 'ap0' lookup 62241"))
        assertTrue(script.contains("-s '192.168.43.0/24' -o 'tun0' -j MASQUERADE"))
        assertTrue(script.contains("ip6tables -t filter -A VIROUTEFS_TETHER_FWD -i 'ap0' -j REJECT"))
        assertTrue(script.contains("trap rollback_tethering EXIT"))
        assertTrue(script.contains("previous_forward"))
        assertFalse(script.contains("iptables -F\n"))
        assertFalse(script.contains("ip route flush"))
        assertFalse(script.contains("nft flush"))
    }

    @Test
    fun cleanupRestoresOnlyRecordedRuleRouteAndForwardingValue() {
        val script = rootTetheringCleanupScript("/data/user/0/app/tethering-state")

        assertTrue(script.contains("rule del priority 16220"))
        assertTrue(script.contains("route del default"))
        assertTrue(script.contains("/proc/sys/net/ipv4/ip_forward"))
        assertTrue(script.contains("VIROUTEFS_TETHER_MSS"))
        assertFalse(script.contains("route flush"))
        assertFalse(script.contains("iptables -F\n"))
    }
}
