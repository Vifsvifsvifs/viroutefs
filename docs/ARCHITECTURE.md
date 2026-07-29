# ViRouteFS runtime architecture

## One Android VPN

ViRouteFS owns exactly one Android `VpnService`. sing-box requests the TUN file
descriptor through the ViRouteFS platform callback. Every outbound socket is
protected from the VPN loop by `VpnService.protect()`. No protocol adapter may
start another Android VPN.

## Engine boundary

Every runtime engine implements `EngineAdapter`. The contract includes:

* supported protocols and dependencies;
* capabilities for TCP, UDP, IPv6, DNS detour and multiple instances;
* profile validation and configuration compilation;
* start, stop, restart, health check and cleanup;
* explicit runtime state and structured last error;
* statistics and connection-test entry points;
* secret masking.

The state `Connected` is only set after the adapter confirms readiness. A
spawned process alone is not treated as a connection.

## Orchestration

`EngineOrchestrator` derives the required profiles from the default route,
enabled route rules and DNS detours. It then:

1. selects only required adapters;
2. adds transitive dependencies;
3. validates routed profiles;
4. compiles one plan;
5. starts dependencies before consumers;
6. requires every adapter to confirm `Connected`;
7. stops a partially started generation in reverse order on failure;
8. keeps the caller fail-closed.

The current Android generation contains:

* a local TCP/TLS compatibility adapter;
* a sing-box adapter that owns the shared TUN and all supported outbounds.

strongSwan, legacy PPP, ZeroTier, SoftEther, Tor, Brook and a rootless zapret2
adapter remain separate implementation stages. Their catalog status does not
claim that a runtime exists.

## Configuration and secrets

`routing_config.json` contains the versioned routing structure and stable
`secretRef` values. It does not contain passwords, VLESS access UUIDs or raw
advanced sing-box objects with credentials.

The secret payload is stored in `noBackupFilesDir`:

* AES-256-GCM;
* a non-exportable key generated in Android Keystore;
* one authenticated encrypted envelope;
* records keyed by stable profile id;
* deletion when the corresponding profile disappears;
* removal together with the key when application data is cleared.

The complete advanced sing-box object is encrypted. This intentionally avoids
missing a newly added nested credential field. The visible structure is stored
with known secret keys replaced by `<redacted>`.

Public beta configuration schema 8 and the old plaintext
`socks5_credentials.json` are migrated to schema 9. The encrypted store and
sanitized main file are written before the plaintext legacy file is removed.

## Failure rules

ViRouteFS does not silently replace an unavailable route with `System`.

* An invalid saved configuration stops activation.
* A missing or disabled routed profile is rejected.
* An unavailable adapter is rejected before engines start.
* A failed dependency tears down the new runtime generation.
* An unexpected engine stop ends the VPN runtime and leaves the related
  traffic fail-closed.

Atomic hot reload without stopping the old generation is a later M6 task.

