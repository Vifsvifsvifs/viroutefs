# Platform roadmap

ViRouteFS is designed as a cross-platform human-readable routing system, not only an Android VPN app.

## Target platforms

- Android first, using the current single `VpnService` and sing-box runtime.
- Linux later, with process/executable matchers and platform routing integration.
- Windows later, with executable/process matchers and platform routing integration.
- macOS is represented in the neutral matcher model for future compatibility.

## Platform-neutral app matcher

An app matcher contains:

- `platform`: `android`, `linux`, `windows`, `macos`, or `any`;
- `value`: package name, process name, executable name, or simple text matcher;
- optional `displayName`.

Examples:

- `android / org.telegram.messenger`;
- `android / ru.sberbankmobile`;
- `linux / telegram-desktop`;
- `linux / firefox`;
- `windows / Telegram.exe`;
- `windows / chrome.exe`;
- `any / telegram`.

Android `0.13.0-beta.1` reads the complete installed-app list and launcher icons locally and requests `QUERY_ALL_PACKAGES` because selecting any installed app is a core per-app VPN-routing feature. Labels, package names and icons are used only by the route picker and Flow Scanner; they are never uploaded, sold or used for advertising.

## Later work

The Android build now has a real single-`VpnService` sing-box runtime, DNS enforcement, fail-closed app/domain/IP/CIDR routing and live per-connection metadata. Future milestones cover desktop platform adapters, IKEv2/IPsec, separately audited legacy adapters and a rootless zapret2 packet-processing adapter if its strategies can be represented safely inside the existing userspace runtime.

## 0.4.1-alpha UI milestone

The Android UI uses four primary destinations: Control, Routes, Scanner and More. DNS, Tools and Settings are available from More.

- VPN becomes the added connection profile manager, not a full protocol catalog on the main page.
- Protocol support is exposed at model/UI level for System, Block, modern VPN/tunnel protocols, proxy/tunnel protocols and legacy corporate protocols.
- Routes become compact grouped cards for assigning apps, domains and IP/CIDR matchers to connection profiles.
- DNS owns lookup checks, app DNS check concept, hosts-like overrides and DNS per connection.
- Scanner is Flow Scanner: friendly live per-connection metadata, route explanations and filtering by installed application, without payload capture or HTTPS decryption.

Future milestones must preserve explicit local processing without telemetry or cloud dependency.
