# VLESS URI import and export

ViRouteFS imports and exports `vless://` profile URIs locally.

Supported runtime fields include host, port, UUID, flow, TCP/raw/WebSocket/gRPC/XHTTP transport, TLS, REALITY, SNI, uTLS fingerprint, REALITY public key/short id, path/Host header, ALPN, gRPC service name, XHTTP mode and XHTTP extra JSON.

## Safety

- Import does not connect automatically.
- The user sees a preview before applying imported data.
- Saving validates required VLESS and REALITY fields.
- UUID and other identifiers are hidden in normal summaries.
- Exported URIs contain connection secrets and must be shared carefully.
- Manual reachability/protocol probes are separate explicit diagnostics.

When network control is active, TCP/raw/WebSocket/gRPC profiles compile into
the sing-box TUN runtime. XHTTP profiles compile into a pinned app-private
Xray-core process, while sing-box remains the only TUN owner and sends only
the selected app/domain/IP/CIDR flows to its localhost endpoint.
