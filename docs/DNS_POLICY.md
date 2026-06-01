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
