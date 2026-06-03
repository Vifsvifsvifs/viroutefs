# ViRouteFS VPN service skeleton — 0.5.0-alpha

ViRouteFS `0.5.0-alpha` adds the safe Android `VpnService` foundation needed before real routing engines are connected.

## What is included

- `ViRouteVpnService`, an Android `VpnService` subclass for the local service lifecycle.
- Manifest registration with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` intent action.
- Android VPN permission flow from the VPN screen via `VpnService.prepare(context)`.
- A foreground notification and Android O+ notification channel for the local VPN service preview.
- UI states for permission required, starting, active, stopped, and error.

## Safety limits

This milestone intentionally does **not** create a TUN interface and does **not** call `Builder.establish()`.

There is no packet capture, packet inspection, routing, Xray, OpenVPN, WireGuard, Hysteria2, SOCKS5, telemetry, analytics, tracking, ads, cloud upload, or hidden interception in this release.

The service is only a permission and foreground-service lifecycle preview, so enabling it should not redirect all device traffic or break normal internet connectivity.

## Manual test notes

- Installing the debug APK should work.
- Pressing the VPN master switch should show the Android VPN permission dialog if permission has not already been granted.
- After permission is granted, the local foreground service should start and show a notification.
- The service should not route or capture traffic.
- Turning the switch off should stop the local service.
