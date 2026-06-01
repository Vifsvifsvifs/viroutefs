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

Future milestones can connect the model to real platform route engines, DNS policy enforcement, and tunnel implementations. Until then, all non-Direct/Block tunnel profiles are mock simulation entries.
