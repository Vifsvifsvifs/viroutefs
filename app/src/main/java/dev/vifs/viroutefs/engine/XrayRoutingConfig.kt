// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.TunnelProfile
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.validateVlessProfile
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal data class XrayLocalProfile(
    val profileId: String,
    val localSocksPort: Int,
    val profile: VlessProfileConfig,
)

internal data class XrayCompiledRuntime(
    val json: String,
    val profilePorts: Map<String, Int>,
)

/**
 * Compiles Xray-only VLESS transports into isolated loopback SOCKS endpoints.
 *
 * The Android TUN stays owned by ViRouteFS/sing-box. Each Xray profile receives
 * one localhost-only SOCKS inbound and one explicit outbound; sing-box routes
 * only the selected flows to that endpoint.
 */
internal fun compileXrayRuntime(profiles: List<XrayLocalProfile>): XrayCompiledRuntime {
    require(profiles.isNotEmpty()) { "At least one Xray profile is required." }
    require(profiles.map { it.profileId }.distinct().size == profiles.size) {
        "Xray profile ids must be unique."
    }
    require(profiles.map { it.localSocksPort }.distinct().size == profiles.size) {
        "Xray local SOCKS ports must be unique."
    }

    val inbounds = JSONArray()
    val outbounds = JSONArray()
    val rules = JSONArray()
    profiles.forEachIndexed { index, item ->
        val errors = validateXrayVlessProfile(item.profile)
        require(errors.isEmpty()) { errors.joinToString(" ") }
        require(item.localSocksPort in 1..65535) { "Invalid local SOCKS port." }
        val inboundTag = "viroutefs-xray-in-$index"
        val outboundTag = "viroutefs-xray-out-$index"
        inbounds.put(
            JSONObject()
                .put("tag", inboundTag)
                .put("listen", "127.0.0.1")
                .put("port", item.localSocksPort)
                .put("protocol", "socks")
                .put(
                    "settings",
                    JSONObject()
                        .put("auth", "noauth")
                        .put("udp", true)
                        .put("userLevel", 8),
                ),
        )
        outbounds.put(item.profile.toXrayVlessOutbound(outboundTag))
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("inboundTag", JSONArray().put(inboundTag))
                .put("outboundTag", outboundTag),
        )
    }
    outbounds.put(
        JSONObject()
            .put("tag", "direct")
            .put("protocol", "freedom")
            .put(
                "streamSettings",
                JSONObject().put(
                    "sockopt",
                    JSONObject().put("domainStrategy", "UseIP"),
                ),
            ),
    )
    outbounds.put(
        JSONObject()
            .put("tag", "block")
            .put("protocol", "blackhole"),
    )

    val root = JSONObject()
        .put("stats", JSONObject())
        .put("log", JSONObject().put("loglevel", "warning"))
        .put(
            "policy",
            JSONObject()
                .put(
                    "levels",
                    JSONObject().put(
                        "8",
                        JSONObject()
                            .put("handshake", 4)
                            .put("connIdle", 300)
                            .put("uplinkOnly", 1)
                            .put("downlinkOnly", 1),
                    ),
                )
                .put(
                    "system",
                    JSONObject()
                        .put("statsOutboundUplink", true)
                        .put("statsOutboundDownlink", true),
                ),
        )
        .put("inbounds", inbounds)
        .put("outbounds", outbounds)
        .put(
            "routing",
            JSONObject()
                .put("domainStrategy", "AsIs")
                .put("rules", rules),
        )

    return XrayCompiledRuntime(
        json = root.toString(2),
        profilePorts = profiles.associate { it.profileId to it.localSocksPort },
    )
}

internal fun validateXrayProfile(profile: TunnelProfile): List<String> = buildList {
    if (profile.type != TunnelType.XrayVlessReality) {
        add("Expected an Xray VLESS profile.")
        return@buildList
    }
    val candidate = profile.vless
    if (candidate == null) {
        add("Xray profile has no VLESS configuration.")
    } else {
        addAll(validateXrayVlessProfile(candidate))
    }
}

internal fun validateXrayVlessProfile(profile: VlessProfileConfig): List<String> = buildList {
    addAll(validateVlessProfile(profile))
    if (!profile.transportType.equals("xhttp", ignoreCase = true)) {
        add("This Xray adapter currently requires the XHTTP transport.")
    }
    profile.xhttpMode?.takeIf(String::isNotBlank)?.let { mode ->
        if (mode !in setOf("auto", "packet-up", "stream-up", "stream-one")) {
            add("XHTTP mode must be auto, packet-up, stream-up, or stream-one.")
        }
    }
    profile.xhttpExtra?.takeIf(String::isNotBlank)?.let { extra ->
        runCatching { JSONObject(extra) }
            .onFailure { add("XHTTP extra must be a valid JSON object.") }
    }
    if (profile.securityMode == VlessSecurityMode.REALITY) {
        if (profile.sni.isNullOrBlank()) add("REALITY SNI is required.")
        if (profile.publicKey.isNullOrBlank()) add("REALITY public key is required.")
        val shortId = profile.shortId.orEmpty()
        if (shortId.length > 16 ||
            shortId.length % 2 != 0 ||
            shortId.any { it !in "0123456789abcdefABCDEF" }
        ) {
            add("REALITY short ID must contain an even number of hex characters, at most 16.")
        }
    }
}

private fun VlessProfileConfig.toXrayVlessOutbound(tag: String): JSONObject {
    val user = JSONObject()
        .put("id", uuid.trim())
        .put("encryption", encryption?.takeIf(String::isNotBlank) ?: "none")
        .put("level", 8)
        .apply {
            flow?.takeIf(String::isNotBlank)?.let { put("flow", it) }
        }
    val stream = JSONObject()
        .put("network", "xhttp")
        .put(
            "xhttpSettings",
            JSONObject()
                .put("host", hostHeader.orEmpty())
                .put("path", path?.takeIf(String::isNotBlank) ?: "/")
                .apply {
                    xhttpMode?.takeIf(String::isNotBlank)?.let { put("mode", it) }
                    xhttpExtra?.takeIf(String::isNotBlank)?.let { put("extra", JSONObject(it)) }
                },
        )
        .put(
            "sockopt",
            JSONObject().put("domainStrategy", "UseIP"),
        )
    when (securityMode) {
        VlessSecurityMode.NONE -> Unit
        VlessSecurityMode.TLS -> {
            stream.put("security", "tls")
            stream.put("tlsSettings", xrayTlsSettings())
        }
        VlessSecurityMode.REALITY -> {
            stream.put("security", "reality")
            stream.put(
                "realitySettings",
                xrayTlsSettings()
                    .put("publicKey", publicKey)
                    .put("shortId", shortId.orEmpty())
                    .put("spiderX", "/"),
            )
        }
    }
    return JSONObject()
        .put("tag", tag)
        .put("protocol", "vless")
        .put(
            "settings",
            JSONObject().put(
                "vnext",
                JSONArray().put(
                    JSONObject()
                        .put("address", host.trim())
                        .put("port", port)
                        .put("users", JSONArray().put(user)),
                ),
            ),
        )
        .put("streamSettings", stream)
        .put("mux", JSONObject().put("enabled", false).put("concurrency", -1))
}

private fun VlessProfileConfig.xrayTlsSettings(): JSONObject = JSONObject()
    .put("serverName", sni?.takeIf(String::isNotBlank) ?: host.trim())
    .apply {
        pinnedPeerCertSha256?.takeIf(String::isNotBlank)?.let {
            put("pinnedPeerCertSha256", it)
        }
        verifyPeerCertByName?.let { put("verifyPeerCertByName", it) }
        fingerprint?.takeIf(String::isNotBlank)?.let { put("fingerprint", it) }
        alpn?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.takeIf(List<String>::isNotEmpty)
            ?.let { put("alpn", JSONArray(it)) }
    }
