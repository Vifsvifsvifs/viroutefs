package dev.vifs.viroutefs.routing

object RoutingConfigDefaults {
    const val DIRECT_PROFILE_ID = "direct"
    const val BLOCK_PROFILE_ID = "block"
    const val XRAY_GERMANY_PROFILE_ID = "xray_de"
    const val HYSTERIA2_NL_PROFILE_ID = "hysteria2_nl"
    const val OPENVPN_WORK_PROFILE_ID = "openvpn_work"
    const val SOCKS5_WORK_VM_PROFILE_ID = "socks5_work_vm"

    const val SYSTEM_DNS_ID = "system_dns"
    const val DIRECT_DNS_ID = "direct_dns"
    const val WORK_DNS_ID = "work_dns_mock"
    const val TUNNEL_DNS_ID = "tunnel_dns_mock"

    fun defaultConfig(): RoutingConfig = RoutingConfig(
        profiles = defaultProfiles(),
        dnsPolicies = defaultDnsPolicies(),
        rules = defaultRules(),
    )

    fun workPersonalConfig(): RoutingConfig = defaultConfig()

    fun mediaFastTunnelConfig(): RoutingConfig = defaultConfig().let { config ->
        config.copy(
            rules = config.rules.map { rule ->
                if (rule.id == "youtube_hysteria2") rule.copy(priority = 5) else rule
            },
        )
    }

    fun banksDirectConfig(): RoutingConfig = defaultConfig().let { config ->
        config.copy(
            rules = config.rules.map { rule ->
                if (rule.id == "banking_direct") rule.copy(priority = 1, dnsPolicyId = DIRECT_DNS_ID) else rule
            },
        )
    }

    fun safeDefaultConfig(): RoutingConfig = defaultConfig().let { config ->
        config.copy(
            rules = config.rules.map { rule ->
                if (rule.type == RouteRuleType.DEFAULT) {
                    rule.copy(targetProfileId = BLOCK_PROFILE_ID, name = "Безопасный дефолт: блокировать остальное", reason = "Неизвестный трафик блокируется до явного правила.", recommendedAction = "Добавьте отдельные правила для разрешённых направлений.")
                } else {
                    rule
                }
            },
        )
    }

    private fun defaultProfiles(): List<TunnelProfile> = listOf(
        TunnelProfile(
            id = DIRECT_PROFILE_ID,
            name = "Напрямую",
            type = TunnelType.Direct,
            description = "Трафик остаётся в текущей сети устройства без тоннеля.",
            mockOnly = false,
            platformNotes = "Подходит для Android, Linux и Windows как модель прямого выхода.",
        ),
        TunnelProfile(
            id = BLOCK_PROFILE_ID,
            name = "Заблокировать",
            type = TunnelType.Block,
            description = "Маршрут должен быть запрещён политикой пользователя.",
            mockOnly = false,
        ),
        TunnelProfile(
            id = XRAY_GERMANY_PROFILE_ID,
            name = "Xray Германия",
            type = TunnelType.XrayMock,
            description = "Mock-профиль будущего Xray-маршрута через Германию.",
            platformNotes = MOCK_PROFILE_LIMITATION,
        ),
        TunnelProfile(
            id = HYSTERIA2_NL_PROFILE_ID,
            name = "Hysteria2 Нидерланды",
            type = TunnelType.Hysteria2Mock,
            description = "Mock-профиль будущего быстрого Hysteria2-маршрута для медиа.",
            platformNotes = MOCK_PROFILE_LIMITATION,
        ),
        TunnelProfile(
            id = OPENVPN_WORK_PROFILE_ID,
            name = "OpenVPN Работа",
            type = TunnelType.OpenVpnMock,
            description = "Mock-профиль будущего рабочего OpenVPN-маршрута.",
            platformNotes = MOCK_PROFILE_LIMITATION,
        ),
        TunnelProfile(
            id = SOCKS5_WORK_VM_PROFILE_ID,
            name = "SOCKS5 рабочая VM",
            type = TunnelType.Socks5Mock,
            description = "Mock-профиль будущего SOCKS5-прокси на рабочей виртуальной машине.",
            platformNotes = MOCK_PROFILE_LIMITATION,
        ),
    )

    private fun defaultDnsPolicies(): List<DnsPolicy> = listOf(
        DnsPolicy(
            id = SYSTEM_DNS_ID,
            name = "System DNS",
            type = DnsPolicyType.System,
            description = "Использовать системный DNS текущей платформы. Сейчас это только метаданные симуляции.",
        ),
        DnsPolicy(
            id = DIRECT_DNS_ID,
            name = "Direct DNS",
            type = DnsPolicyType.Direct,
            serverText = "Провайдер / локальная сеть",
            resolveThroughProfileId = DIRECT_PROFILE_ID,
            description = "DNS должен оставаться напрямую для банков, госуслуг и платежей. Реальное применение будет позже.",
        ),
        DnsPolicy(
            id = WORK_DNS_ID,
            name = "Work DNS mock",
            type = DnsPolicyType.WorkMock,
            serverText = "10.0.0.53 / corp.local",
            resolveThroughProfileId = OPENVPN_WORK_PROFILE_ID,
            description = "Mock-политика корпоративного DNS через рабочий маршрут.",
        ),
        DnsPolicy(
            id = TUNNEL_DNS_ID,
            name = "Tunnel DNS mock",
            type = DnsPolicyType.TunnelMock,
            serverText = "DNS внутри выбранного тоннеля",
            resolveThroughProfileId = HYSTERIA2_NL_PROFILE_ID,
            description = "Mock-политика DNS внутри медиа/тоннельного профиля.",
        ),
    )

    private fun defaultRules(): List<RouteRule> = listOf(
        RouteRule(
            id = "banking_direct",
            name = "Банки, госуслуги и платежи напрямую",
            type = RouteRuleType.APP_GROUP,
            targetProfileId = DIRECT_PROFILE_ID,
            dnsPolicyId = DIRECT_DNS_ID,
            priority = 10,
            matchers = listOf("gosuslugi", "bank", "sber", "tinkoff", "tbank", "payment"),
            appMatchers = listOf(
                AppMatcher(AppMatcherPlatform.Android, "ru.sberbankmobile", "СберБанк Онлайн"),
                AppMatcher(AppMatcherPlatform.Any, "tbank", "T-Банк"),
            ),
            reason = "Финансовые и государственные сервисы обычно лучше оставлять напрямую, чтобы снизить риск блокировок и подозрительных входов.",
            technicalDetails = "APP_GROUP, priority 10, text/app matchers for banks/government/payment.",
            recommendedAction = "Оставьте эти сервисы напрямую и проверяйте DNS-утечки при будущей реализации реального DNS-маршрута.",
        ),
        RouteRule(
            id = "telegram_xray",
            name = "Telegram через Xray Germany",
            type = RouteRuleType.APP_GROUP,
            targetProfileId = XRAY_GERMANY_PROFILE_ID,
            dnsPolicyId = TUNNEL_DNS_ID,
            priority = 20,
            matchers = listOf("telegram", "tg"),
            appMatchers = listOf(
                AppMatcher(AppMatcherPlatform.Android, "org.telegram.messenger", "Telegram"),
                AppMatcher(AppMatcherPlatform.Linux, "telegram-desktop", "Telegram Desktop"),
                AppMatcher(AppMatcherPlatform.Windows, "Telegram.exe", "Telegram Windows"),
            ),
            reason = "Мессенджер направляется в mock-профиль Xray для проверки будущей политики маршрутизации.",
            technicalDetails = "APP_GROUP, priority 20, platform-neutral app matchers.",
            recommendedAction = "Проверьте, подходит ли выбранный регион, и помните, что реальный Xray ещё не подключён.",
        ),
        RouteRule(
            id = "youtube_hysteria2",
            name = "YouTube и медиа через Hysteria2 NL",
            type = RouteRuleType.DOMAIN,
            targetProfileId = HYSTERIA2_NL_PROFILE_ID,
            dnsPolicyId = TUNNEL_DNS_ID,
            priority = 30,
            matchers = listOf("youtube.com", "youtu.be", "googlevideo.com", "youtube", "googlevideo"),
            reason = "Медиа-домены отправляются в быстрый mock-тоннель, чтобы показать будущий сценарий разделения трафика.",
            technicalDetails = "DOMAIN, priority 30, media domains.",
            recommendedAction = "Используйте диагностику только как симуляцию: Hysteria2 пока не выполняет реальное подключение.",
        ),
        RouteRule(
            id = "work_10",
            name = "Рабочая сеть 10.0.0.0/8",
            type = RouteRuleType.CIDR,
            targetProfileId = OPENVPN_WORK_PROFILE_ID,
            dnsPolicyId = WORK_DNS_ID,
            priority = 40,
            matchers = listOf("10.0.0.0/8"),
            reason = "Частная рабочая сеть должна идти через рабочий mock-профиль OpenVPN.",
            technicalDetails = "CIDR, priority 40, 10.0.0.0/8.",
            recommendedAction = "Когда появится реальный VPN, проверьте доступность внутренних адресов и корпоративный DNS.",
        ),
        RouteRule(
            id = "work_172",
            name = "Рабочая сеть 172.16.1.0/22",
            type = RouteRuleType.CIDR,
            targetProfileId = OPENVPN_WORK_PROFILE_ID,
            dnsPolicyId = WORK_DNS_ID,
            priority = 50,
            matchers = listOf("172.16.1.0/22"),
            reason = "Вторая частная рабочая подсеть направляется в рабочий mock-профиль.",
            technicalDetails = "CIDR, priority 50, 172.16.1.0/22.",
            recommendedAction = "Сверьте подсеть с реальной корпоративной маршрутизацией перед включением будущего VPN.",
        ),
        RouteRule(
            id = "corp_domains",
            name = "GitLab/Jira/Confluence и *.corp через OpenVPN Work",
            type = RouteRuleType.DOMAIN,
            targetProfileId = OPENVPN_WORK_PROFILE_ID,
            dnsPolicyId = WORK_DNS_ID,
            priority = 55,
            matchers = listOf("gitlab.corp", "jira.corp", "confluence.corp", "*.corp"),
            reason = "Корпоративные домены требуют рабочего маршрута и корпоративной DNS-политики.",
            technicalDetails = "DOMAIN, priority 55, corp domains with Work DNS mock.",
            recommendedAction = "Проверьте, что корпоративные домены не уходят в системный DNS после будущей реализации DNS-маршрутизации.",
        ),
        RouteRule(
            id = "blocked_domain",
            name = "Блокировать подозрительные домены",
            type = RouteRuleType.DOMAIN,
            targetProfileId = BLOCK_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 60,
            matchers = listOf("blocked.example", "suspicious.example", "malware.test"),
            reason = "Демонстрационное правило показывает безопасную блокировку явно нежелательных направлений.",
            technicalDetails = "DOMAIN, priority 60, demonstration block list.",
            recommendedAction = "Добавляйте только понятные локальные правила. Приложение не выполняет скрытую фильтрацию или облачные проверки.",
        ),
        RouteRule(
            id = "default_direct",
            name = "По умолчанию напрямую",
            type = RouteRuleType.DEFAULT,
            targetProfileId = DIRECT_PROFILE_ID,
            dnsPolicyId = SYSTEM_DNS_ID,
            priority = 1000,
            matchers = emptyList(),
            reason = "Если отдельное правило не подошло, трафик остаётся напрямую.",
            technicalDetails = "DEFAULT, priority 1000.",
            recommendedAction = "Добавьте более точное правило, если этому направлению нужен тоннель, блокировка или отдельная DNS-политика.",
        ),
    )
}
