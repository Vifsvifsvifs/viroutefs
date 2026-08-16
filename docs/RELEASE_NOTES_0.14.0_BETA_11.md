# ViRouteFS 0.14.0-beta.11

- OpenVPN `route` directives imported from `.ovpn` now also become shared-router network rules, so the OpenVPN endpoint starts for those CIDRs without becoming the default VPN.
- Profiles already imported by beta.10 are migrated automatically; credentials and certificates do not need to be selected again.
- The OpenVPN endpoint keeps its own normalized route list, while ViRouteFS shows and applies the same networks in the profile routing settings.
