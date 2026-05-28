package dev.vifs.viroutefs.vpn

import android.net.VpnService

/**
 * Placeholder VPN service for the first ViRouteFS milestone.
 *
 * Real packet routing, tunnel setup, and packet capture are intentionally not
 * implemented yet. Future work should keep routing local-first and require
 * explicit user action before exporting logs or captures.
 */
class ViRouteFsVpnService : VpnService()
