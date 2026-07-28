# ViRouteFS 0.12.0-beta.1

ViRouteFS moves from alpha to beta with a corrected default-routing model and an explicit readiness center.

## Main behavior

- Network control starts without adding a VPN profile.
- Unmatched traffic uses `System`: the phone's normal mobile-data or Wi-Fi connection.
- App, domain, IP and CIDR rules can route selected traffic to any configured VPN/proxy, `System`, `Block` or ByeDPI.
- An unavailable explicit target remains fail-closed and never silently falls back to another route.
- Existing schema 6 configurations with no default route migrate to schema 7 and `System`.

## Readiness center

The VPN screen now shows:

- configuration and default-route readiness;
- configured profiles, routes and DNS status;
- working, planned and legacy protocol groups;
- physical-device validation boundaries;
- an explicit local native-engine validation button for the complete configuration.

## Runtime-ready in this APK

OpenVPN, OpenConnect/AnyConnect-family profiles, VLESS/REALITY, SOCKS5, VMess,
Trojan, Shadowsocks/2022, Hysteria v1/v2, Snell, TUIC, AnyTLS, HTTP/HTTPS
proxy, SSH, WireGuard, Tailscale/Headscale, System, Block and ByeDPI.

## Still required

- end-to-end testing on a physical arm64 Android phone;
- confirmation of DNS detours and per-app attribution on real applications;
- separate audited engines for IKEv2/IPsec, IPsec XAuth/PSK and legacy L2TP/PPTP/SSTP;
- interactive browser-based OpenConnect SSO;
- ZeroTier, SoftEther and Tor adapters.

Flow Scanner stores connection metadata only. It does not record packet payloads
or decrypt HTTPS.
