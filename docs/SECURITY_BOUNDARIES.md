# Security boundaries

## Runtime

ViRouteFS uses one Android `VpnService` and one embedded sing-box runtime. The
service owns the TUN interface and applies app, domain, IP, CIDR, default-route
and DNS decisions from one configuration snapshot.

The built-in `Block` profile is fail-closed. A rule that refers to a disabled,
unsupported or failed profile is compiled to `Block`; it is never silently sent
through `Direct`.

The user-facing TCP/TLS compatibility mode is implemented by ByeDPI running as a local SOCKS proxy owned by the ViRouteFS application process.
It is not a VPN, does not encrypt traffic and does not provide anonymity. If its
process cannot start or exits unexpectedly, routes assigned to it are blocked.

## Secrets

- Configuration is stored in the application's private storage.
- Exported configuration can contain VPN credentials and private keys. The user
  must treat an export as a secret.
- ViRouteFS does not upload configuration, diagnostic results or traffic
  metadata.
- The flow scanner shows locally observed metadata only. It does not save packet
  contents and does not decrypt TLS.

## Network checks

DNS, TCP, TLS and HTTP diagnostics started from the application UI use the
application's own sockets, which are excluded from its TUN to prevent loops.
These checks verify reachability from the device, not passage through a selected
VPN profile. Per-route status is therefore reported separately from endpoint
reachability.

## Native components

Native components are pinned by source commit and SHA-256 and are checked before
every Android build. Reproduction scripts are in `tools/`.

Only the `arm64-v8a` ABI is packaged at present. A build succeeding on a
workstation is not evidence that the application has been installed or tested on
a physical Android device.

## Signing

Debug builds use the standard Android debug key. Public releases must use a new
long-lived owner-controlled keystore configured through environment variables;
the keystore and passwords must not be committed to the repository.
