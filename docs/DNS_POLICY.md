# DNS policy

ViRouteFS applies DNS policies inside the local sing-box TUN runtime.

## Policy fields

A DNS policy contains:

- local id and user-visible name;
- enabled state;
- an ordered list of upstream servers;
- optional `resolveThroughProfileId`;
- optional sequential fallback and a per-server timeout from 1 to 30 seconds;
- local description.

Accepted server forms are a plain IP address or `udp://`, `tcp://`, `tls://`, `quic://`, `https://` and `h3://` URLs.

## Selection

A route may choose a DNS policy directly. Otherwise the target profile’s DNS policy is used. A profile and its DNS server may use different outbound profiles intentionally; this supports cases such as an app routed through one tunnel while DNS goes through another.

Domain and Android package rules are compiled into sing-box DNS rules. Package matching requires Android 10 or newer. CIDR rules do not create a domain-name DNS matcher.

## Fail-closed behavior

- A custom DNS policy whose selected profile is missing or disabled rejects matching queries.
- DNS assigned to `Block` rejects matching queries.
- ViRouteFS does not silently substitute `1.1.1.1`, `8.8.8.8` or Android system DNS.
- A disabled or invalid policy is not presented as an active custom DNS path.

When fallback is enabled, ViRouteFS evaluates servers in the configured order.
A valid DNS response, including `NXDOMAIN`, is returned immediately. A transport
error or timeout advances the same query to the next server. The last server is
the final attempt. Existing configurations keep primary-only behavior until the
user enables fallback explicitly.

The pinned sing-box runtime does not provide parallel or fastest-response DNS
selection, so ViRouteFS does not expose fake parallel/fastest switches. Runtime
fallback reasons are detected from the local engine error stream, stripped of
the queried hostname and kept only in the bounded in-memory scanner journal.

## Endpoint bootstrap

DNS and tunnel servers may use hostnames. Those names must be resolved before
the selected tunnel or encrypted DNS transport exists. A dedicated local
bootstrap resolver therefore uses Android's current underlying network only for
endpoint hostnames. Normal application DNS still follows the selected policy
and detour. Users who require no endpoint-hostname lookup can configure an IP
literal for the server.

## System DNS

The built-in system policy asks Android’s current physical network resolver through the platform interface. It is not represented by a fake public resolver address.

## Privacy

DNS settings remain local. ViRouteFS does not run background DNS checks, upload DNS configuration or persist query contents. Fallback events intentionally discard the queried hostname and response. Manual diagnostics run only after an explicit user action.
