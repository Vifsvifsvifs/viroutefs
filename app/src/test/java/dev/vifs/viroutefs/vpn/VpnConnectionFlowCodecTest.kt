// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import kotlin.test.Test
import kotlin.test.assertEquals

class VpnConnectionFlowCodecTest {
    @Test
    fun connectionMetadataRoundTripsWithoutDelimiterCorruption() {
        val flow = VpnConnectionFlow(
            id = "flow|one",
            createdAt = 1_700_000_000_000L,
            closedAt = 1_700_000_001_000L,
            network = "tcp",
            source = "[fd00::1]:12345",
            destination = "[2001:db8::1]:443",
            domain = "пример.рф",
            protocol = "tls",
            appPackages = listOf("dev.example.one", "dev.example,two"),
            processPath = "/data/app/a path/process",
            outboundTag = "profile_test|route",
            outboundType = "vless",
            matchedRule = "app -> provider",
            uplinkBytes = 123L,
            downlinkBytes = 456L,
        )

        val encoded = VpnServiceController.encodeConnectionFlow(flow)
        val decoded = VpnServiceController.decodeConnectionFlows(arrayListOf(encoded))

        assertEquals(listOf(flow), decoded)
    }
}
