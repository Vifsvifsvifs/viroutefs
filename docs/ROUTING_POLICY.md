# Routing policy

ViRouteFS uses one Android `VpnService` and one sing-box routing table.

## Invariants

1. When network control is active, IPv4, IPv6 and DNS enter the local TUN router.
2. Rules are evaluated by priority, then name, then stable rule UUID. The UI
   exposes explicit move-up/move-down controls and normalizes visible priorities.
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

The normal editor exposes all four domain modes. Exact, suffix and keyword
values are canonicalized to lowercase; regular expressions keep their syntax
and are validated before save. The simulator and native compiler use the same
parser.

## Built-in targets

- `System`: the normal Android network path inside the router;
- `Block`: reject matching traffic;
- `Совместимость TCP/TLS`: local app-private SOCKS route implemented by the pinned ByeDPI engine when enabled.

User profiles add VLESS, SOCKS5, advanced sing-box outbounds and endpoints.

## Profile groups

Manual, ordered failover and round-robin groups compile to sing-box `selector`
outbounds. Latency groups compile to native `urltest`. Automatic groups use the
local libbox command socket to test the user-entered HTTPS URL through only the
explicitly listed members and to change the selector without restarting the
VPN. Ordered failover selects the first healthy member and returns to the first
member when it recovers. Round-robin advances the selector after each observed
new connection so the following new connection uses the next healthy member;
existing connections are not interrupted. A simultaneous burst may share the
currently selected member until its connection events are observed.

`System` is never injected into a group: the user must select it as a member.
A manual group requires only its selected member to be available, a latency
group requires two, and failover/round-robin can remain operational in a
degraded one-member state. If no explicitly listed member is healthy, the
selector is not replaced with `System`. Switch and availability reasons are
kept in a bounded in-memory journal shown in Flow Scanner.

sing-box keeps one URL-test history record per outbound. Therefore automatic
groups that share a member must use the same HTTPS test URL; validation rejects
different URLs instead of allowing one group's result to silently influence
another.

## Emergency block

The emergency switch inserts an all-traffic Block rule before normal routing and makes Block the final route. DNS hijacking is omitted in this mode. Profiles remain stored and become active again only after the switch is disabled.

## DNS

DNS is intercepted by the TUN runtime. A rule’s explicit policy wins; otherwise the target profile policy is used. An unavailable DNS detour rejects the matching query. Custom policies may explicitly enable ordered fallback: a valid answer is final, while timeout or transport failure advances to the next configured server. Endpoint hostnames use the Android bootstrap resolver before the selected tunnel exists; normal application queries never use that bootstrap path.

## Configuration changes

Config is written atomically. If the VPN is active, a saved change triggers a
controlled runtime reload. The replacement is loaded, compiled and checked by
the native engine while the current route stays active; a rejected replacement
does not tear down the current generation. A successful change still replaces
Android's single VPN/TUN and can reconnect briefly. No remote profile
connectivity test runs in the background unless the user explicitly creates an
automatic group and accepts its disclosed HTTPS availability checks.
