# ViRouteFS 0.13.0-beta.1

This is the first large completion release based on the ViRouteFS 1.0
specification. It implements the M0 runtime and security foundation. It is
still a beta and is not a stable or physically verified release.

## Added

* The complete seven-state feature readiness model:
  `ModelOnly`, `ConfigSupported`, `RuntimeIntegrated`, `DeviceVerified`,
  `ProductionReady`, `Unavailable`, and `LegacyRestricted`.
* A common `EngineAdapter` contract with capabilities, validation, compilation,
  lifecycle, state, health, statistics, connection-test and secret-masking
  boundaries.
* A live `EngineOrchestrator` used by the Android VPN service.
* Dependency ordering, partial-start rollback, reverse cleanup and explicit
  `Connected` confirmation.
* AES-256-GCM profile-secret storage with a non-exportable Android Keystore key.
* Stable `secretRef` entries in routing schema 9.
* Encryption of SOCKS5 passwords, VLESS access UUIDs and complete advanced
  sing-box profile objects.
* Migration of schema 8 and the old plaintext SOCKS5 credential file.
* Structured engine error stages with a user-safe summary and recommended
  action.
* Architecture, specification status, threat model and bilingual privacy
  documents.
* Unit tests for engine ordering/rollback, encrypted round-trip, tamper
  detection, nested redaction and plaintext migration.

## Changed

* Protocols are no longer labelled “works” solely because a generic runtime
  exists.
* No protocol claims `DeviceVerified` or `ProductionReady` before physical
  evidence is recorded.
* OpenVPN, OpenConnect and other bundled protocols are labelled
  `RuntimeIntegrated`.
* zapret2 and legacy protocols are labelled unavailable until a real compatible
  adapter exists.
* Only profiles used by active routes or DNS detours are included in the new
  orchestration plan.
* A damaged saved configuration now stops VPN activation instead of silently
  starting with the default `System` configuration.

## Migration

The first successful load encrypts legacy secrets, writes a sanitized schema 9
configuration, and only then removes the old plaintext credential file.
Profile and rule ids are preserved.

## Known limitations

* No physical Android device was available during this build.
* OpenVPN, VLESS, WireGuard, DNS detours, Flow Scanner attribution, network
  change and sleep recovery still require end-to-end device tests.
* Encrypted full backup, QR/share/subscriptions, profile groups, DNS failover,
  atomic hot reload, strongSwan, legacy engines, external adapters, routed
  diagnostics, audit/root tools and PCAP export remain in later milestones.
* arm64-v8a is the only ABI.
* Minimum Android version is 8.0/API 26; per-app routing requires Android
  10/API 29 or newer.

