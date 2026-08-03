// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64
import org.json.JSONObject

const val VPN_GATE_CATALOG_URL = "https://www.vpngate.net/api/iphone/"

data class VpnGateServer(
    val hostName: String,
    val ipAddress: String,
    val score: Long,
    val pingMillis: Int?,
    val speedBitsPerSecond: Long,
    val countryName: String,
    val countryCode: String,
    val activeSessions: Int,
    val uptimeMillis: Long,
    val totalUsers: Long,
    val logType: String,
    val operator: String,
    val message: String,
    val openVpnConfigBase64: String,
) {
    val stableKey: String
        get() = "$hostName|$ipAddress"
}

fun parseVpnGateCatalog(source: String, maxServers: Int = MAX_VPN_GATE_SERVERS): List<VpnGateServer> {
    require(source.toByteArray(StandardCharsets.UTF_8).size <= MAX_VPN_GATE_CATALOG_BYTES) {
        "Каталог VPNGate превышает безопасный размер."
    }
    require(maxServers in 1..MAX_VPN_GATE_SERVERS) { "Некорректный лимит серверов VPNGate." }

    var indexes: Map<String, Int>? = null
    val servers = mutableListOf<VpnGateServer>()
    source.replace("\r\n", "\n").replace('\r', '\n').lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        if (line.isBlank() || line == "*vpn_servers" || line == "*") return@forEach
        if (line.startsWith("#HostName,")) {
            indexes = parseCsvRow(line.removePrefix("#"))
                .mapIndexed { index, name -> name.trim() to index }
                .toMap()
            return@forEach
        }
        val header = indexes ?: return@forEach
        if (servers.size >= maxServers) return@forEach
        val fields = parseCsvRow(line)
        fun field(name: String): String = header[name]
            ?.takeIf { it in fields.indices }
            ?.let(fields::get)
            .orEmpty()
            .trim()

        val hostName = field("HostName").take(MAX_VPN_GATE_TEXT_LENGTH)
        val ipAddress = field("IP").take(MAX_VPN_GATE_TEXT_LENGTH)
        val encodedConfig = field("OpenVPN_ConfigData_Base64")
        if (hostName.isBlank() || ipAddress.isBlank() || encodedConfig.isBlank()) return@forEach
        if (encodedConfig.length > MAX_VPN_GATE_CONFIG_BASE64_CHARS) return@forEach

        servers += VpnGateServer(
            hostName = hostName,
            ipAddress = ipAddress,
            score = field("Score").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            pingMillis = field("Ping").toIntOrNull()?.takeIf { it >= 0 },
            speedBitsPerSecond = field("Speed").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            countryName = field("CountryLong").take(MAX_VPN_GATE_TEXT_LENGTH),
            countryCode = field("CountryShort").uppercase().take(3),
            activeSessions = field("NumVpnSessions").toIntOrNull()?.coerceAtLeast(0) ?: 0,
            uptimeMillis = field("Uptime").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            totalUsers = field("TotalUsers").toLongOrNull()?.coerceAtLeast(0L) ?: 0L,
            logType = field("LogType").take(MAX_VPN_GATE_TEXT_LENGTH),
            operator = field("Operator").take(MAX_VPN_GATE_LONG_TEXT_LENGTH),
            message = field("Message").take(MAX_VPN_GATE_LONG_TEXT_LENGTH),
            openVpnConfigBase64 = encodedConfig,
        )
    }
    require(indexes != null) { "VPNGate вернул каталог без заголовка." }
    require(servers.isNotEmpty()) { "VPNGate не вернул доступных OpenVPN-серверов." }
    return servers.distinctBy(VpnGateServer::stableKey)
}

fun decodeVpnGateOpenVpnConfig(server: VpnGateServer): String {
    require(server.openVpnConfigBase64.length <= MAX_VPN_GATE_CONFIG_BASE64_CHARS) {
        "Профиль VPNGate превышает безопасный размер."
    }
    val decoded = runCatching { Base64.getDecoder().decode(server.openVpnConfigBase64) }
        .getOrElse { error("VPNGate вернул повреждённый OpenVPN-профиль.") }
    require(decoded.size <= MAX_VPN_GATE_CONFIG_BYTES) { "OpenVPN-профиль VPNGate слишком большой." }
    val decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    val source = runCatching { decoder.decode(ByteBuffer.wrap(decoded)).toString() }
        .getOrElse { error("OpenVPN-профиль VPNGate содержит некорректный текст.") }
    require('\u0000' !in source) { "OpenVPN-профиль VPNGate содержит недопустимые данные." }
    return source
}

/**
 * Converts the public VPNGate .ovpn file through the same conservative importer
 * as a local file. Unknown directives and external commands are never executed.
 */
fun previewVpnGateProfile(server: VpnGateServer): ProfileImportPreview {
    val source = decodeVpnGateOpenVpnConfig(server)
    val preview = previewProfileImport(source)
    val original = preview.candidates.singleOrNull()
        ?: error("VPNGate должен содержать ровно один OpenVPN-профиль.")
    require(original.profile.type == TunnelType.OpenVpn) { "VPNGate вернул не OpenVPN-профиль." }

    val options = JSONObject(requireNotNull(original.profile.singBox).optionsJson)
    val usesPublicCredentials = source.lineSequence().any { line ->
        line.trim().substringBefore(' ').equals("auth-user-pass", ignoreCase = true)
    }
    if (usesPublicCredentials) {
        options.put("username", "vpn").put("password", "vpn")
    }
    val country = server.countryName.ifBlank { server.countryCode.ifBlank { "Unknown country" } }
    val name = "VPNGate • $country • ${server.hostName}".take(96)
    val profile = original.profile.copy(
        name = name,
        description = "$country • ${server.hostName} • добровольческий OpenVPN-сервер VPNGate",
        enabled = false,
        mockOnly = false,
        platformNotes = buildString {
            append("Публичный добровольческий сервер VPNGate. Доступность и политика оператора могут меняться.")
            if (usesPublicCredentials) append(" Учётные данные vpn/vpn являются общедоступными.")
        },
        singBox = original.profile.singBox.copy(optionsJson = options.toString(2)),
    )
    val candidate = original.copy(
        profile = profile,
        fingerprint = profileFingerprint(profile),
        warnings = (original.warnings + VPN_GATE_VOLUNTEER_WARNING).distinct(),
    )
    return ProfileImportPreview(
        candidates = listOf(candidate),
        warnings = (preview.warnings + VPN_GATE_VOLUNTEER_WARNING).distinct(),
    )
}

private fun parseCsvRow(line: String): List<String> {
    val values = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var index = 0
    while (index < line.length) {
        val char = line[index]
        when {
            char == '"' && quoted && index + 1 < line.length && line[index + 1] == '"' -> {
                current.append('"')
                index += 1
            }
            char == '"' -> quoted = !quoted
            char == ',' && !quoted -> {
                values += current.toString()
                current.clear()
            }
            else -> current.append(char)
        }
        index += 1
    }
    require(!quoted) { "VPNGate вернул повреждённую CSV-строку." }
    values += current.toString()
    return values
}

const val VPN_GATE_VOLUNTEER_WARNING =
    "VPNGate использует добровольческие сторонние серверы: не передавайте через них чувствительные данные без дополнительного сквозного шифрования."

private const val MAX_VPN_GATE_SERVERS = 512
private const val MAX_VPN_GATE_CATALOG_BYTES = 4 * 1024 * 1024
private const val MAX_VPN_GATE_CONFIG_BYTES = 256 * 1024
private const val MAX_VPN_GATE_CONFIG_BASE64_CHARS = 512 * 1024
private const val MAX_VPN_GATE_TEXT_LENGTH = 160
private const val MAX_VPN_GATE_LONG_TEXT_LENGTH = 512
