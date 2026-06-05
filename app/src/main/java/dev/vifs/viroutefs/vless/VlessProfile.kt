// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import java.util.UUID

const val VLESS_RUNTIME_LIMITATION = "VLESS runtime forwarding is not implemented yet."
const val VLESS_ROUTE_PREVIEW_ONLY = "This profile can be used for route decision preview only."

data class VlessProfileConfig(
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String,
    val flow: String? = null,
    val securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
    val sni: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val fingerprint: String? = null,
    val enabled: Boolean = true,
    val status: VlessProfileStatus = VlessProfileStatus.NotTested,
) {
    fun safeSummary(): String = buildString {
        append(name.trim())
        append(" @ ")
        append(host.trim())
        append(':')
        append(port)
        append(if (enabled) " enabled" else " disabled")
        append(" security=")
        append(securityMode.wireName)
        if (!flow.isNullOrBlank()) append(" flow=provided")
        if (!sni.isNullOrBlank()) append(" sni=provided")
        if (!publicKey.isNullOrBlank()) append(" publicKey=placeholder-provided")
        if (!shortId.isNullOrBlank()) append(" shortId=placeholder-provided")
        if (!fingerprint.isNullOrBlank()) append(" fingerprint=provided")
        append(" status=")
        append(status.safeLabel)
    }
}

enum class VlessSecurityMode(val wireName: String, val label: String) {
    NONE("none", "none"),
    TLS("tls", "tls"),
    REALITY("reality", "reality placeholder"),
}

sealed interface VlessProfileStatus {
    val safeLabel: String

    data object NotTested : VlessProfileStatus {
        override val safeLabel: String = "Not tested"
    }

    data object Invalid : VlessProfileStatus {
        override val safeLabel: String = "Invalid"
    }

    data object ConfigReady : VlessProfileStatus {
        override val safeLabel: String = "Config ready"
    }
}

fun validateVlessProfile(candidate: VlessProfileConfig): List<String> = buildList {
    if (candidate.name.trim().isBlank()) add("VLESS profile name must not be blank.")
    if (candidate.host.trim().isBlank()) add("VLESS host must not be blank.")
    if (candidate.port !in 1..65535) add("VLESS port must be in range 1..65535.")
    if (!candidate.uuid.isValidUuid()) add("VLESS UUID must be a valid UUID.")
}

fun String.isValidUuid(): Boolean = runCatching { UUID.fromString(trim()) }
    .map { it.toString().equals(trim(), ignoreCase = true) }
    .getOrDefault(false)
