# VLESS protocol construction boundary

ViRouteFS `0.8.7-alpha` added a pure/local VLESS TCP request encoder for future manual handshake diagnostics. The encoder constructs only the first VLESS TCP request frame in memory and returns it as a `ByteArray`.

ViRouteFS `0.8.8-alpha` adds an explicit manual plain-TCP VLESS protocol probe that is run only when the user taps **Run VLESS probe** from a saved VLESS profile. The probe opens a TCP socket to the configured VLESS server, builds one minimal VLESS TCP request frame with the existing builder, sends that frame to a user-selected target, briefly observes whether the server closes or keeps the connection, and closes the socket. It sends no HTTP payload and stores only privacy-safe local no-backup history.

## What is included in 0.8.7-alpha

- VLESS version byte `0x00`.
- UUID conversion to the 16 raw bytes required by the VLESS request header.
- Addons length set to `0`.
- TCP command byte `0x01`.
- Destination port encoded big-endian.
- Destination address encoding for:
  - IPv4 literals as four raw bytes.
  - Domain names as one length byte followed by UTF-8 domain bytes.
- Local validation for UUID, host, port, and unsupported address types.
- A safe debug summary helper for tests/developer diagnostics that reports frame length and metadata without printing the UUID or full raw frame bytes.

## What is included in 0.8.8-alpha

- Manual plain-TCP VLESS protocol probe from the VLESS profile screen.
- Target host/port fields with safe defaults (`example.com` and `80`).
- Validation of the VLESS profile and selected target before sending a frame.
- Support only for `security=none` while TLS/REALITY transport is still pending.
- Probe states for TCP connection, VLESS request send, brief keep-open, server close, timeout, refused connection, host/DNS error, validation error, and unsupported security mode.
- Local no-backup history capped at 20 entries per profile.
- History and UI text that do not include UUID values or raw VLESS frame bytes.

## Safety boundary

The VLESS protocol builder remains pure protocol construction only:

- No socket usage.
- No network I/O.
- No DNS resolution.
- No VLESS server connection.
- No runtime forwarding.
- No packets are written back to TUN.
- No DNS proxying.
- No TLS, REALITY, or XTLS handshake.
- No WebSocket, gRPC, Mux, XUDP, or UDP forwarding.
- No telemetry, analytics, tracking SDKs, cloud upload, or automatic export.

The `0.8.8-alpha` manual probe is the only VLESS network diagnostic added in this milestone. It is user-triggered, plain TCP only, and does not enable runtime VLESS forwarding, Android traffic forwarding, TUN writes, DNS proxying, TLS/REALITY/XTLS, or automatic testing.

UUID values are used only to construct the local in-memory VLESS frame. The builder and probe do not log UUIDs, debug helpers redact UUID bytes by default, and validation/result/history text intentionally use safe messages without echoing sensitive input.
