package dev.vifs.viroutefs.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileAppRoutingTest {
    @Test
    fun selectedAppsUseProfileWithoutChangingDefaultRoute() {
        val config = configWithProfiles().withProfileAppRouting(
            profileId = VPN_A,
            mode = ProfileAppRoutingMode.SelectedApps,
            packageNames = listOf("com.example.browser"),
        )

        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, config.defaultProfileId)
        val appRule = config.managedRule("apps", VPN_A)
        assertEquals(VPN_A, appRule.targetProfileId)
        assertEquals(listOf("com.example.browser"), appRule.appMatchers.map { it.value })
    }

    @Test
    fun bypassInvertsAppSelectionButProfileNetworksWinFirst() {
        val config = configWithProfiles().withProfileAppRouting(
            profileId = VPN_A,
            mode = ProfileAppRoutingMode.BypassSelected,
            packageNames = listOf("com.example.browser"),
            networks = listOf("10.0.0.0/24"),
        )

        assertEquals(VPN_A, config.defaultProfileId)
        val networkRule = config.managedRule("networks", VPN_A)
        val appRule = config.managedRule("apps", VPN_A)
        assertEquals(VPN_A, networkRule.targetProfileId)
        assertEquals(listOf("10.0.0.0/24"), networkRule.matchers)
        assertEquals(RoutingConfigDefaults.SYSTEM_PROFILE_ID, appRule.targetProfileId)
        assertTrue(networkRule.priority < appRule.priority)
    }

    @Test
    fun assigningAppToAnotherProfileRemovesOldAssignment() {
        val first = configWithProfiles().withProfileAppRouting(
            profileId = VPN_A,
            mode = ProfileAppRoutingMode.SelectedApps,
            packageNames = listOf("com.example.browser", "com.example.mail"),
        )
        val second = first.withProfileAppRouting(
            profileId = VPN_B,
            mode = ProfileAppRoutingMode.SelectedApps,
            packageNames = listOf("com.example.browser"),
        )

        assertEquals(
            listOf("com.example.mail"),
            second.profiles.first { it.id == VPN_A }.appRoutingPackages,
        )
        assertEquals(
            listOf("com.example.browser"),
            second.profiles.first { it.id == VPN_B }.appRoutingPackages,
        )
        assertFalse(
            second.managedRule("apps", VPN_A).appMatchers.any { it.value == "com.example.browser" },
        )
    }

    @Test
    fun appsAssignedToVpnGateAreHiddenFromAnotherVpnPicker() {
        val base = configWithProfiles()
        val vpnGateGroup = ProfileGroup(
            id = "vpngate-group",
            name = "VPNGate",
            mode = ProfileGroupMode.Latency,
            memberProfileIds = listOf(VPN_B),
            enabled = true,
        )
        val vpnGateRule = RouteRule(
            id = "vpngate-apps",
            name = "VPNGate apps",
            type = RouteRuleType.APP_GROUP,
            targetProfileId = vpnGateGroup.id,
            priority = 100,
            matchers = emptyList(),
            appMatchers = listOf(
                AppMatcher(AppMatcherPlatform.Android, "com.example.video", "Video"),
            ),
            reason = "test",
            technicalDetails = "test",
            recommendedAction = "test",
        )
        val config = base.copy(
            profileGroups = listOf(vpnGateGroup),
            rules = base.rules + vpnGateRule,
        )

        assertEquals(
            setOf("com.example.video"),
            config.packagesAssignedToOtherVpnTargets(VPN_A),
        )
    }

    @Test
    fun profileRoutingFieldsSurviveJsonRoundTrip() {
        val source = configWithProfiles().withProfileAppRouting(
            profileId = VPN_A,
            mode = ProfileAppRoutingMode.BypassSelected,
            packageNames = listOf("com.example.browser"),
            networks = listOf("10.0.0.0/24", "2001:db8::/32"),
        )

        val decoded = RoutingConfigJson.decode(RoutingConfigJson.encode(source))
        val profile = decoded.profiles.first { it.id == VPN_A }
        assertEquals(ProfileAppRoutingMode.BypassSelected, profile.appRoutingMode)
        assertEquals(listOf("com.example.browser"), profile.appRoutingPackages)
        assertEquals(listOf("10.0.0.0/24", "2001:db8::/32"), profile.appRoutingNetworks)
    }

    private fun RoutingConfig.managedRule(kind: String, profileId: String): RouteRule =
        assertNotNull(rules.firstOrNull { it.id == "profile-app-routing:$kind:$profileId" })

    private fun configWithProfiles(): RoutingConfig = RoutingConfigDefaults.defaultConfig().copy(
        profiles = RoutingConfigDefaults.defaultConfig().profiles + listOf(
            testProfile(VPN_A, "VPN A"),
            testProfile(VPN_B, "VPN B"),
        ),
    )

    private fun testProfile(id: String, name: String): TunnelProfile = TunnelProfile(
        id = id,
        name = name,
        type = TunnelType.WireGuard,
        description = "test",
        enabled = true,
        mockOnly = false,
    )

    private companion object {
        const val VPN_A = "vpn-a"
        const val VPN_B = "vpn-b"
    }
}
