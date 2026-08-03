# ViRouteFS engine and license matrix

ViRouteFS is distributed under `GPL-3.0-or-later`. This is an engineering compliance record, not legal advice.

The Android app uses one `VpnService`. App, domain, IP and CIDR rules select a local outbound or endpoint. An unavailable selected target is mapped to `Block`; it must never silently fall back to `System` or another VPN.

## Bundled now

| Component | Role | Upstream license | Exact build | Decision |
| --- | --- | --- | --- | --- |
| sing-box | TUN, routing, DNS, Direct/Block, OpenVPN, OpenConnect, VLESS, SOCKS5, VMess, Trojan, Shadowsocks, Hysteria v1/v2, Snell, TUIC, AnyTLS, HTTP(S), SSH, WireGuard and Tailscale/Headscale | GPL-3.0-or-later plus upstream condition prohibiting derivative branding or implied association | `v1.14.0-alpha.50`, commit `3fcfadd5ee45c460115243b55d48b438279aeacd`; `with_openvpn` and `with_openconnect`; 16 KiB-aligned `libbox.aar` SHA-256 `f3729b42c247c257adc2c7d03b1134ed7139b6f77da174f69f036d4fa4c7b685` | Bundled. Product name remains ViRouteFS. Preserve exact license, source pointer and reproducible build script. |
| Xray-core | VLESS/XHTTP client behind localhost SOCKS endpoints; it does not own the Android TUN | MPL-2.0 | commit `94ffd50060f1cfd5d7482ec90a23a92bdefdff68`; Android arm64 PIE `libxray.so` SHA-256 `9bb0b815086395164066b5fa27b1797bf9a0fcc493d1491f02166560604dcaff`; 64 KiB ELF load alignment | Bundled as a separate app-private child process to avoid a second Gomobile runtime in the sing-box process. Upstream source is unmodified and reproducible with `tools/build-xray.ps1`. |
| ByeDPI | Implements the user-facing **Совместимость TCP/TLS** app-private SOCKS5 route | MIT | commit `ba532298de7b28cfe854aea83d061369d13ca290`; 16 KiB-aligned `libbyedpi.so` SHA-256 `abae93da6e426da5bbe5611f53a550eccb021d7be88b2c13865461024c4862d1` | Bundled for arm64. Keep the upstream name in licenses and technical details. Never describe it as encryption, IP hiding, anonymity or a VPN. |
| zapret2 | Implements the optional user-facing **Адаптация соединений (root)** system module; it is not a VPN profile | MIT | `v1.0.4`, commit/tag target `2c21faa80e1acb71ddceb8b49176f266b7d33f05`; upstream archive SHA-256 `5760b6d41c09459fff00b4a6fec5437a471a00aac15f734723ede149cd26c709`; Android arm64 `nfqws2` stored as `libzapret2.so`, SHA-256 `2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090` | Bundled but disabled by default. Requires an explicit root request, IPv4/IPv6 iptables and NFQUEUE queue-bypass. Runtime integration is local-only until physical KernelSU verification. |
| AndroidX, Jetpack Compose, Kotlin coroutines | Android application/runtime libraries | Primarily Apache-2.0 | Maven coordinates are pinned in `app/build.gradle.kts` | Bundled. Preserve dependency notices and metadata with distributed source. |

The APK contains `GPL-3.0.txt`, the exact sing-box license notice, the Xray-core MPL-2.0 text and the ByeDPI and zapret2 MIT notices under `assets/licenses/`.

## Approved integration paths, not bundled

| Component | Purpose | License path | Current decision |
| --- | --- | --- | --- |
| strongSwan Android | IKEv2/IPsec, IPsec XAuth and IPsec PSK | GPL-2.0-or-later | Planned only after adaptation into the single-router architecture. |
| WireGuard Android tunnel library | Optional fallback WireGuard adapter | Apache-2.0 | Not needed while the bundled sing-box WireGuard endpoint satisfies the architecture. |
| Tor | Local Tor outbound | Requires a separately audited Android Tor executable and its license set | sing-box configuration support alone is not sufficient; disabled until the executable is bundled and tested. |
| ZeroTier, SoftEther | Additional enterprise/mesh adapters | No binary selected | Planned or research only. |

AndroidLibXrayLite is not bundled. A second Gomobile AAR would conflict with sing-box's generated Go/Java bridge, so ViRouteFS ships the unmodified Xray-core command as a separate local process instead.

## Excluded or disabled

- Naive/Cronet is omitted from the current libbox build. The pinned NDK 27 build cannot link the current upstream Cronet/Naive artifact that expects NDK 28.
- PPTP and unencrypted L2TP are cryptographically unsafe and remain disabled until a separately audited adapter exists; the UI must show the risk and must not claim a working tunnel.
- L2TP/IPsec and SSTP remain legacy compatibility labels only; no binary is selected.
- Unknown prebuilt binaries, proprietary tracking SDKs and dependencies with unclear redistribution terms must not enter the APK.

## DNS invariant

A route or profile may use Android system DNS or an explicit UDP, TCP, TLS, QUIC, HTTPS or HTTP/3 upstream. The DNS request may be detoured through a selected runtime profile. If that profile is unavailable or is `Block`, resolution is rejected instead of falling back to System. An explicitly enabled ordered fallback advances only after timeout or transport failure. A separate Android bootstrap resolver is used only for endpoint hostnames that must be resolved before their selected tunnel exists.

## Release source requirements

Every distributed APK must be accompanied by this repository revision or an equivalent complete source archive containing:

1. ViRouteFS application source;
2. `tools/build-libbox.ps1` and its exact sing-box commit;
3. `tools/build-xray.ps1` and its exact Xray-core commit;
4. `tools/build-byedpi.ps1` and its exact ByeDPI commit;
5. `tools/fetch-zapret2.ps1`, its pinned upstream archive and per-file hashes;
6. Gradle files, hashes and license notices;
7. any local modifications needed to reproduce the native artifacts.

Primary sources:

- https://github.com/SagerNet/sing-box
- https://github.com/XTLS/Xray-core
- https://sing-box.sagernet.org/configuration/outbound/
- https://sing-box.sagernet.org/configuration/endpoint/wireguard/
- https://sing-box.sagernet.org/configuration/endpoint/tailscale/
- https://github.com/hufrea/byedpi
- https://github.com/bol-van/zapret2
- https://github.com/OpenVPN/openvpn3
- https://github.com/strongswan/strongswan
- https://git.zx2c4.com/wireguard-android/about/
