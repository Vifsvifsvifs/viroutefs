# Flow Scanner

Flow Scanner (FS) is the future ViRouteFS view for explaining local flow visibility in human language.

## 0.6.5-alpha behavior

FS does **not** claim full traffic analysis yet.

Current user-facing behavior:

- Shows an empty/local state when no counters are available.
- Shows local TEST-NET counters only when the developer diagnostic TEST-NET route has been explicitly enabled or counters are non-zero.
- Does not show fake app flows as real traffic.
- Does not show fake tunnels, fake banking/media/work categories, or fake configured entities.

Current safety boundaries:

- No full-device default route.
- No DNS servers added to the VPN builder.
- No packet payload logging.
- No payload inspection.
- No domain extraction from packets.
- No forwarding.
- No proxying.
- No telemetry, analytics, tracking SDKs, ads, or cloud upload.

The developer TEST-NET route is `203.0.113.0/24`. Packets routed into that preview are counted and dropped. Counters are local runtime state.


## 0.8.0-alpha packet counters

The Android VpnService runtime skeleton now reads packets from the TUN ParcelFileDescriptor when the opt-in TEST-NET developer route is active. It parses IPv4 header metadata only and updates local counters for total packets, bytes, IPv4 packets, TCP, UDP, and ICMP. Packets are dropped after counting. There is no packet forwarding, SOCKS5 forwarding, VLESS, default-route capture, payload logging, telemetry, or background upload.

## 0.8.1-alpha packet inspector

`0.8.1-alpha` adds a local in-memory packet inspector to the VPN runtime skeleton. When packets are read from the safe TUN preview, ViRouteFS parses IPv4 metadata only:

- timestamp;
- protocol (`TCP`, `UDP`, `ICMP`, or `OTHER`);
- source IPv4 address;
- destination IPv4 address;
- source and destination ports for TCP/UDP only;
- packet size.

The VPN Runtime screen shows the latest 50 packet summaries newest first. The summaries are memory-only runtime state. They are not persisted, exported, uploaded, or turned into PCAP files.

Safety boundaries remain unchanged: no payload capture, no payload logging, no hostname extraction, no DNS proxying, no VLESS, no SOCKS5 forwarding, no packet forwarding, and no writes back to TUN.
