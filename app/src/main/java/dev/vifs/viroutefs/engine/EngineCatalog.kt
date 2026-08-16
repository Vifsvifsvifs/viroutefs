// SPDX-License-Identifier: GPL-3.0-or-later

package dev.vifs.viroutefs.engine

import dev.vifs.viroutefs.routing.TunnelType

/**
 * One source of truth for product claims about protocol support.
 *
 * A protocol must never be presented as working only because its name exists in
 * [TunnelType]. The UI and runtime use this catalog to distinguish a working
 * route, an audited implementation target, and an unsafe legacy protocol.
 */
internal object EngineCatalog {
    val protocols: List<ProtocolDescriptor> = listOf(
        deviceVerified(
            TunnelType.Direct,
            "Встроенный маршрут через сеть Android. Проверен на физическом Android-устройстве: запуск без VPN-профиля, IPv4, DNS и штатная остановка.",
            EngineBackend.BuiltIn,
        ),
        integrated(TunnelType.Block, "Запрет сети с безопасным поведением fail-closed.", EngineBackend.BuiltIn),
        integrated(TunnelType.ByeDpi, "Локальный режим совместимости TCP/TLS; это не VPN и не скрывает IP-адрес.", EngineBackend.ByeDpi),
        unavailable(
            TunnelType.Zapret2,
            "Отдельная «Адаптация соединений» на базе zapret2 v1.0.4 встроена в необязательный root-центр. Это системный NFQUEUE-модуль, а не VPN-профиль; до физической проверки он не доступен в списке туннелей.",
            EngineBackend.Zapret2,
        ),

        integrated(TunnelType.VLESS, "VLESS, включая TLS и REALITY.", EngineBackend.SingBox),
        integrated(TunnelType.XrayVlessReality, "Xray VLESS/XHTTP работает как локальный профиль за единым маршрутизатором.", EngineBackend.Xray),
        integrated(TunnelType.VMess, "VMess через общий маршрутизатор.", EngineBackend.SingBox),
        integrated(TunnelType.Trojan, "Trojan через общий маршрутизатор.", EngineBackend.SingBox),
        integrated(TunnelType.Shadowsocks, "Shadowsocks через общий маршрутизатор.", EngineBackend.SingBox),
        integrated(TunnelType.Shadowsocks2022, "Современные методы Shadowsocks 2022.", EngineBackend.SingBox),
        integrated(TunnelType.WireGuard, "WireGuard как отдельный userspace endpoint.", EngineBackend.SingBox),
        integrated(TunnelType.Hysteria, "Hysteria v1 для совместимости с существующими серверами.", EngineBackend.SingBox),
        integrated(TunnelType.Hysteria2, "QUIC-туннель Hysteria2.", EngineBackend.SingBox),
        integrated(TunnelType.Snell, "Snell v4/v6 через общий маршрутизатор.", EngineBackend.SingBox),
        integrated(TunnelType.Tuic, "QUIC-туннель TUIC.", EngineBackend.SingBox),
        integrated(TunnelType.AnyTls, "AnyTLS через общий маршрутизатор.", EngineBackend.SingBox),
        unavailable(TunnelType.NaiveProxy, "Текущая закреплённая libbox-сборка создана без NaiveProxy.", EngineBackend.SingBox),
        configured(TunnelType.ShadowTls, "JSON-профиль проверяется структурно; цепочки и физическая проверка ещё не завершены.", EngineBackend.SingBox),
        integrated(TunnelType.Socks5, "SOCKS5 с локальной проверкой и маршрутизацией.", EngineBackend.SingBox),
        integrated(TunnelType.HttpProxy, "Обычный HTTP proxy.", EngineBackend.SingBox),
        integrated(TunnelType.HttpsProxy, "HTTP proxy с TLS до сервера.", EngineBackend.SingBox),
        integrated(TunnelType.SshTunnel, "SSH-туннель.", EngineBackend.SingBox),
        unavailable(TunnelType.Tor, "Проверенный Tor executable для Android в текущий APK не встроен.", EngineBackend.ExternalAdapter),

        integrated(TunnelType.OpenVpn, "OpenVPN-клиент в закреплённой sing-box 1.14 alpha; до DeviceVerified статус не повышается.", EngineBackend.SingBox),
        integrated(TunnelType.OpenConnectAnyConnect, "OpenConnect интегрирован в runtime; интерактивный WebView SSO ещё не завершён.", EngineBackend.SingBox),
        model(TunnelType.Ikev2IpSec, "IKEv2/IPsec: адаптер strongSwan ещё не встроен.", EngineBackend.StrongSwan),
        model(TunnelType.IpSecXAuth, "IPsec XAuth: адаптер strongSwan ещё не встроен.", EngineBackend.StrongSwan),
        model(TunnelType.IpSecPsk, "IPsec PSK: адаптер strongSwan ещё не встроен.", EngineBackend.StrongSwan),
        integrated(TunnelType.TailscaleCompatible, "Tailscale-compatible userspace endpoint.", EngineBackend.SingBox),
        integrated(TunnelType.HeadscaleCompatible, "Headscale-compatible userspace endpoint.", EngineBackend.SingBox),
        model(TunnelType.ZeroTier, "Отдельный userspace-адаптер ZeroTier ещё не выбран.", EngineBackend.ExternalAdapter),
        model(TunnelType.SoftEther, "Клиентский userspace-адаптер SoftEther ещё не выбран.", EngineBackend.ExternalAdapter),

        unavailable(TunnelType.L2tpIpSec, "Legacy: userspace L2TP/PPP и IPsec-адаптер ещё не встроены.", EngineBackend.LegacyAdapter),
        unavailable(TunnelType.L2tp, "Незашифрованный legacy L2TP: userspace L2TP/PPP ещё не встроен.", EngineBackend.LegacyAdapter),
        unavailable(TunnelType.Pptp, "Криптографически устаревший PPTP: userspace GRE/PPP ещё не встроен.", EngineBackend.LegacyAdapter),
        unavailable(TunnelType.Sstp, "Legacy SSTP: проверенный userspace TLS/PPP-адаптер ещё не встроен.", EngineBackend.LegacyAdapter),

        model(TunnelType.Brook, "Адаптер Brook ещё не выбран и не прошёл лицензионный аудит.", EngineBackend.ExternalAdapter),
    )

    fun descriptor(type: TunnelType): ProtocolDescriptor? = protocols.firstOrNull { it.type == type }

    val selectableProtocols: List<ProtocolDescriptor>
        get() = protocols.filterNot {
            it.type == TunnelType.Direct ||
                it.type == TunnelType.Block ||
                it.type == TunnelType.ByeDpi ||
                it.type == TunnelType.Zapret2
        }

    private fun integrated(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        readiness = FeatureReadiness.RuntimeIntegrated,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )

    private fun deviceVerified(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        readiness = FeatureReadiness.DeviceVerified,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )

    private fun configured(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        readiness = FeatureReadiness.ConfigSupported,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )

    private fun model(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        readiness = FeatureReadiness.ModelOnly,
        summary = summary,
        supportsRouteRules = false,
        supportsCustomDns = false,
    )

    private fun unavailable(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        readiness = FeatureReadiness.Unavailable,
        summary = summary,
        supportsRouteRules = false,
        supportsCustomDns = false,
    )
}

internal data class ProtocolDescriptor(
    val type: TunnelType,
    val backend: EngineBackend,
    val readiness: FeatureReadiness,
    val summary: String,
    val supportsRouteRules: Boolean,
    val supportsCustomDns: Boolean,
) {
    val canCreateProfile: Boolean
        get() = readiness in setOf(
            FeatureReadiness.ConfigSupported,
            FeatureReadiness.RuntimeIntegrated,
            FeatureReadiness.DeviceVerified,
            FeatureReadiness.ProductionReady,
            FeatureReadiness.LegacyRestricted,
        )

    val canStartRuntime: Boolean
        get() = readiness in setOf(
            FeatureReadiness.RuntimeIntegrated,
            FeatureReadiness.DeviceVerified,
            FeatureReadiness.ProductionReady,
            FeatureReadiness.LegacyRestricted,
        )
}

internal enum class EngineBackend(val label: String, val licenseDecision: String) {
    BuiltIn("ViRouteFS", "GPL-3.0-or-later"),
    SingBox("sing-box", "GPL-3.0-or-later"),
    Xray("Xray-core", "MPL-2.0"),
    StrongSwan("strongSwan", "GPL-2.0-or-later"),
    ByeDpi("Движок совместимости ByeDPI", "MIT"),
    Zapret2("zapret2", "MIT; v1.0.4 bundled for optional root mode, device verification pending"),
    LegacyAdapter("Legacy adapter", "No binary selected"),
    ExternalAdapter("External adapter", "Requires separate audit"),
}

internal enum class FeatureReadiness(val userLabel: String) {
    ModelOnly("Есть только модель"),
    ConfigSupported("Профиль и проверка конфигурации"),
    RuntimeIntegrated("Движок интегрирован, нужен тест на телефоне"),
    DeviceVerified("Проверено на физическом устройстве"),
    ProductionReady("Полностью проверено"),
    Unavailable("Сейчас недоступно"),
    LegacyRestricted("Работает с legacy-ограничениями"),
}
