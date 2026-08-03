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
        )

        assertTrue(script.contains("VIROUTEFS_Z2_OUT"))
        assertTrue(script.contains("VIROUTEFS_TETHER_NAT"))
        assertTrue(script.contains("nft delete table inet viroutefs"))
        assertTrue(script.contains("'/data/user/0/dev.vifs.viroutefs/no backup/process.pid'"))
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
    fun shellQuoteRejectsNewlinesAndEscapesApostrophes() {
        assertTrue(shellQuote("a'b").contains("'\\''"))
        runCatching { shellQuote("bad\nvalue") }
            .onSuccess { error("newline should be rejected") }
    }
}
