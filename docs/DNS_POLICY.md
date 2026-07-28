# DNS policy

ViRouteFS applies DNS policies inside the local sing-box TUN runtime.

## Policy fields

A DNS policy contains:

- local id and user-visible name;
- enabled state;
- one upstream server;
- optional `resolveThroughProfileId`;
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

## System DNS

The built-in system policy asks Android’s current physical network resolver through the platform interface. It is not represented by a fake public resolver address.

## Privacy

DNS settings remain local. ViRouteFS does not run background DNS checks, upload DNS configuration or persist query contents. Manual diagnostics run only after an explicit user action.
