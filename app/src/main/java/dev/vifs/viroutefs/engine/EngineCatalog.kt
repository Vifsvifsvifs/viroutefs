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
        ready(TunnelType.Direct, "Встроенный маршрут через сеть Android.", EngineBackend.BuiltIn),
        ready(TunnelType.Block, "Запрет сети с безопасным поведением fail-closed.", EngineBackend.BuiltIn),
        ready(TunnelType.ByeDpi, "Локальный режим совместимости TCP/TLS для сетей с мешающим DPI; это не VPN и не скрывает IP-адрес.", EngineBackend.ByeDpi),

        ready(TunnelType.VLESS, "VLESS, включая TLS и REALITY.", EngineBackend.SingBox),
        planned(TunnelType.XrayVlessReality, "Совместимый импорт старых Xray/VLESS-профилей.", EngineBackend.SingBox),
        ready(TunnelType.VMess, "VMess через общий маршрутизатор.", EngineBackend.SingBox),
        ready(TunnelType.Trojan, "Trojan через общий маршрутизатор.", EngineBackend.SingBox),
        ready(TunnelType.Shadowsocks, "Shadowsocks через общий маршрутизатор.", EngineBackend.SingBox),
        ready(TunnelType.Shadowsocks2022, "Современные методы Shadowsocks 2022.", EngineBackend.SingBox),
        ready(TunnelType.WireGuard, "WireGuard как отдельный endpoint.", EngineBackend.SingBox),
        ready(TunnelType.Hysteria, "Hysteria v1 для совместимости с существующими серверами.", EngineBackend.SingBox),
        ready(TunnelType.Hysteria2, "QUIC-туннель Hysteria2.", EngineBackend.SingBox),
        ready(TunnelType.Snell, "Snell v4/v6 через общий маршрутизатор.", EngineBackend.SingBox),
        ready(TunnelType.Tuic, "QUIC-туннель TUIC.", EngineBackend.SingBox),
        ready(TunnelType.AnyTls, "AnyTLS через общий маршрутизатор.", EngineBackend.SingBox),
        planned(TunnelType.NaiveProxy, "NaiveProxy через общий маршрутизатор.", EngineBackend.SingBox),
        planned(TunnelType.ShadowTls, "ShadowTLS как транспорт/профиль.", EngineBackend.SingBox),
        ready(TunnelType.Socks5, "SOCKS5 с локальной проверкой и маршрутизацией.", EngineBackend.SingBox),
        ready(TunnelType.HttpProxy, "Обычный HTTP proxy.", EngineBackend.SingBox),
        ready(TunnelType.HttpsProxy, "HTTP proxy с TLS до сервера.", EngineBackend.SingBox),
        ready(TunnelType.SshTunnel, "SSH-туннель.", EngineBackend.SingBox),
        planned(TunnelType.Tor, "Нужен отдельный проверенный Tor executable для Android; в текущий APK он не встроен.", EngineBackend.SingBox),

        ready(TunnelType.OpenVpn, "OpenVPN-клиент в sing-box 1.14 alpha; профиль проверяется нативным движком перед сохранением.", EngineBackend.SingBox),
        ready(TunnelType.OpenConnectAnyConnect, "OpenConnect для AnyConnect, GlobalProtect, Fortinet, F5, Pulse и Juniper; интерактивный SSO пока требует готового cookie.", EngineBackend.SingBox),
        planned(TunnelType.Ikev2IpSec, "IKEv2/IPsec для корпоративных сетей.", EngineBackend.StrongSwan),
        planned(TunnelType.IpSecXAuth, "IPsec XAuth для старых корпоративных сетей.", EngineBackend.StrongSwan),
        planned(TunnelType.IpSecPsk, "IPsec PSK для совместимых корпоративных сетей.", EngineBackend.StrongSwan),
        ready(TunnelType.TailscaleCompatible, "Tailscale-compatible endpoint.", EngineBackend.SingBox),
        ready(TunnelType.HeadscaleCompatible, "Headscale-compatible endpoint.", EngineBackend.SingBox),
        planned(TunnelType.ZeroTier, "Отдельный сетевой адаптер ZeroTier.", EngineBackend.ExternalAdapter),
        planned(TunnelType.SoftEther, "Корпоративная совместимость SoftEther.", EngineBackend.ExternalAdapter),

        legacy(TunnelType.L2tpIpSec, "Только для старого оборудования; отдельный Android-движок ещё не подключён, предпочтительнее IKEv2."),
        legacy(TunnelType.L2tp, "Без IPsec трафик не шифруется; отдельный Android-движок ещё не подключён."),
        legacy(TunnelType.Pptp, "Криптографически устарел; отдельный Android-движок ещё не подключён."),
        legacy(TunnelType.Sstp, "Legacy-совместимость; отдельный проверенный Android-движок ещё не подключён."),

        planned(TunnelType.Brook, "Не входит в первый runtime-набор.", EngineBackend.ExternalAdapter),
    )

    fun descriptor(type: TunnelType): ProtocolDescriptor? = protocols.firstOrNull { it.type == type }

    val selectableProtocols: List<ProtocolDescriptor>
        get() = protocols.filterNot {
            it.type == TunnelType.Direct ||
                it.type == TunnelType.Block ||
                it.type == TunnelType.ByeDpi
        }

    private fun ready(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        availability = ProtocolAvailability.RuntimeReady,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )

    private fun planned(type: TunnelType, summary: String, backend: EngineBackend) = ProtocolDescriptor(
        type = type,
        backend = backend,
        availability = ProtocolAvailability.AuditedPlanned,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )

    private fun legacy(type: TunnelType, summary: String) = ProtocolDescriptor(
        type = type,
        backend = EngineBackend.LegacyAdapter,
        availability = ProtocolAvailability.LegacyDisabled,
        summary = summary,
        supportsRouteRules = true,
        supportsCustomDns = true,
    )
}

internal data class ProtocolDescriptor(
    val type: TunnelType,
    val backend: EngineBackend,
    val availability: ProtocolAvailability,
    val summary: String,
    val supportsRouteRules: Boolean,
    val supportsCustomDns: Boolean,
)

internal enum class EngineBackend(val label: String, val licenseDecision: String) {
    BuiltIn("ViRouteFS", "GPL-3.0-or-later"),
    SingBox("sing-box", "GPL-3.0-or-later"),
    StrongSwan("strongSwan", "GPL-2.0-or-later"),
    ByeDpi("ByeDPI", "MIT"),
    LegacyAdapter("Legacy adapter", "No binary selected"),
    ExternalAdapter("External adapter", "Requires separate audit"),
}

internal enum class ProtocolAvailability(val userLabel: String) {
    RuntimeReady("Работает в текущем движке"),
    AuditedPlanned("Лицензия проверена, подключение движка в работе"),
    LegacyDisabled("Устаревший и небезопасный; выключен по умолчанию"),
}
