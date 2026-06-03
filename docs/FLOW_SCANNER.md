# Flow Scanner (FS)

**FS** means **Flow Scanner**.

The FS tab keeps the title **Flow Scanner** and the Russian subtitle:

> кто куда подключается и почему

FS is a friendly network-event explanation module. It is not a raw packet table and it does not use third-party branding.

## 0.6.3-alpha behavior

The screen remains a demo/preview UI for future flow explanations, but it can also display live local counters from the opt-in TEST-NET TUN test route. It does **not** start full packet capture, full-device routing, DNS routing, forwarding/proxying, or real VPN engines.

Main screen behavior:

- shows a compact top control card with an all-apps placeholder, a **Start analysis** button, and demo/local status;
- shows dense sample flow rows with only app name, target domain/IP:port, selected route/tunnel, and a small status chip;
- clearly labels sample rows as **demo / preview**;
- shows a compact **Live test route** row when the VPN TEST-NET preview is active or its counters are non-zero;
- moves explanations, routing reasons, warnings, recommendations, and technical notes into **Details / Подробнее**;
- opens a dedicated flow event details screen when a sample event row is tapped.

Details screen behavior:

- demo details show app, domain, resolved IP when available, port/protocol, DNS policy, selected route/tunnel, route-selection reason, risk/warning, and recommendation;
- live TEST-NET details show route `203.0.113.0/24`, VPN mode `TUN test-route preview`, packets read, bytes read, last packet time, safety notes, and how to test;
- keeps long technical data collapsed behind **Details / Подробнее**.

Live local TEST-NET event in `0.6.3-alpha`:

- Source: `ViRouteFS TUN test route`;
- Route: `203.0.113.0/24`;
- Counters: `packetsRead`, `bytesRead`, `lastPacketAt`;
- Safety notes: no default route, no DNS, no payload logging, and packets are dropped after counting;
- Test hint: open `http://203.0.113.1` or try connecting to `203.0.113.1`.

This live row is local test data only. It is not a claim that Flow Scanner performs real app traffic analysis yet.

Demo sample events:

- Telegram → `api.telegram.org` → Xray Germany;
- Browser → `youtube.com / googlevideo.com` → Media tunnel;
- Bank/Gosuslugi → `gosuslugi.ru` → Direct;
- Work app → `gitlab.corp` → Work VPN;
- Tracker example → `tracker.example.com` → Block.

## Privacy and safety boundary

FS will work only after the user explicitly enables local VPN preview features. `0.6.3-alpha` has no hidden interception, full packet capture, default route, VPN DNS server injection, packet payload logging, packet payload inspection, domain extraction from packets, forwarding, proxying, telemetry, analytics, tracking SDKs, or cloud upload of logs/PCAP files. The live TEST-NET counters are runtime app-local state and are not persisted beyond existing service state behavior.
