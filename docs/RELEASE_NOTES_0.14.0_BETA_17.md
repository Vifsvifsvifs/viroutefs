# ViRouteFS 0.14.0-beta.17

## OpenVPN TCP compatibility update

- Updated the bundled sing-box engine from `v1.14.0-alpha.50` to `v1.14.0-beta.13`.
- Included upstream sing-openvpn fixes for framed TCP control payloads, replay-window handling, reconnects and protocol edge cases.
- Preserved the reproducible arm64, 16 KiB-aligned build with OpenVPN/OpenConnect and without Naive/Cronet.
- Kept all non-root routing features and existing user profiles compatible with an in-place update.

This build is intended for the physical-device retest of the personal TCP OpenVPN profile that connects successfully with OpenVPN 2.5.10 on Windows but stalled in the older embedded Android engine.
