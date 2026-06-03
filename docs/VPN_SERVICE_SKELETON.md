# ViRouteFS VPN service skeleton — 0.5.1-alpha

ViRouteFS `0.5.1-alpha` stabilizes the safe Android `VpnService` foundation before any real routing engines are connected.

## What is included

- `ViRouteVpnService`, an Android `VpnService` subclass for the local service lifecycle.
- Manifest registration with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` intent action.
- Android VPN permission flow from the VPN screen via `VpnService.prepare(context)`.
- Android 13+ `POST_NOTIFICATIONS` handling before the foreground VPN preview service is started.
- A foreground notification and Android O+ notification channel for the local VPN service preview.
- UI states for off, VPN permission required, notification permission required, starting, active, stopped, and error.

## VPN permission behavior

The VPN screen keeps Android's standard `VpnService.prepare(context)` flow:

- If VPN permission is already granted, ViRouteFS can continue directly to the notification-permission check and service start.
- If VPN permission is needed, the Android VPN permission dialog is opened.
- If the user cancels the VPN permission dialog, the UI returns to a non-active state and explains that Android VPN permission was not granted.
- The master switch reflects the actual preview-service state, so it should not remain visually stuck on after a canceled permission flow.

## Android 13+ notification permission behavior

On Android 13 and newer, ViRouteFS checks `POST_NOTIFICATIONS` before starting the foreground VPN preview service:

- If the permission is already granted, the foreground service can start.
- If the permission is missing, ViRouteFS requests it before starting the service.
- If the permission is denied, the VPN screen shows: `Notification permission required to start local VPN preview service`.
- The app does not crash when notification permission is denied.

## Foreground service lifecycle

The local VPN preview service is defensive and idempotent:

- Starting when already active keeps the state active and does not create packet routing.
- Stopping when already stopped keeps the state stopped and does not crash.
- Service state is published to the UI as `Off`, `Permission required`, `Notification permission required`, `Starting`, `Active`, `Stopped`, or `Error`.
- Foreground notification setup is wrapped defensively; if service startup fails, the UI receives an error state.

The foreground notification is intentionally clear:

- Title: `ViRouteFS local VPN preview`
- Text: `No traffic routing or packet capture yet`

## Safety limits

This milestone intentionally does **not** create a TUN interface and does **not** call `Builder.establish()`.

There is no packet capture, packet inspection, routing, Xray, OpenVPN, WireGuard, Hysteria2, SOCKS5, telemetry, analytics, tracking, ads, cloud upload, or hidden interception in this release.

The service is only a permission and foreground-service lifecycle preview, so enabling it should not redirect all device traffic or break normal internet connectivity.

## Manual test notes

- Installing the debug APK should work.
- Pressing the VPN master switch should show the Android VPN permission dialog if permission has not already been granted.
- On Android 13+, the app should request notification permission before starting the foreground preview service when that permission is required.
- After permissions are granted, the local foreground service should start and show the preview notification.
- The service should not route or capture traffic and should not create a TUN interface.
- Turning the switch off should stop the local service.
- Repeating start or stop actions should be safe.
