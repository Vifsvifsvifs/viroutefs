# ViRouteFS UI concept — 0.12.0-beta.2

ViRouteFS 0.12 beta organizes the Android app around four primary tasks:

1. **Control / Контроль** — one primary network-control action, the phone's default route, quick safety actions, configuration health, and user VPN profiles.
2. **Routes / Маршруты** — assign installed apps, domains, IP addresses, or CIDR networks to a VPN/proxy, `System`, `Block`, or `ByeDPI`.
3. **Scanner / Сканер** — live local connection metadata and per-app filtering without payload capture.
4. **More / Ещё** — DNS policies, diagnostics/tools, settings, updates, help, licensing, and developer controls.

DNS and Settings remain full screens, but no longer compete with daily network-control tasks in the bottom navigation.

## Compact rules

- One screen focuses on one main task.
- One card presents one idea.
- The Control screen does not duplicate the packet inspector or repeat built-in `System`/`Block` explanations.
- Content pages use 16 dp horizontal padding, calmer surfaces, and four stable bottom destinations.
- Horizontal choices scroll instead of wrapping or clipping on narrow phones.
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
