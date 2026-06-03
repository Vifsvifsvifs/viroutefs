# ViRouteFS safe TUN skeleton — 0.6.5-alpha

ViRouteFS `0.6.5-alpha` keeps the route-less TUN preview as the default, keeps the opt-in TEST-NET route preview, and links its safe counters into Flow Scanner.

## Default route-less preview

By default, the VPN preview creates only a minimal Android TUN interface:

- TUN address: `10.250.0.2/32`.
- No traffic routes.
- No `0.0.0.0/0` default route.
- No IPv6 default route.
- No DNS servers added to the VPN builder.
- No packet reading, forwarding, proxying, payload logging, or real VPN engines.

Normal internet traffic should remain unchanged in the default mode because there are no routes into the TUN interface.

## Opt-in test-route preview

The normal Networks screen does not expose TEST-NET controls. The safe route remains available only under **Settings → Developer diagnostics** for explicit internal/developer testing. In `0.6.5-alpha`, Flow Scanner may show local counters only after that explicit developer diagnostic path is enabled.

When enabled, ViRouteFS adds exactly one IPv4 route to the TUN builder:

- `203.0.113.0/24`

`203.0.113.0/24` is TEST-NET-3 documentation address space. This route is only for safe lifecycle testing. ViRouteFS does not add any other routes, does not add runtime default-route enforcement, and does not add DNS servers.

## Packet read/drop behavior

In test-route preview mode, a single background packet loop reads from the TUN file descriptor and immediately drops what it reads.

The loop keeps only safe counters:

- `packetsRead`
- `bytesRead`
- `lastPacketAt`

It does not log packet payload bytes, does not inspect payloads, does not parse or extract domains from packets, does not log destinations, does not forward packets, does not proxy packets, and does not capture traffic to a file.

If the TUN read loop fails unexpectedly, the service publishes an `Error` state with a short safe message and stops the preview.

## Flow Scanner visibility in 0.6.5-alpha

Flow Scanner can show current local TEST-NET counters when developer diagnostics enabled the preview or when counters are non-zero:

- Source: `Developer TEST-NET counter`.
- Route: `203.0.113.0/24`.
- Packets and bytes read.
- Last packet time or `never`.
- Active/inactive status.

This is still not full packet capture or real app traffic analysis. The preview does not add a full-device default route, IPv6 default route, DNS servers, forwarding, proxying, payload logging, payload inspection, or domain extraction. Packets routed into the TEST-NET preview are dropped after counting. Counters are app-local runtime state and are not uploaded.

## Lifecycle guarantees

- Starting/stopping is idempotent.
- Switching test-route preview on or off while the preview is active restarts the preview with the requested route mode.
- There is no duplicate packet loop.
- Stopping, service destruction, or VPN revoke closes the TUN descriptor and stops the packet loop.
- Counters reset when the TUN preview is recreated. After stopping, the last runtime counters can remain visible locally in Flow Scanner as inactive test data until the service is started again or the app process is recreated.

## Manual test notes

1. Install the debug APK.
2. Enable Networks control in normal route-less mode.
3. Confirm normal internet still works.
4. Open Settings → Developer diagnostics and explicitly enable test-route preview.
5. Confirm the UI shows test route `203.0.113.0/24` active.
6. Open `http://203.0.113.1` in a browser or try connecting to `203.0.113.1`.
7. Confirm packet counters increase.
8. Confirm normal internet still works.
9. Disable VPN preview.
10. Confirm TUN closes and counters stop.
