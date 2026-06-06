# ViRouteFS 0.9.0-alpha

## Added

- Added a dev-only VLESS TCP bridge for manually validating one test TCP stream from an existing VLESS profile.
- Added Runtime UI controls to open, close, and send test data through the dev TCP session.
- Added local counters for dev-session `bytesIn` and `bytesOut` and a route-preview event line: `Dev session open`.

## Safety notes

- No Android traffic is forwarded in 0.9.0-alpha.
- No UDP forwarding is implemented.
- No DNS forwarding is implemented.
- No REALITY or XTLS support is implemented.
- No telemetry, cloud upload, analytics SDKs, or automatic log/PCAP upload were added.
