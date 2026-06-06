# VLESS protocol construction boundary

ViRouteFS `0.8.7-alpha` added a pure/local VLESS TCP request encoder for future manual handshake diagnostics. The encoder constructs only the first VLESS TCP request frame in memory and returns it as a `ByteArray`.

ViRouteFS `0.8.8-alpha` added an explicit manual plain-TCP VLESS protocol probe that is run only when the user taps **Run VLESS probe** from a saved VLESS profile. The plain probe opens a TCP socket to the configured VLESS server, builds one minimal VLESS TCP request frame with the existing builder, sends that frame to a user-selected target, briefly observes whether the server closes or keeps the connection, and closes the socket.

ViRouteFS `0.8.9-alpha` extends the same user-triggered manual VLESS protocol probe for `security=tls`. TLS probes validate the profile, open TCP to the VLESS server, wrap the connection in TLS, set SNI from the profile SNI or default to the profile host, complete the TLS handshake, send the existing minimal VLESS TCP request frame over TLS, wait briefly for close/error/timeout, and close the socket. The TLS probe sends no HTTP payload and stores only privacy-safe local no-backup history.

ViRouteFS `0.8.10-alpha` adds response classification to the manual probe. After sending the minimal VLESS TCP request frame over the existing plain-TCP or TLS path, the probe reads a small response buffer with a short timeout and records only metadata: classification, elapsed time, response byte count, target, and security mode. Response payload bytes are discarded immediately, never displayed, and never stored.

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

## What is included through 0.8.10-alpha

- Manual plain-TCP VLESS protocol probe from the VLESS profile screen.
- Manual TLS VLESS protocol probe from the VLESS profile screen for `security=tls`.
- Target host/port fields with safe defaults (`example.com` and `80`).
- Validation of the VLESS profile and selected target before sending a frame.
- Support for manual probes with `security=none` and `security=tls`; `security=reality` returns unsupported because REALITY is not implemented yet.
- Probe states for TCP connection, TLS handshake success/failure, VLESS request send, request sent with no immediate response, response received, server close, timeout, invalid/empty response, refused connection, host/DNS error, validation error, and unsupported transport.
- Response classification and response byte count only; payload contents are not shown, stored, or logged.
- Local no-backup history capped at 20 entries per profile, including the security mode used for each manual probe.
- History and UI text that do not include UUID values, raw VLESS frame bytes, or response payload bytes.

## Safety boundary

The VLESS protocol builder remains pure protocol construction only:

- No socket usage.
- No network I/O.
- No DNS resolution.
- No VLESS server connection.
- No runtime forwarding.
- No packets are written back to TUN.
- No DNS proxying.
- The pure builder performs no TLS, REALITY, or XTLS handshake. TLS is only applied by the separate manual `0.8.9-alpha`/`0.8.10-alpha` probe path before it sends the builder output.
- No WebSocket, gRPC, Mux, XUDP, or UDP forwarding.
- No telemetry, analytics, tracking SDKs, cloud upload, or automatic export.

The `0.8.10-alpha` manual probe is user-triggered only from the VLESS profile screen. `security=none` keeps the existing plain TCP path; `security=tls` opens TLS with SNI and sends the same minimal VLESS request frame over TLS. The response probe reads metadata only and does not capture or store payload contents. It does not enable runtime VLESS forwarding, Android traffic forwarding, TUN writes, DNS proxying, REALITY, XTLS, UDP, HTTP payload sending, or automatic testing.

UUID values are used only to construct the local in-memory VLESS frame. The builder and probe do not log UUIDs, debug helpers redact UUID bytes by default, and validation/result/history text intentionally use safe messages without echoing sensitive input.
