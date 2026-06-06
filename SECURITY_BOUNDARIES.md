# Security Boundaries

ViRouteFS is a defensive, local-first Android network toolkit. It should explain routes, DNS behavior, and flow diagnostics in a way normal users can understand.

The guiding principle is:

> Risk detected, exploitation not performed.

## Allowed and implemented direction

Current and planned work may include:

- Local VPN permission flow.
- Safe TUN preview.
- Developer-only TEST-NET route preview using `203.0.113.0/24`.
- Route diagnostics.
- DNS diagnostics.
- Flow Scanner explanations.
- Local-only logs and user-controlled exports.
- Clear explanations, technical details, and recommended actions for diagnostic results.

## Explicitly not included

ViRouteFS must not include offensive or hidden surveillance behavior, including:

- WPS brute force.
- Wi-Fi cracking.
- Deauth attacks.
- Evil twin attacks.
- Credential theft.
- Exploit automation.
- Stealth scanning.
- Hidden interception.
- Packet payload logging without explicit user action.
- Router admin brute force.
- Hidden telemetry, analytics, tracking SDKs, ads, or automatic cloud upload.

## User-control requirements

- Diagnostics should be manually started by the user.
- Logs and future PCAP exports must stay local unless the user explicitly exports them.
- Any future packet capture feature must be explicit, visible, and documented before use.
- Any future root-only feature must be clearly labeled and optional.


## Routing isolation boundary

When network control is off, Android works normally and ViRouteFS does not control traffic. When network control is on in the product model, all traffic enters ViRouteFS: unmatched apps use the built-in System / Система route, matched app/domain/IP rules use only the selected profile, and unavailable selected profiles fail closed / Block. System is an internal default route, not bypass. Full runtime enforcement is a future routing-engine task and must be implemented without hidden interception, payload logging, telemetry, ads, tracking, or cloud upload. See docs/ROUTING_POLICY.md.


## Manual update checks

- ViRouteFS has no background update checks.
- ViRouteFS has no telemetry, analytics, tracking SDKs, ads, or cloud upload.
- The manual update checker contacts the public GitHub Releases API only after the user taps **Check for updates** in Settings.
- The update checker sends no device identifiers.
- There are no background update checks and no automatic update checks at app startup.
- ViRouteFS never auto-downloads APK files; APK download starts only after the user taps **Download APK**.
- ViRouteFS never silently installs APK files. After download, **Install update** opens Android's system package installer and the user must confirm installation in system UI.
- Android enforces package signature compatibility for package updates.
- Downloaded APKs stay in app-specific cache/files storage unless the user explicitly exports or installs them.

## SOCKS5 profiles in 0.7.1-alpha

ViRouteFS 0.7.1-alpha keeps SOCKS5 profile configuration local-only and manual-only while hardening credential storage. SOCKS5 profile metadata such as name, host, port, optional username, enabled flag, and test status is stored locally, but SOCKS5 passwords are stored separately in app-private Android no-backup storage. Passwords are not written to `routing_config.json`, are not included in routing configuration exports, and are not shown in summaries, cards, status, diagnostics, errors, or logs.

Android backup and device-transfer rules exclude `routing_config.json` because routing profiles can reveal private network infrastructure even without stored passwords. Logs and future PCAP exports remain excluded from backup as before.

Connectivity testing runs only when the user taps **Test connection**; there are no startup checks, background checks, periodic checks, auto-connect behavior, silent DNS changes, telemetry, analytics, cloud upload, or public/free proxy dependency.

Full TUN-to-SOCKS device traffic routing is still not implemented: ViRouteFS does not capture the default route for SOCKS5, does not forward runtime packets to SOCKS5, and route explanations must treat SOCKS5 targets as configuration/preview only with: "Selected profile: SOCKS5. Runtime forwarding is not enabled yet." For manual testing, use a trusted/self-owned SOCKS5 server. Public/free SOCKS5 proxies are not required or recommended.

## 0.7.4-alpha SOCKS5 manual CONNECT diagnostics

ViRouteFS 0.7.4-alpha adds an explicit manual SOCKS5 CONNECT diagnostic for configured SOCKS5 profiles. The user edits the target host and port, taps the test button, and ViRouteFS performs only the SOCKS5 greeting/authentication and CONNECT request; it sends no HTTP request or application payload after CONNECT succeeds.

Boundaries for this release:
- CONNECT diagnostics are manual-only: no startup checks, background checks, periodic checks, auto-connect, or automatic profile testing.
- Runtime TUN-to-SOCKS forwarding is still not implemented. ViRouteFS does not capture the Android default route or route real device traffic through SOCKS5.
- Test history is stored locally in app-private no-backup storage (`socks5_test_history.json`) and is not automatically exported or uploaded.
- SOCKS5 credentials are not logged, exported, or stored in diagnostic history.
- No telemetry, ads, analytics, tracking SDKs, or cloud upload are added.

## SOCKS5 readiness summaries

SOCKS5 readiness in 0.7.6-alpha is a local, read-only summary of manual diagnostic history stored in app-private no-backup storage. It must not trigger network checks, run at startup, auto-connect profiles, change DNS, upload history, export history automatically, or expose credentials. The route explanation may show the last manual diagnostic result, but it must continue to warn that SOCKS5 runtime forwarding is not enabled and must not imply Android traffic is routed through SOCKS5.

## 0.7.7-alpha SOCKS5 outbound connector abstraction

ViRouteFS 0.7.7-alpha adds an internal SOCKS5 outbound connector abstraction for manual diagnostics and future routing-engine preparation. The connector performs SOCKS5 greeting/authentication and CONNECT only, closes the socket after a successful manual CONNECT, and does not send HTTP requests, application payloads, packet payloads, or real Android device traffic.

Safety boundaries remain unchanged:
- Runtime TUN-to-SOCKS forwarding is not implemented.
- Android default-route capture is not enabled for SOCKS5.
- VpnService packet forwarding behavior is unchanged.
- Manual diagnostics run only after explicit user action; there are no background checks, startup checks, periodic tests, or auto-connect behavior.
- DNS is not silently changed.
- SOCKS5 credentials are not written to routing exports, diagnostic history, result messages, logs, telemetry, or cloud storage.
- Telemetry, tracking, ads, analytics SDKs, and cloud upload remain out of scope.


## 0.8.10-alpha manual VLESS response probe

- Extend the user-triggered manual VLESS protocol probe to classify the first response after sending the existing minimal VLESS TCP request frame.
- Use the existing plain TCP and TLS probe paths; TLS continues to use SNI. REALITY, XTLS, UDP, HTTP payloads, DNS proxying, runtime forwarding, Android traffic forwarding, and TUN writes remain out of scope.
- Record only metadata: elapsed time, response classification, response byte count, target, and security mode. Response payload bytes, raw frame bytes, and UUID values are not displayed, logged, or stored.
- Keep local no-backup history capped at 20 entries per profile.

## 0.8.8-alpha manual VLESS protocol probe

- Add a user-triggered manual plain-TCP VLESS protocol probe from the VLESS profile screen.
- Build and send one minimal VLESS TCP request frame to a user-selected target using the existing VLESS request builder.
- Store local no-backup history capped at 20 entries per profile without UUID values or raw frame bytes.
- TLS/REALITY transport is not implemented yet.
- Runtime VLESS forwarding, Android traffic forwarding, packets written back to TUN, DNS proxying, auto-testing, telemetry, analytics, and cloud upload remain out of scope.

## 0.8.5-alpha VLESS URI import/export

- Add VLESS URI import/export for local configuration and route decision preview only.
- Store VLESS UUID, transport placeholders, and placeholder TLS/REALITY metadata locally in `routing_config.json`; warn users that route-config exports and explicit VLESS URI exports can contain connection identifiers.
- Validate host, port, and UUID locally without connecting to, testing, or resolving any VLESS server.
- Show the route-preview warning: "Selected profile is VLESS. Runtime forwarding is not enabled yet."
- Do not implement VLESS runtime forwarding, packet forwarding, TUN writes, REALITY/XTLS runtime, DNS proxying, telemetry, analytics, cloud upload, startup tests, or auto-connect.


## VLESS protocol builder boundary in 0.8.7-alpha

The VLESS protocol request builder constructs a first TCP request frame locally and returns bytes to the caller. It must not open sockets, resolve DNS, connect to a VLESS server, perform a VLESS handshake, forward runtime traffic, write packets back to TUN, proxy DNS, or implement TLS/REALITY/XTLS. UUID values are used only in local frame construction and must not be logged, included in validation errors, uploaded, or displayed as raw frame bytes in production UI.
