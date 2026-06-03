# Routing policy

ViRouteFS routing must be explicit, local-first, and fail closed.

## Network control model

When network control is **OFF**:

- Android works normally.
- ViRouteFS does not control traffic.
- Current diagnostics still run only when the user starts them manually.

When network control is **ON** in the product model:

- All device traffic must conceptually enter ViRouteFS.
- No traffic should bypass ViRouteFS.
- Apps without explicit rules use the built-in **System / Система** route inside ViRouteFS.
- Matched apps, domains, hosts, or CIDRs use only the selected route/profile.
- If the selected profile is unavailable, the safe behavior is **Block / fail closed**.
- ViRouteFS must never silently fall back from one explicit route/profile to another.
- No kilobyte should bypass ViRouteFS while network control is active.

This is the corrected product model for docs, configuration, and UI wording. Full runtime enforcement is still a future routing-engine task; this change does not add default-route enforcement, DNS servers in the VPN builder, packet payload logging, forwarding, proxying, or tunnel engines.

## Built-in routes/profiles

- **System / Система** — the default Android/system network path, controlled by ViRouteFS when network control is active. This is the default route for apps without explicit rules. It is internal to ViRouteFS and must not be described as bypass.
- **Block / Блокировать** — drops or blocks matched traffic in the policy model and represents fail-closed behavior.

The old user-facing **Direct** wording was only a confusing duplicate of the phone/system route. It is removed from normal product wording and renamed to **System / Система**. Legacy ids/types may still contain `direct` internally for saved-config compatibility, but they mean the System route unless a future implementation documents a distinct technical difference.

## Strict route isolation

App, domain/host, and IP/CIDR rules are exclusive policy bindings:

- If a rule says an app must use a chosen profile, that traffic must not use another profile.
- If a rule says a domain/host must use a chosen profile, that traffic must not use another profile.
- If a rule says an IP/CIDR range must use a chosen profile, that traffic must not use another profile.
- If the chosen profile is unavailable, the safe behavior is **Block / fail closed**.
- ViRouteFS must never silently fall back to a foreign VPN profile.

Example: if `MAX` is assigned to `System / Система` or to a future `Russia` profile, that traffic must not silently use future Germany/Xray/Hysteria/WireGuard foreign profiles.

## DNS defaults

- If no DNS is configured for a route/profile, ViRouteFS uses Android/system DNS through the ViRouteFS policy model.
- Missing DNS must not be silently replaced with public resolvers such as `1.1.1.1` or `8.8.8.8`.
- User-defined DNS applies only where explicitly configured.
- If user-defined DNS is unavailable, the route/profile should show an error or fail-safe state; ViRouteFS must not silently swap to another resolver.

## Current 0.6.5-alpha behavior

- Normal UI exposes only real built-in profiles: `System / Система` and `Block`.
- User-facing route and DNS targets come from actual available profiles.
- Fake tunnels, fake categories, and TEST-NET route controls are not normal user features.
- The internal TEST-NET route remains developer/testing-only documentation for the safe TUN skeleton.
- No runtime VPN/TUN routing behavior changes are introduced by the routing wording and icon update.
