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

## Routing and DNS boundaries

When network control is off, Android works normally and ViRouteFS does not control traffic. When network control is on in the product model, all traffic must enter ViRouteFS: unmatched apps use the built-in System / Система route, matched rules use only the selected profile, and unavailable selected profiles fail closed / Block.

System / Система is an internal default route, not bypass. Full runtime enforcement is still a future task and must not add hidden interception, packet payload logging, telemetry, analytics, ads, tracking SDKs, or cloud upload.

If no DNS is configured for a route/profile, the model uses Android system DNS. ViRouteFS must not silently replace missing DNS with public resolvers or swap failed user-defined DNS to another resolver.

## VLESS profile model in 0.8.4-alpha

VLESS profiles are configuration-only in 0.8.4-alpha. The app can store and validate local profile fields for route decision preview, including UUID and placeholder TLS/REALITY metadata, but it does not connect to VLESS servers, forward packets, write packets back to TUN, implement REALITY/XTLS runtime, or proxy DNS. Route decision preview must warn: "Selected profile is VLESS. Runtime forwarding is not enabled yet."

`routing_config.json` and user exports may contain VLESS connection identifiers such as UUID, host, SNI, and placeholder key metadata. Treat exported routing configs as sensitive local files. UUID values must not appear in summaries, diagnostics text, logs, or route-preview text. No telemetry, cloud upload, analytics, ads, background validation, startup tests, or auto-connect behavior is added.
