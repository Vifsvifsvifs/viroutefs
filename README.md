# ViRouteFS

ViRouteFS (Visual Route & Flow Scanner) is an open-source Android app for human-readable traffic routing explanations and safe network diagnostics.

The project is local-first and defensive by design. Version `0.2.0-alpha` adds real basic diagnostics that run only after the user presses a button. It still does **not** implement real VPN routing, Xray, OpenVPN, packet capture, cloud upload, analytics, telemetry, ads, tracking, or offensive security features.

## Current milestone: 0.2-alpha

- Kotlin Android app using Gradle Kotlin DSL.
- Jetpack Compose UI with Material 3.
- Minimum SDK 26.
- Package name: `dev.vifs.viroutefs`.
- Bottom navigation screens:
  - Dashboard
  - VPN
  - Routes / route simulator
  - DNS
  - Tools
  - Logs
  - Settings
- Placeholder `ViRouteFsVpnService` declared in `AndroidManifest.xml` with `android.permission.BIND_VPN_SERVICE`.
- Real user-triggered DNS lookup for `A` and `AAAA` records through Android system DNS.
- Real user-triggered TCP connection check.
- Real user-triggered TLS/SNI handshake check with protocol, cipher, and certificate details when available.
- Real user-triggered HTTP/HTTPS check with status code, redirects, content type, and elapsed time.
- App version shown on the Settings screen.
- Sample human-readable flow events on the Logs screen.

## Diagnostics behavior

All diagnostics are manual and user-controlled:

- No automatic background checks.
- No background scanning.
- No telemetry or analytics.
- No cloud upload of diagnostic results.
- No port scanner, vulnerability scanner, brute force, exploit automation, or offensive network behavior.

The DNS screen currently uses Android's system resolver. The DNS server field remains visible for the product flow, but direct custom DNS server querying is planned for a later milestone and is clearly labeled in the UI.

See [docs/DIAGNOSTICS.md](docs/DIAGNOSTICS.md) for detailed behavior and safety boundaries.

## Route simulator status

The Route Simulator is still a mock/simulation-only feature. It explains how future routing decisions should be presented to users, but it does not change device routing and does not start a real VPN tunnel.

## Safety and privacy boundaries

ViRouteFS must remain a user-controlled defensive diagnostics tool.

Allowed future areas include VPN routing, DNS checks, TCP/TLS/HTTP diagnostics, MTU checks, LAN discovery, service discovery, WPS detection, Wi-Fi encryption detection, local logs, and user-controlled PCAP export.

Out of scope: WPS PIN brute force, password cracking, deauthentication attacks, evil twin attacks, router admin brute force, exploit automation, credential theft, hidden traffic interception, hidden telemetry, analytics SDKs, ad SDKs, tracking SDKs, and automatic upload of logs or PCAP files.

## Build instructions

### Prerequisites

Install:

- JDK 17 or newer.
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
- Xray and OpenVPN engines are intentionally not implemented yet.
- Packet capture is intentionally not implemented yet.
- Keep logs and future PCAP exports local unless the user explicitly exports them.
- Prefer small, compiling pull requests with clear commit messages.
