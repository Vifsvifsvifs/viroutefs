// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.vless

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

const val VLESS_RUNTIME_LIMITATION = "VLESS runtime forwarding is not implemented yet."
const val VLESS_ROUTE_PREVIEW_ONLY = "This profile can be used for route decision preview only."

private val supportedTransportTypes = setOf("tcp", "ws", "grpc")
private val supportedSecurityValues = VlessSecurityMode.entries.map { it.wireName }.toSet()

data class VlessProfileConfig(
    val name: String,
    val host: String,
    val port: Int,
    val uuid: String,
    val transportType: String? = null,
    val securityMode: VlessSecurityMode = VlessSecurityMode.NONE,
    val encryption: String? = null,
    val flow: String? = null,
    val sni: String? = null,
    val publicKey: String? = null,
    val shortId: String? = null,
    val fingerprint: String? = null,
    val path: String? = null,
    val hostHeader: String? = null,
    val enabled: Boolean = true,
    val status: VlessProfileStatus = VlessProfileStatus.NotTested,
) {
    fun safeSummary(): String = buildString {
        append(name.trim())
        append(" @ ")
        append(host.trim())
        append(':')
        append(port)
        append(if (enabled) " enabled" else " disabled")
        append(" security=")
        append(securityMode.wireName)
        if (!transportType.isNullOrBlank()) append(" transport=${transportType.trim().lowercase()}")
        if (!encryption.isNullOrBlank()) append(" encryption=provided")
        if (!flow.isNullOrBlank()) append(" flow=provided")
        if (!sni.isNullOrBlank()) append(" sni=provided")
        if (!publicKey.isNullOrBlank()) append(" publicKey=placeholder-provided")
        if (!shortId.isNullOrBlank()) append(" shortId=placeholder-provided")
        if (!fingerprint.isNullOrBlank()) append(" fingerprint=provided")
        if (!path.isNullOrBlank()) append(" path=provided")
        if (!hostHeader.isNullOrBlank()) append(" hostHeader=provided")
        append(" status=")
        append(status.safeLabel)
    }

    fun maskedUuid(): String = uuid.maskUuid()

    fun maskedPreview(): String = buildString {
        appendLine("Name: ${name.ifBlank { "Unnamed VLESS profile" }}")
        appendLine("Endpoint: ${host}:${port}")
        appendLine("UUID: ${maskedUuid()}")
        appendLine("Security: ${securityMode.wireName}")
        appendLine("Transport: ${transportType ?: "not specified"}")
        appendLine("Encryption: ${encryption ?: "not specified"}")
        if (!flow.isNullOrBlank()) appendLine("Flow: provided")
        if (!sni.isNullOrBlank()) appendLine("SNI: ${sni}")
        if (!fingerprint.isNullOrBlank()) appendLine("Fingerprint: ${fingerprint}")
        if (!publicKey.isNullOrBlank()) appendLine("Public key: provided")
        if (!shortId.isNullOrBlank()) appendLine("Short ID: provided")
        if (!path.isNullOrBlank()) appendLine("Path: ${path}")
        if (!hostHeader.isNullOrBlank()) appendLine("Host header: ${hostHeader}")
        append(VLESS_ROUTE_PREVIEW_ONLY)
    }
}

enum class VlessSecurityMode(val wireName: String, val label: String) {
    NONE("none", "none"),
    TLS("tls", "tls"),
    REALITY("reality", "reality placeholder"),
}

sealed interface VlessProfileStatus {
    val safeLabel: String

    data object NotTested : VlessProfileStatus {
        override val safeLabel: String = "Not tested"
    }

    data object Invalid : VlessProfileStatus {
        override val safeLabel: String = "Invalid"
    }

    data object ConfigReady : VlessProfileStatus {
        override val safeLabel: String = "Config ready"
    }
}

sealed interface VlessUriParseResult {
    data class Success(val profile: VlessProfileConfig) : VlessUriParseResult
    data class Error(val messages: List<String>) : VlessUriParseResult {
        constructor(message: String) : this(listOf(message))
    }
}

fun parseVlessUri(rawUri: String): VlessUriParseResult {
    val trimmed = rawUri.trim()
    if (trimmed.isBlank()) return VlessUriParseResult.Error("Paste a vless:// URI before importing.")
    if (!trimmed.startsWith("vless://", ignoreCase = true)) {
        return VlessUriParseResult.Error("Unsupported URI scheme. Expected vless://.")
    }

    val uri = runCatching { URI(trimmed) }.getOrElse {
        return VlessUriParseResult.Error("Malformed VLESS URI: ${it.message ?: "unable to parse URI"}.")
    }
    if (!uri.scheme.equals("vless", ignoreCase = true)) {
        return VlessUriParseResult.Error("Unsupported URI scheme. Expected vless://.")
    }

    val uuid = uri.userInfo?.trim().orEmpty()
    val host = uri.host?.trim().orEmpty()
    val port = uri.port
    val params = parseQuery(uri.rawQuery)
    val type = params["type"]?.trim()?.lowercase()
    val security = params["security"]?.trim()?.lowercase() ?: VlessSecurityMode.NONE.wireName
    val errors = buildList {
        if (uuid.isBlank()) add("VLESS URI must include a UUID before @.")
        if (!uuid.isValidUuid()) add("VLESS UUID must be a valid UUID.")
        if (host.isBlank()) add("VLESS URI must include a host after @.")
        if (port !in 1..65535) add("VLESS port must be in range 1..65535.")
        if (type != null && type !in supportedTransportTypes) add("Unsupported VLESS transport type '$type'. Supported placeholders: tcp, ws, grpc.")
        if (security !in supportedSecurityValues) add("Unsupported VLESS security '$security'. Supported values: none, tls, reality.")
    }
    if (errors.isNotEmpty()) return VlessUriParseResult.Error(errors)

    val profile = VlessProfileConfig(
        name = uri.rawFragment?.percentDecode()?.trim()?.takeIf { it.isNotBlank() } ?: host,
        host = host,
        port = port,
        uuid = UUID.fromString(uuid).toString(),
        transportType = type,
        securityMode = security.toVlessSecurityMode(),
        encryption = params["encryption"]?.trimToNull(),
        flow = params["flow"]?.trimToNull(),
        sni = params["sni"]?.trimToNull(),
        publicKey = params["pbk"]?.trimToNull(),
        shortId = params["sid"]?.trimToNull(),
        fingerprint = params["fp"]?.trimToNull(),
        path = params["path"]?.trimToNull(),
        hostHeader = params["host"]?.trimToNull(),
        status = VlessProfileStatus.ConfigReady,
    )
    val validationErrors = validateVlessProfile(profile)
    return if (validationErrors.isEmpty()) VlessUriParseResult.Success(profile) else VlessUriParseResult.Error(validationErrors)
}

fun exportVlessUri(profile: VlessProfileConfig): String {
    val errors = validateVlessProfile(profile)
    require(errors.isEmpty()) { errors.joinToString("\n") }
    val query = buildList {
        profile.transportType?.trimToNull()?.let { add("type" to it.lowercase()) }
        if (profile.securityMode != VlessSecurityMode.NONE) add("security" to profile.securityMode.wireName)
        profile.encryption?.trimToNull()?.let { add("encryption" to it) }
        profile.flow?.trimToNull()?.let { add("flow" to it) }
        profile.sni?.trimToNull()?.let { add("sni" to it) }
        profile.fingerprint?.trimToNull()?.let { add("fp" to it) }
        profile.publicKey?.trimToNull()?.let { add("pbk" to it) }
        profile.shortId?.trimToNull()?.let { add("sid" to it) }
        profile.path?.trimToNull()?.let { add("path" to it) }
        profile.hostHeader?.trimToNull()?.let { add("host" to it) }
    }.joinToString("&") { (key, value) -> "${key.percentEncode()}=${value.percentEncode()}" }
    val fragment = profile.name.trimToNull()?.percentEncode()?.let { "#$it" }.orEmpty()
    return buildString {
        append("vless://")
        append(UUID.fromString(profile.uuid.trim()).toString())
        append('@')
        append(profile.host.trim())
        append(':')
        append(profile.port)
        if (query.isNotBlank()) append("?$query")
        append(fragment)
    }
}

fun validateVlessProfile(candidate: VlessProfileConfig): List<String> = buildList {
    if (candidate.name.trim().isBlank()) add("VLESS profile name must not be blank.")
    if (candidate.host.trim().isBlank()) add("VLESS host must not be blank.")
    if (candidate.port !in 1..65535) add("VLESS port must be in range 1..65535.")
    if (!candidate.uuid.isValidUuid()) add("VLESS UUID must be a valid UUID.")
    candidate.transportType?.trimToNull()?.let {
        if (it.lowercase() !in supportedTransportTypes) add("VLESS transport type must be tcp, ws, or grpc when provided.")
    }
}

fun String.isValidUuid(): Boolean = runCatching { UUID.fromString(trim()) }
    .map { it.toString().equals(trim(), ignoreCase = true) }
    .getOrDefault(false)

private fun parseQuery(rawQuery: String?): Map<String, String> {
    if (rawQuery.isNullOrBlank()) return emptyMap()
    return rawQuery.split('&')
        .filter { it.isNotBlank() }
        .map { part ->
            val key = part.substringBefore('=').percentDecode().lowercase()
            val value = part.substringAfter('=', "").percentDecode()
            key to value
        }
        .toMap()
}

private fun String.toVlessSecurityMode(): VlessSecurityMode = VlessSecurityMode.entries.firstOrNull {
    it.wireName.equals(this, ignoreCase = true) || it.name.equals(this, ignoreCase = true)
} ?: VlessSecurityMode.NONE

private fun String.trimToNull(): String? = trim().takeIf { it.isNotBlank() }

private fun String.maskUuid(): String {
    val canonical = runCatching { UUID.fromString(trim()).toString() }.getOrElse { return "invalid UUID" }
    return "${canonical.take(8)}-…-${canonical.takeLast(4)}"
}

private fun String.percentDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())

private fun String.percentEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")
