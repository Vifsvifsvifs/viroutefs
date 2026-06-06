# ViRouteFS 0.8.12-alpha Release Notes

ViRouteFS `0.8.12-alpha` prepares TCP bridge architecture for the first future VLESS TCP bridge.

## Included

- Local TCP session model: session id, endpoint metadata, lifecycle state, timestamps, and byte counters.
- Local TCP session manager operations for create, lookup, counter updates, close, idle cleanup, snapshots, and state statistics.
- TCP bridge and bridge factory interfaces only.
- Route preview observation text for TCP packet metadata: `Would create TCP session`.
- VPN Runtime UI preparation showing active session count, session state statistics, and the empty state `No TCP sessions`.

## Not included

This release does **not** implement runtime forwarding. It does **not** proxy Android traffic, write packets back to TUN, implement REALITY, implement UDP, add DNS proxying, add telemetry, or upload logs/PCAPs/cloud data.

All preparation remains local-first and observation-only.
