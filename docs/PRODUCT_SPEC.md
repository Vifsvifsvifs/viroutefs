# ViRouteFS Product Specification

ViRouteFS is an open-source Android VPN router, flow scanner and network diagnostics toolkit.

## Core idea

ViRouteFS uses one Android VpnService and performs routing inside the application.

The user should be able to define rules like:

- 10.0.0.0/8 -> OpenVPN Corp
- 172.16.1.0/22 -> OpenVPN Site B
- Telegram -> Xray
- Banking apps -> Direct
- Everything else -> Xray

## Main modules

### VPN Router

- Android VpnService
- Internal route engine
- Direct outbound
- Block outbound
- Xray outbound
- OpenVPN outbound

### DNS Engine

- Global DNS profile
- Per-outbound DNS profile
- Per-rule DNS override
- DNS lookup through a selected DNS server
- DNS compare
- DNS leak check

### Flow Scanner

Human-readable Flow Scanner event viewer for normal users.

The app should explain traffic in simple terms.

Example:

Telegram connected to 149.154.167.91:443.
Example future rule shape: selected app/domain/IP -> selected actual profile, with fail-closed behavior if that profile is unavailable.
Status: success.
Latency: 84 ms.

Technical details should be available separately.

### Diagnostics

- DNS check
- TCP check
- TLS/SNI check
- HTTP check
- UDP check
- QUIC check
- MTU/MSS check
- route simulator
- VPN server health check

### Security audit

Safe checks only:

- WPS detection
- Wi-Fi encryption check
- router admin exposure check
- UPnP detection
- DNS audit
- LAN device discovery
- open service discovery
- guest network segmentation check
- report generation

### Root tools

Root-only features:

- tcpdump
- interface capture
- ip route viewer
- ip rule viewer
- iptables/nft viewer
- ARP table
- neighbor table
- advanced traceroute
- PCAP export

## Distribution

- GitHub Releases
- Open-source repository
- Donation-based funding
- No hidden telemetry
- Local-first logs
- User-controlled exports
