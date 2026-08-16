// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootPacketCaptureScriptTest {
    @Test
    fun startScriptUsesBundledBinaryFixedLimitsAndSelfStoppingTimer() {
        val script = packetCaptureStartScript(
            binary = File("/data/app/lib/arm64/libtcpdump.so"),
            pidFile = "/data/user/0/app/packet-capture.pid",
            logFile = "/data/user/0/app/packet-capture.log",
            captureFile = "/data/user/0/app/network-capture.pcap",
            appUid = 10_321,
            durationSeconds = 30,
            mode = RootPacketCaptureMode.WebAndDns,
        )

        assertTrue(script.contains("libtcpdump.so"))
        assertTrue(script.contains("-i any -p -n -s 128 -U -B 1024 -c 25000"))
        assertTrue(script.contains("tcp port 80 or tcp port 443 or udp port 443 or port 53 or port 853"))
        assertTrue(script.contains("sleep 30"))
        assertTrue(script.contains("*libtcpdump.so*"))
        assertTrue(script.contains("kill -2"))
        assertTrue(script.contains("chown 10321:10321"))
        assertFalse(script.contains("tcpdump -D"))
        assertFalse(script.contains("rm -rf"))
        assertFalse(script.contains("iptables"))
    }

    @Test
    fun cleanupTargetsOnlyPidWhoseCommandIsBundledTcpdump() {
        val script = packetCaptureCleanupScript(
            pidFile = "/data/user/0/app/packet-capture.pid",
            logFile = "/data/user/0/app/packet-capture.log",
            captureFile = "/data/user/0/app/network-capture.pcap",
            appUid = 10_321,
        )

        assertTrue(script.contains("*libtcpdump.so*"))
        assertTrue(script.contains("kill -2"))
        assertTrue(script.contains("kill -15"))
        assertTrue(script.contains("chmod 600"))
        assertFalse(script.contains("killall"))
        assertFalse(script.contains("pkill"))
        assertFalse(script.contains("VIROUTEFS_Z2_OUT"))
    }
}
