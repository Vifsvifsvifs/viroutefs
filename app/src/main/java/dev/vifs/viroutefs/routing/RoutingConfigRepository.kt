package dev.vifs.viroutefs.routing

import android.content.Context
import dev.vifs.viroutefs.socks5.Socks5ProfileConfig
import dev.vifs.viroutefs.socks5.Socks5ProfileStatus
import dev.vifs.viroutefs.vless.VlessProfileConfig
import dev.vifs.viroutefs.vless.VlessProfileStatus
import dev.vifs.viroutefs.vless.VlessSecurityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class RoutingConfigLoadResult(
    val config: RoutingConfig,
    val errorMessage: String?,
)

class RoutingConfigRepository(
    private val context: Context,
    private val credentialStore: Socks5CredentialStore = Socks5CredentialStore(context),
) {
    private val configFile: File
        get() = File(context.filesDir, ROUTING_CONFIG_FILENAME)

    suspend fun load(): RoutingConfigLoadResult = withContext(Dispatchers.IO) {
        val file = configFile
        if (!file.exists()) {
            return@withContext RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), null)
        }
        runCatching {
            val decodedConfig = RoutingConfigJson.decode(file.readText())
            val legacyPasswords = decodedConfig.socks5PasswordsByProfileId()
            val storedPasswords = credentialStore.load()
            val mergedPasswords = storedPasswords + legacyPasswords
            val config = decodedConfig.withSocks5Passwords(mergedPasswords)
            val errors = validateRoutingConfig(config)
            if (errors.isNotEmpty()) {
                RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Сохранённая конфигурация некорректна: ${errors.joinToString()}")
            } else {
                if (legacyPasswords.isNotEmpty()) {
                    credentialStore.save(mergedPasswords.onlyProfilesIn(config))
                    file.writeText(RoutingConfigJson.encode(config, includeSocks5Passwords = false))
                }
                RoutingConfigLoadResult(config, null)
            }
        }.getOrElse { error ->
            RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Не удалось прочитать конфигурацию маршрутов. Загружены настройки по умолчанию. ${error.message.orEmpty()}")
        }
    }

    suspend fun save(config: RoutingConfig) = withContext(Dispatchers.IO) {
        credentialStore.save(config.socks5PasswordsByProfileId())
        configFile.writeText(RoutingConfigJson.encode(config, includeSocks5Passwords = false))
    }

    fun exportJson(config: RoutingConfig): String = RoutingConfigJson.encode(config, includeSocks5Passwords = false)

    fun importJson(json: String): Result<RoutingConfig> = runCatching {
        val config = RoutingConfigJson.decode(json).withoutSocks5Passwords()
        val errors = validateRoutingConfig(config)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        config
    }

    companion object {
        const val ROUTING_CONFIG_FILENAME = "routing_config.json"
    }
}

class Socks5CredentialStore(
    private val credentialsFile: File,
) {
    constructor(context: Context) : this(File(context.noBackupFilesDir, FILENAME))

    fun load(): Map<String, String> {
        if (!credentialsFile.exists()) return emptyMap()
        return runCatching {
            val root = JSONObject(credentialsFile.readText())
            root.keys().asSequence()
                .mapNotNull { profileId ->
                    root.optNullableString(profileId)?.takeIf { it.isNotEmpty() }?.let { profileId to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    fun save(passwordsByProfileId: Map<String, String>) {
        credentialsFile.parentFile?.mkdirs()
        val root = JSONObject()
        passwordsByProfileId
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotEmpty() }
            .toSortedMap()
            .forEach { (profileId, password) -> root.put(profileId, password) }
        credentialsFile.writeText(root.toString(2))
        credentialsFile.setReadable(false, false)
        credentialsFile.setWritable(false, false)
        credentialsFile.setReadable(true, true)
        credentialsFile.setWritable(true, true)
    }

    companion object {
        const val FILENAME = "socks5_credentials.json"
    }
}

object RoutingConfigJson {
    fun encode(config: RoutingConfig, includeSocks5Passwords: Boolean = false): String = JSONObject().apply {
        put("version", config.version)
        put("profiles", JSONArray(config.profiles.map { it.toJson(includeSocks5Passwords) }))
        put("dnsPolicies", JSONArray(config.dnsPolicies.map { it.toJson() }))
        put("rules", JSONArray(config.rules.map { it.toJson() }))
        put("defaultProfileId", config.defaultProfileId)
        put("hostOverrides", JSONArray(config.hostOverrides.map { it.toJson() }))
    }.toString(2)

    fun decode(json: String): RoutingConfig {
        val root = JSONObject(json)
        return RoutingConfig(
            version = root.optInt("version", CURRENT_ROUTING_CONFIG_VERSION),
            profiles = root.getJSONArray("profiles").mapObjects { it.toTunnelProfile() },
            dnsPolicies = root.getJSONArray("dnsPolicies").mapObjects { it.toDnsPolicy() },
            rules = root.getJSONArray("rules").mapObjects { it.toRouteRule() },
            defaultProfileId = root.optNullableString("defaultProfileId"),
            hostOverrides = root.optJSONArray("hostOverrides")?.mapObjects { it.toDnsHostOverride() }.orEmpty(),
        )
    }

    private fun TunnelProfile.toJson(includeSocks5Password: Boolean): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("description", description)
        put("enabled", enabled)
        put("mockOnly", mockOnly)
        put("platformNotes", platformNotes)
        put("dnsPolicyId", dnsPolicyId)
        put("socks5", socks5?.toJson(includeSocks5Password))
        put("vless", vless?.toJson())
    }

    private fun JSONObject.toTunnelProfile(): TunnelProfile {
        val type = optTunnelType("type", TunnelType.Direct)
        return TunnelProfile(
            id = getString("id"),
            name = getString("name"),
            type = type,
            description = optString("description"),
            enabled = optBoolean("enabled", true),
            mockOnly = optBoolean("mockOnly", type.isMockOnly),
            platformNotes = optNullableString("platformNotes"),
            dnsPolicyId = optNullableString("dnsPolicyId"),
            socks5 = optJSONObject("socks5")?.toSocks5ProfileConfig(),
            vless = optJSONObject("vless")?.toVlessProfileConfig(),
        )
    }

    private fun Socks5ProfileConfig.toJson(includePassword: Boolean): JSONObject = JSONObject().apply {
        put("name", name)
        put("host", host)
        put("port", port)
        put("username", username)
        if (includePassword && !password.isNullOrBlank()) {
            put("password", password)
        }
        put("enabled", enabled)
        put("status", status.toJson())
    }

    private fun JSONObject.toSocks5ProfileConfig(): Socks5ProfileConfig = Socks5ProfileConfig(
        name = getString("name"),
        host = getString("host"),
        port = optInt("port"),
        username = optNullableString("username"),
        password = optNullableString("password"),
        enabled = optBoolean("enabled", true),
        status = optJSONObject("status")?.toSocks5ProfileStatus() ?: Socks5ProfileStatus.NotTested,
    )

    private fun Socks5ProfileStatus.toJson(): JSONObject = JSONObject().apply {
        when (this@toJson) {
            Socks5ProfileStatus.NotTested -> put("state", "NotTested")
            Socks5ProfileStatus.Testing -> put("state", "NotTested")
            Socks5ProfileStatus.Reachable -> put("state", "Reachable")
            is Socks5ProfileStatus.Failed -> {
                put("state", "Failed")
                put("message", message)
            }
        }
    }

    private fun JSONObject.toSocks5ProfileStatus(): Socks5ProfileStatus = when (optString("state")) {
        "Reachable" -> Socks5ProfileStatus.Reachable
        "Failed" -> Socks5ProfileStatus.Failed(optString("message", "invalid response"))
        else -> Socks5ProfileStatus.NotTested
    }

    private fun VlessProfileConfig.toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("host", host)
        put("port", port)
        put("uuid", uuid)
        put("transportType", transportType)
        put("securityMode", securityMode.wireName)
        put("encryption", encryption)
        put("flow", flow)
        put("sni", sni)
        put("publicKey", publicKey)
        put("shortId", shortId)
        put("fingerprint", fingerprint)
        put("path", path)
        put("hostHeader", hostHeader)
        put("alpn", alpn)
        put("serviceName", serviceName)
        put("enabled", enabled)
        put("status", status.toJson())
    }

    private fun JSONObject.toVlessProfileConfig(): VlessProfileConfig = VlessProfileConfig(
        name = getString("name"),
        host = getString("host"),
        port = optInt("port"),
        uuid = getString("uuid"),
        transportType = optNullableString("transportType"),
        securityMode = optVlessSecurityMode("securityMode"),
        encryption = optNullableString("encryption"),
        flow = optNullableString("flow"),
        sni = optNullableString("sni"),
        publicKey = optNullableString("publicKey"),
        shortId = optNullableString("shortId"),
        fingerprint = optNullableString("fingerprint"),
        path = optNullableString("path"),
        hostHeader = optNullableString("hostHeader"),
        alpn = optNullableString("alpn"),
        serviceName = optNullableString("serviceName"),
        enabled = optBoolean("enabled", true),
        status = optJSONObject("status")?.toVlessProfileStatus() ?: VlessProfileStatus.NotTested,
    )

    private fun VlessProfileStatus.toJson(): JSONObject = JSONObject().apply {
        put("state", when (this@toJson) {
            VlessProfileStatus.NotTested -> "NotTested"
            VlessProfileStatus.Invalid -> "Invalid"
            VlessProfileStatus.ConfigReady -> "ConfigReady"
        })
    }

    private fun JSONObject.toVlessProfileStatus(): VlessProfileStatus = when (optString("state")) {
        "Invalid" -> VlessProfileStatus.Invalid
        "ConfigReady" -> VlessProfileStatus.ConfigReady
        else -> VlessProfileStatus.NotTested
    }

    private fun DnsHostOverride.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hostname", hostname)
        put("ipAddress", ipAddress)
        put("enabled", enabled)
        put("note", note)
    }

    private fun JSONObject.toDnsHostOverride(): DnsHostOverride = DnsHostOverride(
        id = getString("id"),
        hostname = getString("hostname"),
        ipAddress = getString("ipAddress"),
        enabled = optBoolean("enabled", true),
        note = optNullableString("note"),
    )

    private fun DnsPolicy.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("serverText", serverText)
        put("resolveThroughProfileId", resolveThroughProfileId)
        put("description", description)
        put("enabled", enabled)
    }

    private fun JSONObject.toDnsPolicy(): DnsPolicy = DnsPolicy(
        id = getString("id"),
        name = getString("name"),
        type = optDnsPolicyType("type"),
        serverText = optNullableString("serverText"),
        resolveThroughProfileId = optNullableString("resolveThroughProfileId"),
        description = optString("description"),
        enabled = optBoolean("enabled", true),
    )

    private fun RouteRule.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("targetProfileId", targetProfileId)
        put("dnsPolicyId", dnsPolicyId)
        put("priority", priority)
        put("matchers", JSONArray(matchers))
        put("appMatchers", JSONArray(appMatchers.map { it.toJson() }))
        put("enabled", enabled)
        put("reason", reason)
        put("technicalDetails", technicalDetails)
        put("recommendedAction", recommendedAction)
    }

    private fun JSONObject.toRouteRule(): RouteRule = RouteRule(
        id = getString("id"),
        name = getString("name"),
        type = optRouteRuleType("type"),
        targetProfileId = optString("targetProfileId", optString("targetTunnelId")),
        dnsPolicyId = optNullableString("dnsPolicyId"),
        priority = optInt("priority", 1000),
        matchers = optJSONArray("matchers")?.mapStrings().orEmpty(),
        appMatchers = optJSONArray("appMatchers")?.mapObjects { it.toAppMatcher() }.orEmpty(),
        enabled = optBoolean("enabled", true),
        reason = optString("reason", "Пользовательское правило маршрутизации."),
        technicalDetails = optString("technicalDetails", "Правило загружено из локальной JSON-конфигурации."),
        recommendedAction = optString("recommendedAction", "Проверьте правило симулятором перед использованием будущего реального маршрута."),
    )

    private fun AppMatcher.toJson(): JSONObject = JSONObject().apply {
        put("platform", platform.wireName)
        put("value", value)
        put("displayName", displayName)
    }

    private fun JSONObject.toAppMatcher(): AppMatcher = AppMatcher(
        platform = AppMatcherPlatform.entries.firstOrNull { it.wireName == optString("platform") } ?: AppMatcherPlatform.Any,
        value = getString("value"),
        displayName = optNullableString("displayName"),
    )

    private fun JSONObject.optRouteRuleType(name: String): RouteRuleType = optEnum(name, RouteRuleType.DOMAIN)

    private fun JSONObject.optDnsPolicyType(name: String): DnsPolicyType = optEnum(name, DnsPolicyType.System)

    private fun JSONObject.optVlessSecurityMode(name: String): VlessSecurityMode {
        val value = optNullableString(name) ?: return VlessSecurityMode.NONE
        return VlessSecurityMode.entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }
            ?: VlessSecurityMode.NONE
    }

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(key: String, fallback: T): T {
        val value = optNullableString(key) ?: return fallback
        return enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } ?: fallback
    }

    private fun JSONObject.optTunnelType(key: String, fallback: TunnelType): TunnelType {
        val value = optNullableString(key) ?: return fallback
        return enumValues<TunnelType>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: legacyTunnelTypeAliases[value.lowercase()]
            ?: fallback
    }

    private val legacyTunnelTypeAliases = mapOf(
        "xray" to TunnelType.XrayMock,
        "xrayvless" to TunnelType.XrayVlessReality,
        "vless" to TunnelType.VLESS,
        "hysteria" to TunnelType.Hysteria2,
        "openvpn" to TunnelType.OpenVpn,
        "socks" to TunnelType.Socks5,
        "socks5" to TunnelType.Socks5,
    )
}

fun RoutingConfig.socks5PasswordsByProfileId(): Map<String, String> = profiles.mapNotNull { profile ->
    profile.socks5?.password?.takeIf { it.isNotEmpty() }?.let { profile.id to it }
}.toMap()

fun RoutingConfig.withoutSocks5Passwords(): RoutingConfig = copy(
    profiles = profiles.map { profile ->
        profile.copy(socks5 = profile.socks5?.copy(password = null))
    },
)

fun RoutingConfig.withSocks5Passwords(passwordsByProfileId: Map<String, String>): RoutingConfig = copy(
    profiles = profiles.map { profile ->
        val socks5 = profile.socks5
        val password = passwordsByProfileId[profile.id]
        if (socks5 != null && password != null) {
            profile.copy(socks5 = socks5.copy(password = password))
        } else {
            profile.copy(socks5 = socks5?.copy(password = null))
        }
    },
)

private fun Map<String, String>.onlyProfilesIn(config: RoutingConfig): Map<String, String> {
    val profileIds = config.profiles.map { it.id }.toSet()
    return filterKeys { it in profileIds }
}

private fun JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> transform(getJSONObject(index)) }

private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }
