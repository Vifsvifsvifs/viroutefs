# Diagnostics

ViRouteFS diagnostics are local and opt-in. No network test starts automatically when the app opens, a profile is saved or the VPN starts.

## Available checks

- DNS lookup through Android's system resolver or an explicitly selected UDP, TCP, DNS-over-TLS or DNS-over-HTTPS server;
- TCP connect;
- TLS handshake and certificate explanation;
- HTTP/HTTPS request;
- UDP response check;
- MTU-oriented check;
- SOCKS5 handshake and CONNECT;
- VLESS reachability/protocol probes;
- route selection explanation.

Reports explain failures in plain language and can be copied or shared only by explicit user action.

## Important boundary

General DNS/TCP/TLS/HTTP diagnostics run from the ViRouteFS app process. The app package is excluded from its own TUN to prevent routing loops, so those checks use the current physical Android network. They verify that a destination is reachable from the phone, not that another application used the selected VPN profile.

The DNS checker sends a real query to the server entered by the user. A blank server field intentionally selects Android's system resolver. Server hostnames require a bootstrap lookup through the system resolver; using a literal IPv4 or IPv6 address avoids that bootstrap. The manual checker does not currently probe DNS-over-QUIC/H3 and reports that limitation instead of silently using another transport.

To validate a route end to end:

1. enable the VPN router;
2. assign a rule to a separate test application;
3. check that application’s external IP and DNS behavior;
4. disable the selected profile and confirm fail-closed blocking;
5. repeat after network changes and reconnects.

## Flow visibility

Normal sing-box runtime state is shown without fake per-flow counters. Detailed packet metadata exists only in the isolated developer TEST-NET route, which reads and drops `203.0.113.0/24` test packets.

Neither mode records payloads, passwords, UUIDs or PCAP files.
