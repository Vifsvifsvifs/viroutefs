# Dev-only VLESS TCP bridge (0.9.0-alpha)

ViRouteFS 0.9.0-alpha adds the first development-only TCP bridge for VLESS profiles. The bridge is intentionally narrow: it can open one explicit test TCP stream to a configured VLESS server and a single target host/port so developers can validate the request and response path.

## Usage

`DevTcpBridge` exposes four test-session methods:

- `openDevSession(profileId, targetHost, targetPort)` opens one VLESS TCP test session.
- `closeDevSession(sessionId)` closes the active dev session.
- `sendTestData(sessionId, byteArray)` writes explicit developer-provided test bytes.
- `receiveTestData(sessionId)` reads response bytes for the active test stream.

Only one session can be active at a time. This is a safety limit to prevent accidental runtime forwarding.

## VLESS integration

The implementation uses the existing VLESS TCP request builder and selects the transport from the VLESS profile:

- `security=none` uses plain TCP.
- `security=tls` wraps the TCP socket in TLS and uses the profile SNI when provided.
- `security=reality` is not implemented yet.

The VLESS request is sent as protocol setup metadata. UUID values and sensitive values are not logged, stored in history, or shown in runtime events.

## Safety boundaries

0.9.0-alpha still has no device runtime forwarding. The dev TCP bridge does not forward Android traffic from the TUN packet inspector and does not accept general packet forwarding input.

Not implemented in this release:

- Android traffic forwarding
- UDP forwarding
- DNS forwarding
- REALITY
- XTLS
- telemetry, analytics, cloud upload, or automatic PCAP/log upload

Logs and counters remain local. The Runtime UI labels the bridge as dev-only and repeats that no Android traffic is forwarded.
