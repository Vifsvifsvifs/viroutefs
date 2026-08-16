# ViRouteFS 0.14.0-beta.12

- Fixes network-control activation for routed OpenVPN profiles.
- The isolated engine check no longer mistakes the temporary validator's missing `DEFAULT` rule for a malformed OpenVPN profile.
- Keeps the beta.11 migration that maps imported `.ovpn` routes to the same profile in ViRouteFS' shared router.
