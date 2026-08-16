// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootNetworkRecoveryTest {
    @Test
    fun cleanupTargetsOnlyNamespacedArtifactsAndQuotesPrivatePaths() {
        val script = rootCleanupScript(
            pidFile = "/data/user/0/dev.vifs.viroutefs/no backup/process.pid",
            logFile = "/data/user/0/dev.vifs.viroutefs/no backup/process.log",
            packetCapturePidFile = "/data/user/0/dev.vifs.viroutefs/no backup/capture.pid",
            packetCaptureLogFile = "/data/user/0/dev.vifs.viroutefs/no backup/capture.log",
            packetCaptureFile = "/data/user/0/dev.vifs.viroutefs/no backup/capture.pcap",
            appUid = 10_321,
            tetheringStateFile = "/data/user/0/dev.vifs.viroutefs/no backup/tethering-state",
        )

        assertTrue(script.contains("VIROUTEFS_Z2_OUT"))
        assertTrue(script.contains("VIROUTEFS_TETHER_NAT"))
        assertTrue(script.contains("nft delete table inet viroutefs"))
        assertTrue(script.contains("'/data/user/0/dev.vifs.viroutefs/no backup/process.pid'"))
        assertTrue(script.contains("'/data/user/0/dev.vifs.viroutefs/no backup/capture.pid'"))
        assertTrue(script.contains("*libtcpdump.so*"))
        assertTrue(script.contains("'/data/user/0/dev.vifs.viroutefs/no backup/tethering-state'"))
        assertTrue(script.contains("VIROUTEFS_TETHER_MSS"))
        assertFalse(script.contains("iptables -F\n"))
        assertFalse(script.contains("nft flush ruleset"))
    }

    @Test
    fun connectionAdaptationCleanupDoesNotTouchOtherRootModules() {
        val script = connectionAdaptationCleanupScript(
            pidFile = "/data/user/0/app/process.pid",
            logFile = "/data/user/0/app/process.log",
        )

        assertTrue(script.contains("VIROUTEFS_Z2_OUT"))
        assertFalse(script.contains("VIROUTEFS_FW_OUT"))
        assertFalse(script.contains("VIROUTEFS_LOCK_OUT"))
        assertFalse(script.contains("VIROUTEFS_TETHER_FWD"))
        assertFalse(script.contains("nft delete table"))
    }

    @Test
    fun appFirewallCleanupRemovesItsChildChainsButNotOtherModules() {
        val script = appFirewallCleanupScript("/data/user/0/app/process.pid")

        assertTrue(script.contains("VIROUTEFS_FW_OUT"))
        assertTrue(script.contains("VIROUTEFS_FW_WIFI"))
        assertTrue(script.contains("VIROUTEFS_FW_CELL"))
        assertTrue(script.contains("VIROUTEFS_FW_VPN"))
        assertFalse(script.contains("VIROUTEFS_Z2_OUT"))
        assertFalse(script.contains("VIROUTEFS_LOCK_OUT"))
    }

    @Test
    fun networkGuardCleanupTouchesOnlyItsLockChain() {
        val script = networkGuardCleanupScript("/data/user/0/app/process.pid")

        assertTrue(script.contains("VIROUTEFS_LOCK_OUT"))
        assertFalse(script.contains("VIROUTEFS_Z2_OUT"))
        assertFalse(script.contains("VIROUTEFS_FW_OUT"))
        assertFalse(script.contains("VIROUTEFS_TETHER_FWD"))
    }

    @Test
    fun shellQuoteRejectsNewlinesAndEscapesApostrophes() {
        assertTrue(shellQuote("a'b").contains("'\\''"))
        runCatching { shellQuote("bad\nvalue") }
            .onSuccess { error("newline should be rejected") }
    }
}
