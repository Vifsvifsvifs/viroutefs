# Routing model

ViRouteFS is an Android policy router built around a single `VpnService`.

## What can be routed

Rules can match:

- an Android application;
- a domain name or domain suffix;
- an IP address or CIDR network;
- all remaining traffic.

Every rule selects one profile. The profile may be a VPN/proxy tunnel, `Direct`,
`Block` or the built-in **Совместимость TCP/TLS** local route. Rules are ordered, and the first
matching rule wins.

When network control is enabled, every flow without a more specific rule uses
`System`: the phone's normal mobile-data or Wi-Fi uplink. A VPN profile is not
required to start network control. Explicit rules send only selected traffic to
a VPN/proxy tunnel, `Block`, **Совместимость TCP/TLS** or an explicit `System` route.

## DNS

Each rule may select its own DNS policy independently from its traffic profile:

- Android system DNS;
- a custom DNS server;
- `hosts` overrides;
- blocked DNS.

A custom DNS server supports IP/UDP, TCP, TLS, QUIC, HTTPS and HTTP/3 transports
and may itself be reached through a selected tunnel profile. If the selected DNS
detour is unavailable, the request is blocked rather than leaked to another
resolver.

## Runtime profiles

The current sing-box-backed profile model covers OpenVPN,
OpenConnect/AnyConnect, VLESS, SOCKS5, VMess, Trojan, Shadowsocks, Hysteria
v1/v2, Snell, TUIC, AnyTLS, HTTP/HTTPS proxy, SSH, WireGuard and
Tailscale/Headscale. The compatibility route is implemented by an embedded
ByeDPI local SOCKS process with a dedicated toggle; the upstream name remains
in technical and license details.

Tor, ShadowTLS chains, Naive, IKEv2/IPsec, L2TP, PPTP and SSTP remain outside
the current working runtime. The UI must not present these as active tunnels.
Unencrypted legacy protocols such as bare L2TP and PPTP are intentionally kept
disabled.

## Failure policy

Unsupported, disabled, invalid and failed targets compile to `Block`. ViRouteFS
never falls back from a requested protected route to `Direct` without an
explicit user rule. This fail-closed rule applies to explicit VPN/proxy routes;
the normal unmatched route is intentionally `System`.
