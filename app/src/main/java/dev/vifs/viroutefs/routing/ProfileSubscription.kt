// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.routing

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings

enum class SubscriptionProfileChangeKind {
    Added,
    Updated,
    Unchanged,
}

data class SubscriptionProfileChange(
    val kind: SubscriptionProfileChangeKind,
    val profile: TunnelProfile,
    val entryKey: String,
    val maskedPreview: String,
    val warnings: List<String>,
)

data class ProfileSubscriptionUpdatePreview(
    val subscription: ProfileSubscription,
    val changes: List<SubscriptionProfileChange>,
    val removedProfiles: List<TunnelProfile>,
    val warnings: List<String>,
    val fetchedAtEpochMs: Long,
) {
    val addedCount: Int
        get() = changes.count { it.kind == SubscriptionProfileChangeKind.Added }
    val updatedCount: Int
        get() = changes.count { it.kind == SubscriptionProfileChangeKind.Updated }
    val unchangedCount: Int
        get() = changes.count { it.kind == SubscriptionProfileChangeKind.Unchanged }
}

fun previewProfileSubscriptionImport(rawBody: String): ProfileImportPreview {
    val source = decodeSubscriptionBody(rawBody)
    val rawPreview = if (looksLikeClashYaml(source)) {
        previewClashSubscription(source)
    } else {
        previewProfileImport(source)
    }
    val preview = sanitizeSubscriptionPreview(rawPreview)
    require(preview.candidates.isNotEmpty()) {
        preview.warnings.joinToString(" ").ifBlank {
            "В подписке не найдено поддерживаемых профилей."
        }
    }
    require(preview.candidates.size <= MAX_SUBSCRIPTION_PROFILES) {
        "В подписке больше $MAX_SUBSCRIPTION_PROFILES профилей. Разделите её на несколько списков."
    }
    return preview
}

fun previewProfileSubscriptionUpdate(
    config: RoutingConfig,
    subscription: ProfileSubscription,
    imported: ProfileImportPreview,
    fetchedAtEpochMs: Long,
): ProfileSubscriptionUpdatePreview {
    require(subscription.id.isNotBlank() && subscription.name.isNotBlank()) {
        "У подписки должны быть имя и идентификатор."
    }
    validateSubscriptionUrlSyntax(subscription.url)?.let { error(it) }
    require(imported.candidates.isNotEmpty()) { "Подписка не содержит поддерживаемых профилей." }
    require(imported.candidates.size <= MAX_SUBSCRIPTION_PROFILES) {
        "В подписке больше $MAX_SUBSCRIPTION_PROFILES профилей."
    }

    val existing = config.profiles
        .filter { it.sourceSubscriptionId == subscription.id && !it.sourceEntryKey.isNullOrBlank() }
        .associateBy { requireNotNull(it.sourceEntryKey) }
    val occurrenceByBase = mutableMapOf<String, Int>()
    val changes = imported.candidates.map { candidate ->
        val baseKey = subscriptionEntryBaseKey(candidate.profile)
        val occurrence = occurrenceByBase.getOrDefault(baseKey, 0)
        occurrenceByBase[baseKey] = occurrence + 1
        val entryKey = "$baseKey:$occurrence"
        val previous = existing[entryKey]
        val enabled = previous?.enabled ?: false
        val incoming = candidate.profile.copy(
            id = previous?.id ?: candidate.profile.id,
            enabled = enabled,
            dnsPolicyId = previous?.dnsPolicyId ?: candidate.profile.dnsPolicyId,
            platformNotes = previous?.platformNotes
                ?.lineSequence()
                ?.filterNot { it.startsWith(SUBSCRIPTION_REMOVED_NOTE_PREFIX) }
                ?.joinToString("\n")
                ?.takeIf(String::isNotBlank)
                ?: candidate.profile.platformNotes,
            socks5 = candidate.profile.socks5?.copy(enabled = enabled),
            vless = candidate.profile.vless?.copy(enabled = enabled),
            sourceSubscriptionId = subscription.id,
            sourceEntryKey = entryKey,
            appRoutingMode = previous?.appRoutingMode ?: candidate.profile.appRoutingMode,
            appRoutingPackages = previous?.appRoutingPackages ?: candidate.profile.appRoutingPackages,
            appRoutingNetworks = previous?.appRoutingNetworks ?: candidate.profile.appRoutingNetworks,
        )
        val kind = when {
            previous == null -> SubscriptionProfileChangeKind.Added
            profileFingerprint(previous) == profileFingerprint(incoming) ->
                SubscriptionProfileChangeKind.Unchanged
            else -> SubscriptionProfileChangeKind.Updated
        }
        SubscriptionProfileChange(
            kind = kind,
            profile = incoming,
            entryKey = entryKey,
            maskedPreview = candidate.maskedPreview,
            warnings = candidate.warnings,
        )
    }
    val currentKeys = changes.mapTo(hashSetOf(), SubscriptionProfileChange::entryKey)
    val removed = existing
        .filterKeys { it !in currentKeys }
        .values
        .sortedBy(TunnelProfile::name)
    return ProfileSubscriptionUpdatePreview(
        subscription = subscription.copy(
            lastUpdatedAtEpochMs = fetchedAtEpochMs,
            lastProfileCount = changes.size,
        ),
        changes = changes,
        removedProfiles = removed,
        warnings = imported.warnings,
        fetchedAtEpochMs = fetchedAtEpochMs,
    )
}

fun applyProfileSubscriptionUpdate(
    config: RoutingConfig,
    preview: ProfileSubscriptionUpdatePreview,
): RoutingConfig {
    val subscriptionId = preview.subscription.id
    val appliedSubscription = config.subscriptions
        .firstOrNull { it.id == subscriptionId }
        ?.let { current -> preview.subscription.copy(enabled = current.enabled) }
        ?: preview.subscription
    val replacementsById = preview.changes.associateBy { it.profile.id }
    val removedIds = preview.removedProfiles.mapTo(hashSetOf(), TunnelProfile::id)
    val nextProfiles = config.profiles.map { profile ->
        replacementsById[profile.id]?.profile ?: if (profile.id in removedIds) {
            profile.copy(
                enabled = false,
                socks5 = profile.socks5?.copy(enabled = false),
                vless = profile.vless?.copy(enabled = false),
                platformNotes = appendSubscriptionNote(
                    profile.platformNotes,
                    "Профиль больше не входит в подписку «${preview.subscription.name}» и оставлен выключенным, чтобы не сломать пользовательские маршруты.",
                ),
            )
        } else {
            profile
        }
    }.toMutableList()
    val knownIds = nextProfiles.mapTo(hashSetOf(), TunnelProfile::id)
    preview.changes
        .map(SubscriptionProfileChange::profile)
        .filter { it.id !in knownIds }
        .forEach(nextProfiles::add)
    val nextSubscriptions = config.subscriptions
        .filterNot { it.id == subscriptionId } + appliedSubscription
    return config.copy(
        version = CURRENT_ROUTING_CONFIG_VERSION,
        profiles = nextProfiles,
        subscriptions = nextSubscriptions,
    )
}

fun RoutingConfig.withSubscriptionEnabled(
    subscriptionId: String,
    enabled: Boolean,
): RoutingConfig = copy(
    subscriptions = subscriptions.map { subscription ->
        if (subscription.id == subscriptionId) subscription.copy(enabled = enabled) else subscription
    },
)

fun RoutingConfig.withoutSubscription(subscriptionId: String): RoutingConfig = copy(
    subscriptions = subscriptions.filterNot { it.id == subscriptionId },
    profiles = profiles.map { profile ->
        if (profile.sourceSubscriptionId == subscriptionId) {
            profile.copy(
                enabled = false,
                socks5 = profile.socks5?.copy(enabled = false),
                vless = profile.vless?.copy(enabled = false),
                sourceSubscriptionId = null,
                sourceEntryKey = null,
                platformNotes = appendSubscriptionNote(
                    profile.platformNotes,
                    "Подписка удалена; профиль сохранён выключенным, чтобы пользовательские маршруты не потеряли цель.",
                ),
            )
        } else {
            profile
        }
    },
)

internal fun decodeSubscriptionBody(rawBody: String): String {
    val trimmed = rawBody.removePrefix("\uFEFF").trim()
    require(trimmed.isNotBlank()) { "Сервер вернул пустую подписку." }
    if (looksLikeDirectSubscription(trimmed)) return trimmed

    val compact = trimmed.filterNot(Char::isWhitespace)
    val candidates = buildList {
        runCatching { Base64.getDecoder().decode(padBase64(compact)) }.getOrNull()?.let(::add)
        runCatching { Base64.getUrlDecoder().decode(padBase64(compact)) }.getOrNull()?.let(::add)
        runCatching { Base64.getMimeDecoder().decode(trimmed) }.getOrNull()?.let(::add)
    }
    val decoded = candidates.asSequence()
        .filter { it.size <= MAX_SUBSCRIPTION_BYTES }
        .mapNotNull(::decodeUtf8OrNull)
        .map(String::trim)
        .firstOrNull(::looksLikeDirectSubscription)
    return decoded ?: error("Формат подписки не распознан: ожидаются URI, Base64, sing-box JSON или Clash YAML.")
}

private fun looksLikeDirectSubscription(value: String): Boolean =
    value.startsWith("{") ||
        value.startsWith("[") ||
        "://" in value ||
        value.lineSequence().any { it.trimStart().startsWith("proxies:") }

private fun looksLikeClashYaml(value: String): Boolean =
    !value.startsWith("{") &&
        !value.startsWith("[") &&
        value.lineSequence().any { it.trimStart().startsWith("proxies:") }

private fun previewClashSubscription(source: String): ProfileImportPreview {
    require(source.lineSequence().all { line -> line.takeWhile(Char::isWhitespace).length <= 48 }) {
        "Clash YAML имеет слишком глубокую структуру."
    }
    val settings = LoadSettings.builder()
        .setLabel("ViRouteFS Clash subscription")
        .setAllowDuplicateKeys(false)
        .setAllowRecursiveKeys(false)
        .setAllowNonScalarKeys(false)
        .setMaxAliasesForCollections(10)
        .setCodePointLimit(MAX_SUBSCRIPTION_BYTES)
        .build()
    val root = runCatching { Load(settings).loadFromString(source) as? Map<*, *> }
        .getOrElse { error("Не удалось прочитать Clash YAML: структура повреждена или небезопасна.") }
        ?: error("Clash YAML должен содержать объект верхнего уровня.")
    val proxies = root["proxies"] as? List<*>
        ?: error("В Clash YAML отсутствует список proxies.")
    require(proxies.size <= MAX_SUBSCRIPTION_PROFILES) {
        "В подписке больше $MAX_SUBSCRIPTION_PROFILES профилей."
    }
    val warnings = mutableListOf<String>()
    val outbounds = JSONArray()
    proxies.forEachIndexed { index, rawProxy ->
        val proxy = rawProxy as? Map<*, *> ?: run {
            warnings += "Запись Clash №${index + 1} пропущена: ожидался объект."
            return@forEachIndexed
        }
        runCatching { clashProxyToSingBox(proxy, warnings) }
            .onSuccess(outbounds::put)
            .onFailure {
                val name = proxy.string("name") ?: "№${index + 1}"
                warnings += "Профиль Clash «$name» пропущен: ${it.message.orEmpty()}"
            }
    }
    require(outbounds.length() > 0) {
        warnings.joinToString(" ").ifBlank { "Clash YAML не содержит поддерживаемых профилей." }
    }
    val imported = previewProfileImport(outbounds.toString())
    return imported.copy(warnings = (warnings + imported.warnings).distinct())
}

private fun clashProxyToSingBox(
    proxy: Map<*, *>,
    warnings: MutableList<String>,
): JSONObject {
    val name = proxy.string("name")?.takeIf(String::isNotBlank)
        ?: error("не указано имя.")
    val type = proxy.string("type")?.lowercase(Locale.ROOT)
        ?: error("не указан type.")
    val server = proxy.string("server")?.takeIf(String::isNotBlank)
        ?: error("не указан server.")
    val port = proxy.int("port")?.takeIf { it in 1..65535 }
        ?: error("порт отсутствует или некорректен.")
    val outbound = JSONObject()
        .put("server", server)
        .put("server_port", port)
    when (type) {
        "ss" -> {
            require(proxy.string("plugin").isNullOrBlank()) {
                "Shadowsocks plugin «${proxy.string("plugin")}» нельзя безопасно перенести автоматически."
            }
            outbound
                .put("type", "shadowsocks")
                .put("method", proxy.requiredString("cipher"))
                .put("password", proxy.requiredString("password"))
        }
        "vmess" -> {
            outbound
                .put("type", "vmess")
                .put("uuid", proxy.requiredString("uuid"))
                .put("security", proxy.string("cipher") ?: "auto")
            proxy.int("alterId")?.takeIf { it > 0 }?.let {
                warnings += "VMess «$name» использует устаревший alterId=$it; проверьте подключение вручную."
                outbound.put("alter_id", it)
            }
            applyClashTransport(proxy, outbound)
            applyClashTls(proxy, outbound, defaultEnabled = proxy.boolean("tls") == true)
        }
        "vless" -> {
            outbound
                .put("type", "vless")
                .put("uuid", proxy.requiredString("uuid"))
            proxy.string("flow")?.takeIf(String::isNotBlank)?.let { outbound.put("flow", it) }
            applyClashTransport(proxy, outbound)
            applyClashTls(
                proxy,
                outbound,
                defaultEnabled = proxy.boolean("tls") == true || proxy["reality-opts"] is Map<*, *>,
            )
        }
        "trojan" -> {
            outbound
                .put("type", "trojan")
                .put("password", proxy.requiredString("password"))
            applyClashTransport(proxy, outbound)
            applyClashTls(proxy, outbound, defaultEnabled = true)
        }
        "hysteria2", "hy2" -> {
            val password = proxy.string("password")
                ?: proxy.string("auth")
                ?: error("не указано поле password/auth.")
            outbound
                .put("type", "hysteria2")
                .put("password", password)
            proxy.string("obfs")?.takeIf(String::isNotBlank)?.let { obfuscation ->
                outbound.put(
                    "obfs",
                    JSONObject()
                        .put("type", obfuscation)
                        .put("password", proxy.string("obfs-password") ?: ""),
                )
            }
            applyClashTls(proxy, outbound, defaultEnabled = true)
        }
        "tuic" -> {
            outbound
                .put("type", "tuic")
                .put("uuid", proxy.requiredString("uuid"))
                .put("password", proxy.requiredString("password"))
                .put("congestion_control", proxy.string("congestion-controller") ?: "bbr")
            applyClashTls(proxy, outbound, defaultEnabled = true)
        }
        "http" -> {
            outbound.put("type", "http")
            proxy.string("username")?.let { outbound.put("username", it) }
            proxy.string("password")?.let { outbound.put("password", it) }
            applyClashTls(proxy, outbound, defaultEnabled = proxy.boolean("tls") == true)
        }
        else -> error("тип $type пока не поддерживается безопасным импортом.")
    }
    return JSONObject()
        .put("tag", name)
        .apply {
            outbound.keys().forEach { key -> put(key, outbound.get(key)) }
        }
}

private fun applyClashTransport(proxy: Map<*, *>, outbound: JSONObject) {
    when (proxy.string("network")?.lowercase(Locale.ROOT)) {
        "ws" -> {
            val options = proxy["ws-opts"] as? Map<*, *>
            val transport = JSONObject()
                .put("type", "ws")
                .put("path", options?.string("path") ?: "/")
            val headers = options?.get("headers") as? Map<*, *>
            if (!headers.isNullOrEmpty()) {
                transport.put(
                    "headers",
                    JSONObject().apply {
                        headers.forEach { (key, value) ->
                            if (key is String && value != null) put(key, value.toString())
                        }
                    },
                )
            }
            outbound.put("transport", transport)
        }
        "grpc" -> {
            val options = proxy["grpc-opts"] as? Map<*, *>
            outbound.put(
                "transport",
                JSONObject()
                    .put("type", "grpc")
                    .put(
                        "service_name",
                        options?.string("grpc-service-name")
                            ?: options?.string("service-name")
                            ?: "",
                    ),
            )
        }
    }
}

private fun applyClashTls(
    proxy: Map<*, *>,
    outbound: JSONObject,
    defaultEnabled: Boolean,
) {
    if (!defaultEnabled) return
    val tls = JSONObject()
        .put("enabled", true)
        .put("server_name", proxy.string("servername") ?: proxy.requiredString("server"))
    if (proxy.boolean("skip-cert-verify") == true) tls.put("insecure", true)
    val reality = proxy["reality-opts"] as? Map<*, *>
    if (reality != null) {
        tls.put(
            "reality",
            JSONObject()
                .put("enabled", true)
                .put("public_key", reality.requiredString("public-key"))
                .put("short_id", reality.string("short-id") ?: ""),
        )
    }
    outbound.put("tls", tls)
}

private fun subscriptionEntryBaseKey(profile: TunnelProfile): String {
    val normalizedName = profile.name.trim().lowercase(Locale.ROOT)
    val material = "${profile.type.name}|$normalizedName"
    return MessageDigest.getInstance("SHA-256")
        .digest(material.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }
}

private fun appendSubscriptionNote(current: String?, note: String): String =
    listOfNotNull(current?.takeIf(String::isNotBlank), note).distinct().joinToString("\n")

private fun sanitizeSubscriptionPreview(preview: ProfileImportPreview): ProfileImportPreview =
    preview.copy(
        candidates = preview.candidates.map { candidate ->
            val name = candidate.profile.name
            require(
                name.length <= 200 &&
                    name.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() },
            ) {
                "Подписка содержит профиль с недопустимым именем."
            }
            candidate.copy(
                maskedPreview = candidate.maskedPreview.take(MAX_MASKED_PREVIEW_CHARS),
                warnings = candidate.warnings.map(::sanitizeSubscriptionMessage),
            )
        },
        warnings = preview.warnings.map(::sanitizeSubscriptionMessage),
    )

private fun sanitizeSubscriptionMessage(message: String): String {
    val safeCharacters = message.take(MAX_WARNING_CHARS).map { character ->
        if (character.isISOControl() || Character.getType(character) == Character.FORMAT.toInt()) {
            ' '
        } else {
            character
        }
    }.joinToString("")
    return SECRET_URI_PATTERN.replace(safeCharacters, "<скрытая ссылка>")
}

private fun Map<*, *>.string(key: String): String? =
    this[key]?.toString()?.trim()

private fun Map<*, *>.requiredString(key: String): String =
    string(key)?.takeIf(String::isNotBlank) ?: error("не указано поле $key.")

private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
    is Number -> value.toInt()
    else -> value?.toString()?.toIntOrNull()
}

private fun Map<*, *>.boolean(key: String): Boolean? = when (val value = this[key]) {
    is Boolean -> value
    else -> value?.toString()?.toBooleanStrictOrNull()
}

private fun padBase64(value: String): String =
    value + "=".repeat((4 - value.length % 4) % 4)

private fun decodeUtf8OrNull(bytes: ByteArray): String? {
    val decoder = Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    return runCatching { decoder.decode(ByteBuffer.wrap(bytes)).toString() }.getOrNull()
}

private const val MAX_MASKED_PREVIEW_CHARS = 8_192
private const val MAX_WARNING_CHARS = 1_000
private const val SUBSCRIPTION_REMOVED_NOTE_PREFIX = "Профиль больше не входит в подписку"
private val SECRET_URI_PATTERN = Regex("[a-zA-Z][a-zA-Z0-9+.-]*://\\S+")
