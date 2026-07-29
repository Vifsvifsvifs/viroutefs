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

    @Test
    fun profileGroupEventRoundTripsWithoutDelimiterCorruption() {
        val event = ProfileGroupRuntimeEvent(
            timestamp = 123_456_789L,
            groupId = "office|group",
            groupName = "Офис | резерв",
            selectedProfileId = "reserve-2",
            selectedProfileName = "Резерв №2",
            reason = ProfileGroupRuntimeReason.Failover,
            message = "Основной недоступен: выбран резерв | без System.",
        )

        val encoded = VpnServiceController.encodeProfileGroupEvent(event)
        val decoded = VpnServiceController.decodeProfileGroupEvents(arrayListOf(encoded))

        assertEquals(listOf(event), decoded)
    }
}
