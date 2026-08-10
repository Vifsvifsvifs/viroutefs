// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import android.content.Context
import dev.vifs.viroutefs.engine.EngineRuntimeContext
import dev.vifs.viroutefs.vpn.XrayEngineAdapter
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.net.ssl.HttpsURLConnection

data class VpnGateCatalogSnapshot(
    val servers: List<VpnGateServer>,
    val fetchedAtEpochMillis: Long,
    val fromCache: Boolean,
    val transportProfileName: String? = null,
)

class VpnGateCatalogClient(context: Context) {
    private val applicationContext = context.applicationContext
    private val cacheFile = File(applicationContext.cacheDir, VPN_GATE_CACHE_FILE)

    fun loadCached(): VpnGateCatalogSnapshot? = runCatching {
        if (!cacheFile.isFile || cacheFile.length() !in 1..MAX_VPN_GATE_HTTP_BYTES.toLong()) return@runCatching null
        VpnGateCatalogSnapshot(
            servers = parseVpnGateCatalog(cacheFile.readText(Charsets.UTF_8)),
            fetchedAtEpochMillis = cacheFile.lastModified().coerceAtLeast(0L),
            fromCache = true,
        )
    }.getOrNull()

    fun fetch(config: RoutingConfig? = null): VpnGateCatalogSnapshot {
        val profile = config?.preferredVpnGateTransportProfile()
        if (profile != null) {
            val proxied = runCatching { fetchThroughXray(config, profile) }
            if (proxied.isSuccess) return proxied.getOrThrow()
            return runCatching { fetchCatalog(proxy = null, transportProfileName = null) }
                .getOrElse { directError ->
                    val proxyError = proxied.exceptionOrNull()
                    error(
                        "VPNGate недоступен и через профиль «${profile.name}», и напрямую. " +
                            "Через профиль: ${proxyError?.localizedMessage ?: "ошибка"}. " +
                            "Напрямую: ${directError.localizedMessage ?: "ошибка сети"}.",
                    )
                }
        }
        return fetchCatalog(proxy = null, transportProfileName = null)
    }

    private fun fetchThroughXray(
        config: RoutingConfig,
        profile: TunnelProfile,
    ): VpnGateCatalogSnapshot {
        val adapter = XrayEngineAdapter(applicationContext)
        val runtimeContext = EngineRuntimeContext()
        return try {
            val compiled = adapter.compile(config, setOf(profile.id)).getOrThrow()
            adapter.start(compiled, runtimeContext).getOrThrow()
            val endpoint = requireNotNull(
                runtimeContext.profileEndpoint(XrayEngineAdapter.ID, profile.id),
            ) { "Локальный прокси выбранного профиля не запустился." }
            fetchCatalog(
                proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress(endpoint.host, endpoint.port)),
                transportProfileName = profile.name,
            )
        } finally {
            runCatching { adapter.cleanup() }
        }
    }

    private fun fetchCatalog(
        proxy: Proxy?,
        transportProfileName: String?,
    ): VpnGateCatalogSnapshot {
        val url = URL(VPN_GATE_CATALOG_URL)
        val connection = ((if (proxy == null) url.openConnection() else url.openConnection(proxy)) as HttpsURLConnection).apply {
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
            val source = connection.inputStream.use {
                it.readUtf8Bounded(MAX_VPN_GATE_HTTP_BYTES, MAX_VPN_GATE_DOWNLOAD_MILLIS)
            }
            val servers = parseVpnGateCatalog(source)
            saveCache(source)
            return VpnGateCatalogSnapshot(
                servers = servers,
                fetchedAtEpochMillis = cacheFile.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                fromCache = false,
                transportProfileName = transportProfileName,
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

private fun RoutingConfig.preferredVpnGateTransportProfile(): TunnelProfile? {
    val preferredIds = buildList {
        defaultProfileId?.let(::add)
        profileGroups.firstOrNull { it.id == defaultProfileId }?.let { group ->
            group.selectedProfileId?.let(::add)
            addAll(group.memberProfileIds)
        }
    }
    return (preferredIds.mapNotNull { id -> profiles.firstOrNull { it.id == id } } + profiles)
        .distinctBy(TunnelProfile::id)
        .firstOrNull { profile ->
            profile.enabled && profile.type == TunnelType.XrayVlessReality && profile.vless != null
        }
}

private fun InputStream.readUtf8Bounded(maxBytes: Int, maxDurationMillis: Long): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maxDurationMillis)
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        require(System.nanoTime() <= deadline) { "Загрузка VPNGate превысила общий лимит времени." }
        total += read
        require(total <= maxBytes) { "Ответ VPNGate превышает безопасный размер." }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val VPN_GATE_CACHE_FILE = "vpngate-openvpn-catalog.csv"
private const val MAX_VPN_GATE_HTTP_BYTES = 4 * 1024 * 1024
private const val MAX_VPN_GATE_DOWNLOAD_MILLIS = 45_000L
