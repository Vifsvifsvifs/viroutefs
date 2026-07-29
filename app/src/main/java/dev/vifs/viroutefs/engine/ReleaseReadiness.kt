// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.RouteRuleType
import dev.vifs.viroutefs.routing.RoutingConfig
import dev.vifs.viroutefs.routing.RoutingConfigDefaults
import dev.vifs.viroutefs.routing.TunnelType
import dev.vifs.viroutefs.routing.defaultRouteActivationError
import dev.vifs.viroutefs.routing.validateRoutingConfig

internal enum class ReadinessState(val userLabel: String) {
    Ready("Готово локально"),
    Attention("Нужна проверка"),
    Blocked("Нужно исправить"),
    Planned("Осталось реализовать"),
}

internal data class ReadinessItem(
    val id: String,
    val title: String,
    val state: ReadinessState,
    val summary: String,
    val recommendedAction: String? = null,
)

internal data class ReleaseReadinessReport(
    val items: List<ReadinessItem>,
    val runtimeReadyProtocols: List<TunnelType>,
    val plannedProtocols: List<TunnelType>,
    val legacyProtocols: List<TunnelType>,
) {
    val blockingCount: Int
        get() = items.count { it.state == ReadinessState.Blocked }

    val attentionCount: Int
        get() = items.count { it.state == ReadinessState.Attention || it.state == ReadinessState.Planned }

    val readyCount: Int
        get() = items.count { it.state == ReadinessState.Ready }
}

internal fun evaluateReleaseReadiness(config: RoutingConfig): ReleaseReadinessReport {
    val runtimeReady = EngineCatalog.protocols
        .filter(ProtocolDescriptor::canStartRuntime)
        .map { it.type }
    val planned = EngineCatalog.protocols
        .filter {
            it.readiness == FeatureReadiness.ModelOnly ||
                it.readiness == FeatureReadiness.ConfigSupported
        }
        .map { it.type }
    val legacy = EngineCatalog.protocols
        .filter {
            it.backend == EngineBackend.LegacyAdapter ||
                it.readiness == FeatureReadiness.LegacyRestricted
        }
        .map { it.type }

    val validationErrors = validateRoutingConfig(config)
    val defaultRouteError = defaultRouteActivationError(config)
    val explicitRules = config.rules.filter { it.enabled && it.type != RouteRuleType.DEFAULT }
    val profileById = config.profiles.associateBy { it.id }
    val unavailableRuleTargets = explicitRules.filter { rule ->
        val profile = profileById[rule.targetProfileId]
        profile == null ||
            !profile.enabled ||
            EngineCatalog.descriptor(profile.type)?.canStartRuntime != true
    }
    val configuredProfiles = config.profiles.filterNot { profile ->
        profile.id in setOf(
            RoutingConfigDefaults.SYSTEM_PROFILE_ID,
            RoutingConfigDefaults.BLOCK_PROFILE_ID,
            RoutingConfigDefaults.BYEDPI_PROFILE_ID,
        )
    }
    val configuredUnavailableProfiles = configuredProfiles.filter { profile ->
        EngineCatalog.descriptor(profile.type)?.canStartRuntime != true
    }
    val customDnsPolicies = config.dnsPolicies.filter { policy ->
        policy.enabled && policy.id != RoutingConfigDefaults.SYSTEM_DNS_ID
    }
    val appRuleCount = explicitRules.count { rule ->
        rule.type == RouteRuleType.APP || rule.type == RouteRuleType.APP_GROUP
    }

    val items = buildList {
        add(
            ReadinessItem(
                id = "configuration",
                title = "Структура конфигурации",
                state = if (validationErrors.isEmpty()) ReadinessState.Ready else ReadinessState.Blocked,
                summary = if (validationErrors.isEmpty()) {
                    "Профили, DNS и правила согласованы между собой."
                } else {
                    "Найдено ошибок: ${validationErrors.size}. Первая: ${validationErrors.first()}"
                },
                recommendedAction = validationErrors.takeIf { it.isNotEmpty() }
                    ?.let { "Исправьте отмеченные профили или правила и повторите нативную проверку." },
            ),
        )
        add(
            ReadinessItem(
                id = "default_route",
                title = "Основной маршрут",
                state = if (defaultRouteError == null) ReadinessState.Ready else ReadinessState.Blocked,
                summary = defaultRouteError ?: if (config.defaultProfileId == RoutingConfigDefaults.SYSTEM_PROFILE_ID) {
                    "Трафик без отдельного правила использует обычный мобильный интернет или Wi‑Fi телефона."
                } else {
                    "Трафик без отдельного правила использует выбранный пользовательский профиль."
                },
                recommendedAction = defaultRouteError?.let { "Верните System или выберите полностью настроенный профиль." },
            ),
        )
        add(
            ReadinessItem(
                id = "profiles",
                title = "Настроенные VPN-профили",
                state = when {
                    configuredProfiles.isEmpty() -> ReadinessState.Attention
                    configuredUnavailableProfiles.isNotEmpty() -> ReadinessState.Attention
                    else -> ReadinessState.Ready
                },
                summary = when {
                    configuredProfiles.isEmpty() -> "Пользовательские VPN-профили ещё не добавлены."
                    configuredUnavailableProfiles.isNotEmpty() ->
                        "Настроено: ${configuredProfiles.size}; требуют отдельного движка: ${configuredUnavailableProfiles.joinToString { it.type.label }}."
                    else -> "Настроено профилей с поддерживаемым runtime: ${configuredProfiles.size}."
                },
                recommendedAction = when {
                    configuredProfiles.isEmpty() -> "VPN не обязателен: добавьте профиль только для приложений или сетей, которым он нужен."
                    configuredUnavailableProfiles.isNotEmpty() -> "Не назначайте незавершённые профили рабочим правилам."
                    else -> null
                },
            ),
        )
        add(
            ReadinessItem(
                id = "routes",
                title = "Маршруты приложений и сетей",
                state = if (unavailableRuleTargets.isEmpty()) ReadinessState.Ready else ReadinessState.Blocked,
                summary = when {
                    unavailableRuleTargets.isNotEmpty() ->
                        "Правил с недоступной целью: ${unavailableRuleTargets.size}. Они будут заблокированы без fallback."
                    explicitRules.isEmpty() ->
                        "Исключений нет: весь трафик использует обычное подключение телефона."
                    else ->
                        "Активных исключений: ${explicitRules.size}; правил по приложениям: $appRuleCount."
                },
                recommendedAction = unavailableRuleTargets.takeIf { it.isNotEmpty() }
                    ?.let { "Выберите для этих правил включённый профиль минимум со статусом RuntimeIntegrated." },
            ),
        )
        add(
            ReadinessItem(
                id = "dns",
                title = "DNS-маршрутизация",
                state = if (validationErrors.none { it.contains("DNS", ignoreCase = true) }) {
                    ReadinessState.Ready
                } else {
                    ReadinessState.Blocked
                },
                summary = if (customDnsPolicies.isEmpty()) {
                    "Используется системный DNS через TUN."
                } else {
                    "Активных пользовательских DNS-политик: ${customDnsPolicies.size}."
                },
                recommendedAction = "Для каждого правила при необходимости можно назначить отдельный DNS.",
            ),
        )
        add(
            ReadinessItem(
                id = "runtime",
                title = "Протоколы в текущем APK",
                state = ReadinessState.Attention,
                summary = "${runtimeReady.size} вариантов интегрированы в runtime, но ещё не имеют статуса DeviceVerified: ${runtimeReady.joinToString { it.label }}.",
                recommendedAction = "Проверьте реальные серверы на физическом телефоне; только после этого статус можно повысить.",
            ),
        )
        add(
            ReadinessItem(
                id = "scanner",
                title = "Flow Scanner",
                state = ReadinessState.Attention,
                summary = "Подключён к потоку реальных событий runtime, но attribution ещё требует физической проверки Android 10–16.",
                recommendedAction = "Проверить TCP/UDP и короткие соединения нескольких приложений на физическом телефоне.",
            ),
        )
        add(
            ReadinessItem(
                id = "device",
                title = "Проверка на физическом Android",
                state = ReadinessState.Attention,
                summary = "Сборка и тесты проходят, но end-to-end маршрут, DNS, совместимость TCP/TLS и определение приложения ещё нужно подтвердить на arm64-телефоне.",
                recommendedAction = "Установите beta APK, включите контроль сети с маршрутом System и проверьте несколько приложений и правил.",
            ),
        )
        add(
            ReadinessItem(
                id = "planned",
                title = "Следующие протоколы",
                state = ReadinessState.Planned,
                summary = "В плане: ${planned.joinToString { it.label }}.",
                recommendedAction = "Добавлять только после интеграции и лицензионной проверки отдельного движка.",
            ),
        )
        add(
            ReadinessItem(
                id = "legacy",
                title = "Legacy-совместимость",
                state = ReadinessState.Planned,
                summary = "Пока не подключены: ${legacy.joinToString { it.label }}. Предупреждения уже показываются.",
                recommendedAction = "PPTP/L2TP/SSTP нельзя обозначать рабочими до появления проверенного адаптера.",
            ),
        )
    }

    return ReleaseReadinessReport(
        items = items,
        runtimeReadyProtocols = runtimeReady,
        plannedProtocols = planned,
        legacyProtocols = legacy,
    )
}
