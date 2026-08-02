# ViRouteFS 0.14.0-beta.2

This beta is the next large installable block after `0.14.0-beta.1`. It adds
private QR and subscription import, encrypted full backups, deterministic route
editing, managed route groups, sequential DNS fallback and a more useful Flow
Scanner.

## Import and backups

* Standard WireGuard/wg-quick `.conf` files import without executing shell
  fields such as `PostUp` or `PreDown`.
* CameraX and ZXing scan QR codes locally. Frames are not saved or uploaded and
  decoded profiles still pass through the masked preview.
* Manual HTTPS subscriptions support URI/Base64 lists, sing-box JSON and a
  bounded Clash YAML subset. Every redirect is checked against public-address,
  size and profile-count limits.
* Subscription URLs are encrypted separately with Android Keystore and omitted
  from diagnostic JSON.
* Password-protected `.vrfs` backups preserve the complete local configuration.
  Restore shows a masked preview and performs native preflight before replacing
  the active model.

## Routing and groups

* Domain rules expose exact, suffix, keyword and regular-expression modes.
* Visible route order can be changed with Up/Down actions and is compiled
  deterministically.
* Groups support manual selection, native HTTPS latency selection, ordered
  failover and new-connection round-robin.
* Failover returns to a recovered primary. `System` is never injected as a
  hidden group member and an all-unavailable group remains fail-closed.
* Group member order, health URL and interval are editable. Selection reasons
  are kept in a bounded memory-only journal.

## DNS

* Custom policies can enable sequential primary/backup resolution and choose a
  timeout from 1 to 30 seconds for each server.
* A valid answer, including `NXDOMAIN`, is returned immediately. Only a timeout
  or transport error advances the same query to the next configured server.
* Existing configurations remain primary-only until fallback is enabled.
* DNS rules from different policies now follow the global route priority, and
  local hosts entries correctly win over the default DNS policy.
* A dedicated Android bootstrap resolver resolves tunnel and encrypted-DNS
  endpoint hostnames before those routes exist, avoiding circular startup.
* The scanner records a generic fallback reason without retaining the queried
  hostname or DNS response.

## Flow Scanner

* Any installed application can be selected from a searchable list with its
  local icon.
* Filters cover application, text, protocol, active/closed state,
  allowed/blocked result, IP version and start time.
* Completed flows show finish time and duration. CSV export includes only the
  visible metadata and uses Android's system save dialog.
* Actual sing-box outbounds are compared with the local rule calculation using
  application, domain/IP, port and TCP/UDP constraints.

## Verification boundary

The complete local test, lint, APK and GitHub CI results are attached to the
release process. The generated DNS fallback configuration is additionally
accepted by the exact pinned sing-box `1.14.0-alpha.50` checker.

This is still a beta. QR camera behavior, provider subscriptions, external VPN
profiles, DNS detours/fallback, group switching, per-app attribution, sleep,
network changes and emergency shutdown still require the physical arm64 device
and server matrix. No external VPN protocol is promoted to `DeviceVerified` by
this release alone.
