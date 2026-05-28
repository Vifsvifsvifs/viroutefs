# AGENTS.md

## Project

ViRouteFS is an open-source Android network toolkit.

Full meaning:
Visual Route & Flow Scanner.

Main goals:
- Android VPN router based on a single VpnService.
- Internal policy routing.
- Xray and OpenVPN as primary tunnel engines.
- Per-app, CIDR, domain and DNS-based routing.
- Human-readable packet/event log.
- DNS, TCP, TLS, HTTP, UDP and MTU diagnostics.
- Wireshark-like flow viewer for normal users.
- Safe network security audit tools.
- Optional root-only advanced diagnostics.

## Language and stack

Use:
- Kotlin
- Jetpack Compose
- Material 3
- Gradle Kotlin DSL where possible
- Minimum SDK 26
- Package name: dev.vifs.viroutefs

## Project rules

Do not implement offensive security features.

Allowed:
- WPS detection
- Wi-Fi encryption detection
- DNS checks
- TCP/TLS/HTTP diagnostics
- LAN discovery
- service discovery
- route simulation
- VPN logs
- PCAP export from VpnService traffic
- root-only tcpdump wrapper later
- root-only local interface diagnostics

Not allowed:
- WPS PIN brute force
- WPA/WPA2/WPA3 password cracking
- deauth attacks
- evil twin attacks
- router admin brute force
- exploit automation
- credential theft
- hidden interception of third-party traffic

## Privacy

The app must be local-first.

Do not add:
- hidden telemetry
- analytics SDKs
- ad SDKs
- tracking SDKs
- cloud upload of logs
- automatic upload of PCAP files

Logs and PCAP exports must stay local unless the user explicitly exports them.

## Development style

Prefer small pull requests.

Each task should:
- compile successfully
- not break existing screens
- include clear commit messages
- update docs when behavior changes

## UI style

The app should be understandable for non-experts.

Every technical result should have:
- a simple explanation
- technical details
- recommended action when possible

## First milestone

Create an Android Compose skeleton with:
- Dashboard
- VPN
- DNS
- Tools
- Logs
- Settings
- placeholder VpnService
- DNS checker UI placeholder
- sample human-readable logs
