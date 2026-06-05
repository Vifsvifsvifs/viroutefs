# ViRouteFS Network profile types — 0.6.7-alpha planning

This document describes current and future Network profile types. It is documentation/planning only: unimplemented profile type buttons must not be exposed in the normal app UI yet.

Every future type must preserve the ViRouteFS safety model: explicit user control, local-first operation, no hidden telemetry, no packet payload logging, no silent fallback from a selected unavailable profile, and fail-closed / Block behavior when required.

| Type | User-facing name | Category | Expected role in routing | Implementation status |
| --- | --- | --- | --- | --- |
| System | System / Система | System route | Built-in internal route target for traffic without a more specific ViRouteFS rule. It represents the normal Android system path inside the ViRouteFS model. | planned |
| Block | Block / Блокировать | System route | Built-in deny target for traffic that should be closed instead of sent through another profile. | planned |
| SOCKS5 | SOCKS5 | Proxy | Early real outbound candidate for validating route-target architecture without full VPN protocol complexity. | planned |
| HTTP proxy | HTTP proxy | Proxy | Early outbound candidate for explicit HTTP proxy routing where supported by the future engine layer. | planned |
| HTTPS proxy | HTTPS proxy | Proxy | Early outbound candidate for explicit HTTPS proxy routing where supported by the future engine layer. | planned |
| WireGuard | WireGuard | VPN | Future VPN profile for WireGuard-style tunnels through a GPL-compatible userspace implementation. | research |
| OpenVPN3 | OpenVPN3 | VPN | Preferred future OpenVPN-compatible profile path using OpenVPN3 Core rather than embedding GPLv2-only OpenVPN 2.x core. | research |
| Xray / VLESS / Reality | Xray / VLESS / Reality | VPN/tunnel | Future tunnel/import profile family for Xray-based configurations, subject to license and Android feasibility audit. | research |
| Hysteria2 | Hysteria2 | VPN/tunnel | Future external tunnel profile candidate for explicit route targets. | research |
| Shadowsocks | Shadowsocks | Proxy/tunnel | Future outbound profile candidate using a permissive or GPL-compatible implementation. | research |
| Trojan | Trojan | VPN/tunnel | Future tunnel/import profile candidate, potentially related to Xray-compatible imports. | research |
| TUIC | TUIC | VPN/tunnel | Future tunnel candidate requiring deeper audit before any profile exposure. | research |
| NaiveProxy | NaiveProxy | Proxy/tunnel | Future proxy/tunnel candidate requiring deeper audit before any profile exposure. | research |
| OpenConnect / AnyConnect-compatible | OpenConnect / AnyConnect-compatible | VPN | Future enterprise VPN compatibility candidate requiring deeper license, Android, and security review. | research |
| IKEv2/IPSec | IKEv2/IPSec | VPN | Future VPN profile candidate requiring Android feasibility, native dependency, and security review. | research |
| SoftEther | SoftEther | VPN | Future VPN profile candidate requiring deeper audit before any profile exposure. | research |
| ByeDPI-style local DPI bypass | ByeDPI-style local DPI bypass | Local DPI bypass | Future local profile type, not a full VPN. It may appear as a Network profile and route target; if selected for an app/domain and unavailable, it must fail closed / Block and never fallback to another profile. | research |

## UI exposure rule

Do not expose unimplemented profile type buttons, fake configured tunnels, or fake route targets in the normal app UI. Future profile types may be documented here and linked from admin/developer help, but normal users should only see features that have real, safe behavior.

## VLESS profile model in 0.8.4-alpha

VLESS profiles are configuration-only in 0.8.4-alpha. The app can store and validate local profile fields for route decision preview, including UUID and placeholder TLS/REALITY metadata, but it does not connect to VLESS servers, forward packets, write packets back to TUN, implement REALITY/XTLS runtime, or proxy DNS. Route decision preview must warn: "Selected profile is VLESS. Runtime forwarding is not enabled yet."

`routing_config.json` and user exports may contain VLESS connection identifiers such as UUID, host, SNI, and placeholder key metadata. Treat exported routing configs as sensitive local files. UUID values must not appear in summaries, diagnostics text, logs, or route-preview text. No telemetry, cloud upload, analytics, ads, background validation, startup tests, or auto-connect behavior is added.
