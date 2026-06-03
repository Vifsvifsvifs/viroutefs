# Routing configuration

ViRouteFS stores routing configuration locally. The model is intended to explain future policy routing without changing device routing in the current alpha.

## 0.6.4-alpha defaults

The clean default configuration includes only real built-in profiles:

- `Direct` — keep traffic on the current device network.
- `Block` — fail closed for traffic that must not continue.

The default route model is `Default Direct` for planning. No fake Xray, Hysteria2, OpenVPN, SOCKS5, banking, media, or work categories are created as real user configuration.

## Rule targets

Route targets must be selected from actual available profiles:

- Direct.
- Block.
- Future user-created active profiles when those flows are implemented.

App selection has started at the UI/model boundary with Android launchable-app discovery through `PackageManager`. Full route creation/editing remains future work.

## Strict isolation

Rules are exclusive: app/domain/IP bindings must not silently fall back to another tunnel/profile. If the selected profile is unavailable, the safe behavior is Block / fail closed.

See [ROUTING_POLICY.md](ROUTING_POLICY.md).
