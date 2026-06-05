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
