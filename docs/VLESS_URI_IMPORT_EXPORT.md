# VLESS URI import and export

ViRouteFS imports and exports `vless://` profile URIs locally.

Supported runtime fields include host, port, UUID, flow, TCP/WebSocket/gRPC transport, TLS, REALITY, SNI, uTLS fingerprint, REALITY public key/short id, WebSocket path/Host header, ALPN and gRPC service name.

## Safety

- Import does not connect automatically.
- The user sees a preview before applying imported data.
- Saving validates required VLESS and REALITY fields.
- UUID and other identifiers are hidden in normal summaries.
- Exported URIs contain connection secrets and must be shared carefully.
- Manual reachability/protocol probes are separate explicit diagnostics.

When network control is active, the saved profile is compiled into the sing-box TUN runtime and can be selected by app/domain/IP/CIDR rules.
