# ViRouteFS Roadmap

ViRouteFS development is intentionally incremental. The project should stay local-first, privacy-preserving, and honest about what is implemented versus planned.

## 0.6.x: safe preview and release polish

Focus: prove the Android VPN permission and TUN lifecycle without routing normal user traffic.

- Safe TUN test route preview.
- Optional TEST-NET route `203.0.113.0/24` for controlled local tests.
- Flow Scanner live test counters for the TEST-NET preview.
- Public alpha README, release notes, support placeholder, and security boundaries.
- No default route.
- No DNS injection into the VPN builder.
- No forwarding, proxying, packet payload logging, or real tunnel engine.

## 0.7.x: controlled route experiments

Focus: start carefully testing route decisions while keeping full-device routing disabled until packet handling is safe.

- Controlled Direct/Block route experiments.
- Route explanations for why a destination would be direct, blocked, or reserved for a future tunnel.
- Safer UX around warnings, limitations, and diagnostics.
- No full-device default route until safe packet handling exists.
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
