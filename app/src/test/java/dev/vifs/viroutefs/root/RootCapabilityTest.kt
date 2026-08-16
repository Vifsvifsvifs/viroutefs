// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.root

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootCapabilityTest {
    @Test
    fun parsesReadOnlyRootProbeWithoutTrustingUnknownLines() {
        val snapshot = parseRootCapabilityProbe(
            """
                uid=0
                identity=uid=0(root) gid=0(root)
                selinux=Enforcing
                kernel=6.1.99-android
                cap_eff=000001ffffffffff
                ip=1
                iptables=1
                ip6tables=1
                nft=0
                nfqueue=1
                tcpdump=0
                tc=1
                conntrack=0
                wireguard_kernel=1
                ignored=untrusted
            """.trimIndent(),
            checkedAtEpochMillis = 123L,
        )

        assertTrue(snapshot.rootGranted)
        assertEquals(0, snapshot.uid)
        assertEquals("Enforcing", snapshot.selinuxMode)
        assertTrue(snapshot.hasNfQueue)
        assertTrue(snapshot.hasWireGuardKernelModule)
        assertFalse(snapshot.hasNftables)
        assertEquals(123L, snapshot.checkedAtEpochMillis)
    }

    @Test
    fun nonZeroUidNeverCountsAsRoot() {
        val snapshot = parseRootCapabilityProbe("uid=2000\niptables=1\nnfqueue=1")

        assertFalse(snapshot.rootGranted)
        assertEquals(2000, snapshot.uid)
    }
}
