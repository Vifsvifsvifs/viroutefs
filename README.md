# ViRouteFS

ViRouteFS (Visual Route & Flow Scanner) is an open-source Android app for human-readable traffic routing explanations and safe network diagnostics.

The project is local-first and defensive by design. Version `0.4.0-alpha` adds editable local routing configuration: platform-neutral tunnel profiles, DNS policy metadata, route rules, scenarios, and clipboard JSON import/export. Route diagnostics still run only after a user action and still use the current Android network. ViRouteFS does **not** implement real VPN routing, Xray, OpenVPN, Hysteria2, WireGuard, SOCKS5 proxying, packet capture, cloud upload, analytics, telemetry, ads, tracking, or offensive security features.

## Current milestone: 0.4-alpha

- Kotlin Android app using Gradle Kotlin DSL.
- Jetpack Compose UI with Material 3.
- Minimum SDK 26.
- Package name: `dev.vifs.viroutefs`.
- App version shown on the Settings screen.
- Bottom navigation screens: Dashboard, VPN, Routes, DNS, Tools, Logs, Settings.
- Placeholder `ViRouteFsVpnService` declared with `android.permission.BIND_VPN_SERVICE`.
- Editable **Маршруты** screen with sections for simulator, route diagnostics, route profiles, DNS policies, rules, scenarios, and import/export.
- Platform-neutral routing model for Android, Linux, Windows, macOS, and `any` text/app matchers.
- Local app-private JSON persistence for routing configuration.
- Clipboard JSON export/import; no storage permission and no cloud sync.
- Default route profiles: Direct, Block, Xray Germany mock, Hysteria2 NL mock, OpenVPN Work mock, and SOCKS5 Work VM mock.
- DNS policies as simulation/config metadata: System DNS, Direct DNS, Work DNS mock, and Tunnel DNS mock.
- Route rules with enabled state, priority, target profile, optional DNS policy, APP_GROUP/APP/DOMAIN/CIDR/DEFAULT types, validation, and reset to defaults.
- User-triggered DNS/TCP/TLS/HTTP diagnostics and route diagnostic reports with copy/share actions.
- Last five route diagnostic reports kept in memory for the current session only.
- Sample human-readable flow events on the Logs screen.

## Diagnostics behavior

All diagnostics are manual and user-controlled:

- No automatic background checks.
- No background scanning.
- No telemetry or analytics.
- No cloud upload of diagnostic results.
- No port scanner, vulnerability scanner, brute force, exploit automation, or offensive network behavior.

The DNS screen currently uses Android's system resolver. Per-route DNS policy is now stored in the routing config, but it is still explanation and leak-risk metadata only. Real DNS routing will be added later.

See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md), [docs/ROUTE_DIAGNOSTICS.md](docs/ROUTE_DIAGNOSTICS.md), [docs/ROUTING_CONFIG.md](docs/ROUTING_CONFIG.md), and [docs/DNS_POLICY.md](docs/DNS_POLICY.md) for detailed behavior and safety boundaries.

## Route simulator status

The Route Simulator and Route Diagnostics route selection use the saved local routing configuration. They explain how future routing decisions should be presented to users, but they do not change device routing and do not start a real VPN tunnel. Route Diagnostics network checks run through the current Android connection only.

## Safety and privacy boundaries

ViRouteFS must remain a user-controlled defensive diagnostics tool.

Allowed future areas include VPN routing, DNS checks, TCP/TLS/HTTP diagnostics, MTU checks, LAN discovery, service discovery, WPS detection, Wi-Fi encryption detection, local logs, and user-controlled PCAP export.

Out of scope: WPS PIN brute force, password cracking, deauthentication attacks, evil twin attacks, router admin brute force, exploit automation, credential theft, hidden traffic interception, hidden telemetry, analytics SDKs, ad SDKs, tracking SDKs, and automatic upload of logs or PCAP files.

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
- Packet capture is intentionally not implemented yet.
- Routing configuration is local app-private JSON unless the user explicitly copies it.
- Keep logs and future PCAP exports local unless the user explicitly exports them.
- Prefer small, compiling pull requests with clear commit messages.

## 0.4.1-alpha compact UI concept

ViRouteFS 0.4.1-alpha reorganizes the Android UI into a compact, friendly product structure:

- **VPN** manages added connection profiles and a global demonstration switch. Adding profiles supports UI placeholders for QR code, clipboard, file import and manual creation.
- **Routes / Маршруты** answers “Что через какое подключение ходит?” by grouping app, site/domain and IP/CIDR matchers under connection profile cards.
- **DNS** contains DNS lookup, app DNS check concept, hosts-like local overrides and DNS per connection.
- **FS** is **Flow Scanner**, a friendly flow-event explanation module. It is demo-only until explicit local VPN observation is implemented.
- **Settings / Настройки** contains language, theme and support-project sections.

The release remains local-first: no telemetry, analytics, ads, tracking SDKs, cloud log upload, hidden interception, root features, real VPN routing or real packet capture are added.
