// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.outbound

import dev.vifs.viroutefs.socks5.Socks5DiagnosticState
import dev.vifs.viroutefs.socks5.Socks5HandshakeTester
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.Socks5TestResult
import dev.vifs.viroutefs.socks5.performSocks5Connect
import dev.vifs.viroutefs.socks5.performSocks5Greeting
import dev.vifs.viroutefs.socks5.sanitizeSocks5Diagnostic
import dev.vifs.viroutefs.socks5.validateSocks5Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

internal class Socks5OutboundConnector(
    private val profile: Socks5ProfileConfig,
    private val socketFactory: () -> Socket = { Socket() },
) : OutboundConnector {
    override suspend fun connect(request: OutboundConnectRequest): OutboundConnectResult = withContext(Dispatchers.IO) {
        val validationErrors = validateSocks5Profile(profile) + request.validationErrors()
        if (validationErrors.isNotEmpty()) {
            return@withContext OutboundConnectResult.ValidationError(validationErrors.joinToString(" ").sanitizeSocks5Diagnostic())
        }

        runCatching {
            socketFactory().use { socket ->
                socket.soTimeout = request.timeoutMs
                socket.connect(InetSocketAddress(profile.host.trim(), profile.port), request.timeoutMs)
                when (val greeting = performSocks5Greeting(socket, profile)) {
                    Socks5TestResult.Reachable -> performSocks5Connect(socket, request.target.host.trim(), request.target.port).toOutboundResult()
                    is Socks5TestResult.Failed -> greeting.toOutboundResult()
                }
            }
        }.getOrElse { error -> error.toOutboundResult() }
    }
}

internal fun Socks5TestResult.Failed.toOutboundResult(): OutboundConnectResult = when (this) {
    Socks5TestResult.Failed.AuthenticationRejected -> OutboundConnectResult.AuthenticationRejected(message.sanitizeSocks5Diagnostic())
    Socks5TestResult.Failed.UnsupportedMethod -> OutboundConnectResult.UnsupportedAuthMethod(message.sanitizeSocks5Diagnostic())
    Socks5TestResult.Failed.ConnectionTimeout -> OutboundConnectResult.Timeout(message.sanitizeSocks5Diagnostic())
    Socks5TestResult.Failed.ConnectionRefused -> OutboundConnectResult.TargetUnreachable(message.sanitizeSocks5Diagnostic())
    is Socks5TestResult.Failed.InvalidResponse -> OutboundConnectResult.InvalidResponse(message.sanitizeSocks5Diagnostic())
}

internal fun Throwable.toOutboundResult(): OutboundConnectResult = when (this) {
    is SocketTimeoutException -> OutboundConnectResult.Timeout("SOCKS5 CONNECT timed out.")
    is ConnectException -> OutboundConnectResult.TargetUnreachable(message?.sanitizeSocks5Diagnostic() ?: "TCP connection failed.")
    else -> OutboundConnectResult.InvalidResponse(message?.sanitizeSocks5Diagnostic() ?: "SOCKS5 CONNECT failed.")
}

internal fun dev.vifs.viroutefs.socks5.Socks5DiagnosticResult.toOutboundResult(): OutboundConnectResult = when (state) {
    Socks5DiagnosticState.ConnectSuccess -> OutboundConnectResult.Success(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.ConnectRejectedByProxy -> OutboundConnectResult.ConnectRejectedByProxy(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.TargetUnreachable -> OutboundConnectResult.TargetUnreachable(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.Timeout -> OutboundConnectResult.Timeout(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.InvalidSocks5Response -> OutboundConnectResult.InvalidResponse(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.AuthenticationRejected -> OutboundConnectResult.AuthenticationRejected(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.UnsupportedAuthMethod -> OutboundConnectResult.UnsupportedAuthMethod(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.ValidationError -> OutboundConnectResult.ValidationError(message.sanitizeSocks5Diagnostic())
    Socks5DiagnosticState.HandshakeReachable -> OutboundConnectResult.InvalidResponse("SOCKS5 CONNECT did not run after handshake.")
}

internal fun OutboundConnectResult.toSocks5DiagnosticResult(): dev.vifs.viroutefs.socks5.Socks5DiagnosticResult {
    val state = when (this) {
        is OutboundConnectResult.Success -> Socks5DiagnosticState.ConnectSuccess
        is OutboundConnectResult.AuthenticationRejected -> Socks5DiagnosticState.AuthenticationRejected
        is OutboundConnectResult.UnsupportedAuthMethod -> Socks5DiagnosticState.UnsupportedAuthMethod
        is OutboundConnectResult.ConnectRejectedByProxy -> Socks5DiagnosticState.ConnectRejectedByProxy
        is OutboundConnectResult.TargetUnreachable -> Socks5DiagnosticState.TargetUnreachable
        is OutboundConnectResult.Timeout -> Socks5DiagnosticState.Timeout
        is OutboundConnectResult.InvalidResponse -> Socks5DiagnosticState.InvalidSocks5Response
        is OutboundConnectResult.ValidationError -> Socks5DiagnosticState.ValidationError
    }
    return dev.vifs.viroutefs.socks5.Socks5DiagnosticResult(
        testType = dev.vifs.viroutefs.socks5.Socks5DiagnosticTestType.Connect,
        state = state,
        message = message.sanitizeSocks5Diagnostic(),
    )
}
