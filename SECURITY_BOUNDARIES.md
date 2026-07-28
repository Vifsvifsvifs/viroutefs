# ViRouteFS security boundaries

ViRouteFS is a defensive, local-first Android VPN router and network diagnostic
tool. The detailed current runtime boundaries are documented in
[`docs/SECURITY_BOUNDARIES.md`](docs/SECURITY_BOUNDARIES.md).

## Allowed

- user-controlled VPN and proxy routing;
- DNS, TCP, TLS, HTTP, UDP and MTU diagnostics;
- local route, leak and connectivity checks;
- transparent, rate-limited LAN and service discovery;
- Wi-Fi encryption and WPS risk detection;
- local flow metadata and explicit user exports.

## Not allowed

- credential theft or password/PIN cracking;
- deauthentication, evil-twin or hidden interception;
- brute force, exploit automation or stealth scanning;
- analytics, advertising, tracking or hidden telemetry;
- automatic cloud upload of logs, configuration or captures.

## Runtime guarantees

The active Android runtime uses one `VpnService`. Unavailable, disabled, invalid
or failed route targets fail closed to `Block`; they do not silently fall back
to the ordinary network.

ByeDPI is presented as a local DPI-circumvention proxy, not as encryption, a VPN
or anonymity protection.

Diagnostics explain the difference between endpoint reachability and proof that
traffic traversed a selected tunnel. Findings are described as observations,
risks or misconfiguration indicators—not as proof of compromise.

Exports may contain credentials and private keys and must be treated as secrets.
Traffic content is not logged or uploaded automatically.
