# ViRouteFS UI concept — 0.6.4-alpha

ViRouteFS 0.6.4-alpha organizes the Android app around five compact product tasks:

1. **Networks / Сети** — real network profiles and safe local network control.
2. **Routes** — assign apps, domains/sites and IP/CIDR matchers to connection profiles.
3. **DNS** — run DNS lookup checks, preview app DNS behavior, manage hosts-like overrides and assign DNS policies per connection.
4. **FS** — Flow Scanner: local counters when available; no claim of full traffic analysis yet.
5. **Settings / Настройки** — language, theme, Help / Справка, project support, and developer diagnostics.

The main navigation is: Networks / Сети, Routes / Маршруты, DNS, FS, and Settings / Настройки. Home / Главная content moved into Settings → Help / Справка.

## Compact rules

- One screen focuses on one main task.
- One card presents one idea.
- Raw advanced fields such as priority, technical details and recommended action are hidden from the primary Routes view.
- Russian-first labels are used for user-facing concepts.
- Technical details remain available in result cards and documentation.

## Current limitations

This release is UI/model-level only for connection engines and flow capture:

- Real Android VPN routing is not implemented yet.
- Xray, OpenVPN, WireGuard, Hysteria2 and other engines are not implemented yet and must not be shown as real configured tunnels.
- Flow Scanner does not claim full traffic analysis yet.
- DNS per connection and hosts-like overrides are configuration/simulation metadata for now.
- There is no telemetry, analytics, ads, tracking, cloud log upload or background monitoring.


See also docs/UI_DIRECTION.md for placeholder and icon direction.
