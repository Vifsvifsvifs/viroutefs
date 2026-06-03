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
