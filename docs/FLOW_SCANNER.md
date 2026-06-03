# Flow Scanner

Flow Scanner (FS) is the future ViRouteFS view for explaining local flow visibility in human language.

## 0.6.5-alpha behavior

FS does **not** claim full traffic analysis yet.

Current user-facing behavior:

- Shows an empty/local state when no counters are available.
- Shows local TEST-NET counters only when the developer diagnostic TEST-NET route has been explicitly enabled or counters are non-zero.
- Does not show fake app flows as real traffic.
- Does not show fake tunnels, fake banking/media/work categories, or fake configured entities.

Current safety boundaries:

- No full-device default route.
- No DNS servers added to the VPN builder.
- No packet payload logging.
- No payload inspection.
- No domain extraction from packets.
- No forwarding.
- No proxying.
- No telemetry, analytics, tracking SDKs, ads, or cloud upload.

The developer TEST-NET route is `203.0.113.0/24`. Packets routed into that preview are counted and dropped. Counters are local runtime state.
