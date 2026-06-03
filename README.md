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

## Current status: 0.6.10-alpha

Version `0.6.10-alpha` extends the manual Settings update flow so a user can check GitHub Releases, manually download an APK asset such as `ViRouteFS-0.6.10-alpha.apk`, and open Android's system package installer. The user must confirm installation in Android system UI. There are still no background update checks, no auto-downloads, no silent installs, and no runtime VPN/TUN routing behavior changes.

What exists now:

- Android `VpnService` permission flow exists.
- Foreground VPN preview service exists.
- Safe TUN preview exists.
- Optional TEST-NET route `203.0.113.0/24` remains for developer/local preview testing only and is hidden from normal user UI.
- Flow Scanner can show local counters when developer diagnostics explicitly enable the TEST-NET preview.
- The app has Compose screens for Networks, Routes, DNS, Flow Scanner, Tools, and Settings with Help.
- GitHub Actions builds APK artifacts with dynamic version-based names.
- Published releases contain friendly APK assets, such as `ViRouteFS-0.6.10-alpha.apk`, attached to GitHub Releases.
- Stable update-over-install for alpha APKs requires the alpha signing secrets documented in [`docs/ALPHA_SIGNING.md`](docs/ALPHA_SIGNING.md).
- Settings includes a manual update checker that contacts GitHub Releases only after the user taps **Check for updates**. When a newer release has an APK asset, the user can tap **Download APK** and then **Install update** to open Android's system package installer.
- Routing configuration, DNS policy metadata, diagnostics reports, and logs are local-first.
- The built-in System / Система route is the safe internal default for apps without explicit rules in the ViRouteFS model; it is not bypass when network control is active.

What is intentionally not implemented yet:

- No real user traffic routing yet.
- No runtime full-device default-route enforcement yet.
- No DNS server is added to the VPN builder.
- No packet payload logging.
- No forwarding or proxying.
- No Xray, OpenVPN, WireGuard, Hysteria2, or SOCKS5 proxy engines yet.
- No Play Store or F-Droid release is claimed yet.
- No background update checks, automatic APK downloads, silent install behavior, telemetry, analytics, tracking, ads, or cloud upload.

## Releases

Stable APK downloads should come from [GitHub Releases](https://github.com/Vifsvifsvifs/viroutefs/releases). GitHub Releases are the user-facing distribution channel and include friendly APK assets such as `ViRouteFS-0.6.10-alpha.apk` plus release notes.

GitHub Actions artifacts are mainly CI artifacts for maintainers and testers. They are useful for validating pull requests and pushes, but Releases should be preferred for normal alpha APK downloads because they are published intentionally with a changelog and attached APK asset.

## APK artifacts and manual updates

APK artifacts are built by GitHub Actions. To make alpha artifacts updateable over previous alpha APKs, CI must be configured with the stable alpha signing secrets. Without those secrets, artifacts are still built with default debug signing, but update-over-install is not stable and users may need to uninstall the old APK first.

Because ViRouteFS now uses stable alpha signing for published alpha APKs, future stable alpha updates should install over previous alpha builds signed with the same key. Users who installed an older randomly-signed debug APK may need one uninstall before moving to the stable alpha-signed release channel.

The in-app update checker is manual-only: it uses the public GitHub Releases API only when the user taps **Check for updates** in Settings. It compares published release versions with the local `BuildConfig.VERSION_NAME` and `BuildConfig.VERSION_CODE`. It does not run on app startup, does not run in the background, and does not send device identifiers. APK download starts only after the user taps **Download APK**. After the APK is downloaded into app-specific cache, **Install update** opens Android's system package installer; ViRouteFS does not silently install APKs.

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
