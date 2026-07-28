# ViRouteFS engine and license matrix

ViRouteFS is distributed under `GPL-3.0-or-later`. This is an engineering compliance record, not legal advice.

The Android app uses one `VpnService`. App, domain, IP and CIDR rules select a local outbound or endpoint. An unavailable selected target is mapped to `Block`; it must never silently fall back to `System` or another VPN.

## Bundled now

| Component | Role | Upstream license | Exact build | Decision |
| --- | --- | --- | --- | --- |
| sing-box | TUN, routing, DNS, Direct/Block, OpenVPN, OpenConnect, VLESS, SOCKS5, VMess, Trojan, Shadowsocks, Hysteria v1/v2, Snell, TUIC, AnyTLS, HTTP(S), SSH, WireGuard and Tailscale/Headscale | GPL-3.0-or-later plus upstream condition prohibiting derivative branding or implied association | `v1.14.0-alpha.50`, commit `3fcfadd5ee45c460115243b55d48b438279aeacd`; `with_openvpn` and `with_openconnect`; 16 KiB-aligned `libbox.aar` SHA-256 `f3729b42c247c257adc2c7d03b1134ed7139b6f77da174f69f036d4fa4c7b685` | Bundled. Product name remains ViRouteFS. Preserve exact license, source pointer and reproducible build script. |
| ByeDPI | Implements the user-facing **Совместимость TCP/TLS** app-private SOCKS5 route | MIT | commit `ba532298de7b28cfe854aea83d061369d13ca290`; 16 KiB-aligned `libbyedpi.so` SHA-256 `abae93da6e426da5bbe5611f53a550eccb021d7be88b2c13865461024c4862d1` | Bundled for arm64. Keep the upstream name in licenses and technical details. Never describe it as encryption, IP hiding, anonymity or a VPN. |
| AndroidX, Jetpack Compose, Kotlin coroutines | Android application/runtime libraries | Primarily Apache-2.0 | Maven coordinates are pinned in `app/build.gradle.kts` | Bundled. Preserve dependency notices and metadata with distributed source. |

The APK contains `GPL-3.0.txt`, the exact sing-box license notice and the ByeDPI MIT notice under `assets/licenses/`.

## Approved integration paths, not bundled

| Component | Purpose | License path | Current decision |
| --- | --- | --- | --- |
| strongSwan Android | IKEv2/IPsec, IPsec XAuth and IPsec PSK | GPL-2.0-or-later | Planned only after adaptation into the single-router architecture. |
| zapret2 | Optional packet-processing compatibility adapter | MIT; audited at `v1.0.3`, commit `b78b52c4cd7f843da3ff0848a3430afbd401bdf2` | Not bundled. Upstream Android execution expects NFQUEUE/root; a separate rootless adapter inside the existing `VpnService` is required. |
| WireGuard Android tunnel library | Optional fallback WireGuard adapter | Apache-2.0 | Not needed while the bundled sing-box WireGuard endpoint satisfies the architecture. |
| Tor | Local Tor outbound | Requires a separately audited Android Tor executable and its license set | sing-box configuration support alone is not sufficient; disabled until the executable is bundled and tested. |
| ZeroTier, SoftEther | Additional enterprise/mesh adapters | No binary selected | Planned or research only. |

Xray-core and AndroidLibXrayLite are no longer bundled. They were removed to avoid two Go runtimes in one Android process and to keep one routing engine.

## Excluded or disabled

- Naive/Cronet is omitted from the current libbox build. The pinned NDK 27 build cannot link the current upstream Cronet/Naive artifact that expects NDK 28.
- PPTP and unencrypted L2TP are cryptographically unsafe and remain disabled until a separately audited adapter exists; the UI must show the risk and must not claim a working tunnel.
- L2TP/IPsec and SSTP remain legacy compatibility labels only; no binary is selected.
- Unknown prebuilt binaries, proprietary tracking SDKs and dependencies with unclear redistribution terms must not enter the APK.

## DNS invariant

A route or profile may use Android system DNS or an explicit UDP, TCP, TLS, QUIC, HTTPS or HTTP/3 upstream. The DNS request may be detoured through a selected runtime profile. If that profile is unavailable or is `Block`, resolution is rejected instead of falling back to another resolver.

## Release source requirements

Every distributed APK must be accompanied by this repository revision or an equivalent complete source archive containing:

1. ViRouteFS application source;
2. `tools/build-libbox.ps1` and its exact sing-box commit;
3. `tools/build-byedpi.ps1` and its exact ByeDPI commit;
4. Gradle files, hashes and license notices;
5. any local modifications needed to reproduce the native artifacts.

Primary sources:

- https://github.com/SagerNet/sing-box
- https://sing-box.sagernet.org/configuration/outbound/
- https://sing-box.sagernet.org/configuration/endpoint/wireguard/
- https://sing-box.sagernet.org/configuration/endpoint/tailscale/
- https://github.com/hufrea/byedpi
- https://github.com/bol-van/zapret2
- https://github.com/OpenVPN/openvpn3
- https://github.com/strongswan/strongswan
- https://git.zx2c4.com/wireguard-android/about/
