# Routing policy

ViRouteFS uses one Android `VpnService` and one sing-box routing table.

## Invariants

1. When network control is active, IPv4, IPv6 and DNS enter the local TUN router.
2. Rules are evaluated by priority.
3. A matching rule selects exactly one outbound or endpoint.
4. An unavailable target maps to `Block`; there is no silent fallback to another VPN or `System`.
5. Unmatched traffic uses the explicit default rule.
6. The app’s own UID is excluded from TUN to prevent loops in sing-box and the ByeDPI child process.

## Matchers

- Android package name (`package_name`, Android 10+);
- exact domain;
- domain suffix;
- domain keyword;
- regular expression;
- IP/CIDR;
- default.

## Built-in targets

- `System`: the normal Android network path inside the router;
- `Block`: reject matching traffic;
- `ByeDPI`: local app-private SOCKS route when enabled.

User profiles add VLESS, SOCKS5, advanced sing-box outbounds and endpoints.

## Emergency block

The emergency switch inserts an all-traffic Block rule before normal routing and makes Block the final route. DNS hijacking is omitted in this mode. Profiles remain stored and become active again only after the switch is disabled.

## DNS

DNS is intercepted by the TUN runtime. A rule’s explicit policy wins; otherwise the target profile policy is used. An unavailable DNS detour rejects the matching query.

## Configuration changes

Config is written atomically. If the VPN is active, a saved change triggers a controlled runtime reload. No profile is tested in the background.
