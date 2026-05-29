# ViRouteFS routing vision

ViRouteFS is a human-readable traffic routing and diagnostics app for Android. The product goal is simple:

> Show where traffic goes, explain why, and help fix the route.

## Architecture direction

ViRouteFS should use one Android `VpnService` as the local traffic entry point. Future routing logic can then decide which internal outbound tunnel should handle each flow. This keeps Android integration understandable for users while allowing multiple internal paths behind one VPN permission prompt.

Planned outbound profile types include:

- Direct device networking.
- Blocked routes for explicit local policy decisions.
- Xray outbound profiles.
- Hysteria2 outbound profiles.
- OpenVPN outbound profiles.

These profile types describe the direction of the product. The current milestone does not start, configure, or connect any real tunnel engine.

## Route rules

The route engine is planned around clear, human-readable rules. Rules can match traffic by:

- App or app group.
- Domain name.
- CIDR network range.
- DNS-derived routing information in a later milestone.
- A safe default route when no more specific rule matches.

Each rule should explain which tunnel profile it selects, why it matched, and what the user should do if the result is unexpected.

## Route simulator

The first Route Intelligence milestone adds a mock route simulator. A user can enter a domain, IP address, or app-like keyword and see the route that would be selected by local in-memory sample rules.

The simulator is intentionally local-only and read-only:

- It does not enable Android VPN routing.
- It does not connect Xray, Hysteria2, or OpenVPN.
- It does not capture packets.
- It does not upload diagnostics or logs.

This simulation-first workflow lets users understand policy routing before real traffic handling exists.

## Human-readable explanations

Every route decision should include:

1. The selected route.
2. The matched rule.
3. A simple explanation in plain language.
4. Technical details such as rule type and priority.
5. A recommended action when possible.

The app should remain useful for non-experts while still exposing enough detail for troubleshooting.

## Route diagnostics in 0.3-alpha

The `0.3.0-alpha` milestone attaches defensive diagnostics to a simulated route decision. A user enters a target, port, and optional SNI; ViRouteFS selects a mock route with `RouteEngine`, explains the matched rule, and then checks the target through the current Android network.

This is intentionally not real VPN routing: selected tunnels are mock profiles, and diagnostics do not travel through Xray, Hysteria2, OpenVPN, or packet-capture logic. Reports stay local unless the user explicitly copies or shares them, and the last five reports are stored only in memory for the current app session.

## Future diagnostics per route

Later milestones can attach defensive diagnostics to a real selected route, including:

- DNS checks.
- TCP connectivity checks.
- TLS/SNI checks.
- HTTP status and redirect checks.
- MTU and MSS diagnostics.

Diagnostics must stay defensive, transparent, permission-based, and local-first.

## Current milestone scope

The current milestone remains simulation-first. It has the Russian "Маршруты" screen, sample tunnel profiles, sample route rules, an in-memory route engine, and Route Diagnostics reports that combine simulated route selection with real current-network DNS/TCP/TLS/HTTP checks. It does not implement real VPN routing, packet capture, Xray, Hysteria2, or OpenVPN behavior.

## Launcher icon assets

The launcher icon in this milestone is XML/vector only. ViRouteFS avoids PNG, JPG, WebP, and other binary icon assets in text-focused pull requests so changes remain reviewable in text diffs.
