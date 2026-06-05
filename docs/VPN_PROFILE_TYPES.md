# VPN profile types

0.4.1-alpha broadens the connection profile model and UI. These profile types are **model/UI options only** in this release; real engines are not implemented.

## Base

- System / Система (legacy Direct id/type may remain only for compatibility)
- Block

## Modern VPN/tunnel

- WireGuard
- OpenVPN
- OpenConnect / AnyConnect
- IKEv2 / IPSec
- SoftEther
- ZeroTier
- Tailscale-compatible
- Headscale-compatible

## Proxy / censorship-resistant / tunnel

- Xray / VLESS / Reality
- VMess
- Trojan
- Shadowsocks
- Shadowsocks 2022
- Hysteria2
- TUIC
- NaiveProxy
- Brook
- ShadowTLS
- SOCKS5
- HTTP proxy
- HTTPS proxy
- SSH tunnel

## Legacy / corporate old

- L2TP/IPSec
- L2TP
- PPTP
- SSTP
- IPSec XAuth
- IPSec PSK

## Legacy warnings

- PPTP: "Устаревший и небезопасный протокол. Используйте только для старых сетей, если другого варианта нет."
- L2TP without IPSec: "Устаревший и небезопасный режим."
- L2TP/IPSec and SSTP are marked as legacy/corporate compatibility.

## DNS metadata

Each network profile may reference a DNS policy. DNS is configured primarily on the DNS page; the Networks page only shows compact profile details.

## VLESS profile model in 0.8.5-alpha

VLESS profiles are configuration-only in 0.8.5-alpha. The app can store and validate local profile fields for route decision preview, including UUID, transport placeholders (`tcp`, `ws`, or `grpc`), and placeholder TLS/REALITY metadata. It can also import and explicitly export usable `vless://` URIs as documented in [`VLESS_URI_IMPORT_EXPORT.md`](VLESS_URI_IMPORT_EXPORT.md). It does not connect to VLESS servers, forward packets, write packets back to TUN, implement REALITY/XTLS runtime, or proxy DNS. Route decision preview must warn: "Selected profile is VLESS. Runtime forwarding is not enabled yet."

`routing_config.json`, routing exports, and explicit VLESS URI exports may contain VLESS connection identifiers such as UUID, host, SNI, and placeholder key metadata. Treat exported routing configs and VLESS URIs as sensitive local files. UUID values must not appear in summaries, diagnostics text, logs, route-preview text, or masked import previews. No telemetry, cloud upload, analytics, ads, background validation, startup tests, or auto-connect behavior is added.
