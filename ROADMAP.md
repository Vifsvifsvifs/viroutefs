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

## 0.7.x: controlled route experiments

Focus: start carefully testing route decisions while keeping full runtime enforcement disabled until packet handling is safe.

- Controlled System/Block route experiments.
- Route explanations for why a destination would use System, be blocked, or be reserved for a future tunnel.
- Safer UX around warnings, limitations, and diagnostics.
- No runtime full-device enforcement until safe packet handling exists.
- No hidden interception or payload logging.

## 0.8.x: first external outbound experiment

Focus: first explicitly enabled outbound experiment after safe route handling is designed.

- First external outbound experiment, likely SOCKS5.
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

- System / Система is the built-in internal default route for apps without explicit rules when network control is active; it is not bypass.
- The old Direct wording is removed from normal UI as a duplicate of System. Legacy `direct` ids/types may remain only for saved-config compatibility.
- DNS defaults to Android system DNS unless the user explicitly configures DNS; missing DNS is not silently replaced by public resolvers.
- Explicit rules are exclusive: matched traffic uses only the selected profile, and unavailable selected profiles fail closed / Block.
- Full runtime enforcement remains a future task: no default-route enforcement, no DNS servers in the VPN builder, no payload logging, no forwarding/proxying.
- Adaptive icon direction is aggressive black/red stylized V: sharp, technical, adult, not childish and not a generic VPN shield.
