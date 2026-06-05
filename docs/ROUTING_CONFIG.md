# Routing configuration

ViRouteFS stores routing configuration locally. The model is intended to explain future policy routing without changing device routing in the current alpha.

## 0.6.5-alpha defaults

The clean default configuration includes only real built-in profiles:

- `System / Система` — default Android/system network path inside ViRouteFS. It is the default for apps without explicit rules and is not bypass when network control is active.
- `Block` — fail closed for traffic that must not continue.

The default route model is `Default System` for planning. No fake Xray, Hysteria2, OpenVPN, SOCKS5, banking, media, or work categories are created as real user configuration.

The old user-facing `Direct` name was removed as a duplicate of System. Legacy saved config ids may still say `direct` for compatibility, but normal UI should show System unless a future technical difference is implemented and documented.

## Rule targets

Route targets must be selected from actual available profiles:

- System / Система.
- Block.
- Future user-created active profiles when those flows are implemented.

App selection has started at the UI/model boundary with Android launchable-app discovery through `PackageManager`. Full route creation/editing remains future work.

## DNS defaults

If no DNS is configured for a route/profile, the model says **Uses Android system DNS**. ViRouteFS must not silently substitute public resolvers. User-defined DNS applies only where explicitly configured.

## Strict isolation

Rules are exclusive: app/domain/IP bindings must not silently fall back to another tunnel/profile. If the selected profile is unavailable, the safe behavior is Block / fail closed.

See [ROUTING_POLICY.md](ROUTING_POLICY.md).

## SOCKS5 readiness in route explanations

Starting in 0.7.6-alpha, route explanations may show a selected SOCKS5 profile's last manual diagnostic readiness, such as a recent manual CONNECT success target or the latest safe failure message. This readiness is derived from local no-backup SOCKS5 test history only.

The routing model still does not implement runtime TUN-to-SOCKS forwarding, Android default-route capture, or real device traffic forwarding through SOCKS5. Any route explanation for SOCKS5 must keep the warning: "Selected profile: SOCKS5. Runtime forwarding is not enabled yet."

## SOCKS5 outbound connector foundation

Starting in 0.7.7-alpha, the app has an internal SOCKS5 outbound connector abstraction used by explicit manual CONNECT diagnostics. It is a foundation for future runtime routing, but the routing configuration model still does not route Android device traffic through SOCKS5. It does not enable TUN-to-SOCKS forwarding, default-route capture, background checks, startup tests, auto-connect, silent DNS changes, credential export, telemetry, or cloud upload.

## VLESS profile model in 0.8.4-alpha

VLESS profiles are configuration-only in 0.8.4-alpha. The app can store and validate local profile fields for route decision preview, including UUID and placeholder TLS/REALITY metadata, but it does not connect to VLESS servers, forward packets, write packets back to TUN, implement REALITY/XTLS runtime, or proxy DNS. Route decision preview must warn: "Selected profile is VLESS. Runtime forwarding is not enabled yet."

`routing_config.json` and user exports may contain VLESS connection identifiers such as UUID, host, SNI, and placeholder key metadata. Treat exported routing configs as sensitive local files. UUID values must not appear in summaries, diagnostics text, logs, or route-preview text. No telemetry, cloud upload, analytics, ads, background validation, startup tests, or auto-connect behavior is added.
