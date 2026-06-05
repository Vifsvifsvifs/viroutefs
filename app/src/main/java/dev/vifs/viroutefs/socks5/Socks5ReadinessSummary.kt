// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import java.text.DateFormat
import java.util.Date

sealed interface Socks5ReadinessState {
    val badgeLabel: String

    data object NotTested : Socks5ReadinessState {
        override val badgeLabel: String = "Not tested"
    }

    data object HandshakeReachable : Socks5ReadinessState {
        override val badgeLabel: String = "Handshake OK"
    }

    data object ConnectSuccess : Socks5ReadinessState {
        override val badgeLabel: String = "CONNECT OK"
    }

    data object LastTestFailed : Socks5ReadinessState {
        override val badgeLabel: String = "Last test failed"
    }

    data object UnknownStale : Socks5ReadinessState {
        override val badgeLabel: String = "Unknown"
    }
}

data class Socks5ReadinessSummary(
    val state: Socks5ReadinessState,
    val lastTestType: Socks5DiagnosticTestType? = null,
    val lastResultState: Socks5DiagnosticState? = null,
    val lastTargetHost: String? = null,
    val lastTargetPort: Int? = null,
    val timestamp: Long? = null,
    val elapsedMs: Long? = null,
    val userSafeMessage: String,
    val lastHandshake: Socks5TestHistoryItem? = null,
    val lastConnect: Socks5TestHistoryItem? = null,
    val lastConnectSuccess: Socks5TestHistoryItem? = null,
) {
    val badgeLabel: String = state.badgeLabel

    val compactTimestamp: String?
        get() = timestamp?.toCompactLocalTime()

    val compactListLine: String
        get() = buildString {
            append(badgeLabel)
            compactTimestamp?.let { append(" • ").append(it) }
        }

    val lastConnectSuccessLine: String?
        get() = lastConnectSuccess?.targetLabel()?.let { target -> "Last CONNECT OK: $target" }

    val routeExplanationLine: String
        get() = when (state) {
            Socks5ReadinessState.NotTested -> "SOCKS5 profile has not been tested yet."
            Socks5ReadinessState.ConnectSuccess -> {
                val target = lastConnectSuccess?.targetLabel()
                val time = timestamp?.toCompactLocalTime()
                if (target != null && time != null) "Last manual CONNECT test: success at $time for $target" else userSafeMessage
            }
            Socks5ReadinessState.LastTestFailed -> "Last manual SOCKS5 test failed: $userSafeMessage"
            Socks5ReadinessState.HandshakeReachable -> {
                val time = timestamp?.toCompactLocalTime()
                if (time != null) "Last manual SOCKS5 handshake test: success at $time." else userSafeMessage
            }
            Socks5ReadinessState.UnknownStale -> userSafeMessage
        }
}

fun deriveSocks5ReadinessSummary(history: List<Socks5TestHistoryItem>): Socks5ReadinessSummary {
    val sorted = history.sortedByDescending { it.timestamp }
    val latest = sorted.firstOrNull()
    val lastHandshake = sorted.firstOrNull { it.testType == Socks5DiagnosticTestType.Handshake }
    val lastConnect = sorted.firstOrNull { it.testType == Socks5DiagnosticTestType.Connect }
    val lastConnectSuccess = sorted.firstOrNull {
        it.testType == Socks5DiagnosticTestType.Connect && it.state == Socks5DiagnosticState.ConnectSuccess
    }

    if (latest == null) {
        return Socks5ReadinessSummary(
            state = Socks5ReadinessState.NotTested,
            userSafeMessage = "SOCKS5 profile has not been tested yet.",
        )
    }

    val state = when (latest.state) {
        Socks5DiagnosticState.ConnectSuccess -> Socks5ReadinessState.ConnectSuccess
        Socks5DiagnosticState.HandshakeReachable -> Socks5ReadinessState.HandshakeReachable
        Socks5DiagnosticState.AuthenticationRejected,
        Socks5DiagnosticState.UnsupportedAuthMethod,
        Socks5DiagnosticState.ConnectRejectedByProxy,
        Socks5DiagnosticState.TargetUnreachable,
        Socks5DiagnosticState.Timeout,
        Socks5DiagnosticState.InvalidSocks5Response,
        Socks5DiagnosticState.ValidationError,
        -> Socks5ReadinessState.LastTestFailed
    }

    val latestTargetHost = latest.targetHost?.sanitizeSocks5Diagnostic()?.takeIf { latest.testType == Socks5DiagnosticTestType.Connect }
    val safeMessage = latest.safeSummaryMessage()
    return Socks5ReadinessSummary(
        state = state,
        lastTestType = latest.testType,
        lastResultState = latest.state,
        lastTargetHost = latestTargetHost,
        lastTargetPort = latest.targetPort?.takeIf { latest.testType == Socks5DiagnosticTestType.Connect },
        timestamp = latest.timestamp,
        elapsedMs = latest.elapsedMs,
        userSafeMessage = safeMessage,
        lastHandshake = lastHandshake,
        lastConnect = lastConnect,
        lastConnectSuccess = lastConnectSuccess,
    )
}

fun socks5ReadinessRouteExplanation(history: List<Socks5TestHistoryItem>): String =
    deriveSocks5ReadinessSummary(history).routeExplanationLine

private fun Socks5TestHistoryItem.safeSummaryMessage(): String = buildString {
    append(state.label)
    val target = targetLabel()
    if (target != null) append(" for ").append(target)
    elapsedMs?.let { append(" (${it} ms)") }
    val safeMessage = message.sanitizeSocks5Diagnostic()
    if (safeMessage.isNotBlank()) append(": ").append(safeMessage)
}.sanitizeSocks5Diagnostic()

private fun Socks5TestHistoryItem.targetLabel(): String? {
    val host = targetHost?.sanitizeSocks5Diagnostic()?.takeIf { it.isNotBlank() } ?: return null
    val port = targetPort?.takeIf { it in 1..65535 } ?: return null
    return "$host:$port"
}

private fun Long.toCompactLocalTime(): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(this))
