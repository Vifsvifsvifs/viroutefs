// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class VpnGateCatalogSnapshot(
    val servers: List<VpnGateServer>,
    val fetchedAtEpochMillis: Long,
    val fromCache: Boolean,
)

class VpnGateCatalogClient(context: Context) {
    private val cacheFile = File(context.applicationContext.cacheDir, VPN_GATE_CACHE_FILE)

    fun loadCached(): VpnGateCatalogSnapshot? = runCatching {
        if (!cacheFile.isFile || cacheFile.length() !in 1..MAX_VPN_GATE_HTTP_BYTES.toLong()) return@runCatching null
        VpnGateCatalogSnapshot(
            servers = parseVpnGateCatalog(cacheFile.readText(Charsets.UTF_8)),
            fetchedAtEpochMillis = cacheFile.lastModified().coerceAtLeast(0L),
            fromCache = true,
        )
    }.getOrNull()

    fun fetch(): VpnGateCatalogSnapshot {
        val connection = (URL(VPN_GATE_CATALOG_URL).openConnection() as HttpsURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Accept", "text/plain, text/csv")
            setRequestProperty("User-Agent", "ViRouteFS Android VPNGate catalog")
        }
        try {
            val status = connection.responseCode
            require(status == HttpsURLConnection.HTTP_OK) { "VPNGate ответил с кодом HTTP $status." }
            val declaredLength = connection.contentLengthLong
            require(declaredLength < 0 || declaredLength <= MAX_VPN_GATE_HTTP_BYTES) {
                "Ответ VPNGate превышает безопасный размер."
            }
            val source = connection.inputStream.use { it.readUtf8Bounded(MAX_VPN_GATE_HTTP_BYTES) }
            val servers = parseVpnGateCatalog(source)
            saveCache(source)
            return VpnGateCatalogSnapshot(
                servers = servers,
                fetchedAtEpochMillis = cacheFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                fromCache = false,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun saveCache(source: String) {
        val temporary = File.createTempFile("vpngate-catalog-", ".tmp", cacheFile.parentFile)
        try {
            temporary.writeText(source, Charsets.UTF_8)
            require(temporary.length() in 1..MAX_VPN_GATE_HTTP_BYTES.toLong()) {
                "Каталог VPNGate не сохранён из-за некорректного размера."
            }
            if (cacheFile.exists() && !cacheFile.delete()) error("Не удалось обновить локальный кэш VPNGate.")
            if (!temporary.renameTo(cacheFile)) error("Не удалось сохранить локальный кэш VPNGate.")
            cacheFile.setLastModified(System.currentTimeMillis())
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}

private fun InputStream.readUtf8Bounded(maxBytes: Int): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "Ответ VPNGate превышает безопасный размер." }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val VPN_GATE_CACHE_FILE = "vpngate-openvpn-catalog.csv"
private const val MAX_VPN_GATE_HTTP_BYTES = 4 * 1024 * 1024
