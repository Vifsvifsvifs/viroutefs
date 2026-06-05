# ViRouteFS 0.8.9-alpha release notes

ViRouteFS `0.8.9-alpha` adds a manual VLESS TLS transport probe for saved VLESS profiles.

## Added

- Manual `security=tls` VLESS protocol probe from the VLESS profile screen.
- TLS probes open TCP, wrap the socket with TLS, use profile SNI when present or the profile host when SNI is blank, perform the TLS handshake, send the existing minimal VLESS TCP request frame over TLS, wait briefly for close/error/timeout, and close the socket.
- Probe results now distinguish TCP connection, TLS handshake success/failure, VLESS request sent, server close, brief keep-open, timeout, refusal, DNS/host errors, validation errors, and unsupported transports.
- Local no-backup VLESS protocol probe history remains capped at 20 entries per profile and stores only sanitized state, message, elapsed time, target, and security mode.

## Safety boundaries

- `security=none` keeps the existing plain TCP VLESS probe behavior.
- REALITY is not implemented yet and returns: "REALITY transport is not implemented yet."
- Runtime VLESS forwarding is not implemented.
- Android traffic forwarding is not implemented.
- Packets are not written back to TUN.
- DNS proxying is not implemented.
- XTLS is not implemented.
- The probe sends no HTTP payload and does not auto-test.
- UUID values and raw VLESS frame bytes are not shown in result messages or stored in probe history.
