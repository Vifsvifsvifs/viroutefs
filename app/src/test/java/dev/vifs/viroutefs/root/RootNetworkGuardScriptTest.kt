// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootNetworkGuardScriptTest {
    @Test
    fun vpnLockAllowsTunnelAndOwnUidBeforeRejectingUserApps() {
        val script = rootNetworkGuardStartScript(
            config = RootNetworkGuardConfig(vpnLock = true),
            appUid = 10_777,
            cleanup = networkGuardCleanupScript("/data/user/0/app/root.pid"),
        )

        val tunnelRule = script.indexOf("-o 'tun+' -j RETURN")
        val ownUidRule = script.indexOf("--uid-owner 10777 -j RETURN")
        val rejectRule = script.indexOf("--uid-owner 10000-99999999 -j REJECT")
        assertTrue(tunnelRule >= 0 && tunnelRule < rejectRule)
        assertTrue(ownUidRule >= 0 && ownUidRule < rejectRule)
        assertTrue(script.contains("tool=iptables"))
        assertTrue(script.contains("tool=ip6tables"))
        assertTrue(script.contains("VIROUTEFS_LOCK_OUT"))
        assertFalse(script.contains("iptables -F\n"))
    }

    @Test
    fun dnsOnlyGuardRejectsDirectDnsAndDotButNotAllTraffic() {
        val script = rootNetworkGuardStartScript(
            config = RootNetworkGuardConfig(blockDirectDns = true),
            appUid = 10_777,
            cleanup = networkGuardCleanupScript("/data/user/0/app/root.pid"),
        )

        assertTrue(script.contains("-p udp --dport 53"))
        assertTrue(script.contains("-p tcp --dport 53"))
        assertTrue(script.contains("-p udp --dport 853"))
        assertTrue(script.contains("-p tcp --dport 853"))
        assertFalse(script.lineSequence().any { line ->
            line.contains("--uid-owner 10000-99999999 -j REJECT") && !line.contains("--dport")
        })
    }

    @Test
    fun ipv6OnlyGuardAddsBroadRejectOnlyAfterSwitchingToIp6tables() {
        val script = rootNetworkGuardStartScript(
            config = RootNetworkGuardConfig(blockDirectIpv6 = true),
            appUid = 10_777,
            cleanup = networkGuardCleanupScript("/data/user/0/app/root.pid"),
        )

        val ip6Start = script.indexOf("tool=ip6tables")
        val reject = script.indexOf("--uid-owner 10000-99999999 -j REJECT")
        assertTrue(ip6Start >= 0 && reject > ip6Start)
    }
}
