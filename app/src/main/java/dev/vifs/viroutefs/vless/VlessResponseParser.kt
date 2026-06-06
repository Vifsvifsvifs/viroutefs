// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/**
 * Metadata-only classifier for manual VLESS response probes.
 *
 * This parser never accepts or returns raw payload bytes and never needs the
 * profile UUID. Callers pass only the read byte count or the safe failure type.
 */
enum class VlessResponseClassification(val label: String) {
    ResponseReceived("Response received"),
    EmptyResponse("Empty response"),
    Timeout("Timeout"),
    ServerClosed("Server closed connection"),
    InvalidResponse("Invalid response"),
    TLSHandshakeFailed("TLS handshake failed"),
    UnsupportedTransport("Unsupported transport"),
    ValidationError("Validation error"),
}

data class VlessResponseMetadata(
    val classification: VlessResponseClassification,
    val byteCount: Int = 0,
    val elapsedMs: Long? = null,
    val securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
) {
    val safeMessage: String
        get() = when (classification) {
            VlessResponseClassification.ResponseReceived -> "Response received: $byteCount bytes"
            VlessResponseClassification.EmptyResponse -> "Invalid response"
            VlessResponseClassification.Timeout -> "Timeout"
            VlessResponseClassification.ServerClosed -> "Server closed connection"
            VlessResponseClassification.InvalidResponse -> "Invalid response"
            VlessResponseClassification.TLSHandshakeFailed -> "TLS handshake failed"
            VlessResponseClassification.UnsupportedTransport -> "Security mode not supported"
            VlessResponseClassification.ValidationError -> "Invalid response"
        }
}

object VlessResponseParser {
    fun fromReadResult(
        readByteCount: Int,
        elapsedMs: Long?,
        securityMode: VlessSecurityMode,
    ): VlessResponseMetadata {
        val classification = when {
            readByteCount > 0 -> VlessResponseClassification.ResponseReceived
            readByteCount == -1 -> VlessResponseClassification.ServerClosed
            readByteCount == 0 -> VlessResponseClassification.EmptyResponse
            else -> VlessResponseClassification.InvalidResponse
        }
        return VlessResponseMetadata(
            classification = classification,
            byteCount = readByteCount.coerceAtLeast(0),
            elapsedMs = elapsedMs,
            securityMode = securityMode,
        )
    }

    fun fromFailure(
        error: Throwable,
        elapsedMs: Long?,
        securityMode: VlessSecurityMode,
        requestSent: Boolean,
    ): VlessResponseMetadata {
        val classification = when (error) {
            is SocketTimeoutException -> VlessResponseClassification.Timeout
            is SSLException -> VlessResponseClassification.TLSHandshakeFailed
            is SecurityException, is IllegalArgumentException -> VlessResponseClassification.ValidationError
            is IOException -> if (requestSent) VlessResponseClassification.ServerClosed else VlessResponseClassification.InvalidResponse
            else -> VlessResponseClassification.InvalidResponse
        }
        return VlessResponseMetadata(
            classification = classification,
            elapsedMs = elapsedMs,
            securityMode = securityMode,
        )
    }

    fun validationError(securityMode: VlessSecurityMode): VlessResponseMetadata = VlessResponseMetadata(
        classification = VlessResponseClassification.ValidationError,
        securityMode = securityMode,
    )

    fun unsupportedTransport(securityMode: VlessSecurityMode): VlessResponseMetadata = VlessResponseMetadata(
        classification = VlessResponseClassification.UnsupportedTransport,
        securityMode = securityMode,
    )
}

fun VlessResponseClassification.toProbeState(): VlessProtocolProbeState = when (this) {
    VlessResponseClassification.ResponseReceived -> VlessProtocolProbeState.ResponseReceived
    VlessResponseClassification.EmptyResponse -> VlessProtocolProbeState.InvalidEmptyResponse
    VlessResponseClassification.Timeout -> VlessProtocolProbeState.RequestSentNoImmediateResponse
    VlessResponseClassification.ServerClosed -> VlessProtocolProbeState.ServerClosedConnection
    VlessResponseClassification.InvalidResponse -> VlessProtocolProbeState.InvalidEmptyResponse
    VlessResponseClassification.TLSHandshakeFailed -> VlessProtocolProbeState.TlsHandshakeFailed
    VlessResponseClassification.UnsupportedTransport -> VlessProtocolProbeState.UnsupportedSecurityMode
    VlessResponseClassification.ValidationError -> VlessProtocolProbeState.ValidationError
}

fun VlessProtocolProbeState.toVlessResponseClassification(): VlessResponseClassification = when (this) {
    VlessProtocolProbeState.ResponseReceived -> VlessResponseClassification.ResponseReceived
    VlessProtocolProbeState.InvalidEmptyResponse -> VlessResponseClassification.EmptyResponse
    VlessProtocolProbeState.RequestSentNoImmediateResponse,
    VlessProtocolProbeState.Timeout -> VlessResponseClassification.Timeout
    VlessProtocolProbeState.ServerClosedConnection -> VlessResponseClassification.ServerClosed
    VlessProtocolProbeState.TlsHandshakeFailed -> VlessResponseClassification.TLSHandshakeFailed
    VlessProtocolProbeState.UnsupportedSecurityMode -> VlessResponseClassification.UnsupportedTransport
    VlessProtocolProbeState.ValidationError -> VlessResponseClassification.ValidationError
    else -> VlessResponseClassification.InvalidResponse
}
