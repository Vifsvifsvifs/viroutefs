# ViRouteFS Roadmap

ViRouteFS development is intentionally incremental. The project should stay local-first, privacy-preserving, and honest about what is implemented versus planned.

## 0.6.x: safe preview and release polish

Focus: prove the Android VPN permission and TUN lifecycle without routing normal user traffic, while keeping normal UI clean and centered on Networks / Сети.

- Safe TUN test route preview.
- Optional TEST-NET route `203.0.113.0/24` for controlled local tests.
- Flow Scanner live test counters for the TEST-NET preview.
- Public alpha README, release notes, support information, and security boundaries.
- No runtime default-route enforcement yet; System / Система is the internal model default for unmatched apps when network control is active.
- No DNS injection into the VPN builder.
- No forwarding, proxying, packet payload logging, or real tunnel engine.


## 0.6.10-alpha manual APK download and install

- Manual APK download from GitHub Releases after explicit user action.
- Open Android system installer for the downloaded APK with user confirmation; no silent install.
- No background update checks, auto-downloads, telemetry, runtime VPN engine integration, default route, VPN builder DNS servers, packet payload logging, forwarding, or proxying.

## 0.6.8-alpha stable alpha signing and manual updates

- Stable alpha signing support through GitHub Secrets for updateable GitHub Actions APK artifacts.
- Manual Settings update checker using GitHub Releases only after user action.
- No background update checks, telemetry, auto-download, auto-install, runtime VPN engine integration, default route, VPN builder DNS servers, packet payload logging, forwarding, or proxying.

## 0.6.7-alpha engine candidates and license planning

- Engine candidates and third-party license planning are documented before any runtime integration.
- Profile type documentation defines built-in, early outbound, and future VPN/tunnel/local DPI bypass profile families.
- ByeDPI and OpenVPN3 are added to the roadmap as planned candidates. ByeDPI is planned as a local DPI bypass profile candidate; OpenVPN support should prefer the OpenVPN3 Core path.
- No runtime engine integration, dependency additions, native binaries, default route, VPN builder DNS servers, packet payload logging, forwarding/proxying, telemetry, analytics, tracking, ads, or cloud upload are added in this milestone.

## 0.7.0-alpha first outbound experiment

Focus: start carefully testing route decisions while keeping full runtime enforcement disabled until packet handling is safe.

- First real outbound experiment, likely SOCKS5.
- Route target can use first real outbound.
- Runtime must still preserve no-payload-logging and fail-closed policy.
- Controlled System/Block route experiments.
- Route explanations for why a destination would use System, be blocked, or be reserved for a future tunnel.
- Safer UX around warnings, limitations, and diagnostics.
- No runtime full-device enforcement until safe packet handling exists.
- No hidden interception or payload logging.

## 0.7.7-alpha SOCKS5 outbound connector abstraction

- Add an internal SOCKS5 outbound connector abstraction for explicit manual diagnostics and future runtime routing preparation.
- Reuse the connector/shared SOCKS5 CONNECT path from the manual CONNECT diagnostic.
- Keep runtime VPN behavior unchanged: no TUN-to-SOCKS forwarding, no Android default-route capture, no device traffic forwarding, no background checks, no auto-connect, no silent DNS changes, and no credential exposure.

## 0.8.x: expanded outbound experiments

Focus: expand explicitly enabled outbound experiments after safe route handling is designed.

- Route decision explanation before and during a test.
- Clear user controls for enabling, testing, and disabling experimental outbound handling.
- Diagnostics that separate policy decisions from network failures.
- Continued local-first behavior with no telemetry, ads, tracking SDKs, or cloud upload.

## 0.9.x: import preparation

Focus: prepare user-friendly import flows while staying honest about implementation status.

- Import preparation for WireGuard, Xray, and Hysteria2 configs.
- Validation and explanation of imported config fields.
- Clear labels for implemented behavior versus planned behavior.
- No claim that a tunnel engine works until it actually routes traffic safely.
- Privacy review for export/reporting flows.

## Later engine research

- ByeDPI local profile research.
- OpenVPN3 Core research.
- WireGuard userspace research.
- Xray/Hysteria2 import and engine research.

## 1.0: stable local-first routing profiles

Focus: ship a stable defensive routing and diagnostics app for normal users.

- Stable local-first routing profiles.
- DNS policy that is understandable and enforceable.
- Flow Scanner explanations for route and DNS decisions.
- Privacy-safe export/reporting controlled by the user.
- Clear security boundaries: risk explanation, no exploitation.


## 0.6.4-alpha navigation cleanup (retained)

- Home / Главная is removed from bottom navigation; overview, goals, license, privacy promises, and alpha status live under Settings → Help / Справка.
- VPN is renamed to Networks / Сети in normal UI.
- TEST-NET route controls are developer/testing-only and hidden from normal user UI.
- Fake tunnels, fake banking/media/work route categories, and placeholder buttons should not appear as real configured entities.
- Route and DNS targets must come from actual profiles: System, Block, and future user-created active profiles.
- App/domain/IP route bindings must be exclusive and fail closed; never silently fall back to a foreign VPN profile. See docs/ROUTING_POLICY.md.
- No runtime routing behavior change: no default route, no DNS servers in VPN builder, no payload logging, no forwarding/proxying.


## 0.6.5-alpha routing defaults and icon

## 0.6.6-alpha route editor and conflict validation

- Route creation/editing becomes an admin-style flow: route name, matcher type, matcher value, target profile, and enabled state.
- Matchers supported in the local editor: installed Android app, domain/host, and IPv4/IP-CIDR.
- App selection uses installed applications from PackageManager with label/package display and search filtering.
- Target route list uses actual profiles only: System / Система, Block / Блокировать, and user-created real profiles. Mock tunnels, fake categories, and TEST-NET developer routes are not normal targets.
- Exact duplicate conflict validation blocks saving duplicate app, duplicate domain/host, and exact duplicate IP/CIDR rules. Default System route is not a conflict. Broad CIDR overlap detection remains TODO.
- System remains the internal route for apps without explicit rules. Block remains fail-closed behavior. Explicit rules remain exclusive and must never silently fall back to another profile.
- Runtime enforcement remains future work. Full "no kilobyte bypass" enforcement requires default-route capture plus a safe forwarding/routing engine in a later milestone. 0.6.6-alpha does not add default-route enforcement, VPN builder DNS servers, packet payload logging, forwarding/proxying, or tunnel engines.

- System / Система is the built-in internal default route for apps without explicit rules when network control is active; it is not bypass.
- The old Direct wording is removed from normal UI as a duplicate of System. Legacy `direct` ids/types may remain only for saved-config compatibility.
- DNS defaults to Android system DNS unless the user explicitly configures DNS; missing DNS is not silently replaced by public resolvers.
- Explicit rules are exclusive: matched traffic uses only the selected profile, and unavailable selected profiles fail closed / Block.
- Full runtime enforcement remains a future task: no default-route enforcement, no DNS servers in the VPN builder, no payload logging, no forwarding/proxying.
- Adaptive icon direction is aggressive black/red stylized V: sharp, technical, adult, not childish and not a generic VPN shield.

## SOCKS5 profiles in 0.7.0-alpha

ViRouteFS 0.7.0-alpha adds local-only SOCKS5 profile configuration and an explicit manual SOCKS5 handshake connectivity tester. A user can store a SOCKS5 name, host, port, optional username, optional password, enabled flag, and test status locally on the device. Connectivity testing runs only when the user taps **Test connection**; there are no startup checks, background checks, periodic checks, auto-connect behavior, silent DNS changes, telemetry, analytics, cloud upload, or public/free proxy dependency.

Full TUN-to-SOCKS device traffic routing is not implemented yet: ViRouteFS does not capture the default route for SOCKS5, does not forward runtime packets to SOCKS5, and route explanations must treat SOCKS5 targets as configuration/preview only with: "Selected profile: SOCKS5. Runtime forwarding is not enabled yet." For manual testing, use a trusted/self-owned SOCKS5 server. Public/free SOCKS5 proxies are not required or recommended. Credentials remain local, and passwords must not be logged, shown in diagnostics, or included in docs/PR text.

## 0.7.4-alpha SOCKS5 manual CONNECT diagnostics

ViRouteFS 0.7.4-alpha adds an explicit manual SOCKS5 CONNECT diagnostic for configured SOCKS5 profiles. The user edits the target host and port, taps the test button, and ViRouteFS performs only the SOCKS5 greeting/authentication and CONNECT request; it sends no HTTP request or application payload after CONNECT succeeds.

Boundaries for this release:
- CONNECT diagnostics are manual-only: no startup checks, background checks, periodic checks, auto-connect, or automatic profile testing.
- Runtime TUN-to-SOCKS forwarding is still not implemented. ViRouteFS does not capture the Android default route or route real device traffic through SOCKS5.
- Test history is stored locally in app-private no-backup storage (`socks5_test_history.json`) and is not automatically exported or uploaded.
- SOCKS5 credentials are not logged, exported, or stored in diagnostic history.
- No telemetry, ads, analytics, tracking SDKs, or cloud upload are added.

## 0.7.7-alpha

- Add an internal SOCKS5 outbound connector abstraction for explicit manual diagnostics and future runtime routing preparation.
- Reuse the connector/shared SOCKS5 CONNECT path from the manual CONNECT diagnostic.
- Keep runtime VPN behavior unchanged: no TUN-to-SOCKS forwarding, no Android default-route capture, no device traffic forwarding, no background checks, no auto-connect, no silent DNS changes, and no credential exposure.

## 0.7.6-alpha — SOCKS5 readiness summaries

- Add read-only SOCKS5 readiness summaries derived from local no-backup manual diagnostic history.
- Show compact readiness in SOCKS5 profile lists and clearer last handshake / CONNECT status in profile details.
- Include manual SOCKS5 diagnostic readiness in route explanations while preserving the warning that runtime forwarding is not enabled.
- Keep runtime TUN-to-SOCKS forwarding, Android default-route capture, background checks, startup checks, auto-connect, and silent DNS changes out of scope.

## 0.8.4-alpha VLESS profile model

- Add VLESS as a local configuration/profile type for route decision preview only.
- Store VLESS UUID and placeholder TLS/REALITY metadata locally in `routing_config.json`; warn users that exports can contain connection identifiers.
- Validate host, port, and UUID manually without connecting to any VLESS server.
- Show the route-preview warning: "Selected profile is VLESS. Runtime forwarding is not enabled yet."
- Do not implement VLESS runtime forwarding, packet forwarding, TUN writes, REALITY/XTLS runtime, DNS proxying, telemetry, analytics, cloud upload, startup tests, or auto-connect.
