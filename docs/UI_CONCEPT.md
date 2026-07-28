# ViRouteFS UI concept — 0.12.0-beta.1

ViRouteFS 0.6.5-alpha organizes the Android app around five compact product tasks:

1. **Networks / Сети** — real network profiles and safe local network control.
2. **Routes** — assign apps, domains/sites and IP/CIDR matchers to connection profiles.
3. **DNS** — run DNS lookup checks, preview app DNS behavior, manage hosts-like overrides and assign DNS policies per connection.
4. **FS** — Flow Scanner: live local connection metadata and per-app filtering without payload capture.
5. **Settings / Настройки** — language, theme, Help / Справка, project support, and developer diagnostics.

The main navigation is: Networks / Сети, Routes / Маршруты, DNS, FS, and Settings / Настройки. Home / Главная content moved into Settings → Help / Справка.

## Compact rules

- One screen focuses on one main task.
- One card presents one idea.
- Raw advanced fields such as priority, technical details and recommended action are hidden from the primary Routes view.
- Russian-first labels are used for user-facing concepts.
- Technical details remain available in result cards and documentation.

## Current boundaries

- The real single-`VpnService` runtime is arm64-only and still needs testing on a physical device.
- OpenVPN/OpenConnect and the other catalog entries marked **Работает** are compiled into the pinned sing-box runtime.
- IKEv2/IPsec and legacy L2TP/PPTP/SSTP do not have audited Android adapters and must not be shown as working.
- Flow Scanner shows metadata, not decrypted HTTPS or packet contents.
- DNS per profile/rule is enforced by the runtime and fails closed when its selected detour is unavailable.
- There is no telemetry, analytics, ads, tracking, cloud log upload or background monitoring.


See also docs/UI_DIRECTION.md for placeholder and icon direction.
