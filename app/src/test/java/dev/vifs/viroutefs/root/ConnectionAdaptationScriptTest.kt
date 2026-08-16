// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConnectionAdaptationScriptTest {
    @Test
    fun startScriptUsesDaemonPidfileNamespaceAndFailOpenQueue() {
        val runtime = Zapret2RuntimeFiles(
            binary = File("/data/app/lib/libzapret2.so"),
            library = File("/data/user/0/app/zapret-lib.lua"),
            antiDpi = File("/data/user/0/app/zapret-antidpi.lua"),
            automatic = File("/data/user/0/app/zapret-auto.lua"),
        )

        val script = connectionAdaptationStartScript(
            runtime = runtime,
            pidFile = "/data/user/0/app/runtime.pid",
            logFile = "/data/user/0/app/runtime.log",
        )

        assertTrue(script.contains("--daemon"))
        assertTrue(script.contains("--pidfile=/data/user/0/app/runtime.pid"))
        assertTrue(script.contains("--queue-bypass"))
        assertTrue(script.contains("VIROUTEFS_Z2_OUT"))
        assertTrue(script.contains("iptables ip6tables"))
        assertTrue(script.contains("trap rollback_start EXIT"))
        assertTrue(script.contains("trap 'rollback_start; exit 1' HUP INT TERM"))
        assertFalse(script.contains("iptables -F\n"))
        assertFalse(script.contains("nft flush ruleset"))
    }
}
