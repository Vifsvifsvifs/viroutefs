# DNS policy metadata

ViRouteFS `0.4.0-alpha` adds per-route DNS policy as configuration metadata. It is not real DNS routing yet.

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

Default policies are System DNS, Direct DNS, Work DNS mock, and Tunnel DNS mock.

## Route integration

Rules can reference `dnsPolicyId`. The route decision shows the selected profile, matched rule, priority, DNS policy, mock-profile status, and DNS leak warning.

If a DNS policy expects a different profile from the selected route, ViRouteFS warns the user. Example: a route selected through OpenVPN Work with a DNS policy that is not actually applied yet warns that future DNS should resolve through the work route.

## Privacy

DNS policies are local metadata. The app does not run background DNS checks, upload DNS settings, or contact policy servers automatically.

## 0.4.1-alpha DNS page concept

The DNS tab has four compact blocks:

1. **DNS lookup checker** — domain/address input, DNS server text, optional profile context and record type. Android may still use the system resolver in this version; direct selected-server DNS queries will be improved later.
2. **Проверить приложение** — text-based app selector concept that previews matched route/profile and DNS policy. It does not query installed packages and does not capture app traffic.
3. **hosts-like local overrides** — local hostname-to-IP metadata using `DnsHostOverride` (`id`, `hostname`, `ipAddress`, `enabled`, optional `note`). Overrides are configuration/simulation only until a DNS engine exists.
4. **DNS per connection** — each connection profile can point to System DNS, Direct DNS, Work DNS mock, Tunnel DNS mock or a custom DNS server text policy.

DNS per connection and hosts-like overrides are local-first configuration/simulation features in 0.4.1-alpha.
