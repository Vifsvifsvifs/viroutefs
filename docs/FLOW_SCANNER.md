# Flow Scanner

Flow Scanner explains network state in plain language without recording packet contents.

## Current behavior

In normal VPN mode it shows:

- whether the Android VPN and sing-box router are active;
- whether IPv4, IPv6 and DNS are handled by the runtime;
- the fail-closed safety model;
- configuration and runtime warnings;
- live and recently closed connections from the local libbox command stream;
- installed application/package when Android can identify the owner;
- destination domain or IP, port and transport;
- selected outbound, matched rule, upload/download bytes and start/close state;
- filtering for one selected installed application.

The event history is kept in process memory and can be paused or cleared. It is
not uploaded and is not written as packet capture.

## Developer TEST-NET mode

The separate developer route for `203.0.113.0/24` reads and drops only those test packets. It can show source/destination IP, TCP/UDP ports, protocol, size and time. It does not store payloads, hostnames, passwords, UUIDs or PCAP files.

TEST-NET observations are never presented as normal forwarded traffic.

## Deliberate boundary

Flow Scanner is a readable connection-metadata viewer, not an HTTPS
interception proxy. It does not decrypt TLS, store messages/files/passwords,
record packet payloads, generate PCAP, run background telemetry, or upload data
to a cloud service. A domain may be absent when an app connects directly by IP
or the protocol does not expose a hostname.
