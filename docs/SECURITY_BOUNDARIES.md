# Security Boundaries

ViRouteFS is a defensive network diagnostics and VPN routing tool.

## Allowed features

- VPN routing
- DNS diagnostics
- TCP/TLS/HTTP/UDP checks
- MTU checks
- LAN discovery
- service discovery
- Wi-Fi security posture detection
- WPS detection
- route leak checks
- DNS leak checks
- local packet/event logging
- PCAP export for user-controlled traffic
- root-only local diagnostics

## Not allowed features

ViRouteFS must not implement:

- WPS PIN brute force
- WPA/WPA2/WPA3 cracking
- Wi-Fi handshake capture for cracking
- deauthentication attacks
- evil twin attacks
- credential theft
- router admin brute force
- exploit automation
- stealth scanning
- hidden traffic interception
- automatic upload of captured traffic

## Reporting language

Reports must distinguish between:

- detected risk
- likely vulnerability
- confirmed misconfiguration
- confirmed compromise

Example:

"WPS is enabled" means detected risk.
It does not mean confirmed compromise.
