# ViRouteFS 0.14.0-beta.14

- Subscribes to the native sing-box OpenVPN status channel for every active routed OpenVPN endpoint.
- Shows the actual OpenVPN stage and native TLS, certificate, or authentication error instead of reducing every failure to a generic TCP connection message.
- Distinguishes an established OpenVPN tunnel with a failed HTTPS check from a tunnel that never completed its handshake.
- Explains interactive authentication challenges without exposing passwords, private keys, or other secret values.
- Keeps the beta.13 automatic VPNGate repair, one-card management, guided setup, failover, and Android Back fixes unchanged.
