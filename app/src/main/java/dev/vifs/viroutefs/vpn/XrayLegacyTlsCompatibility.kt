// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vpn

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.json.JSONArray
import org.json.JSONObject

internal data class XrayTlsPinTarget(
    val address: String,
    val port: Int,
    val serverName: String,
) {
    init {
        require(address.isNotBlank()) { "The legacy Xray TLS endpoint has no address." }
        require(port in 1..65535) { "The legacy Xray TLS endpoint has an invalid port." }
        require(serverName.isNotBlank()) { "The legacy Xray TLS endpoint has no server name." }
    }

    val key: String = "$address\u0000$port\u0000$serverName"
}

internal data class XrayTlsCompatibilityResult(
    val json: String,
    val migratedPins: Int,
)

/**
 * Converts v2rayNG-era `allowInsecure: true` settings to a persistent TOFU
 * certificate pin before the pinned Xray 26 runtime sees the configuration.
 *
 * Xray 26.6.1 rejects the legacy field. Keeping a certificate pin is also
 * safer than silently dropping certificate checks on every connection.
 */
internal fun normalizeLegacyXrayTlsConfig(
    configJson: String,
    resolvePin: (XrayTlsPinTarget) -> String,
): XrayTlsCompatibilityResult {
    val root = JSONObject(configJson)
    var migratedPins = 0
    val outbounds = root.optJSONArray("outbounds") ?: JSONArray()
    for (index in 0 until outbounds.length()) {
        val outbound = outbounds.optJSONObject(index) ?: continue
        val vnext = outbound.optJSONObject("settings")
            ?.optJSONArray("vnext")
            ?.optJSONObject(0)
        val fallback = vnext?.let { endpoint ->
            endpoint.toPinTargetOrNull(serverName = endpoint.optString("address"))
        }
        val stream = outbound.optJSONObject("streamSettings") ?: continue
        migratedPins += normalizeTlsTree(stream, fallback, resolvePin)
    }
    return XrayTlsCompatibilityResult(root.toString(2), migratedPins)
}

private fun normalizeTlsTree(
    value: Any?,
    inheritedTarget: XrayTlsPinTarget?,
    resolvePin: (XrayTlsPinTarget) -> String,
): Int = when (value) {
    is JSONObject -> {
        val target = value.toPinTargetOrNull() ?: inheritedTarget
        var migrated = 0
        val keys = value.keys().asSequence().toList()
        keys.forEach { key ->
            val child = value.opt(key)
            if (key == "tlsSettings" && child is JSONObject) {
                migrated += normalizeTlsSettings(child, target, resolvePin)
            }
            migrated += normalizeTlsTree(child, target, resolvePin)
        }
        migrated
    }
    is JSONArray -> {
        var migrated = 0
        for (index in 0 until value.length()) {
            migrated += normalizeTlsTree(value.opt(index), inheritedTarget, resolvePin)
        }
        migrated
    }
    else -> 0
}

private fun normalizeTlsSettings(
    tls: JSONObject,
    inheritedTarget: XrayTlsPinTarget?,
    resolvePin: (XrayTlsPinTarget) -> String,
): Int {
    when (val verifyByName = tls.opt("verifyPeerCertByName")) {
        is Boolean -> {
            val serverName = tls.optString("serverName").trim()
            if (verifyByName && serverName.isNotBlank()) {
                tls.put("verifyPeerCertByName", serverName)
            } else {
                tls.remove("verifyPeerCertByName")
            }
        }
    }
    if (!tls.has("allowInsecure")) return 0
    val allowedInsecure = tls.optBoolean("allowInsecure", false)
    tls.remove("allowInsecure")
    if (!allowedInsecure) return 0
    if (tls.optString("pinnedPeerCertSha256").isNotBlank()) return 0

    val fallback = requireNotNull(inheritedTarget) {
        "The imported XHTTP profile uses legacy allowInsecure TLS without a pin, " +
            "but its certificate endpoint could not be determined."
    }
    val target = XrayTlsPinTarget(
        address = fallback.address,
        port = fallback.port,
        serverName = tls.optString("serverName").takeIf(String::isNotBlank) ?: fallback.serverName,
    )
    val pin = resolvePin(target).trim()
    require(pin.isNotBlank()) { "The Xray TLS certificate pin is empty." }
    require(pin.length <= MAX_PIN_LENGTH) { "The Xray TLS certificate pin is invalid." }
    tls.put("pinnedPeerCertSha256", pin)
    return 1
}

private fun JSONObject.toPinTargetOrNull(serverName: String? = null): XrayTlsPinTarget? {
    val address = optString("address")
        .takeIf(String::isNotBlank)
        ?: optString("server").takeIf(String::isNotBlank)
        ?: return null
    val port = when {
        optInt("port") in 1..65535 -> optInt("port")
        optInt("server_port") in 1..65535 -> optInt("server_port")
        else -> return null
    }
    return XrayTlsPinTarget(address, port, serverName?.takeIf(String::isNotBlank) ?: address)
}

internal class XrayCertificatePinStore(
    private val file: File,
) {
    @Synchronized
    fun getOrResolve(
        target: XrayTlsPinTarget,
        resolver: (XrayTlsPinTarget) -> String,
    ): String {
        val pins = load()
        if (pins.has(target.key)) {
            val storedPin = pins.optString(target.key).trim()
            require(storedPin.isNotBlank() && storedPin.length <= MAX_PIN_LENGTH) {
                "The stored Xray TLS certificate pin is invalid; refusing to trust a new certificate."
            }
            return storedPin
        }
        val pin = resolver(target).trim()
        require(pin.isNotBlank()) { "The remote Xray TLS certificate did not provide a usable SHA-256 pin." }
        require(pin.length <= MAX_PIN_LENGTH) { "The remote Xray TLS certificate pin is invalid." }
        pins.put(target.key, pin)
        file.parentFile?.let { directory ->
            check(directory.exists() || directory.mkdirs()) {
                "Could not create the private Xray certificate pin directory."
            }
        }
        val stagedFile = File(file.parentFile, "${file.name}.tmp")
        stagedFile.writeText(pins.toString(2), Charsets.UTF_8)
        stagedFile.setReadable(false, false)
        stagedFile.setWritable(false, false)
        check(stagedFile.setReadable(true, true) && stagedFile.setWritable(true, true)) {
            "Could not restrict the private Xray certificate pin store."
        }
        runCatching {
            Files.move(
                stagedFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(
                stagedFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        return pin
    }

    private fun load(): JSONObject = if (!file.isFile) {
        JSONObject()
    } else {
        runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrElse { cause ->
            throw IllegalStateException(
                "The private Xray certificate pin store is corrupted; refusing to trust a new certificate.",
                cause,
            )
        }
    }
}

internal fun fetchPeerCertificateSha256(
    target: XrayTlsPinTarget,
    timeoutMillis: Int = TLS_CONNECT_TIMEOUT_MS,
): String {
    val trustManager = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) = Unit
    }
    val context = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
    }
    Socket().use { rawSocket ->
        rawSocket.connect(InetSocketAddress(target.address, target.port), timeoutMillis)
        rawSocket.soTimeout = timeoutMillis
        val sslSocket = context.socketFactory.createSocket(
            rawSocket,
            target.address,
            target.port,
            true,
        ) as SSLSocket
        sslSocket.use { socket ->
            socket.soTimeout = timeoutMillis
            runCatching {
                socket.sslParameters = socket.sslParameters.apply {
                    serverNames = listOf(SNIHostName(target.serverName))
                }
            }
            socket.startHandshake()
            val certificate = socket.session.peerCertificates.firstOrNull() as? X509Certificate
                ?: error("The remote Xray TLS endpoint returned no X.509 certificate.")
            return MessageDigest.getInstance("SHA-256")
                .digest(certificate.encoded)
                .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xFF) }
        }
    }
}

private const val MAX_PIN_LENGTH = 256
private const val TLS_CONNECT_TIMEOUT_MS = 8_000
