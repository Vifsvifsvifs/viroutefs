# Flow Scanner (FS)

**FS** means **Flow Scanner**.

The FS tab keeps the title **Flow Scanner** and the Russian subtitle:

> кто куда подключается и почему

FS is a friendly network-event explanation module. It is not a raw packet table and it does not use third-party branding.

## 0.4.5-alpha behavior

The screen is still a demo/preview UI. It does **not** start real packet capture, a real `VpnService`, or real VPN engines.

Main screen behavior:

- shows a compact top control card with an all-apps placeholder, a **Start analysis** button, and demo/local status;
- shows dense sample flow rows with only app name, target domain/IP:port, selected route/tunnel, and a small status chip;
- moves explanations, routing reasons, warnings, recommendations, and technical notes into **Details / Подробнее**;
- opens a dedicated flow event details screen when a sample event row is tapped.

Details screen behavior:

- shows app, domain, resolved IP when available, port/protocol, DNS policy, selected route/tunnel, route-selection reason, risk/warning, and recommendation;
- keeps long technical data collapsed behind **Details / Подробнее**.

Demo sample events:

- Telegram → `api.telegram.org` → Xray Germany;
- Browser → `youtube.com / googlevideo.com` → Media tunnel;
- Bank/Gosuslugi → `gosuslugi.ru` → Direct;
- Work app → `gitlab.corp` → Work VPN;
- Tracker example → `tracker.example.com` → Block.

## Privacy and safety boundary

FS will work only after the user explicitly enables future local VPN mode. 0.4.5-alpha has no hidden interception, packet capture, real `VpnService`, real VPN engines, telemetry, analytics, tracking SDKs, or cloud upload of logs/PCAP files.
