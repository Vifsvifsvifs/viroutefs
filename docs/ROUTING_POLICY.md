# Routing policy

ViRouteFS uses one Android `VpnService` and one sing-box routing table.

## Invariants

1. When network control is active, IPv4, IPv6 and DNS enter the local TUN router.
2. Rules are evaluated by priority.
3. A matching rule selects exactly one profile or an explicitly configured group.
4. An unavailable target maps to `Block`; there is no silent fallback to another VPN or `System`.
5. Unmatched traffic uses the explicit `System` default rule: the phone's normal uplink.
6. The app’s own UID is excluded from TUN to prevent loops in sing-box and the TCP/TLS compatibility child process.

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
- `Совместимость TCP/TLS`: local app-private SOCKS route implemented by the pinned ByeDPI engine when enabled.

User profiles add VLESS, SOCKS5, advanced sing-box outbounds and endpoints.

## Profile groups

Manual groups compile to a sing-box `selector`; changing the chosen member is a
normal validated configuration reload. Latency groups compile to `urltest` and
periodically check the user-entered HTTPS URL through only the explicitly
listed members. `System` is never injected into a group: the user must select it
as a member. A manual group with any unavailable member fails closed; a latency
group requires at least two available members. Ordered failover and round-robin
are not claimed yet.

## Emergency block

The emergency switch inserts an all-traffic Block rule before normal routing and makes Block the final route. DNS hijacking is omitted in this mode. Profiles remain stored and become active again only after the switch is disabled.

## DNS

DNS is intercepted by the TUN runtime. A rule’s explicit policy wins; otherwise the target profile policy is used. An unavailable DNS detour rejects the matching query.

## Configuration changes

Config is written atomically. If the VPN is active, a saved change triggers a
controlled runtime reload. The replacement is loaded, compiled and checked by
the native engine while the current route stays active; a rejected replacement
does not tear down the current generation. A successful change still replaces
Android's single VPN/TUN and can reconnect briefly. No remote profile
connectivity test runs in the background unless the user explicitly creates a
latency group and accepts its disclosed HTTPS availability checks.
