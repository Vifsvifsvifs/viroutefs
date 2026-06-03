# DNS policy metadata

ViRouteFS keeps per-route DNS policy as local configuration metadata. It is not real DNS routing yet.

The app shows this limitation in Russian:

> DNS-политика пока используется для объяснения и проверки риска утечки. Реальное DNS-маршрутизирование будет добавлено позже.

## Fields

A DNS policy contains:

- `id`;
- `name`;
- `type`;
- optional `serverText`;
- optional `resolveThroughProfileId`;
- `description`;
- `enabled`.

The default policy is Android system DNS bound to the built-in System route in the model.

## Default behavior

If a route/profile has no explicit DNS configuration, ViRouteFS shows **Uses Android system DNS / Использует системный DNS Android**. Missing DNS must not be silently replaced with public resolvers such as `1.1.1.1` or `8.8.8.8`.

User-defined DNS applies only where explicitly configured. If user-defined DNS is unavailable, future enforcement should show an error or fail-safe state rather than silently swapping to another resolver.

## Route integration

Rules can reference `dnsPolicyId`. The route decision shows the selected profile, matched rule, priority, DNS policy, mock-profile status, and DNS leak warning.

If a DNS policy expects a different profile from the selected route, ViRouteFS warns the user. Example: a route selected through a future OpenVPN Work profile with a DNS policy that is not actually applied yet warns that future DNS should resolve through the work route.

## Privacy

DNS policies are local metadata. The app does not run background DNS checks, upload DNS settings, or contact policy servers automatically.

## DNS page concept

The DNS tab stays compact and honest:

1. **DNS lookup checker** — domain/address input and record type. Android system DNS is used unless a later feature explicitly implements selected-server querying.
2. **hosts-like local overrides** — local hostname-to-IP metadata using `DnsHostOverride` (`id`, `hostname`, `ipAddress`, `enabled`, optional `note`). Overrides are configuration/simulation only until a DNS engine exists.
3. **DNS per route/profile** — each real route/profile can point to Android system DNS or a user-defined policy when those flows are implemented.
