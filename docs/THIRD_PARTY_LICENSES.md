# ViRouteFS third-party components

ViRouteFS application code is `GPL-3.0-or-later`.

This file records actual APK inputs separately from future candidates.

## Native files bundled in the APK

### sing-box libbox

- Upstream: https://github.com/SagerNet/sing-box
- Tag: `v1.14.0-alpha.50`
- Commit: `3fcfadd5ee45c460115243b55d48b438279aeacd`
- Artifact: `app/libs/libbox.aar`
- SHA-256: `f3729b42c247c257adc2c7d03b1134ed7139b6f77da174f69f036d4fa4c7b685`
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

### Android libraries

AndroidX, Jetpack Compose, Kotlin coroutines and their exact Maven coordinates are declared in `app/build.gradle.kts`. They are primarily Apache-2.0 components. Their upstream notices and dependency metadata must remain available with any public distribution.

## Not bundled

- AndroidLibXrayLite (the wrapper was used only as an API/build reference and is not shipped);
- separate OpenVPN 2 or OpenVPN 3 libraries (OpenVPN client support is supplied by bundled sing-box);
- strongSwan;
- WireGuard Android tunnel library;
- Tor executable;
- zapret2 (`v1.0.3` was audited under MIT, but its NFQUEUE/root execution model is not bundled);
- separate OpenConnect, ZeroTier and SoftEther adapters (OpenConnect client support is supplied by bundled sing-box);
- Naive/Cronet.

PPTP, L2TP and SSTP have no selected binary. See `ENGINE_LICENSE_MATRIX.md` for the security and compatibility boundary.
