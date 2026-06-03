# Platform roadmap

ViRouteFS is designed as a cross-platform human-readable routing system, not only an Android VPN app.

## Target platforms

- Android first, using a future single `VpnService` entry point.
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

Android `0.4.0-alpha` does not detect installed apps and does not request `QUERY_ALL_PACKAGES`. Matchers are text/config entries only.

## Later work

Future milestones can connect the model to real platform route engines, DNS policy enforcement, and tunnel implementations. Until then, all non-System/Block tunnel profiles are mock simulation entries.

## 0.4.1-alpha UI milestone

The Android UI is reorganized around VPN, Routes, DNS, FS, Tools and Settings.

- VPN becomes the added connection profile manager, not a full protocol catalog on the main page.
- Protocol support is exposed at model/UI level for System, Block, modern VPN/tunnel protocols, proxy/tunnel protocols and legacy corporate protocols.
- Routes become compact grouped cards for assigning apps, domains and IP/CIDR matchers to connection profiles.
- DNS owns lookup checks, app DNS check concept, hosts-like overrides and DNS per connection.
- FS becomes Flow Scanner: friendly traffic explanations after future explicit local VPN mode, with no hidden capture in this milestone.

Future milestones must still implement real routing, DNS engine behavior and packet/flow observation explicitly and locally, without telemetry or cloud dependency.
