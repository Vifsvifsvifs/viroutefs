# ViRouteFS 0.14.0-beta.19

## Focused OpenVPN session diagnostics

- Keeps only native OpenVPN session start/end/failure lines in the bounded in-memory profile diagnostic.
- Drops route pre-match, connectivity probe and `endpoint is not ready` noise that previously displaced the actual failure stage.
- Includes the reproducible patch-file cleanup for the diagnostic sing-openvpn build.

No credentials, destination history or persistent connection log is added.
