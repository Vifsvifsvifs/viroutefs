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
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class RoutingConfigLoadResult(
    val config: RoutingConfig,
    val errorMessage: String?,
)

class RoutingConfigRepository internal constructor(
    filesDirectory: File,
    private val secretStore: ProfileSecretStore,
    private val legacyCredentialStore: Socks5CredentialStore? = null,
    private val subscriptionSecretStore: SubscriptionSecretStore = InMemorySubscriptionSecretStore(),
) {
    private val configFile = File(filesDirectory, ROUTING_CONFIG_FILENAME)

    constructor(
        context: Context,
    ) : this(
        filesDirectory = context.filesDir,
        secretStore = AndroidKeystoreProfileSecretStore(context),
        legacyCredentialStore = Socks5CredentialStore(context),
        subscriptionSecretStore = AndroidKeystoreSubscriptionSecretStore(context),
    )

    suspend fun load(): RoutingConfigLoadResult = withContext(Dispatchers.IO) {
        val file = configFile
        if (!file.exists()) {
            return@withContext RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), null)
        }
        runCatching {
            val rawConfig = RoutingConfigJson.decode(file.readText())
            val requiresNormalization =
                rawConfig.version != CURRENT_ROUTING_CONFIG_VERSION ||
                    rawConfig.defaultProfileId == null ||
                    rawConfig.defaultProfileId == RoutingConfigDefaults.BLOCK_PROFILE_ID ||
                    rawConfig.defaultProfileId == RoutingConfigDefaults.BYEDPI_PROFILE_ID ||
                    listOf(
                        RoutingConfigDefaults.SYSTEM_PROFILE_ID,
                        RoutingConfigDefaults.BLOCK_PROFILE_ID,
                        RoutingConfigDefaults.BYEDPI_PROFILE_ID,
                    ).any { requiredId -> rawConfig.profiles.none { it.id == requiredId } }
            val decodedConfig = RoutingConfigDefaults.ensureRequiredProfiles(rawConfig)
            val embeddedSecrets = decodedConfig.profileSecretsByProfileId()
            val encryptedSecrets = secretStore.load()
            val embeddedSubscriptionUrls = decodedConfig.subscriptionUrlsById()
            val encryptedSubscriptionUrls = subscriptionSecretStore.load()
            val legacySecrets = legacyCredentialStore
                ?.load()
                .orEmpty()
                .mapValues { (_, password) -> ProfileSecrets(socks5Password = password) }
            val mergedSecrets = encryptedSecrets
                .mergeSecrets(legacySecrets)
                .mergeSecrets(embeddedSecrets)
            val mergedSubscriptionUrls = embeddedSubscriptionUrls + encryptedSubscriptionUrls
            val configWithSecrets = decodedConfig
                .withProfileSecrets(mergedSecrets)
                .withSubscriptionUrls(mergedSubscriptionUrls)
            val openVpnMigrated = if (rawConfig.version < OPENVPN_ROUTE_ROUTER_MIGRATION_VERSION) {
                configWithSecrets
                    .withMigratedOpenVpnEndpointRoutes()
                    .withSyncedProfileAppRoutingRules()
            } else {
                configWithSecrets
            }
            val config = if (rawConfig.version < VPN_GATE_MANAGEMENT_MIGRATION_VERSION) {
                openVpnMigrated.withMigratedVpnGateManagement()
            } else {
                openVpnMigrated
            }
            val errors = validateRoutingConfig(config)
            if (errors.isNotEmpty()) {
                RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Сохранённая конфигурация некорректна: ${errors.joinToString()}")
            } else {
                if (
                    embeddedSecrets.isNotEmpty() ||
                    embeddedSubscriptionUrls.isNotEmpty() ||
                    legacySecrets.isNotEmpty() ||
                    requiresNormalization
                ) {
                    secretStore.save(mergedSecrets.onlyProfilesIn(config))
                    subscriptionSecretStore.save(config.subscriptionUrlsById())
                    file.writeTextAtomically(RoutingConfigJson.encode(config, includeSocks5Passwords = false))
                    legacyCredentialStore?.clear()
                }
                RoutingConfigLoadResult(config, null)
            }
        }.getOrElse { error ->
            RoutingConfigLoadResult(RoutingConfigDefaults.defaultConfig(), "Не удалось прочитать конфигурацию маршрутов. Загружены настройки по умолчанию. ${error.message.orEmpty()}")
        }
    }

    suspend fun save(config: RoutingConfig) = withContext(Dispatchers.IO) {
        secretStore.save(config.profileSecretsByProfileId().onlyProfilesIn(config))
        subscriptionSecretStore.save(config.subscriptionUrlsById())
        configFile.writeTextAtomically(RoutingConfigJson.encode(config, includeSocks5Passwords = false))
        legacyCredentialStore?.clear()
    }

    fun exportJson(config: RoutingConfig): String = RoutingConfigJson.encode(config, includeSocks5Passwords = false)

    suspend fun exportEncryptedBackup(
        config: RoutingConfig,
        password: CharArray,
    ): ByteArray = withContext(Dispatchers.Default) {
        RoutingConfigBackup.encrypt(config, password)
    }

    suspend fun previewEncryptedBackup(
        bytes: ByteArray,
        password: CharArray,
    ): RoutingConfig = withContext(Dispatchers.Default) {
        RoutingConfigBackup.decrypt(bytes, password)
    }

    fun importJson(json: String): Result<RoutingConfig> = runCatching {
        val config = RoutingConfigDefaults.ensureRequiredProfiles(
            RoutingConfigJson.decode(json),
        )
            .withoutProfileSecrets()
            .withoutSubscriptionsForDiagnosticImport()
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
        credentialsFile.writeTextAtomically(root.toString(2))
        credentialsFile.setReadable(false, false)
        credentialsFile.setWritable(false, false)
        credentialsFile.setReadable(true, true)
        credentialsFile.setWritable(true, true)
    }

    fun clear() {
        if (credentialsFile.exists() && !credentialsFile.delete()) {
            error("Could not remove migrated plaintext SOCKS5 credential file.")
        }
    }

    companion object {
        const val FILENAME = "socks5_credentials.json"
    }
}

object RoutingConfigJson {
    /**
     * The compatibility parameter name is kept for callers from public beta
     * tests. When false, every known profile secret is redacted, not only the
     * SOCKS5 password.
     */
    fun encode(config: RoutingConfig, includeSocks5Passwords: Boolean = false): String = JSONObject().apply {
        put("version", config.version)
        put("profiles", JSONArray(config.profiles.map { it.toJson(includeSocks5Passwords) }))
        put("profileGroups", JSONArray(config.profileGroups.map { it.toJson() }))
        put("subscriptions", JSONArray(config.subscriptions.map { it.toJson(includeSocks5Passwords) }))
        put("dnsPolicies", JSONArray(config.dnsPolicies.map { it.toJson() }))
        put("rules", JSONArray(config.rules.map { it.toJson() }))
        put("defaultProfileId", config.defaultProfileId)
        put("hostOverrides", JSONArray(config.hostOverrides.map { it.toJson() }))
        put("emergencyBlockEnabled", config.emergencyBlockEnabled)
    }.toString(2)

    fun decode(json: String): RoutingConfig {
        val root = JSONObject(json)
        return RoutingConfig(
            version = root.optInt("version", CURRENT_ROUTING_CONFIG_VERSION),
            profiles = root.getJSONArray("profiles").mapObjects { it.toTunnelProfile() },
            profileGroups = root.optJSONArray("profileGroups")?.mapObjects { it.toProfileGroup() }.orEmpty(),
            subscriptions = root.optJSONArray("subscriptions")?.mapObjects { it.toProfileSubscription() }.orEmpty(),
            dnsPolicies = root.getJSONArray("dnsPolicies").mapObjects { it.toDnsPolicy() },
            rules = root.getJSONArray("rules").mapObjects { it.toRouteRule() },
            defaultProfileId = root.optNullableString("defaultProfileId"),
            hostOverrides = root.optJSONArray("hostOverrides")?.mapObjects { it.toDnsHostOverride() }.orEmpty(),
            emergencyBlockEnabled = root.optBoolean("emergencyBlockEnabled", false),
        )
    }

    private fun TunnelProfile.toJson(includeSecrets: Boolean): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("description", description)
        put("enabled", enabled)
        put("mockOnly", mockOnly)
        put("platformNotes", platformNotes)
        put("dnsPolicyId", dnsPolicyId)
        put(
            "secretRef",
            secretRef ?: profileSecrets()
                .takeIf { !it.isEmpty }
                ?.let { "profile-secrets:$id" },
        )
        put("socks5", socks5?.toJson(includeSecrets))
        put("vless", vless?.toJson(includeSecrets))
        put("singBox", singBox?.toJson(includeSecrets))
        put("sourceSubscriptionId", sourceSubscriptionId)
        put("sourceEntryKey", sourceEntryKey)
        put("appRoutingMode", appRoutingMode.name)
        put("appRoutingPackages", JSONArray(appRoutingPackages))
        put("appRoutingNetworks", JSONArray(appRoutingNetworks))
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
            secretRef = optNullableString("secretRef"),
            socks5 = optJSONObject("socks5")?.toSocks5ProfileConfig(),
            vless = optJSONObject("vless")?.toVlessProfileConfig(),
            singBox = optJSONObject("singBox")?.toSingBoxProfileConfig(),
            sourceSubscriptionId = optNullableString("sourceSubscriptionId"),
            sourceEntryKey = optNullableString("sourceEntryKey"),
            appRoutingMode = optEnum("appRoutingMode", ProfileAppRoutingMode.SelectedApps),
            appRoutingPackages = optJSONArray("appRoutingPackages")?.mapStrings().orEmpty(),
            appRoutingNetworks = optJSONArray("appRoutingNetworks")?.mapStrings().orEmpty(),
        )
    }

    private fun ProfileSubscription.toJson(includeSecrets: Boolean): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("url", if (includeSecrets) url else REDACTED_SECRET)
        put("enabled", enabled)
        put("lastUpdatedAtEpochMs", lastUpdatedAtEpochMs)
        put("lastProfileCount", lastProfileCount)
    }

    private fun JSONObject.toProfileSubscription(): ProfileSubscription = ProfileSubscription(
        id = getString("id"),
        name = getString("name"),
        url = optString("url", REDACTED_SECRET),
        enabled = optBoolean("enabled", true),
        lastUpdatedAtEpochMs = optLong("lastUpdatedAtEpochMs")
            .takeIf { has("lastUpdatedAtEpochMs") && !isNull("lastUpdatedAtEpochMs") },
        lastProfileCount = optInt("lastProfileCount", 0),
    )

    private fun ProfileGroup.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mode", mode.name)
        put("memberProfileIds", JSONArray(memberProfileIds))
        put("selectedProfileId", selectedProfileId)
        put("testUrl", testUrl)
        put("testIntervalSeconds", testIntervalSeconds)
        put("toleranceMs", toleranceMs)
        put("preferredCountryCode", preferredCountryCode)
        put("enabled", enabled)
    }

    private fun JSONObject.toProfileGroup(): ProfileGroup = ProfileGroup(
        id = getString("id"),
        name = getString("name"),
        mode = optEnum("mode", ProfileGroupMode.Manual),
        memberProfileIds = optJSONArray("memberProfileIds")?.mapStrings().orEmpty(),
        selectedProfileId = optNullableString("selectedProfileId"),
        testUrl = optString("testUrl", "https://www.gstatic.com/generate_204"),
        testIntervalSeconds = optInt("testIntervalSeconds", 180),
        toleranceMs = optInt("toleranceMs", 50),
        preferredCountryCode = optNullableString("preferredCountryCode"),
        enabled = optBoolean("enabled", true),
    )

    private fun SingBoxProfileConfig.toJson(includeSecrets: Boolean): JSONObject = JSONObject().apply {
        put("kind", kind.name)
        put("optionsJson", if (includeSecrets) optionsJson else redactSensitiveJson(optionsJson))
    }

    private fun JSONObject.toSingBoxProfileConfig(): SingBoxProfileConfig = SingBoxProfileConfig(
        kind = optEnum("kind", SingBoxProfileKind.Outbound),
        optionsJson = getString("optionsJson"),
    )

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

    private fun VlessProfileConfig.toJson(includeSecrets: Boolean): JSONObject = JSONObject().apply {
        put("name", name)
        put("host", host)
        put("port", port)
        put("uuid", if (includeSecrets) uuid else REDACTED_SECRET)
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
        put("xhttpMode", xhttpMode)
        put("pinnedPeerCertSha256", pinnedPeerCertSha256)
        put("verifyPeerCertByName", verifyPeerCertByName)
        if (includeSecrets && !xhttpExtra.isNullOrBlank()) {
            put("xhttpExtra", xhttpExtra)
        }
        put("enabled", enabled)
        put("status", status.toJson())
    }

    private fun JSONObject.toVlessProfileConfig(): VlessProfileConfig = VlessProfileConfig(
        name = getString("name"),
        host = getString("host"),
        port = optInt("port"),
        uuid = optString("uuid", REDACTED_SECRET),
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
        xhttpMode = optNullableString("xhttpMode"),
        xhttpExtra = optNullableString("xhttpExtra"),
        pinnedPeerCertSha256 = optNullableString("pinnedPeerCertSha256"),
        verifyPeerCertByName = when (val verifyByName = opt("verifyPeerCertByName")) {
            is String -> verifyByName.trim().takeIf(String::isNotBlank)
            is Boolean -> if (verifyByName) optNullableString("sni") else null
            else -> null
        },
        enabled = optBoolean("enabled", true),
        status = optJSONObject("status")?.toVlessProfileStatus() ?: VlessProfileStatus.NotTested,
    )

    private fun VlessProfileStatus.toJson(): JSONObject = JSONObject().apply {
        put("state", when (this@toJson) {
            VlessProfileStatus.NotTested -> "NotTested"
            VlessProfileStatus.Invalid -> "Invalid"
            VlessProfileStatus.ConfigReady -> "ConfigReady"
            VlessProfileStatus.Testing -> "Testing"
            VlessProfileStatus.TcpReachable -> "TcpReachable"
            VlessProfileStatus.LastTestFailed -> "LastTestFailed"
        })
    }

    private fun JSONObject.toVlessProfileStatus(): VlessProfileStatus = when (optString("state")) {
        "Invalid" -> VlessProfileStatus.Invalid
        "ConfigReady" -> VlessProfileStatus.ConfigReady
        "Testing" -> VlessProfileStatus.Testing
        "TcpReachable" -> VlessProfileStatus.TcpReachable
        "LastTestFailed" -> VlessProfileStatus.LastTestFailed
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
        put("servers", JSONArray(servers.map { it.toJson() }))
        put("fallbackEnabled", fallbackEnabled)
        put("queryTimeoutSeconds", queryTimeoutSeconds)
    }

    private fun JSONObject.toDnsPolicy(): DnsPolicy = DnsPolicy(
        id = getString("id"),
        name = getString("name"),
        type = optDnsPolicyType("type"),
        serverText = optNullableString("serverText"),
        resolveThroughProfileId = optNullableString("resolveThroughProfileId"),
        description = optString("description"),
        enabled = optBoolean("enabled", true),
        servers = optJSONArray("servers")?.mapObjects { it.toDnsServerConfig() }.orEmpty(),
        fallbackEnabled = optBoolean("fallbackEnabled", false),
        queryTimeoutSeconds = optInt("queryTimeoutSeconds", 5),
    )

    private fun DnsServerConfig.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("address", address)
        put("priority", priority)
        put("enabled", enabled)
    }

    private fun JSONObject.toDnsServerConfig(): DnsServerConfig = DnsServerConfig(
        id = getString("id"),
        address = getString("address"),
        priority = optInt("priority", 0),
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
        put("transport", transport.name)
        put("destinationPorts", JSONArray(destinationPorts.map { it.toDisplayText() }))
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
        transport = optEnum("transport", RouteTransport.Any),
        destinationPorts = optJSONArray("destinationPorts")
            ?.mapStrings()
            ?.flatMap { parseDestinationPortRanges(it) }
            .orEmpty(),
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

internal fun RoutingConfig.profileSecretsByProfileId(): Map<String, ProfileSecrets> = profiles.mapNotNull { profile ->
    val secrets = profile.profileSecrets()
    profile.id.takeIf { it.isNotBlank() && !secrets.isEmpty }?.let { it to secrets }
}.toMap()

internal fun RoutingConfig.subscriptionUrlsById(): Map<String, String> = subscriptions.mapNotNull { subscription ->
    subscription.url
        .takeIf { subscription.id.isNotBlank() && it.isNotBlank() && it != REDACTED_SECRET }
        ?.let { subscription.id to it }
}.toMap()

private fun TunnelProfile.profileSecrets(): ProfileSecrets =
    ProfileSecrets(
        socks5Password = socks5?.password?.takeIf { it.isNotEmpty() && it != REDACTED_SECRET },
        vlessUuid = vless?.uuid?.takeIf { it.isNotEmpty() && it != REDACTED_SECRET },
        vlessXhttpExtra = vless?.xhttpExtra?.takeIf { it.isNotEmpty() && it != REDACTED_SECRET },
        singBoxOptionsJson = singBox
            ?.optionsJson
            ?.takeIf { it.isNotEmpty() && REDACTED_SECRET !in it },
    )

internal fun RoutingConfig.withoutProfileSecrets(): RoutingConfig = copy(
    profiles = profiles.map { profile ->
        profile.copy(
            socks5 = profile.socks5?.copy(password = null),
            vless = profile.vless?.copy(
                uuid = REDACTED_SECRET,
                xhttpExtra = null,
            ),
            singBox = profile.singBox?.copy(
                optionsJson = redactSensitiveJson(profile.singBox.optionsJson),
            ),
        )
    },
)

internal fun RoutingConfig.withProfileSecrets(
    secretsByProfileId: Map<String, ProfileSecrets>,
): RoutingConfig = copy(
    profiles = profiles.map { profile ->
        val secrets = secretsByProfileId[profile.id]
        profile.copy(
            socks5 = profile.socks5?.copy(
                password = secrets?.socks5Password,
            ),
            vless = profile.vless?.let { vless ->
                vless.copy(
                    uuid = secrets?.vlessUuid ?: vless.uuid,
                    xhttpExtra = secrets?.vlessXhttpExtra ?: vless.xhttpExtra,
                )
            },
            singBox = profile.singBox?.let { singBox ->
                singBox.copy(optionsJson = secrets?.singBoxOptionsJson ?: singBox.optionsJson)
            },
        )
    },
)

internal fun RoutingConfig.withSubscriptionUrls(
    urlsBySubscriptionId: Map<String, String>,
): RoutingConfig = copy(
    subscriptions = subscriptions.map { subscription ->
        subscription.copy(
            url = urlsBySubscriptionId[subscription.id] ?: subscription.url,
        )
    },
)

internal fun RoutingConfig.withoutSubscriptionsForDiagnosticImport(): RoutingConfig = copy(
    subscriptions = emptyList(),
    profiles = profiles.map { profile ->
        profile.copy(
            sourceSubscriptionId = null,
            sourceEntryKey = null,
        )
    },
)

private fun Map<String, ProfileSecrets>.onlyProfilesIn(
    config: RoutingConfig,
): Map<String, ProfileSecrets> {
    val profileIds = config.profiles.map { it.id }.toSet()
    return filterKeys { it in profileIds }
}

private fun Map<String, ProfileSecrets>.mergeSecrets(
    newer: Map<String, ProfileSecrets>,
): Map<String, ProfileSecrets> = (keys + newer.keys).associateWith { profileId ->
    val oldSecrets = get(profileId) ?: ProfileSecrets()
    newer[profileId]?.let(oldSecrets::merge) ?: oldSecrets
}.filterValues { !it.isEmpty }

internal fun redactSensitiveJson(rawJson: String): String {
    val root = runCatching { JSONObject(rawJson) }.getOrElse { return REDACTED_SECRET }
    redactObject(root)
    return root.toString()
}

private fun redactObject(value: JSONObject) {
    value.keys().asSequence().toList().forEach { key ->
        when {
            key.lowercase() in SENSITIVE_JSON_KEYS -> value.put(key, REDACTED_SECRET)
            value.optJSONObject(key) != null -> redactObject(value.getJSONObject(key))
            value.optJSONArray(key) != null -> redactArray(value.getJSONArray(key))
        }
    }
}

private fun redactArray(value: JSONArray) {
    for (index in 0 until value.length()) {
        when (val item = value.opt(index)) {
            is JSONObject -> redactObject(item)
            is JSONArray -> redactArray(item)
        }
    }
}

private val SENSITIVE_JSON_KEYS = setOf(
    "password",
    "passphrase",
    "private_key",
    "private-key",
    "client_key",
    "static_key",
    "key",
    "preshared_key",
    "pre_shared_key",
    "psk",
    "uuid",
    "id",
    "auth",
    "auth_str",
    "auth_key",
    "token",
    "access_token",
    "refresh_token",
    "cookie",
    "client_secret",
    "tls_auth",
    "tls_crypt",
    "tls_crypt_v2",
    "pkcs12",
)

private fun JSONArray.mapStrings(): List<String> = (0 until length()).map { getString(it) }

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    (0 until length()).map { index -> transform(getJSONObject(index)) }

private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

internal fun File.writeTextAtomically(content: String) {
    val directory = requireNotNull(parentFile) { "Atomic config file must have a parent directory." }
    directory.mkdirs()
    val temporary = File(directory, ".$name.tmp")
    FileOutputStream(temporary).use { output ->
        output.write(content.toByteArray(Charsets.UTF_8))
        output.fd.sync()
    }
    runCatching {
        Files.move(
            temporary.toPath(),
            toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.recoverCatching {
        Files.move(
            temporary.toPath(),
            toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }.getOrThrow()
}
