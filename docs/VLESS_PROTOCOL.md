# VLESS protocol construction boundary

ViRouteFS `0.8.7-alpha` adds a pure/local VLESS TCP request encoder for future manual handshake diagnostics. The encoder constructs only the first VLESS TCP request frame in memory and returns it as a `ByteArray`.

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

## Safety boundary

The VLESS protocol builder is pure protocol construction only:

- No socket usage.
- No network I/O.
- No DNS resolution.
- No VLESS server connection.
- No VLESS handshake execution yet.
- No VLESS runtime forwarding.
- No packets are sent to a VLESS server.
- No packets are written back to TUN.
- No DNS proxying.
- No TLS, REALITY, or XTLS handshake.
- No WebSocket, gRPC, Mux, XUDP, or UDP forwarding.
- No telemetry, analytics, tracking SDKs, cloud upload, or automatic export.

UUID values are used only to construct the local in-memory VLESS frame. The builder does not log UUIDs, debug helpers redact UUID bytes by default, and validation errors intentionally use safe messages such as `invalid UUID`, `invalid host`, `invalid port`, or `unsupported address type` without echoing sensitive input.
