# Routing policy

ViRouteFS routing must be explicit, local-first, and fail closed.

## Strict route isolation

App, domain/host, and IP/CIDR rules are exclusive policy bindings:

- If a rule says an app must use a chosen profile, that traffic must not use another profile.
- If a rule says a domain/host must use a chosen profile, that traffic must not use another profile.
- If a rule says an IP/CIDR range must use a chosen profile, that traffic must not use another profile.
- If the chosen profile is unavailable, the safe behavior is **Block / fail closed**.
- ViRouteFS must never silently fall back to a foreign VPN profile.

Example: if `MAX` is assigned to `Direct` or to a future `Russia` profile, that traffic must not silently use future Germany/Xray/Hysteria/WireGuard foreign profiles.

This is documented now for the model and future engine work. Full runtime enforcement belongs to later routing-engine milestones.

## Current 0.6.4-alpha behavior

- Normal UI exposes only real built-in profiles: `Direct` and `Block`.
- User-facing route and DNS targets should come from actual available profiles.
- Fake tunnels, fake categories, and TEST-NET route controls are not normal user features.
- The internal TEST-NET route remains developer/testing-only documentation for the safe TUN skeleton.
- No runtime VPN/TUN routing behavior changes are introduced by the navigation cleanup.
