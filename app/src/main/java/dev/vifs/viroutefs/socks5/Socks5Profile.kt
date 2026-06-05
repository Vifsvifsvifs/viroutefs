// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import java.util.Locale

data class Socks5ProfileConfig(
    val name: String,
    val host: String,
    val port: Int,
    val username: String? = null,
    val password: String? = null,
    val enabled: Boolean = true,
    val status: Socks5ProfileStatus = Socks5ProfileStatus.NotTested,
) {
    val credentialsProvided: Boolean
        get() = !username.isNullOrBlank() || !password.isNullOrBlank()

    fun maskedSummary(): String = buildString {
        append(name.trim())
        append(" @ ")
        append(host.trim())
        append(':')
        append(port)
        append(if (enabled) " enabled" else " disabled")
        if (credentialsProvided) append(" auth=provided") else append(" auth=no-auth")
        append(" status=")
        append(status.safeLabel)
    }
}

sealed interface Socks5ProfileStatus {
    val safeLabel: String

    data object NotTested : Socks5ProfileStatus {
        override val safeLabel: String = "Not tested"
    }

    data object Testing : Socks5ProfileStatus {
        override val safeLabel: String = "Testing…"
    }

    data object Reachable : Socks5ProfileStatus {
        override val safeLabel: String = "Reachable"
    }

    data class Failed(val message: String) : Socks5ProfileStatus {
        override val safeLabel: String = "Failed: ${message.sanitizeSocks5Diagnostic()}"
    }
}

fun validateSocks5Profile(
    candidate: Socks5ProfileConfig,
    existingProfiles: List<Socks5ProfileConfig> = emptyList(),
    originalName: String? = null,
): List<String> = buildList {
    val name = candidate.name.trim()
    val host = candidate.host.trim()
    if (name.isBlank()) add("SOCKS5 profile name must not be blank.")
    if (host.isBlank()) add("SOCKS5 host must not be blank.")
    if (candidate.port !in 1..65535) add("SOCKS5 port must be in range 1..65535.")
    val normalizedOriginal = originalName?.trim()?.lowercase(Locale.ROOT)
    val duplicateName = existingProfiles.any { profile ->
        val normalized = profile.name.trim().lowercase(Locale.ROOT)
        normalized == name.lowercase(Locale.ROOT) && normalized != normalizedOriginal
    }
    if (duplicateName) add("SOCKS5 profile name must be unique.")
}

fun String.sanitizeSocks5Diagnostic(): String = replace(Regex("(?i)(password|pass|pwd|secret)=\\S+"), "$1=***")
