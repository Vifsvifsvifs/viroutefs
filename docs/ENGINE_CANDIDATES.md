# ViRouteFS engine candidates — 0.6.7-alpha

This document records candidate network engines and profile families for future ViRouteFS work. It is planning documentation only: `0.6.7-alpha` does not add runtime VPN engines, proxy implementations, native binaries, packet forwarding, default-route capture, VPN builder DNS servers, or packet payload logging.

ViRouteFS remains GPL-3.0-or-later and local-first. Every implementation must be audited before embedding for license compatibility, Android feasibility, security, maintenance, privacy, and source availability.

## Green / preferred candidates

- **SOCKS5**
  - Status: likely first real outbound candidate.
  - Reason: simple, useful for architecture validation, no VPN protocol complexity.
  - License note: implementation must be checked before embedding.

- **HTTP / HTTPS proxy**
  - Status: useful early outbound candidate.
  - License note: implementation must be checked before embedding.

- **ByeDPI-style local DPI bypass**
  - Status: important future local profile type.
  - Role: local DPI bypass profile, not a full VPN.
  - UI model: can appear as a Network profile and be used as route target.
  - Safety: if selected for an app/domain and unavailable, fail closed / Block; never fallback to another profile.
  - License note: preferred implementation must be audited before embedding.

- **Hysteria2**
  - Status: future external tunnel candidate.
  - License note: implementation must be audited before embedding.

- **Shadowsocks**
  - Status: future outbound candidate.
  - License note: prefer permissive/GPL-compatible implementation.

- **WireGuard userspace**
  - Status: important future VPN profile.
  - License note: prefer GPL-compatible userspace implementation; avoid GPLv2-only tools embedded into GPLv3 app.

- **OpenVPN3 Core**
  - Status: preferred OpenVPN path.
  - License note: use MPL-2.0 path if integrated; preserve MPL notices/source obligations.
  - Important: do not embed OpenVPN 2.x GPLv2-only core into GPL-3.0-or-later app.

- **Xray / VLESS / Reality / VMess / Trojan**
  - Status: future tunnel/import candidate.
  - License note: MPL-2.0 style audit required; preserve notices/source obligations.

- **sing-box**
  - Status: possible future engine/core candidate.
  - License note: GPL-3.0-or-later compatible, but large dependency/architecture decision.

## Yellow / requires deeper audit

- NaiveProxy
- SoftEther
- OpenConnect / AnyConnect-compatible
- IKEv2 / IPSec implementations
- SSTP
- TUIC
- Brook
- ShadowTLS

These may be useful, but must be audited for license, Android feasibility, native dependencies, maintenance, and security before any integration decision. A yellow candidate must not be treated as approved for bundling until that audit is complete and recorded in `docs/THIRD_PARTY_LICENSES.md`.

## Red / do not embed without major decision

- **OpenVPN 2.x core**
  - Reason: GPLv2-only risk with GPL-3.0-or-later app.
- **wireguard-tools**
  - Reason: GPLv2-only tools are risky to embed in GPL-3.0-or-later APK.
- **unknown binaries without LICENSE**
- **proprietary SDKs or tracking SDKs**
- **engines with unclear source availability**
- **anything requiring hidden interception or payload logging**

Red items are not acceptable for routine embedding. Changing this requires a major project decision, explicit documentation, and confirmation that ViRouteFS still respects GPL-3.0-or-later licensing and its privacy/safety boundaries.
