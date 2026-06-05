// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import dev.vifs.viroutefs.outbound.OutboundConnectRequest
import dev.vifs.viroutefs.outbound.OutboundTarget
import dev.vifs.viroutefs.outbound.Socks5OutboundConnector
import dev.vifs.viroutefs.outbound.toSocks5DiagnosticResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.system.measureTimeMillis

class Socks5HandshakeTester(
    private val socketFactory: () -> Socket = { Socket() },
) {
    suspend fun test(profile: Socks5ProfileConfig, timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): Socks5TestResult = withContext(Dispatchers.IO) {
        testHandshake(profile, timeoutMillis).toLegacyResult()
    }

    suspend fun testHandshake(profile: Socks5ProfileConfig, timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): Socks5DiagnosticResult = withContext(Dispatchers.IO) {
        val validationErrors = validateSocks5Profile(profile)
        if (validationErrors.isNotEmpty()) {
            return@withContext Socks5DiagnosticResult(
                testType = Socks5DiagnosticTestType.Handshake,
                state = Socks5DiagnosticState.ValidationError,
                message = validationErrors.joinToString(" "),
            )
        }

        timedDiagnostic(Socks5DiagnosticTestType.Handshake) {
            socketFactory().use { socket ->
                socket.soTimeout = timeoutMillis
                socket.connect(InetSocketAddress(profile.host.trim(), profile.port), timeoutMillis)
                performSocks5Greeting(socket, profile).toHandshakeDiagnostic()
            }
        }
    }

    suspend fun testConnect(
        profile: Socks5ProfileConfig,
        targetHost: String,
        targetPort: Int,
        timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS,
    ): Socks5DiagnosticResult = withContext(Dispatchers.IO) {
        val validationErrors = validateSocks5Profile(profile) + validateSocks5ConnectTarget(targetHost, targetPort)
        if (validationErrors.isNotEmpty()) {
            return@withContext Socks5DiagnosticResult(
                testType = Socks5DiagnosticTestType.Connect,
                state = Socks5DiagnosticState.ValidationError,
                message = validationErrors.joinToString(" "),
            )
        }

        timedDiagnostic(Socks5DiagnosticTestType.Connect) {
            kotlinx.coroutines.runBlocking {
                Socks5OutboundConnector(profile, socketFactory).connect(
                    OutboundConnectRequest(
                        profileId = profile.name.ifBlank { profile.host },
                        target = OutboundTarget(targetHost.trim(), targetPort),
                        timeoutMs = timeoutMillis,
                    ),
                ).toSocks5DiagnosticResult()
            }
        }
    }

    private inline fun timedDiagnostic(testType: Socks5DiagnosticTestType, block: () -> Socks5DiagnosticResult): Socks5DiagnosticResult {
        var result: Socks5DiagnosticResult? = null
        val elapsed = measureTimeMillis {
            result = runCatching(block).getOrElse { error ->
                when (error) {
                    is SocketTimeoutException -> Socks5DiagnosticResult(testType, Socks5DiagnosticState.Timeout, "SOCKS5 test timed out.")
                    is ConnectException -> Socks5DiagnosticResult(testType, Socks5DiagnosticState.TargetUnreachable, error.message?.sanitizeSocks5Diagnostic() ?: "TCP connection failed.")
                    else -> Socks5DiagnosticResult(testType, Socks5DiagnosticState.InvalidSocks5Response, error.message?.sanitizeSocks5Diagnostic() ?: "SOCKS5 test failed.")
                }
            }
        }
        return result!!.copy(elapsedMs = elapsed)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000
        const val SOCKS_VERSION: Byte = 0x05
        const val NO_AUTH_METHOD: Byte = 0x00
        const val USERNAME_PASSWORD_METHOD: Byte = 0x02
        const val NO_ACCEPTABLE_METHOD: Byte = 0xFF.toByte()
        const val AUTH_VERSION: Byte = 0x01
        const val CONNECT_COMMAND: Byte = 0x01
        const val RESERVED: Byte = 0x00
        const val ADDRESS_TYPE_IPV4 = 0x01
        const val ADDRESS_TYPE_DOMAIN = 0x03
        const val ADDRESS_TYPE_IPV6 = 0x04
    }
}

internal fun performSocks5Greeting(socket: Socket, profile: Socks5ProfileConfig): Socks5TestResult {
    val input = socket.getInputStream()
    val output = socket.getOutputStream()
    val method = if (profile.credentialsProvided) Socks5HandshakeTester.USERNAME_PASSWORD_METHOD else Socks5HandshakeTester.NO_AUTH_METHOD
    output.write(byteArrayOf(Socks5HandshakeTester.SOCKS_VERSION, 1, method))
    output.flush()

    val version = input.readRequiredByte()
    val selectedMethod = input.readRequiredByte()
    if (version != Socks5HandshakeTester.SOCKS_VERSION.toInt()) return Socks5TestResult.Failed.InvalidResponse("Invalid SOCKS version in greeting response.")
    if (selectedMethod == Socks5HandshakeTester.NO_ACCEPTABLE_METHOD.toInt()) return Socks5TestResult.Failed.UnsupportedMethod
    if (selectedMethod != method.toInt()) return Socks5TestResult.Failed.UnsupportedMethod
    if (selectedMethod == Socks5HandshakeTester.NO_AUTH_METHOD.toInt()) return Socks5TestResult.Reachable

    val username = profile.username.orEmpty().encodeToByteArray()
    val password = profile.password.orEmpty().encodeToByteArray()
    if (username.size > 255 || password.size > 255) return Socks5TestResult.Failed.InvalidResponse("SOCKS5 username/password fields must be 255 bytes or shorter.")

    output.write(byteArrayOf(Socks5HandshakeTester.AUTH_VERSION, username.size.toByte()))
    output.write(username)
    output.write(password.size)
    output.write(password)
    output.flush()

    val authVersion = input.readRequiredByte()
    val authStatus = input.readRequiredByte()
    if (authVersion != Socks5HandshakeTester.AUTH_VERSION.toInt()) return Socks5TestResult.Failed.InvalidResponse("Invalid SOCKS5 authentication response.")
    return if (authStatus == 0) Socks5TestResult.Reachable else Socks5TestResult.Failed.AuthenticationRejected
}

internal fun performSocks5Connect(socket: Socket, targetHost: String, targetPort: Int): Socks5DiagnosticResult {
    val input = socket.getInputStream()
    val output = socket.getOutputStream()
    output.write(encodeSocks5ConnectRequest(targetHost, targetPort))
    output.flush()
    return parseSocks5ConnectResponse(input.readSocks5ConnectResponseBytes())
}

private fun java.io.InputStream.readRequiredByte(): Int {
    val value = read()
    if (value < 0) throw EOFException("SOCKS5 server closed the connection before the handshake completed.")
    return value
}

private fun java.io.InputStream.readSocks5ConnectResponseBytes(): ByteArray {
    val header = ByteArray(4)
    readFully(header)
    val addressLength = when (header[3].toInt() and 0xFF) {
        Socks5HandshakeTester.ADDRESS_TYPE_IPV4 -> 4
        Socks5HandshakeTester.ADDRESS_TYPE_DOMAIN -> readRequiredByte()
        Socks5HandshakeTester.ADDRESS_TYPE_IPV6 -> 16
        else -> throw EOFException("Invalid SOCKS5 address type in CONNECT response.")
    }
    val address = ByteArray(addressLength)
    readFully(address)
    val port = ByteArray(2)
    readFully(port)
    return if ((header[3].toInt() and 0xFF) == Socks5HandshakeTester.ADDRESS_TYPE_DOMAIN) header + byteArrayOf(addressLength.toByte()) + address + port else header + address + port
}

private fun java.io.InputStream.readFully(buffer: ByteArray) {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        if (count < 0) throw EOFException("SOCKS5 server closed the connection before the response completed.")
        offset += count
    }
}

enum class Socks5DiagnosticTestType {
    Handshake,
    Connect;

    companion object {
        fun fromWireName(value: String): Socks5DiagnosticTestType = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Handshake
    }
}

enum class Socks5DiagnosticState(val label: String) {
    HandshakeReachable("Handshake reachable"),
    AuthenticationRejected("Authentication rejected"),
    UnsupportedAuthMethod("Unsupported auth method"),
    ConnectSuccess("CONNECT success"),
    ConnectRejectedByProxy("CONNECT rejected by proxy"),
    TargetUnreachable("Target unreachable"),
    Timeout("Timeout"),
    InvalidSocks5Response("Invalid SOCKS5 response"),
    ValidationError("Validation error"),
}

data class Socks5DiagnosticResult(
    val testType: Socks5DiagnosticTestType,
    val state: Socks5DiagnosticState,
    val message: String,
    val elapsedMs: Long? = null,
) {
    val displayMessage: String
        get() = buildString {
            append(state.label)
            if (message.isNotBlank()) append(": ${message.sanitizeSocks5Diagnostic()}")
            elapsedMs?.let { append(" (${it} ms)") }
        }
}

fun validateSocks5ConnectTarget(targetHost: String, targetPort: Int): List<String> = buildList {
    val host = targetHost.trim()
    if (host.isBlank()) add("Target host must not be blank.")
    if (host.any { it.isISOControl() || it.isWhitespace() }) add("Target host must not contain whitespace or control characters.")
    if (host.encodeToByteArray().size > 255) add("Target host must be 255 bytes or shorter for SOCKS5 domain-name CONNECT.")
    if (targetPort !in 1..65535) add("Target port must be in range 1..65535.")
}

fun encodeSocks5ConnectRequest(targetHost: String, targetPort: Int): ByteArray {
    val host = targetHost.trim()
    val targetErrors = validateSocks5ConnectTarget(host, targetPort)
    require(targetErrors.isEmpty()) { targetErrors.joinToString(" ") }
    val portBytes = byteArrayOf(((targetPort ushr 8) and 0xFF).toByte(), (targetPort and 0xFF).toByte())
    val ipv4Parts = host.split('.').mapNotNull { part -> part.toIntOrNull()?.takeIf { it in 0..255 } }
    return if (ipv4Parts.size == 4 && host.count { it == '.' } == 3) {
        byteArrayOf(Socks5HandshakeTester.SOCKS_VERSION, Socks5HandshakeTester.CONNECT_COMMAND, Socks5HandshakeTester.RESERVED, Socks5HandshakeTester.ADDRESS_TYPE_IPV4.toByte()) +
            ipv4Parts.map { it.toByte() }.toByteArray() + portBytes
    } else {
        val hostBytes = host.encodeToByteArray()
        byteArrayOf(Socks5HandshakeTester.SOCKS_VERSION, Socks5HandshakeTester.CONNECT_COMMAND, Socks5HandshakeTester.RESERVED, Socks5HandshakeTester.ADDRESS_TYPE_DOMAIN.toByte(), hostBytes.size.toByte()) +
            hostBytes + portBytes
    }
}

fun parseSocks5ConnectResponse(response: ByteArray): Socks5DiagnosticResult {
    if (response.size < 7) return invalidConnectResponse("SOCKS5 CONNECT response is too short.")
    val version = response[0].toInt() and 0xFF
    val reply = response[1].toInt() and 0xFF
    val reserved = response[2].toInt() and 0xFF
    val addressType = response[3].toInt() and 0xFF
    if (version != Socks5HandshakeTester.SOCKS_VERSION.toInt()) return invalidConnectResponse("Invalid SOCKS version in CONNECT response.")
    if (reserved != 0) return invalidConnectResponse("Invalid SOCKS5 reserved field in CONNECT response.")
    val expectedSize = when (addressType) {
        Socks5HandshakeTester.ADDRESS_TYPE_IPV4 -> 10
        Socks5HandshakeTester.ADDRESS_TYPE_IPV6 -> 22
        Socks5HandshakeTester.ADDRESS_TYPE_DOMAIN -> {
            if (response.size < 5) return invalidConnectResponse("Missing SOCKS5 domain length in CONNECT response.")
            7 + (response[4].toInt() and 0xFF)
        }
        else -> return invalidConnectResponse("Invalid SOCKS5 address type in CONNECT response.")
    }
    if (response.size < expectedSize) return invalidConnectResponse("SOCKS5 CONNECT response ended before bound address and port.")
    val state = when (reply) {
        0x00 -> Socks5DiagnosticState.ConnectSuccess
        0x03, 0x04, 0x05, 0x06 -> Socks5DiagnosticState.TargetUnreachable
        in 0x01..0x08 -> Socks5DiagnosticState.ConnectRejectedByProxy
        else -> Socks5DiagnosticState.InvalidSocks5Response
    }
    val message = when (reply) {
        0x00 -> "Proxy opened the TCP CONNECT target; no application payload was sent."
        0x01 -> "General SOCKS5 server failure."
        0x02 -> "Connection is not allowed by proxy rules."
        0x03 -> "Network unreachable from proxy."
        0x04 -> "Host unreachable from proxy."
        0x05 -> "Connection refused by target from proxy."
        0x06 -> "TTL expired while proxy reached target."
        0x07 -> "CONNECT command is not supported by proxy."
        0x08 -> "Target address type is not supported by proxy."
        else -> "Unknown SOCKS5 reply code."
    }
    return Socks5DiagnosticResult(Socks5DiagnosticTestType.Connect, state, message)
}

private fun invalidConnectResponse(message: String) = Socks5DiagnosticResult(
    testType = Socks5DiagnosticTestType.Connect,
    state = Socks5DiagnosticState.InvalidSocks5Response,
    message = message,
)

private fun Socks5TestResult.toHandshakeDiagnostic(): Socks5DiagnosticResult = when (this) {
    Socks5TestResult.Reachable -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.HandshakeReachable, "SOCKS5 greeting/auth completed.")
    Socks5TestResult.Failed.AuthenticationRejected -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.AuthenticationRejected, Socks5TestResult.Failed.AuthenticationRejected.message)
    Socks5TestResult.Failed.UnsupportedMethod -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.UnsupportedAuthMethod, Socks5TestResult.Failed.UnsupportedMethod.message)
    Socks5TestResult.Failed.ConnectionTimeout -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.Timeout, Socks5TestResult.Failed.ConnectionTimeout.message)
    Socks5TestResult.Failed.ConnectionRefused -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.TargetUnreachable, Socks5TestResult.Failed.ConnectionRefused.message)
    is Socks5TestResult.Failed.InvalidResponse -> Socks5DiagnosticResult(Socks5DiagnosticTestType.Handshake, Socks5DiagnosticState.InvalidSocks5Response, this.message)
}

private fun Socks5DiagnosticResult.toLegacyResult(): Socks5TestResult = when (state) {
    Socks5DiagnosticState.HandshakeReachable -> Socks5TestResult.Reachable
    Socks5DiagnosticState.AuthenticationRejected -> Socks5TestResult.Failed.AuthenticationRejected
    Socks5DiagnosticState.UnsupportedAuthMethod -> Socks5TestResult.Failed.UnsupportedMethod
    Socks5DiagnosticState.Timeout -> Socks5TestResult.Failed.ConnectionTimeout
    Socks5DiagnosticState.TargetUnreachable -> Socks5TestResult.Failed.ConnectionRefused
    else -> Socks5TestResult.Failed.InvalidResponse(message)
}

sealed interface Socks5TestResult {
    data object Reachable : Socks5TestResult

    sealed interface Failed : Socks5TestResult {
        val message: String

        data object ConnectionTimeout : Failed { override val message = "connection timeout" }
        data object ConnectionRefused : Failed { override val message = "connection refused" }
        data object AuthenticationRejected : Failed { override val message = "authentication rejected" }
        data object UnsupportedMethod : Failed { override val message = "unsupported SOCKS5 method" }
        data class InvalidResponse(override val message: String = "invalid response") : Failed
    }
}

fun Socks5TestResult.toProfileStatus(): Socks5ProfileStatus = when (this) {
    Socks5TestResult.Reachable -> Socks5ProfileStatus.Reachable
    is Socks5TestResult.Failed -> Socks5ProfileStatus.Failed(message.sanitizeSocks5Diagnostic())
}

fun Socks5DiagnosticResult.toProfileStatus(): Socks5ProfileStatus = when (state) {
    Socks5DiagnosticState.HandshakeReachable,
    Socks5DiagnosticState.ConnectSuccess,
    -> Socks5ProfileStatus.Reachable
    else -> Socks5ProfileStatus.Failed(displayMessage.sanitizeSocks5Diagnostic())
}
