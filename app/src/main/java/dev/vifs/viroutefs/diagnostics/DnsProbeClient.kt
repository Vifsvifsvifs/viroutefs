package dev.vifs.viroutefs.diagnostics

import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class DnsProbeClient(
    timeoutMs: Long,
) {
    private val timeout = timeoutMs.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()

    fun query(endpoint: DnsEndpoint, query: ByteArray): ByteArray = when (endpoint.transport) {
        DnsTransport.UDP -> queryUdp(endpoint, query)
        DnsTransport.TCP -> queryTcp(endpoint, query, tls = false)
        DnsTransport.TLS -> queryTcp(endpoint, query, tls = true)
        DnsTransport.HTTPS -> queryHttps(endpoint, query)
        DnsTransport.SYSTEM -> error("System DNS is handled by Android's resolver.")
    }

    private fun queryUdp(endpoint: DnsEndpoint, query: ByteArray): ByteArray {
        val target = endpoint.socketAddress()
        return DatagramSocket().use { socket ->
            socket.soTimeout = timeout
            socket.connect(target)
            socket.send(DatagramPacket(query, query.size))
            val buffer = ByteArray(MAX_DNS_MESSAGE_SIZE)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            response.data.copyOfRange(response.offset, response.offset + response.length)
        }
    }

    private fun queryTcp(endpoint: DnsEndpoint, query: ByteArray, tls: Boolean): ByteArray {
        val socket = if (tls) createTlsSocket(endpoint) else Socket()
        return socket.use {
            if (!it.isConnected) it.connect(endpoint.socketAddress(), timeout)
            it.soTimeout = timeout
            if (it is SSLSocket) it.startHandshake()
            val output = DataOutputStream(it.getOutputStream())
            output.writeShort(query.size)
            output.write(query)
            output.flush()
            val input = DataInputStream(it.getInputStream())
            val responseSize = input.readUnsignedShort()
            if (responseSize !in 1..MAX_DNS_MESSAGE_SIZE) throw IOException("DNS response has an invalid size: $responseSize bytes.")
            ByteArray(responseSize).also(input::readFully)
        }
    }

    private fun createTlsSocket(endpoint: DnsEndpoint): SSLSocket {
        val socket = SSLSocketFactory.getDefault().createSocket() as SSLSocket
        socket.connect(endpoint.socketAddress(), timeout)
        socket.soTimeout = timeout
        val host = requireNotNull(endpoint.host)
        socket.sslParameters = socket.sslParameters.apply {
            endpointIdentificationAlgorithm = "HTTPS"
            if (!host.contains(':') && host.any(Char::isLetter)) {
                serverNames = listOf(SNIHostName(host))
            }
        }
        return socket
    }

    private fun queryHttps(endpoint: DnsEndpoint, query: ByteArray): ByteArray {
        val connection = URL(requireNotNull(endpoint.httpsUrl)).openConnection() as HttpsURLConnection
        return try {
            connection.connectTimeout = timeout
            connection.readTimeout = timeout
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", DNS_MESSAGE_CONTENT_TYPE)
            connection.setRequestProperty("Content-Type", DNS_MESSAGE_CONTENT_TYPE)
            connection.setFixedLengthStreamingMode(query.size)
            connection.outputStream.use { it.write(query) }

            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = connection.errorStream?.use { it.readLimited(256) }?.toString(Charsets.UTF_8).orEmpty()
                throw IOException("DoH server returned HTTP $status${detail.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}")
            }
            val contentType = connection.contentType.orEmpty().substringBefore(';').trim()
            if (!contentType.equals(DNS_MESSAGE_CONTENT_TYPE, ignoreCase = true)) {
                throw IOException("DoH server returned unexpected Content-Type: ${contentType.ifBlank { "not specified" }}")
            }
            connection.inputStream.use { it.readLimited(MAX_DNS_MESSAGE_SIZE) }
        } finally {
            connection.disconnect()
        }
    }

    private fun DnsEndpoint.socketAddress(): InetSocketAddress =
        InetSocketAddress(requireNotNull(host), requireNotNull(port)).also {
            if (it.isUnresolved) throw java.net.UnknownHostException("Не удалось найти адрес DNS-сервера ${host.orEmpty()}.")
        }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4_096)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > limit) throw IOException("DNS response is larger than $limit bytes.")
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_DNS_MESSAGE_SIZE = 65_535
        const val DNS_MESSAGE_CONTENT_TYPE = "application/dns-message"
    }
}
