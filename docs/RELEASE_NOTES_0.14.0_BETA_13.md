# ViRouteFS 0.14.0-beta.13

- Repairs the invalid automatic VPNGate profiles created by beta.11/beta.12 and safely restores System as the default route during migration.
- Represents automatic VPNGate as one managed item; its internal OpenVPN servers can be enabled, disabled, or removed together without cluttering the VPN list.
- Refreshes the official VPNGate catalog on every activation, excludes the device country, prepares up to six low-latency candidates, and keeps HTTPS-tested automatic failover.
- Adds the guided “Настрой всё за меня” flow: select affected apps, configure VPNGate in the background, keep all other apps on System, and start network control.
- Makes Android Back return through nested VPN, route, DNS, and scanner screens instead of minimizing the app.
