# ViRouteFS 0.13.0-beta.2

This hotfix restores the primary ViRouteFS scenario: network control can start
with the normal Android `System` connection and no configured VPN profile.

## Fixed

* Built-in `System` and `Block` routing targets are no longer validated as
  external VPN connection profiles.
* Saved built-in profiles from older beta versions are canonicalized during
  migration. Stale protocol payloads cannot remain attached to `System` or
  `Block` and prevent activation.
* The default route still fails closed when a user-selected external profile is
  missing, disabled or invalid.

## Physical device evidence

The signed update was installed over `0.13.0-beta.1` on an arm64 Android device
without clearing app data.

* Android accepted the existing signing certificate.
* Network control started with zero user VPN profiles.
* A full IPv4/IPv6 TUN interface was established.
* Android reported the ViRouteFS VPN network as `VALIDATED`.
* IPv4 reachability and DNS name resolution succeeded through the active
  router.
* Turning network control off removed the service and VPN network cleanly.

This evidence raises only the built-in `System` route to `DeviceVerified`.
External VPN protocols remain `RuntimeIntegrated` until their own server and
device tests are completed.

