// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.socks5

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.EOFException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class Socks5HandshakeTester(
    private val socketFactory: () -> Socket = { Socket() },
) {
    suspend fun test(profile: Socks5ProfileConfig, timeoutMillis: Int = DEFAULT_TIMEOUT_MILLIS): Socks5TestResult = withContext(Dispatchers.IO) {
        val validationErrors = validateSocks5Profile(profile)
        if (validationErrors.isNotEmpty()) return@withContext Socks5TestResult.Failed.InvalidResponse(validationErrors.joinToString(" "))

        runCatching {
            socketFactory().use { socket ->
                socket.soTimeout = timeoutMillis
                socket.connect(InetSocketAddress(profile.host.trim(), profile.port), timeoutMillis)
                performGreeting(socket, profile)
            }
        }.getOrElse { error ->
            when (error) {
                is SocketTimeoutException -> Socks5TestResult.Failed.ConnectionTimeout
                is ConnectException -> Socks5TestResult.Failed.ConnectionRefused
                else -> Socks5TestResult.Failed.InvalidResponse(error.message?.sanitizeSocks5Diagnostic() ?: "SOCKS5 handshake failed.")
            }
        }
    }

    private fun performGreeting(socket: Socket, profile: Socks5ProfileConfig): Socks5TestResult {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()
        val method = if (profile.credentialsProvided) USERNAME_PASSWORD_METHOD else NO_AUTH_METHOD
        output.write(byteArrayOf(SOCKS_VERSION, 1, method))
        output.flush()

        val version = input.readRequiredByte()
        val selectedMethod = input.readRequiredByte()
        if (version != SOCKS_VERSION.toInt()) return Socks5TestResult.Failed.InvalidResponse("Invalid SOCKS version in greeting response.")
        if (selectedMethod == NO_ACCEPTABLE_METHOD.toInt()) return Socks5TestResult.Failed.UnsupportedMethod
        if (selectedMethod != method.toInt()) return Socks5TestResult.Failed.UnsupportedMethod
        if (selectedMethod == NO_AUTH_METHOD.toInt()) return Socks5TestResult.Reachable

        val username = profile.username.orEmpty().encodeToByteArray()
        val password = profile.password.orEmpty().encodeToByteArray()
        if (username.size > 255 || password.size > 255) return Socks5TestResult.Failed.InvalidResponse("SOCKS5 username/password fields must be 255 bytes or shorter.")

        output.write(byteArrayOf(AUTH_VERSION, username.size.toByte()))
        output.write(username)
        output.write(password.size)
        output.write(password)
        output.flush()

        val authVersion = input.readRequiredByte()
        val authStatus = input.readRequiredByte()
        if (authVersion != AUTH_VERSION.toInt()) return Socks5TestResult.Failed.InvalidResponse("Invalid SOCKS5 authentication response.")
        return if (authStatus == 0) Socks5TestResult.Reachable else Socks5TestResult.Failed.AuthenticationRejected
    }

    private fun java.io.InputStream.readRequiredByte(): Int {
        val value = read()
        if (value < 0) throw EOFException("SOCKS5 server closed the connection before the handshake completed.")
        return value
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 5_000
        const val SOCKS_VERSION: Byte = 0x05
        const val NO_AUTH_METHOD: Byte = 0x00
        const val USERNAME_PASSWORD_METHOD: Byte = 0x02
        const val NO_ACCEPTABLE_METHOD: Byte = 0xFF.toByte()
        const val AUTH_VERSION: Byte = 0x01
    }
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
