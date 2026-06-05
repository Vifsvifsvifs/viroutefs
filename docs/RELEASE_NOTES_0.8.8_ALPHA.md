# ViRouteFS 0.8.8-alpha release notes

ViRouteFS `0.8.8-alpha` adds an explicit manual plain-TCP VLESS protocol probe for configured VLESS profiles.

## Added

- Manual **VLESS protocol probe** section on the VLESS profile screen.
- User-selected probe target host and port, defaulting to `example.com:80`.
- Plain TCP socket connection to the configured VLESS server only after the user taps **Run VLESS probe**.
- Local construction of one minimal VLESS TCP request frame with the existing VLESS request builder.
- Local no-backup probe history, capped at 20 entries per profile.
- Privacy-safe result/history text that does not include UUID values or raw frame bytes.

## Boundaries

- TLS/REALITY transport is not implemented yet; TLS and REALITY profiles report that limitation instead of probing.
- Runtime VLESS forwarding is not implemented.
- Android traffic is not forwarded.
- Packets are not written back to TUN.
- DNS proxying is not implemented.
- The probe sends no HTTP payload after the VLESS request frame.
- No telemetry, cloud upload, analytics, ad SDKs, automatic checks, or automatic tests are added.
