# ViRouteFS third-party components

ViRouteFS application code is `GPL-3.0-or-later`.

This file records actual APK inputs separately from future candidates.

## Native files bundled in the APK

### sing-box libbox

- Upstream: https://github.com/SagerNet/sing-box
- Tag: `v1.14.0-beta.13`
- Commit: `2dea956ea11ed9fdc47dc69fba56bea71c69ea9b`
- Artifact: `app/libs/libbox.aar`
- SHA-256: `57a6029fcd63655f2fb189708a07a482a75d1a717663639199e38f977ba393db`
- native ELF alignment: 16 KiB
- License: upstream `GPL-3.0-or-later` notice plus its additional naming/association condition
- Rebuild: `tools/build-libbox.ps1`
- Local build difference: upstream `with_openvpn` and `with_openconnect` are enabled, `with_naive_outbound` is omitted, and the product is branded only as ViRouteFS.

The exact upstream license text is included in `app/src/main/assets/licenses/sing-box-LICENSE.txt`. The application’s full GPL-3.0 text is included as `GPL-3.0.txt`.

### ByeDPI

- Upstream: https://github.com/hufrea/byedpi
- Commit: `ba532298de7b28cfe854aea83d061369d13ca290`
- Artifact: `app/src/main/jniLibs/arm64-v8a/libbyedpi.so`
- SHA-256: `abae93da6e426da5bbe5611f53a550eccb021d7be88b2c13865461024c4862d1`
- native ELF alignment: 16 KiB
- License: MIT
- Rebuild: `tools/build-byedpi.ps1`

The copyright and MIT permission notice are included in `app/src/main/assets/licenses/byedpi-MIT.txt`.

### Xray-core

- Upstream: https://github.com/XTLS/Xray-core
- Commit: `94ffd50060f1cfd5d7482ec90a23a92bdefdff68`
- Go module version: `v1.260327.1-0.20260601021109-94ffd50060f1`
- Artifact: `app/src/main/jniLibs/arm64-v8a/libxray.so`
- SHA-256: `9bb0b815086395164066b5fa27b1797bf9a0fcc493d1491f02166560604dcaff`
- native ELF load alignment: 64 KiB, compatible with 16 KiB Android pages
- License: Mozilla Public License 2.0
- Rebuild: `tools/build-xray.ps1`
- Local integration: the unmodified upstream command is cross-compiled as an app-private Android arm64 PIE executable. ViRouteFS generates an ephemeral localhost-only SOCKS configuration and does not copy application code from v2rayNG or AndroidLibXrayLite.

The exact MPL-2.0 text from the pinned Xray-core revision is included in `app/src/main/assets/licenses/Xray-core-MPL-2.0.txt`. The runtime configuration is removed when the child process stops.

### zapret2

- Upstream: https://github.com/bol-van/zapret2
- Release: `v1.0.4`
- Commit/tag target: `2c21faa80e1acb71ddceb8b49176f266b7d33f05`
- Upstream release archive SHA-256: `5760b6d41c09459fff00b4a6fec5437a471a00aac15f734723ede149cd26c709`
- Artifact: `app/src/main/jniLibs/arm64-v8a/libzapret2.so`
- Artifact SHA-256: `2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090`
- License: MIT
- Re-fetch and verification: `tools/fetch-zapret2.ps1`

The Android arm64 `nfqws2` executable and the standard Lua runtime files are
bundled only for the optional, disabled-by-default root module named
**Адаптация соединений**. They are not used by the ordinary `VpnService` path.
The upstream MIT notice is included in
`app/src/main/assets/licenses/zapret2-MIT.txt`.

### tcpdump and libpcap

- Upstream: https://github.com/the-tcpdump-group/tcpdump and https://github.com/the-tcpdump-group/libpcap
- tcpdump release: `4.99.6`
- libpcap release: `1.10.6`
- Official source archive SHA-256: `40a8cefd45f0d2a06827e6658efb830d484868c449ad80f7efb33516af44f3da` (tcpdump) and `ec97d1206bdd19cb6bdd043eaa9f0037aa732262ec68e070fd7c7b5f834d5dfc` (libpcap)
- Artifact: `app/src/main/jniLibs/arm64-v8a/libtcpdump.so`
- Artifact SHA-256: `adb46aa539d42efb6d07c1afc42edc39954fd59a46c09561411bb98bb176c4da`
- Native ELF load alignment: 16 KiB
- License: BSD 3-Clause
- Rebuild and verification: `tools/build-tcpdump.ps1`

The static libpcap and tcpdump command are built from the official release
archives with Android NDK `27.0.12077973`. Crypto, SMB, remote capture,
Bluetooth, netmap, D-Bus and RDMA integrations are disabled. The result is
used only by the optional, explicit root PCAP screen. ViRouteFS supplies fixed
capture modes rather than accepting user shell or BPF text, caps captures at
25,000 packets and less than 4 MiB, and exports them only through an Android
system file picker. The exact BSD notice is included in
`app/src/main/assets/licenses/tcpdump-libpcap-BSD.txt`.

### WireGuard Android tunnel library and command tools

- Official upstream: https://git.zx2c4.com/wireguard-android
- Maven coordinate: `com.wireguard.android:tunnel:1.0.20260102`
- Maven AAR SHA-256: `2b9c16db026496123e4db695d26d03d1958a201096c7c4c89b21077dc70f3119`
- Signed upstream tag: `1.0.20260102`
- Tagged commit: `09b75c2bd37f749e2a8c85876394854113c74be7`
- wireguard-tools source commit: `e2ecaaa739144997ccff89d6ad6ec81698ea6ced`
- wireguard-tools source archive SHA-256: `b5f838a044e9daa19eb011524efd813d12997fe5b44c1224a15cde4e99c10f75`
- Bundled Android ABI: `arm64-v8a`
- Native commands inside the AAR: `libwg.so`, `libwg-quick.so`, and `libwg-go.so`
- Licenses: Apache-2.0 for the Android tunnel/config library, GPL-2.0 for the
  separately executed WireGuard command tools, and MIT for wireguard-go

The library is used only by the explicit, disabled-by-default **Системный
WireGuard (root)** screen. ViRouteFS converts an already saved WireGuard
profile into a bounded wg-quick configuration, validates it with the official
parser, and invokes only a fixed allow-list of commands. It does not call the
upstream persistent root shell or its Magisk policy update. The recovery copy
is encrypted with Android Keystore and is removed only after an addressed
`wg-quick down` succeeds. The existing sing-box/VpnService WireGuard path
remains available without root.

The Apache-2.0 text is included as
`app/src/main/assets/licenses/Apache-2.0.txt`, the complete GPL-2.0 text as
`app/src/main/assets/licenses/WireGuard-tools-GPL-2.0.txt`, and the wireguard-go
MIT notice as `app/src/main/assets/licenses/WireGuard-go-MIT.txt`.
The exact wireguard-tools source archive is attached to every GitHub release
that contains these GPL-2.0 commands.

### Android libraries

AndroidX, Jetpack Compose, Kotlin coroutines and their exact Maven coordinates are declared in `app/build.gradle.kts`. They are primarily Apache-2.0 components. Their upstream notices and dependency metadata must remain available with any public distribution.

CameraX `1.6.1` (`camera-camera2`, `camera-lifecycle`, and `camera-view`) is used
only for the local QR camera preview and frame delivery. ZXing core `3.5.4` is
used for local QR decoding. Both are distributed under Apache License 2.0.
ViRouteFS does not use Google Play Services for scanning, does not modify these
libraries, and includes the Apache-2.0 text as
`app/src/main/assets/licenses/Apache-2.0.txt`. The upstream ZXing attribution
notice is preserved as `app/src/main/assets/licenses/ZXing-NOTICE.txt`.

- CameraX upstream: https://developer.android.com/jetpack/androidx/releases/camera
- ZXing upstream: https://github.com/zxing/zxing

SnakeYAML Engine `3.0.1` parses a bounded subset of user-supplied Clash YAML.
It is distributed under Apache License 2.0. ViRouteFS uses the safe generic
loader with duplicate keys, recursive keys and non-scalar keys disabled; no
user-specified Java class is constructed.

- SnakeYAML Engine upstream: https://bitbucket.org/snakeyaml/snakeyaml-engine
- Maven coordinate: `org.snakeyaml:snakeyaml-engine:3.0.1`

## Not bundled

- AndroidLibXrayLite (the wrapper was used only as an API/build reference and is not shipped);
- separate OpenVPN 2 or OpenVPN 3 libraries (OpenVPN client support is supplied by bundled sing-box);
- strongSwan;
- Tor executable;
- separate OpenConnect, ZeroTier and SoftEther adapters (OpenConnect client support is supplied by bundled sing-box);
- Naive/Cronet.

PPTP, L2TP and SSTP have no selected binary. See `ENGINE_LICENSE_MATRIX.md` for the security and compatibility boundary.
