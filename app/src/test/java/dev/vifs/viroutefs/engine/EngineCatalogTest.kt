// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.TunnelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EngineCatalogTest {
    @Test
    fun everyNonCompatibilityTypeHasAnExplicitProductDecision() {
        val compatibilityOnly = setOf(
            TunnelType.XrayMock,
            TunnelType.Hysteria2Mock,
            TunnelType.OpenVpnMock,
            TunnelType.WireGuardMock,
            TunnelType.Socks5Mock,
        )

        TunnelType.entries
            .filterNot { it in compatibilityOnly }
            .forEach { type -> assertNotNull(EngineCatalog.descriptor(type), "Missing catalog decision for $type") }
    }

    @Test
    fun legacyProtocolsAreNeverClaimedAsRuntimeReady() {
        listOf(TunnelType.Pptp, TunnelType.L2tp, TunnelType.L2tpIpSec, TunnelType.Sstp).forEach { type ->
            assertEquals(FeatureReadiness.Unavailable, EngineCatalog.descriptor(type)?.readiness)
            assertEquals(EngineBackend.LegacyAdapter, EngineCatalog.descriptor(type)?.backend)
        }
    }

    @Test
    fun openVpnAndOpenConnectAreBackedByTheBundledRuntime() {
        listOf(TunnelType.OpenVpn, TunnelType.OpenConnectAnyConnect).forEach { type ->
            val descriptor = EngineCatalog.descriptor(type)
            assertEquals(FeatureReadiness.RuntimeIntegrated, descriptor?.readiness)
            assertEquals(EngineBackend.SingBox, descriptor?.backend)
        }
    }

    @Test
    fun zapret2IsAuditedButNotClaimedAsRuntimeReady() {
        val descriptor = EngineCatalog.descriptor(TunnelType.Zapret2)

        assertEquals(FeatureReadiness.Unavailable, descriptor?.readiness)
        assertEquals(EngineBackend.Zapret2, descriptor?.backend)
        assertTrue(descriptor?.summary.orEmpty().contains("NFQUEUE"))
    }

    @Test
    fun everyCreatableProtocolSupportsRoutingAndCustomDnsInTheProductModel() {
        assertTrue(EngineCatalog.selectableProtocols.isNotEmpty())
        EngineCatalog.selectableProtocols.filter(ProtocolDescriptor::canCreateProfile).forEach { protocol ->
            assertTrue(protocol.supportsRouteRules, protocol.type.name)
            assertTrue(protocol.supportsCustomDns, protocol.type.name)
        }
    }

    @Test
    fun noProtocolClaimsDeviceVerificationWithoutPhysicalEvidence() {
        EngineCatalog.protocols.forEach { descriptor ->
            assertTrue(
                descriptor.readiness != FeatureReadiness.DeviceVerified &&
                    descriptor.readiness != FeatureReadiness.ProductionReady,
                descriptor.type.name,
            )
        }
    }
}
