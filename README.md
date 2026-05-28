# ViRouteFS

ViRouteFS (Visual Route & Flow Scanner) is an open-source Android VPN router, flow scanner, and network diagnostics toolkit.

The project is local-first and defensive by design. This initial milestone provides a Jetpack Compose skeleton only; it does **not** implement real VPN routing, packet capture, cloud upload, analytics, telemetry, ads, tracking, or offensive security features.

## Current skeleton

- Kotlin Android app using Gradle Kotlin DSL.
- Jetpack Compose UI with Material 3.
- Minimum SDK 26.
- Package name: `dev.vifs.viroutefs`.
- Bottom navigation screens:
  - Dashboard
  - VPN
  - DNS
  - Tools
  - Logs
  - Settings
- Placeholder `ViRouteFsVpnService` declared in `AndroidManifest.xml` with `android.permission.BIND_VPN_SERVICE`.
- DNS checker UI placeholder with domain, DNS server, record type, Check button, and result card.
- Tools placeholders for TCP, TLS/SNI, HTTP, MTU, LAN scanner, and Security audit checks.
- Sample human-readable flow events on the Logs screen.

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
gradle :app:assembleDebug
```

If you generate the Gradle wrapper locally, you can also use:

```bash
./gradlew :app:assembleDebug
```

### Open in Android Studio

1. Open the repository root.
2. Let Android Studio sync Gradle.
3. Select the `app` configuration.
4. Run on an emulator or Android device running Android 8.0 (API 26) or newer.

## Development notes

- Real VPN routing is intentionally not implemented yet.
- Keep logs and future PCAP exports local unless the user explicitly exports them.
- Prefer small, compiling pull requests with clear commit messages.
