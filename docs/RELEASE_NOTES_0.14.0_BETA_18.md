# ViRouteFS 0.14.0-beta.18

## Safer VPNGate and precise OpenVPN diagnostics

- VPNGate can no longer become the default route automatically, including when an older saved configuration previously used it as the default.
- Enabling VPNGate without selected applications opens the application picker; all unselected applications keep the System route.
- The easy setup warns against selecting banking, government-service, payment and sensitive-data applications.
- Added a preferred-country list. Automatic mode can stay within a selected country and fail over only between that country's servers.
- Newly prepared VPNGate members remain disabled until applications are selected.
- Native OpenVPN probe noise no longer hides the actual session failure.
- The bundled sing-openvpn source now adds a small auditable diagnostic-only patch that reports whether a failure happened during reset, TLS, key exchange, pull configuration, cipher negotiation or data-channel setup.

The personal TCP OpenVPN profile is still under physical-device diagnosis: beta.17 changed the failure from `Connection reset` to `EOF`, while System traffic remained available. This build identifies the exact failing protocol stage without logging credentials.
