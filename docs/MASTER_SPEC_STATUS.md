# ViRouteFS 1.0 specification status

This file tracks the completion specification whose baseline is
`0.12.0-beta.3`. A feature is not described as working merely because a model,
editor or generic JSON field exists.

## Status scale

The application uses the same seven states as the specification:

1. `ModelOnly`
2. `ConfigSupported`
3. `RuntimeIntegrated`
4. `DeviceVerified`
5. `ProductionReady`
6. `Unavailable`
7. `LegacyRestricted`

Only the built-in `System` route is currently marked `DeviceVerified`: it was
tested on a physical Android device with activation without a VPN profile,
validated IPv4/DNS traffic and clean shutdown. No external VPN protocol is
marked `DeviceVerified` or `ProductionReady`.

## Release consolidation

The planned minimum release line is:

* `0.13.x beta` — M0 foundation plus profile/import and routing work that can be
  completed without new native engines;
* `0.14.x beta` — remaining protocol adapters, DNS/routing, scanner,
  diagnostics, audit and root tools;
* `1.0.0-rc` — feature freeze and physical/server matrix;
* `1.0.0` — only after all stable acceptance criteria pass.

Patch releases are reserved for defects. A new beta is published only for a
large, installable block.

## Milestones

| Milestone | State | Current evidence |
|---|---|---|
| M0 truthful status and architecture | Implemented in `0.13.0-beta.2`; external protocol validation still required | seven-state catalog, `EngineAdapter`, live `EngineOrchestrator`, AES-GCM/Keystore secret migration, structured errors, physically verified System route |
| M1 profiles and import | In progress | VLESS URI, OpenVPN file parser, SOCKS5 and advanced editors exist; QR/share/subscriptions/encrypted full export remain |
| M2 sing-box protocols | In progress | one TUN and multiple outbounds compile; protocol-specific editors/importers and physical matrix remain |
| M3 strongSwan | Not started | no embedded strongSwan adapter or binary |
| M4 legacy | Not started | warnings exist; L2TP/PPP/PPTP/SSTP engines do not |
| M5 external adapters | Research | zapret2 feasibility is documented; ZeroTier/SoftEther/Tor/Brook are not integrated |
| M6 routing and DNS | In progress | app/domain/CIDR/default/System/Block/custom DNS exist; extended matchers, groups, DNS failover and atomic reload remain |
| M7 Flow Scanner and diagnostics | In progress | real sing-box connection events and physical-network tests exist; full attribution matrix and routed diagnostics remain |
| M8 audit and root tools | Not started | must remain manual, bounded and non-offensive |
| M9 feature freeze | Blocked by M1–M8 | no freeze yet |
| M10 full debugging | Blocked by feature completion | server lab, four-device matrix and soak tests remain |
| M11 release candidate | Blocked | RC criteria are not met |
| M12 stable | Blocked | stable criteria are not met |

## Immediate completion order

1. Finish structured import/export and secret-aware profile lifecycle.
2. Finish matchers, profile groups, DNS failover and atomic reload.
3. Finish sing-box structured protocol support and routed diagnostics.
4. Add audited native adapters: strongSwan, then legacy, then external.
5. Finish scanner, local audit, optional root tools and PCAP export.
6. Freeze features and run the required physical/server matrix.

No stable release may be created automatically. Physical evidence is a manual
release gate.

