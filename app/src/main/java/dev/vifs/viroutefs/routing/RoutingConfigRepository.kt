package dev.vifs.viroutefs.routing

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RoutingConfigRepository(
    private val context: Context,
) {
    private val configFile: File
        get() = File(context.filesDir, "routing_config.json")

    suspend fun load(): RoutingConfigLoadResult = withContext(Dispatchers.IO) {
        val file = configFile
        if (!file.exists()) {
            return@withContext RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), null)
        }
        runCatching {
            val config = decodeConfig(file.readText())
            val errors = validateRoutingConfig(config)
            if (errors.isNotEmpty()) {
                RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Сохранённая конфигурация некорректна: ${errors.joinToString()}")
            } else {
                RoutingConfigLoadResult(config, null)
            }
        }.getOrElse { error ->
            RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Не удалось прочитать конфигурацию маршрутов. Загружены настройки по умолчанию. ${error.message.orEmpty()}")
        }
    }

    suspend fun save(config: RoutingConfig) = withContext(Dispatchers.IO) {
        configFile.writeText(encodeConfig(config))
    }

    fun exportJson(config: RoutingConfig): String = encodeConfig(config)

    fun importJson(json: String): Result<RoutingConfig> = runCatching {
        val config = decodeConfig(json)
        val errors = validateRoutingConfig(config)
        require(errors.isEmpty()) { errors.joinToString("\n") }
        config
    }

    private fun encodeConfig(config: RoutingConfig): String = JSONObject().apply {
        put("version", config.version)
        put("profiles", JSONArray(config.profiles.map { it.toJson() }))
        put("dnsPolicies", JSONArray(config.dnsPolicies.map { it.toJson() }))
        put("rules", JSONArray(config.rules.map { it.toJson() }))
        put("defaultProfileId", config.defaultProfileId)
        put("hostOverrides", JSONArray(config.hostOverrides.map { it.toJson() }))
    }.toString(2)

    private fun decodeConfig(json: String): RoutingConfig {
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

    private fun TunnelProfile.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("description", description)
        put("enabled", enabled)
        put("mockOnly", mockOnly)
        put("platformNotes", platformNotes)
        put("dnsPolicyId", dnsPolicyId)
    }

    private fun JSONObject.toTunnelProfile(): TunnelProfile = TunnelProfile(
        id = getString("id"),
        name = getString("name"),
        type = enumValueOf(optString("type", TunnelType.Direct.name)),
        description = optString("description"),
        enabled = optBoolean("enabled", true),
        mockOnly = optBoolean("mockOnly", enumValueOf<TunnelType>(optString("type", TunnelType.Direct.name)).isMockOnly),
        platformNotes = optNullableString("platformNotes"),
        dnsPolicyId = optNullableString("dnsPolicyId"),
    )

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
        type = enumValueOf(optString("type", DnsPolicyType.System.name)),
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
        type = enumValueOf(optString("type", RouteRuleType.DOMAIN.name)),
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

    private fun JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = (0 until length()).map { index ->
        transform(getJSONObject(index))
    }

    private fun JSONObject.optNullableString(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}

data class RoutingConfigLoadResult(
    val config: RoutingConfig,
    val errorMessage: String?,
)
