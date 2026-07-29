# ViRouteFS 0.12.0-beta.3

This beta makes application-based routing and connection inspection easier to recognize while clarifying the local TCP/TLS compatibility feature.

## Installed-application icons

- The route editor shows each installed application's own launcher icon next to its name and package.
- Flow Scanner app filters show the same local icons.
- Live connection rows and connection details show the attributed application's icon when Android supplies a package name.
- Icons are loaded locally from Android PackageManager and are not persisted, exported or uploaded.

## TCP/TLS compatibility naming

- The user-facing `ByeDPI` profile is now named **Совместимость TCP/TLS**.
- Existing saved configurations are migrated to the new built-in profile label.
- Technical details and license screens continue to identify the pinned ByeDPI MIT implementation, commit, hash and rebuild script.
- The mode is still described accurately: it is not a VPN, does not encrypt all traffic and does not hide the device IP address.

## zapret2 audit

- Current upstream zapret2 `v1.0.3` was reviewed at commit `b78b52c4cd7f843da3ff0848a3430afbd401bdf2`.
- Its project license is MIT.
- It is listed as audited/planned, not working or bundled.
- Upstream Android execution expects NFQUEUE and root-level network capabilities. A rootless adapter inside the existing ViRouteFS `VpnService` is required before it can become a real route.

## Safety boundary

Routing behavior remains fail-closed. A selected unavailable tunnel or compatibility engine still compiles to `Block` rather than silently falling back to `System`.

Physical arm64-device validation is still required for icon rendering across vendor launchers, end-to-end routing, DNS, TCP/TLS compatibility and application attribution.
