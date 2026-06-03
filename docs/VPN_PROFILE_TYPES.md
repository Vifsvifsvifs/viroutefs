# VPN profile types

0.4.1-alpha broadens the connection profile model and UI. These profile types are **model/UI options only** in this release; real engines are not implemented.

## Base

- Direct
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
