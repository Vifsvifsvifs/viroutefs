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

## Current status: 0.6.3-alpha

Version `0.6.3-alpha` is a public alpha polish release. It documents the current safe preview state and prepares the repository for public presentation without changing runtime VPN/TUN routing behavior.

What exists now:

- Android `VpnService` permission flow exists.
- Foreground VPN preview service exists.
- Safe TUN preview exists.
- Optional TEST-NET route `203.0.113.0/24` exists for local preview testing.
- Flow Scanner can show live local test-route counters when the TEST-NET preview is enabled.
- The app has Compose screens for Dashboard, VPN, Routes, DNS, Tools, Logs, and Settings.
- Routing configuration, DNS policy metadata, diagnostics reports, and logs are local-first.

What is intentionally not implemented yet:

- No real user traffic routing yet.
- No full-device default route yet.
- No DNS server is added to the VPN builder.
- No packet payload logging.
- No forwarding or proxying.
- No Xray, OpenVPN, WireGuard, Hysteria2, or SOCKS5 proxy engines yet.
- No Play Store or F-Droid release is claimed yet.

See [`docs/TUN_SKELETON.md`](docs/TUN_SKELETON.md) for the safe Android TUN preview, [`docs/FLOW_SCANNER.md`](docs/FLOW_SCANNER.md) for Flow Scanner behavior, and [`docs/RELEASE_NOTES_0.6.3_ALPHA.md`](docs/RELEASE_NOTES_0.6.3_ALPHA.md) for the release notes.

## Screenshots

Actual screenshots are not committed yet. Planned public release screenshots:

- TODO: VPN profiles and TUN preview.
- TODO: Routes.
- TODO: DNS policies.
- TODO: Flow Scanner live test route.
- TODO: Settings / themes.

## Build artifacts

GitHub Actions builds debug APK artifacts on pushes and pull requests to `main`. The workflow uploads a versioned debug APK artifact named like `ViRouteFS-debug-<versionName>`.

Debug artifacts are for testing only. ViRouteFS is not yet published on Google Play or F-Droid.

## Diagnostics behavior

All diagnostics are manual and user-controlled:

- No automatic background checks.
- No background scanning.
- No telemetry or analytics.
- No cloud upload of diagnostic results.
- No port scanner, vulnerability scanner, brute force, exploit automation, or offensive network behavior.

The DNS screen currently uses Android's system resolver. Per-route DNS policy is stored in the routing config, but it is still explanation and leak-risk metadata only. Real DNS routing will be added later.

See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md), [docs/ROUTE_DIAGNOSTICS.md](docs/ROUTE_DIAGNOSTICS.md), [docs/ROUTING_CONFIG.md](docs/ROUTING_CONFIG.md), [docs/DNS_POLICY.md](docs/DNS_POLICY.md), and [SECURITY_BOUNDARIES.md](SECURITY_BOUNDARIES.md) for detailed behavior and safety boundaries.

## Route simulator status

The Route Simulator and Route Diagnostics route selection use the saved local routing configuration. They explain how future routing decisions should be presented to users, but they do not change device routing and do not start a real VPN tunnel. Route Diagnostics network checks run through the current Android connection only.

## Roadmap

The public roadmap is maintained in [`ROADMAP.md`](ROADMAP.md). In short:

- `0.6.x`: safe TUN test route, Flow Scanner live test counters, release polish.
- `0.7.x`: controlled Direct/Block route experiments without a full-device default route.
- `0.8.x`: first external outbound experiment, likely SOCKS5, with route decision explanation.
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
