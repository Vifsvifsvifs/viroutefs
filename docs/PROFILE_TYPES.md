# Network profile types

The UI uses one catalog so a protocol name is never confused with working runtime support.

## Runtime-ready in the current arm64 build

| Type | Runtime |
| --- | --- |
| System / Block | Built into ViRouteFS/sing-box |
| Совместимость TCP/TLS | Pinned MIT-licensed ByeDPI local SOCKS process; upstream name remains in technical and license details |
| OpenVPN client | sing-box 1.14 alpha endpoint |
| OpenConnect / AnyConnect family | sing-box 1.14 alpha endpoint |
| VLESS, TLS, REALITY | sing-box |
| SOCKS5 | sing-box |
| VMess, Trojan | sing-box advanced profile |
| Shadowsocks, Shadowsocks 2022 | sing-box advanced profile |
| Hysteria v1/v2, Snell v4/v6, TUIC, AnyTLS | sing-box advanced profile |
| HTTP, HTTPS proxy, SSH | sing-box advanced profile |
| WireGuard | sing-box endpoint |
| Tailscale/Headscale-compatible | sing-box endpoint |

Advanced profiles require a JSON fragment because these protocols have different option sets. ViRouteFS overwrites the internal tag, rejects full sing-box configurations and runs native config validation before saving.

## Planned, not selectable as working

- IKEv2/IPsec and older enterprise IPsec modes;
- ZeroTier and SoftEther;
- Tor executable;
- ShadowTLS chain editor;
- Naive/Cronet.

## Legacy-disabled

PPTP, unencrypted L2TP and SSTP are displayed only to explain compatibility limits. They have no selected binary and cannot be saved as active secure tunnels.
