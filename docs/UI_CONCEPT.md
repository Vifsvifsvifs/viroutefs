# ViRouteFS UI concept — 0.4.1-alpha

ViRouteFS 0.4.1-alpha reorganizes the Android app around five compact product tasks:

1. **VPN** — manage added connection profiles and a single global demo switch.
2. **Routes** — assign apps, domains/sites and IP/CIDR matchers to connection profiles.
3. **DNS** — run DNS lookup checks, preview app DNS behavior, manage hosts-like overrides and assign DNS policies per connection.
4. **FS** — Flow Scanner: explain friendly flow events after explicit local VPN mode in the future.
5. **Settings** — language, theme and project support.

The main navigation is: Dashboard / Главная, VPN, Routes / Маршруты, DNS, FS, Tools / Инструменты and Settings / Настройки.

## Compact rules

- One screen focuses on one main task.
- One card presents one idea.
- Raw advanced fields such as priority, technical details and recommended action are hidden from the primary Routes view.
- Russian-first labels are used for user-facing concepts.
- Technical details remain available in result cards and documentation.

## Current limitations

This release is UI/model-level only for connection engines and flow capture:

- Real Android VPN routing is not implemented yet.
- Xray, OpenVPN, WireGuard, Hysteria2 and other engines are not implemented yet.
- Flow Scanner does not capture packets yet.
- DNS per connection and hosts-like overrides are configuration/simulation metadata for now.
- There is no telemetry, analytics, ads, tracking, cloud log upload or background monitoring.
