# ViRouteFS

**ViRouteFS** means **Visual Route & Flow Scanner**.

> **One Android VPN. Many internal routes.**<br>
> **Один системный VPN. Много внутренних маршрутов.**

ViRouteFS is a **local-first Android app** for understandable VPN routing, DNS policy, and Flow Scanner diagnostics. It is being built as a user-friendly way to see how routes, DNS decisions, and flow explanations should work before real traffic routing is enabled.

ViRouteFS is **free software** licensed under **GPL-3.0-or-later**. The project is designed for privacy and defensive diagnostics:

- No ads.
- No telemetry.
- No analytics.
- No tracking SDKs.
- No cloud upload of logs, routes, diagnostics, or future PCAP files.
- No hidden interception of third-party traffic.
- No offensive security features.

## Current status: 0.8.5-alpha

Version `0.8.5-alpha` adds explicit VLESS URI import/export for local configuration only and keeps the 0.8.x local packet-inspection safety boundary. Version `0.8.1-alpha` added a local in-memory packet inspector to the Android VpnService runtime skeleton. The app requests Android VPN permission, establishes the existing safe TUN preview, reads packets from the ParcelFileDescriptor in the opt-in TEST-NET developer route mode, parses IPv4 metadata locally, and shows the latest 50 packet summaries newest first in the VPN Runtime screen. Summaries contain timestamp, protocol, IPv4 source/destination addresses, TCP/UDP ports when present, and packet size. Packets are counted and dropped only; there is still no packet forwarding, no SOCKS5 forwarding, no VLESS runtime forwarding, no DNS proxying, no default route capture, no payload capture, no payload logging, no PCAP export, no persistence, no telemetry, and no background upload.

What exists now:

- Android `VpnService` permission flow exists.
- Foreground VPN preview service exists.
- Safe TUN preview exists.
- Optional TEST-NET route `203.0.113.0/24` remains for developer/local preview testing only and is hidden from normal user UI.
- Flow Scanner and VPN Runtime can show local counters/metadata summaries when developer diagnostics explicitly enable the TEST-NET preview.
- The app has Compose screens for Networks, Routes, DNS, Flow Scanner, Tools, and Settings with Help collapsed by default.
- GitHub Actions builds APK artifacts with dynamic version-based names.
- Published releases contain friendly APK assets, such as `ViRouteFS-0.6.15-alpha.apk`, attached to GitHub Releases.
- Stable update-over-install for alpha APKs requires the alpha signing secrets documented in [`docs/ALPHA_SIGNING.md`](docs/ALPHA_SIGNING.md).
- Settings includes a manual update checker that contacts GitHub Releases only after the user taps **Check for updates**. Settings → Updates shows the current version, newest available release, and a compact recent release history with notes previews and links. When a release has an APK asset, the user can tap **Download APK** and then **Install update** to open Android's system package installer.
- Routing configuration, DNS policy metadata, diagnostics reports, and logs are local-first.
- The built-in System / Система route is the safe internal default for apps without explicit rules in the ViRouteFS model; it is not bypass when network control is active.

What is intentionally not implemented yet:

- No real user traffic routing yet.
- No runtime full-device default-route enforcement yet.
- No DNS server is added to the VPN builder.
- No packet payload logging.
- No forwarding or proxying.
- No Xray, OpenVPN, WireGuard, Hysteria2, VLESS runtime, REALITY/XTLS runtime, or runtime SOCKS5 proxy engines yet. The internal SOCKS5 outbound connector is used for explicit manual diagnostics only, not runtime forwarding.
- No Play Store or F-Droid release is claimed yet.
- No background update checks, automatic APK downloads, silent install behavior, telemetry, analytics, tracking, ads, or cloud upload.

## 0.8.5-alpha VLESS profile model and URI import/export

ViRouteFS 0.8.5-alpha keeps VLESS as a configurable local profile type for route decision preview only and adds explicit `vless://` URI import/export. A VLESS profile can store name, host, port, UUID, transport placeholder (`tcp`, `ws`, or `grpc`), security mode (`none`, `tls`, or `reality` placeholder), encryption, flow, SNI, public-key (`pbk`) placeholder, short-ID (`sid`) placeholder, fingerprint (`fp`) placeholder, path, host header, enabled state, and validation status. Manual validation checks that host is not blank, port is in `1..65535`, UUID is valid, and transport placeholders are supported. See [`docs/VLESS_URI_IMPORT_EXPORT.md`](docs/VLESS_URI_IMPORT_EXPORT.md).

Safety boundaries for VLESS in 0.8.5-alpha:

- VLESS runtime forwarding is not implemented yet.
- This profile can be used for route decision preview only.
- ViRouteFS does not connect to a VLESS server, forward packets, write packets back to TUN, implement REALITY/XTLS runtime, or add DNS proxying.
- VLESS UUID values are treated as connection identifiers: they are hidden from profile summaries, route previews, diagnostics text, logs, and human-readable status lines.
- `routing_config.json`, manual route-config exports, and explicit VLESS URI exports may store the VLESS UUID locally because the profile is configuration data. Treat exports as sensitive because they can contain connection identifiers and private infrastructure details.
- No telemetry, cloud upload, analytics, ads, background checks, startup tests, auto-connect behavior, or automatic PCAP/log export is added.

## 0.8.1-alpha local packet inspector

ViRouteFS 0.8.1-alpha keeps the safe local-first VPN boundary and adds a metadata-only packet inspector for the opt-in TEST-NET route preview. The foreground VpnService establishes a TUN interface, reads packets from the ParcelFileDescriptor, parses IPv4 source/destination addresses, protocol, TCP/UDP ports when present, and packet size, then keeps only the latest 50 summaries in memory. Packets are dropped without forwarding them. The normal route-less TUN preview still adds no default route, and the developer TEST-NET route remains the only explicit packet-producing preview route. No payload, hostname, DNS proxying, PCAP export, persistence, packet forwarding, SOCKS5 forwarding, VLESS, telemetry, or cloud upload is added.

## 0.7.7-alpha SOCKS5 outbound connector abstraction

ViRouteFS 0.7.7-alpha introduces a small internal outbound connector model and a SOCKS5 TCP outbound connector. The connector can validate a target, perform SOCKS5 greeting/authentication, issue a SOCKS5 CONNECT request, map sanitized results, and then close the socket without sending HTTP requests, application payloads, raw packet payloads, or device traffic. The model does not contain passwords and connector results must not expose credentials.

The existing manual **Test CONNECT target** diagnostic now reuses this connector/shared protocol path so there is one CONNECT implementation for manual diagnostics. History, readiness summaries, and UI boundaries stay unchanged. Runtime forwarding is still not enabled yet: no TUN-to-SOCKS forwarding, no Android default-route capture, no real device traffic proxying, no background checks, no startup tests, no auto-connect, no silent DNS changes, and no telemetry or cloud upload.

## 0.7.6-alpha SOCKS5 readiness summaries

ViRouteFS 0.7.6-alpha summarizes each SOCKS5 profile's local manual diagnostic history. The summary is derived from `socks5_test_history.json` in app-private no-backup storage and can show **Not tested**, **Handshake OK**, **CONNECT OK**, or **Last test failed** plus compact time and target details when a manual CONNECT success exists.

Route explanations can include the last manual SOCKS5 diagnostic result for the selected SOCKS5 profile, but they must still say: "Selected profile: SOCKS5. Runtime forwarding is not enabled yet." This does not mean Android traffic is being routed through SOCKS5. Runtime TUN-to-SOCKS forwarding, default-route capture, and device traffic proxying are still not implemented.

Safety and privacy remain unchanged: no background SOCKS5 checks, no startup checks, no auto-connect, no silent DNS changes, no telemetry, no cloud upload, and no password storage in routing exports or diagnostic history.

## Notices

ViRouteFS is GPL-3.0-or-later free software. The project may use AI-assisted coding tools, while architecture, safety boundaries, review, release decisions, and product direction are maintained by the project owner. The ViRouteFS name, logo, and icon are project marks. Unofficial forks must not present themselves as official ViRouteFS releases. See [NOTICE.md](NOTICE.md) for project notices and redistribution guidance.

## Releases

Stable APK downloads should come from [GitHub Releases](https://github.com/Vifsvifsvifs/viroutefs/releases). GitHub Releases are the official alpha APK channel and include friendly APK assets such as `ViRouteFS-0.6.15-alpha.apk` plus release notes. The app icon direction is a minimalist dark launcher tile with a central red `V` and white network branches, maintained as XML/vector resources for adaptive, round, installer, package-parser, and legacy consistency with unique launcher resource names to avoid stale `ic_launcher` cache fallbacks. Android themed launcher icon metadata is intentionally omitted for now so launchers do not recolor the alpha brand icon into an inconsistent simplified mark.

GitHub Actions artifacts are mainly CI artifacts for maintainers and testers. They are useful for validating pull requests and pushes, but Releases should be preferred for normal alpha APK downloads because they are published intentionally with a changelog and attached APK asset.

## APK artifacts and manual updates

APK artifacts are built by GitHub Actions. To make alpha artifacts updateable over previous alpha APKs, CI must be configured with the stable alpha signing secrets. Without those secrets, artifacts are still built with default debug signing, but update-over-install is not stable and users may need to uninstall the old APK first.

Because ViRouteFS now uses stable alpha signing for published alpha APKs, future stable alpha updates should install over previous alpha builds signed with the same key. Users who installed an older randomly-signed debug APK may need one uninstall before moving to the stable alpha-signed release channel.

The in-app update checker is manual-only: it uses the public GitHub Releases API only when the user taps **Check for updates** in Settings. It compares published release versions with the local `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`, and then shows recent GitHub Releases in Settings → Updates. It does not run on app startup, does not run in the background, and does not send device identifiers. APK download starts only after the user taps **Download APK**. Downloads are written to a temporary cache file, checked for existence and size, moved to a final `.apk` filename, and then opened through Android's system installer via FileProvider. ViRouteFS does not silently install APKs. Android may show an unverified app warning because this APK is installed outside Google Play. This is a system warning for sideloaded APKs. The app cannot suppress system install warnings, and users must confirm installation in Android system UI.

See [`docs/TUN_SKELETON.md`](docs/TUN_SKELETON.md) for the safe Android TUN preview, [`docs/FLOW_SCANNER.md`](docs/FLOW_SCANNER.md) for Flow Scanner behavior, [`docs/ROUTING_POLICY.md`](docs/ROUTING_POLICY.md) for strict route isolation, and [`docs/UI_DIRECTION.md`](docs/UI_DIRECTION.md) for navigation/icon direction.

## Engine roadmap and licenses

Future engines and profile types are planned before integration. See [`docs/ENGINE_CANDIDATES.md`](docs/ENGINE_CANDIDATES.md), [`docs/THIRD_PARTY_LICENSES.md`](docs/THIRD_PARTY_LICENSES.md), and [`docs/PROFILE_TYPES.md`](docs/PROFILE_TYPES.md). No external VPN/proxy/DPI engine binaries are bundled yet.

## Screenshots

Actual screenshots are not committed yet. Future screenshots should show real UI states only: Networks, Routes, DNS, Flow Scanner empty/local counter states, and Settings → Help. Do not add fake configured tunnels or fake route categories as public screenshots.

## Build artifacts

GitHub Actions builds debug APK artifacts on pushes and pull requests to `main`. The workflow uploads a versioned debug APK artifact named like `ViRouteFS-debug-<versionName>`.

Stable update-over-install requires the alpha signing secrets in CI. Without those secrets, GitHub Actions still produces test APKs with default debug signing, but Android may reject updates over APKs signed by a different debug key. See [`docs/ALPHA_SIGNING.md`](docs/ALPHA_SIGNING.md).

Debug artifacts are for testing only. ViRouteFS is not yet published on Google Play or F-Droid.

## Diagnostics behavior

All diagnostics are manual and user-controlled:

- No automatic background checks.
- No background scanning.
- No telemetry or analytics.
- No cloud upload of diagnostic results.
- No port scanner, vulnerability scanner, brute force, exploit automation, or offensive network behavior.

The DNS screen currently uses Android's system resolver. If no DNS is configured for a route/profile, the model says it uses Android system DNS through ViRouteFS policy. ViRouteFS must not silently replace missing DNS with public resolvers. Per-route DNS policy is stored in the routing config, but it is still explanation and leak-risk metadata only. Real DNS routing will be added later.

See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md), [docs/ROUTE_DIAGNOSTICS.md](docs/ROUTE_DIAGNOSTICS.md), [docs/ROUTING_CONFIG.md](docs/ROUTING_CONFIG.md), [docs/ROUTING_POLICY.md](docs/ROUTING_POLICY.md), [docs/DNS_POLICY.md](docs/DNS_POLICY.md), and [SECURITY_BOUNDARIES.md](SECURITY_BOUNDARIES.md) for detailed behavior and safety boundaries.

## Route simulator status

The Route Simulator and Route Diagnostics route selection use the saved local routing configuration. They explain that network control ON means all traffic conceptually enters ViRouteFS, unmatched apps use System / Система, explicit rules are exclusive, and unavailable selected profiles fail closed. They do not change device routing and do not start a real VPN tunnel. Route Diagnostics network checks run through the current Android connection only.

## Roadmap

The public roadmap is maintained in [`ROADMAP.md`](ROADMAP.md). In short:

- `0.6.x`: navigation cleanup, Networks naming, route editor work, engine candidate/license planning, profile type documentation, release polish.
- `0.7.x`: first real outbound experiment, likely SOCKS5, while preserving no-payload-logging and fail-closed policy.
- `0.8.x`: expanded outbound experiments with route decision explanation.
- `0.9.x`: import preparation for WireGuard, Xray, and Hysteria2 configs.
- `1.0`: stable local-first routing profiles, DNS policy, Flow Scanner explanations, and privacy-safe export/reporting.

## Safety and privacy boundaries

ViRouteFS must remain a user-controlled defensive diagnostics tool.

Allowed future areas include VPN routing, DNS checks, TCP/TLS/HTTP diagnostics, MTU checks, LAN discovery, service discovery, WPS detection, Wi-Fi encryption detection, local logs, and user-controlled PCAP export.

Out of scope: WPS PIN brute force, password cracking, deauthentication attacks, evil twin attacks, router admin brute force, exploit automation, credential theft, hidden traffic interception, hidden telemetry, analytics SDKs, ad SDKs, tracking SDKs, and automatic upload of logs or PCAP files.

## License and project freedoms

ViRouteFS is free software. The Android app source code is licensed under the GNU General Public License v3.0 or later (`GPL-3.0-or-later`).

Under the license terms, users are free to run, study, modify, and share the software. ViRouteFS is also local-first by design: no telemetry, ads, tracking SDKs, or cloud upload are included.

Future server or web components, if any, should use the GNU Affero General Public License v3.0 or later (`AGPL-3.0-or-later`). Documentation may later move to Creative Commons Attribution-ShareAlike 4.0 (`CC BY-SA 4.0`), but for now repository documentation stays under the project license unless a separate documentation license file is added.

## Build instructions

### Prerequisites

Install:

- JDK 17.
- Android SDK command-line tools or Android Studio.
- Android SDK Platform 36.
- Android SDK Build Tools.

Set `ANDROID_HOME` to your Android SDK directory, or create an untracked `local.properties` file:

```properties
sdk.dir=/path/to/android/sdk
```

### Build from the command line

```bash
gradle :app:assembleDebug --stacktrace
```

If you generate the Gradle wrapper locally, you can also use:

```bash
./gradlew :app:assembleDebug --stacktrace
```

### Open in Android Studio

1. Open the repository root.
2. Let Android Studio sync Gradle.
3. Select the `app` configuration.
4. Run on an emulator or Android device running Android 8.0 (API 26) or newer.

## Development notes

- Real VPN routing is intentionally not implemented yet.
- Xray, Hysteria2, OpenVPN, WireGuard, and SOCKS5 engines are intentionally not implemented yet.
- Packet capture and packet payload logging are intentionally not implemented yet; the opt-in TEST-NET preview only counts bytes and packets before dropping them.
- Routing configuration is local app-private JSON unless the user explicitly copies it.
- Keep logs and future PCAP exports local unless the user explicitly exports them.
- Prefer small, compiling pull requests with clear commit messages.

## SOCKS5 profiles in 0.7.1-alpha

ViRouteFS 0.7.1-alpha keeps SOCKS5 profile configuration local-only and manual-only while hardening credential storage. SOCKS5 profile metadata such as name, host, port, optional username, enabled flag, and test status is stored locally, but SOCKS5 passwords are stored separately in app-private Android no-backup storage. Passwords are not written to `routing_config.json`, are not included in routing configuration exports, and are not shown in summaries, cards, status, diagnostics, errors, or logs.

Android backup and device-transfer rules exclude `routing_config.json` because routing profiles can reveal private network infrastructure even without stored passwords. Logs and future PCAP exports remain excluded from backup as before.

Connectivity testing runs only when the user taps **Test connection**; there are no startup checks, background checks, periodic checks, auto-connect behavior, silent DNS changes, telemetry, analytics, cloud upload, or public/free proxy dependency.

Full TUN-to-SOCKS device traffic routing is still not implemented: ViRouteFS does not capture the default route for SOCKS5, does not forward runtime packets to SOCKS5, and route explanations must treat SOCKS5 targets as configuration/preview only with: "Selected profile: SOCKS5. Runtime forwarding is not enabled yet." For manual testing, use a trusted/self-owned SOCKS5 server. Public/free SOCKS5 proxies are not required or recommended.

## 0.7.4-alpha SOCKS5 manual CONNECT diagnostics

ViRouteFS 0.7.4-alpha adds an explicit manual SOCKS5 CONNECT diagnostic for configured SOCKS5 profiles. The user edits the target host and port, taps the test button, and ViRouteFS performs only the SOCKS5 greeting/authentication and CONNECT request; it sends no HTTP request or application payload after CONNECT succeeds.

Boundaries for this release:
- CONNECT diagnostics are manual-only: no startup checks, background checks, periodic checks, auto-connect, or automatic profile testing.
- Runtime TUN-to-SOCKS forwarding is still not implemented. ViRouteFS does not capture the Android default route or route real device traffic through SOCKS5.
- Test history is stored locally in app-private no-backup storage (`socks5_test_history.json`) and is not automatically exported or uploaded.
- SOCKS5 credentials are not logged, exported, or stored in diagnostic history.
- No telemetry, ads, analytics, tracking SDKs, or cloud upload are added.
