# ViRouteFS 0.6.3-alpha Release Notes

ViRouteFS 0.6.3-alpha is a public alpha polish release. It updates presentation, documentation, roadmap, support placeholders, and safety boundaries without changing runtime VPN/TUN routing behavior.

## What works

- Android VpnService permission flow.
- Foreground VPN preview service.
- Safe TUN preview.
- Optional TEST-NET route preview for `203.0.113.0/24`.
- Local packet and byte counters for the TEST-NET preview.
- Flow Scanner display of live local test-route counters.
- Dashboard, VPN, Routes, DNS, Tools, Logs, and Settings Compose screens.
- Local routing configuration and DNS policy metadata.
- Manual DNS/TCP/TLS/HTTP diagnostics through the current Android network connection.
- Human-readable logs and diagnostic explanations.

## What does not work yet

- No real user traffic routing yet.
- No full-device default route yet.
- No DNS server is added to the VPN builder.
- No packet payload logging.
- No forwarding or proxying.
- No Xray, OpenVPN, WireGuard, Hysteria2, or SOCKS5 engine yet.
- No Play Store or F-Droid release is claimed yet.
- Route and DNS policy screens still include simulation and configuration metadata for future routing behavior.

## Safety boundaries

ViRouteFS is local-first and defensive. It does not include telemetry, analytics, tracking SDKs, ads, hidden interception, automatic cloud upload, offensive scanning, brute force, credential theft, exploit automation, or packet payload logging.

Guiding phrase:

> Risk detected, exploitation not performed.

See [`../SECURITY_BOUNDARIES.md`](../SECURITY_BOUNDARIES.md) for the full public safety boundary document.

## Manual test checklist

- Install APK.
- Enable VPN preview.
- Enable TUN preview.
- Enable TEST-NET route preview.
- Open `http://203.0.113.1`.
- See counters in VPN screen and Flow Scanner.
- Confirm normal internet still works.
- Disable VPN preview.

## Known limitations

- TEST-NET traffic is counted and dropped; it is not forwarded to an external destination.
- Counter visibility depends on Android sending the test destination through the configured TEST-NET route while the preview is enabled.
- Normal internet should continue to use the device network because there is no full-device default route.
- The app is not a production VPN yet.
- The app is not a packet capture tool yet.
- The app does not include outbound proxy engines yet.
