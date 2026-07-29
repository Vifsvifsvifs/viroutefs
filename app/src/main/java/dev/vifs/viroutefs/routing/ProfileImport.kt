// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessSecurityMode
import dev.vifs.viroutefs.vless.VlessUriParseResult
import dev.vifs.viroutefs.vless.parseVlessUri
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

enum class ImportDuplicateResolution {
    Skip,
    Replace,
    Copy,
}

data class ImportedProfileCandidate(
    val profile: TunnelProfile,
    val maskedPreview: String,
    val fingerprint: String,
    val warnings: List<String> = emptyList(),
)

data class ProfileImportPreview(
    val candidates: List<ImportedProfileCandidate>,
    val warnings: List<String>,
) {
    val isEmpty: Boolean
        get() = candidates.isEmpty()
}

data class ProfileImportApplyResult(
    val config: RoutingConfig,
    val added: Int,
    val replaced: Int,
    val skipped: Int,
)

fun previewProfileImport(source: String): ProfileImportPreview {
    val normalized = source.trim()
    require(normalized.isNotBlank()) { "Вставьте ссылку, JSON или содержимое файла профиля." }
    val warnings = mutableListOf<String>()
    val profiles = when {
        normalized.looksLikeOpenVpn() -> listOf(importOpenVpnCandidate(normalized))
        normalized.startsWith("{") || normalized.startsWith("[") -> importJsonCandidates(normalized, warnings)
        else -> normalized.lineSequence()
            .flatMap { line -> line.trim().split(Regex("\\s+(?=[a-zA-Z][a-zA-Z0-9+.-]*://)")).asSequence() }
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { value ->
                runCatching { importUriCandidate(value) }
                    .onFailure { warnings += it.message ?: "Не удалось прочитать строку импорта." }
                    .getOrNull()
            }
            .toList()
    }
    val unique = profiles.distinctBy(ImportedProfileCandidate::fingerprint)
    if (unique.size < profiles.size) warnings += "Одинаковые профили внутри импорта объединены."
    return ProfileImportPreview(
        candidates = unique,
        warnings = warnings.distinct(),
    )
}

fun applyProfileImport(
    config: RoutingConfig,
    preview: ProfileImportPreview,
    duplicateResolution: ImportDuplicateResolution,
): ProfileImportApplyResult {
    val mutable = config.profiles.toMutableList()
    var added = 0
    var replaced = 0
    var skipped = 0

    preview.candidates.forEach { candidate ->
        val existingIndex = mutable.indexOfFirst { existing ->
            profileFingerprint(existing) == candidate.fingerprint
        }
        if (existingIndex < 0) {
            mutable += candidate.profile
            added += 1
        } else {
            when (duplicateResolution) {
                ImportDuplicateResolution.Skip -> skipped += 1
                ImportDuplicateResolution.Replace -> {
                    val existing = mutable[existingIndex]
                    mutable[existingIndex] = candidate.profile.copy(id = existing.id)
                    replaced += 1
                }
                ImportDuplicateResolution.Copy -> {
                    mutable += candidate.profile.copy(
                        id = "profile_${UUID.randomUUID()}",
                        name = "${candidate.profile.name} (копия)",
                    )
                    added += 1
                }
            }
        }
    }
    return ProfileImportApplyResult(
        config = config.copy(profiles = mutable),
        added = added,
        replaced = replaced,
        skipped = skipped,
    )
}

fun profileFingerprint(profile: TunnelProfile): String {
    val material = buildString {
        append(profile.type.name)
        append('|')
        when {
            profile.vless != null -> append(
                listOf(
                    profile.vless.host,
                    profile.vless.port,
                    profile.vless.uuid,
                    profile.vless.securityMode.wireName,
                    profile.vless.transportType.orEmpty(),
                    profile.vless.flow.orEmpty(),
                    profile.vless.sni.orEmpty(),
                    profile.vless.publicKey.orEmpty(),
                    profile.vless.shortId.orEmpty(),
                    profile.vless.path.orEmpty(),
                    profile.vless.hostHeader.orEmpty(),
                    profile.vless.serviceName.orEmpty(),
                    profile.vless.xhttpMode.orEmpty(),
                    profile.vless.xhttpExtra.orEmpty(),
                ).joinToString("|"),
            )
            profile.socks5 != null -> append(
                listOf(
                    profile.socks5.host,
                    profile.socks5.port,
                    profile.socks5.username.orEmpty(),
                    profile.socks5.password.orEmpty(),
                ).joinToString("|"),
            )
            profile.singBox != null -> append(canonicalJson(profile.singBox.optionsJson))
            else -> append(profile.name)
        }
    }
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

private fun importUriCandidate(value: String): ImportedProfileCandidate {
    val scheme = value.substringBefore("://", "").lowercase(Locale.ROOT)
    return when (scheme) {
        "vless" -> importVlessCandidate(value)
        "vmess" -> importVmessCandidate(value)
        "trojan" -> importStandardProxyUri(value, TunnelType.Trojan, "trojan", "password")
        "ss" -> importShadowsocksCandidate(value)
        "hysteria2", "hy2" -> importStandardProxyUri(value, TunnelType.Hysteria2, "hysteria2", "password")
        "tuic" -> importTuicCandidate(value)
        "socks", "socks5" -> importSocks5Candidate(value)
        "http" -> importStandardProxyUri(value, TunnelType.HttpProxy, "http", "password", tls = false)
        "https" -> importStandardProxyUri(value, TunnelType.HttpsProxy, "http", "password", tls = true)
        else -> error("Формат «${scheme.ifBlank { "без схемы" }}» пока не распознан.")
    }
}

private fun importVlessCandidate(value: String): ImportedProfileCandidate {
    val parsed = parseVlessUri(value)
    val vless = when (parsed) {
        is VlessUriParseResult.Success -> parsed.profile.copy(enabled = false)
        is VlessUriParseResult.Error -> error(parsed.messages.joinToString(" "))
    }
    val profile = TunnelProfile(
        id = "profile_${UUID.randomUUID()}",
        name = vless.name,
        type = if (vless.transportType.equals("xhttp", true)) {
            TunnelType.XrayVlessReality
        } else {
            TunnelType.VLESS
        },
        description = "${vless.host}:${vless.port} • VLESS ${vless.transportType ?: "tcp"} ${vless.securityMode.wireName}",
        enabled = false,
        mockOnly = false,
        vless = vless,
    )
    return candidate(profile, vless.maskedPreview())
}

private fun importVmessCandidate(value: String): ImportedProfileCandidate {
    val root = JSONObject(decodeBase64(value.substringAfter("://")))
    val server = root.optString("add").ifBlank { root.optString("server") }
    val port = root.optString("port").toIntOrNull() ?: root.optInt("server_port", -1)
    val uuid = root.optString("id").ifBlank { root.optString("uuid") }
    require(server.isNotBlank() && port in 1..65535 && uuid.isNotBlank()) {
        "VMess URI не содержит корректные server, port и id."
    }
    val outbound = JSONObject()
        .put("type", "vmess")
        .put("server", server)
        .put("server_port", port)
        .put("uuid", uuid)
        .put("security", root.optString("scy", root.optString("security", "auto")))
    val tlsEnabled = root.optString("tls").equals("tls", true)
    if (tlsEnabled) {
        outbound.put(
            "tls",
            JSONObject()
                .put("enabled", true)
                .put("server_name", root.optString("sni", server)),
        )
    }
    root.optString("net").takeIf { it == "ws" || it == "grpc" }?.let { network ->
        outbound.put(
            "transport",
            JSONObject().put("type", network).apply {
                if (network == "ws") put("path", root.optString("path", "/"))
                if (network == "grpc") put("service_name", root.optString("path"))
            },
        )
    }
    return advancedCandidate(
        TunnelType.VMess,
        root.optString("ps").ifBlank { server },
        outbound,
    )
}

private fun importShadowsocksCandidate(value: String): ImportedProfileCandidate {
    val raw = value.substringAfter("://").substringBefore('#')
    val decodedWhole = if ('@' !in raw) decodeBase64(raw) else raw
    val authority = decodedWhole.substringAfterLast('@')
    val credentialsRaw = decodedWhole.substringBeforeLast('@', "")
    val credentials = if (credentialsRaw.isNotBlank()) {
        runCatching { decodeBase64(credentialsRaw) }.getOrDefault(credentialsRaw)
    } else {
        error("Shadowsocks URI не содержит method:password.")
    }
    val method = credentials.substringBefore(':')
    val password = credentials.substringAfter(':', "")
    val endpoint = parseHostPort(authority)
    require(method.isNotBlank() && password.isNotBlank()) { "Shadowsocks URI не содержит method или password." }
    val outbound = JSONObject()
        .put("type", "shadowsocks")
        .put("server", endpoint.first)
        .put("server_port", endpoint.second)
        .put("method", percentDecode(method))
        .put("password", percentDecode(password))
    val name = value.substringAfter('#', endpoint.first).let(::percentDecode)
    return advancedCandidate(TunnelType.Shadowsocks, name, outbound)
}

private fun importStandardProxyUri(
    value: String,
    type: TunnelType,
    engineType: String,
    secretField: String,
    tls: Boolean = true,
): ImportedProfileCandidate {
    val uri = URI(value)
    val host = uri.host ?: error("В URI отсутствует адрес сервера.")
    val port = uri.port.takeIf { it in 1..65535 } ?: if (tls) 443 else 8080
    val userInfo = uri.rawUserInfo.orEmpty().let(::percentDecode)
    val secret = userInfo.substringAfter(':', userInfo)
    require(secret.isNotBlank()) { "В URI отсутствуют данные аутентификации." }
    val params = parseQuery(uri.rawQuery)
    val outbound = JSONObject()
        .put("type", engineType)
        .put("server", host)
        .put("server_port", port)
        .put(secretField, secret)
    userInfo.substringBefore(':', "").takeIf { it.isNotBlank() && engineType == "http" }?.let {
        outbound.put("username", it)
    }
    if (tls) {
        outbound.put(
            "tls",
            JSONObject()
                .put("enabled", true)
                .put("server_name", params["sni"] ?: host),
        )
    }
    return advancedCandidate(
        type,
        uri.rawFragment?.let(::percentDecode)?.takeIf(String::isNotBlank) ?: host,
        outbound,
    )
}

private fun importTuicCandidate(value: String): ImportedProfileCandidate {
    val uri = URI(value)
    val host = uri.host ?: error("TUIC URI не содержит адрес сервера.")
    val port = uri.port.takeIf { it in 1..65535 } ?: 443
    val userInfo = uri.rawUserInfo.orEmpty().let(::percentDecode)
    val uuid = userInfo.substringBefore(':')
    val password = userInfo.substringAfter(':', "")
    require(uuid.isNotBlank() && password.isNotBlank()) { "TUIC URI должен содержать UUID и пароль." }
    val params = parseQuery(uri.rawQuery)
    val outbound = JSONObject()
        .put("type", "tuic")
        .put("server", host)
        .put("server_port", port)
        .put("uuid", uuid)
        .put("password", password)
        .put("congestion_control", params["congestion_control"] ?: "bbr")
        .put(
            "tls",
            JSONObject()
                .put("enabled", true)
                .put("server_name", params["sni"] ?: host),
        )
    return advancedCandidate(
        TunnelType.Tuic,
        uri.rawFragment?.let(::percentDecode)?.takeIf(String::isNotBlank) ?: host,
        outbound,
    )
}

private fun importSocks5Candidate(value: String): ImportedProfileCandidate {
    val uri = URI(value.replaceFirst("socks://", "socks5://"))
    val host = uri.host ?: error("SOCKS5 URI не содержит адрес сервера.")
    val port = uri.port.takeIf { it in 1..65535 } ?: 1080
    val userInfo = uri.rawUserInfo.orEmpty().let(::percentDecode)
    val username = userInfo.substringBefore(':', "").takeIf(String::isNotBlank)
    val password = userInfo.substringAfter(':', "").takeIf(String::isNotBlank)
    val socks = Socks5ProfileConfig(
        name = uri.rawFragment?.let(::percentDecode)?.takeIf(String::isNotBlank) ?: host,
        host = host,
        port = port,
        username = username,
        password = password,
        enabled = false,
    )
    val profile = TunnelProfile(
        id = "profile_${UUID.randomUUID()}",
        name = socks.name,
        type = TunnelType.Socks5,
        description = "$host:$port • SOCKS5",
        enabled = false,
        mockOnly = false,
        socks5 = socks,
    )
    return candidate(
        profile,
        "${socks.name}\nSOCKS5 $host:$port\nПользователь: ${username ?: "не указан"}\nПароль: ${if (password == null) "не указан" else "<скрыт>"}",
    )
}

private fun importJsonCandidates(
    source: String,
    warnings: MutableList<String>,
): List<ImportedProfileCandidate> {
    val trimmed = source.trim()
    if (trimmed.startsWith("[")) {
        val array = JSONArray(trimmed)
        return (0 until array.length()).map { importSingBoxObject(array.getJSONObject(it)) }
    }
    val root = JSONObject(trimmed)
    if (root.isXrayConfiguration()) {
        warnings += analyzeXrayConfiguration(root)
        return emptyList()
    }
    val objects = buildList {
        root.optJSONArray("outbounds")?.let { array ->
            for (index in 0 until array.length()) add(array.getJSONObject(index))
        }
        root.optJSONArray("endpoints")?.let { array ->
            for (index in 0 until array.length()) add(array.getJSONObject(index))
        }
        if (isEmpty()) add(root)
    }
    if (objects.size > 1) warnings += "Из полной конфигурации взяты только поддерживаемые outbounds/endpoints; глобальные route и dns не импортируются."
    return objects.mapNotNull { objectValue ->
        runCatching { importSingBoxObject(objectValue) }
            .onFailure { warnings += it.message ?: "Объект профиля не распознан." }
            .getOrNull()
    }
}

private fun JSONObject.isXrayConfiguration(): Boolean {
    val values = optJSONArray("outbounds") ?: return false
    return (0 until values.length()).any { index ->
        values.optJSONObject(index)?.optString("protocol").orEmpty().isNotBlank()
    }
}

private fun analyzeXrayConfiguration(root: JSONObject): List<String> {
    val outbounds = root.optJSONArray("outbounds") ?: return emptyList()
    val protocols = mutableSetOf<String>()
    val transports = mutableSetOf<String>()
    for (index in 0 until outbounds.length()) {
        val outbound = outbounds.optJSONObject(index) ?: continue
        outbound.optString("protocol")
            .takeIf(String::isNotBlank)
            ?.lowercase(Locale.ROOT)
            ?.let(protocols::add)
        outbound.optJSONObject("streamSettings")
            ?.optString("network")
            ?.takeIf(String::isNotBlank)
            ?.lowercase(Locale.ROOT)
            ?.let(transports::add)
    }
    return buildList {
        add(
            "Распознана полная конфигурация Xray/v2rayNG" +
                protocols.takeIf(Set<String>::isNotEmpty)?.joinToString(
                    prefix = " (протоколы: ",
                    postfix = ")",
                ).orEmpty() +
                ". Глобальные DNS и routing не переносятся автоматически.",
        )
        if ("xhttp" in transports || "splithttp" in transports) {
            add(
                "Профиль использует XHTTP. ViRouteFS запускает такие профили через отдельный локальный Xray-core, но полная конфигурация не переносится автоматически: экспортируйте отдельный vless:// URI, чтобы не смешать чужие глобальные DNS и routing.",
            )
        } else {
            add(
                "Для безопасного переноса отдельного совместимого профиля используйте его URI/QR; импорт полной Xray-конфигурации в текущий sing-box runtime не выполняется.",
            )
        }
    }
}

private fun importSingBoxObject(source: JSONObject): ImportedProfileCandidate {
    val root = JSONObject(source.toString()).apply { remove("tag") }
    val engineType = root.optString("type").lowercase(Locale.ROOT)
    if (engineType == "vless") return importVlessObject(root, source.optString("tag"))
    val type = when (engineType) {
        "vmess" -> TunnelType.VMess
        "trojan" -> TunnelType.Trojan
        "shadowsocks" -> TunnelType.Shadowsocks
        "hysteria" -> TunnelType.Hysteria
        "hysteria2" -> TunnelType.Hysteria2
        "snell" -> TunnelType.Snell
        "tuic" -> TunnelType.Tuic
        "anytls" -> TunnelType.AnyTls
        "shadowtls" -> TunnelType.ShadowTls
        "http" -> if (root.optJSONObject("tls")?.optBoolean("enabled") == true) TunnelType.HttpsProxy else TunnelType.HttpProxy
        "ssh" -> TunnelType.SshTunnel
        "wireguard" -> TunnelType.WireGuard
        "openvpn-client" -> TunnelType.OpenVpn
        "openconnect" -> TunnelType.OpenConnectAnyConnect
        "tailscale" -> if (root.optString("control_url").isBlank()) TunnelType.TailscaleCompatible else TunnelType.HeadscaleCompatible
        else -> error("Тип sing-box «$engineType» не поддерживается импортом.")
    }
    val schema = requireNotNull(singBoxProtocolSchema(type))
    val config = SingBoxProfileConfig(schema.kind, root.toString())
    val errors = validateSingBoxProfile(type, config)
    require(errors.isEmpty()) { errors.joinToString(" ") }
    val name = source.optString("tag").ifBlank {
        root.optString("server").ifBlank { type.label }
    }
    return advancedCandidate(type, name, root)
}

private fun importVlessObject(root: JSONObject, sourceTag: String): ImportedProfileCandidate {
    val tls = root.optJSONObject("tls")
    val reality = tls?.optJSONObject("reality")
    val transport = root.optJSONObject("transport")
    val profile = VlessProfileConfig(
        name = sourceTag.ifBlank { root.optString("server", "VLESS") },
        host = root.getString("server"),
        port = root.getInt("server_port"),
        uuid = root.getString("uuid"),
        transportType = transport?.optString("type")?.takeIf(String::isNotBlank),
        securityMode = when {
            reality?.optBoolean("enabled") == true -> VlessSecurityMode.REALITY
            tls?.optBoolean("enabled") == true -> VlessSecurityMode.TLS
            else -> VlessSecurityMode.NONE
        },
        flow = root.optString("flow").takeIf(String::isNotBlank),
        sni = tls?.optString("server_name")?.takeIf(String::isNotBlank),
        publicKey = reality?.optString("public_key")?.takeIf(String::isNotBlank),
        shortId = reality?.optString("short_id")?.takeIf(String::isNotBlank),
        path = transport?.optString("path")?.takeIf(String::isNotBlank),
        serviceName = transport?.optString("service_name")?.takeIf(String::isNotBlank),
        xhttpMode = transport?.optString("mode")?.takeIf(String::isNotBlank),
        xhttpExtra = transport?.opt("extra")?.let { value ->
            if (value == JSONObject.NULL) null else value.toString()
        },
        enabled = false,
    )
    val tunnel = TunnelProfile(
        id = "profile_${UUID.randomUUID()}",
        name = profile.name,
        type = if (profile.transportType.equals("xhttp", true)) {
            TunnelType.XrayVlessReality
        } else {
            TunnelType.VLESS
        },
        description = "${profile.host}:${profile.port} • VLESS ${profile.securityMode.wireName}",
        enabled = false,
        mockOnly = false,
        vless = profile,
    )
    return candidate(tunnel, profile.maskedPreview())
}

private fun importOpenVpnCandidate(source: String): ImportedProfileCandidate {
    val imported = importOpenVpnProfile(source)
    return advancedCandidate(
        TunnelType.OpenVpn,
        "OpenVPN ${JSONObject(imported.optionsJson).optString("server", "profile")}",
        JSONObject(imported.optionsJson),
        imported.warnings,
    )
}

private fun advancedCandidate(
    type: TunnelType,
    name: String,
    options: JSONObject,
    warnings: List<String> = emptyList(),
): ImportedProfileCandidate {
    val schema = requireNotNull(singBoxProtocolSchema(type)) { "${type.label} нельзя импортировать как sing-box профиль." }
    val config = SingBoxProfileConfig(schema.kind, options.toString())
    val errors = validateSingBoxProfile(type, config)
    require(errors.isEmpty()) { errors.joinToString(" ") }
    val profile = TunnelProfile(
        id = "profile_${UUID.randomUUID()}",
        name = name.ifBlank { type.label },
        type = type,
        description = "${options.optString("server").ifBlank { "Локальный профиль" }} • ${type.label}",
        enabled = false,
        mockOnly = false,
        singBox = config,
    )
    return candidate(
        profile,
        "${profile.name}\n${type.label}\n${redactSensitiveJson(options.toString())}",
        warnings,
    )
}

private fun candidate(
    profile: TunnelProfile,
    preview: String,
    warnings: List<String> = emptyList(),
): ImportedProfileCandidate = ImportedProfileCandidate(
    profile = profile,
    maskedPreview = preview,
    fingerprint = profileFingerprint(profile),
    warnings = warnings,
)

private fun String.looksLikeOpenVpn(): Boolean =
    lineSequence().any { it.trim().startsWith("remote ") } &&
        lineSequence().any { it.trim().equals("client", true) }

private fun parseHostPort(value: String): Pair<String, Int> {
    val uri = URI("ss://$value")
    val host = uri.host ?: error("URI не содержит адрес сервера.")
    val port = uri.port.takeIf { it in 1..65535 } ?: error("URI не содержит корректный порт.")
    return host to port
}

private fun parseQuery(raw: String?): Map<String, String> = raw
    .orEmpty()
    .split('&')
    .filter(String::isNotBlank)
    .associate { item ->
        percentDecode(item.substringBefore('=')).lowercase(Locale.ROOT) to
            percentDecode(item.substringAfter('=', ""))
    }

private fun percentDecode(value: String): String =
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())

private fun decodeBase64(value: String): String {
    val normalized = value.trim().substringBefore('?').substringBefore('#')
    val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
    val decoded = runCatching { Base64.getUrlDecoder().decode(padded) }
        .recoverCatching { Base64.getDecoder().decode(padded) }
        .getOrElse { error("Некорректная Base64-строка.") }
    return decoded.toString(StandardCharsets.UTF_8)
}

private fun canonicalJson(source: String): String =
    runCatching { JSONObject(source).toString() }.getOrDefault(source.trim())
