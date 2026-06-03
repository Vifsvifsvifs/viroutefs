# ViRouteFS route-less TUN skeleton — 0.6.0-alpha

ViRouteFS `0.6.0-alpha` adds the first safe Android `VpnService` TUN preview. The goal is only to prove that Android can establish and close a VPN interface from the app without routing user traffic yet.

## What is included

- `ViRouteVpnService`, an Android `VpnService` subclass for the local service lifecycle.
- Manifest registration with `android.permission.BIND_VPN_SERVICE` and the `android.net.VpnService` intent action.
- Android VPN permission flow from the VPN screen via `VpnService.prepare(context)`.
- Android 13+ `POST_NOTIFICATIONS` handling before the foreground VPN preview service is started.
- A foreground notification and Android O+ notification channel for the local VPN preview.
- A route-less TUN preview session named `ViRouteFS TUN preview`.
- One private IPv4 address on the preview interface: `10.250.0.2/32`.
- A safe MTU value of `1500`.
- UI states for `Off`, `PermissionRequired`, `NotificationPermissionRequired`, `Starting`, `ServiceActiveNoTun`, `TunPreviewActive`, `Stopped`, and `Error`.

## Route-less TUN behavior

The 0.6.0-alpha TUN preview intentionally creates only the minimum interface needed to validate the `VpnService.Builder.establish()` lifecycle:

- It does **not** add routes.
- It does **not** add `0.0.0.0/0`.
- It does **not** add an IPv6 default route.
- It does **not** add DNS servers.
- It does **not** read packets from the TUN descriptor.
- It does **not** inspect, parse, log, capture, forward, or proxy packets.
- It does **not** start Xray, OpenVPN, WireGuard, Hysteria2, SOCKS5, or any other VPN/proxy engine.

Because no traffic routes are installed, normal device internet connectivity should remain unchanged while the preview is active.

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

- Starting when the route-less TUN is already active keeps the state active and does not create duplicate TUN handles.
- Stopping when already stopped keeps the state stopped and does not crash.
- Stop closes the stored `ParcelFileDescriptor` when present.
- Service destroy and VPN revoke also close the descriptor.
- If `Builder.establish()` returns `null`, the UI receives `Error` with a clear message.
- If `Builder.establish()` throws, the exception is caught, the UI receives `Error`, and the app does not crash.

The foreground notification is intentionally clear:

- Title: `ViRouteFS local VPN preview`
- Text when TUN is active: `TUN preview active — no traffic routes installed`
- Text when only the foreground service is active: `Local VPN preview — no traffic routing yet`

## Safety limits

There is no packet capture, packet inspection, routing, Xray, OpenVPN, WireGuard, Hysteria2, SOCKS5, telemetry, analytics, tracking, ads, cloud upload, or hidden interception in this release.

Logs and future PCAP exports remain local-first and are not uploaded automatically.

## Next milestone

The next VPN milestone is controlled Direct/Block route experiments. Those experiments should stay narrow, explicit, and reversible; they should not introduce full-device default routing by default.

## Manual test notes

- Installing the debug APK should work.
- Pressing the VPN master switch should show the Android VPN permission dialog if permission has not already been granted.
- On Android 13+, the app should request notification permission before starting the foreground preview service when that permission is required.
- After permissions are granted, the VPN preview should start.
- The TUN preview state should become active.
- Browser and normal internet access should still work because no routes are installed.
- Turning the switch off should close the TUN descriptor and stop the service cleanly.
- Repeating start or stop actions should be safe.
