# ViRouteFS threat model

ViRouteFS is a local network router. It does not provide public VPN servers,
decrypt HTTPS, upload traffic or run offensive security checks.

| Threat | Primary control | Remaining validation |
|---|---|---|
| Traffic leak | one TUN, no implicit `System` fallback, failed routes map to Block | IPv4/IPv6 device matrix and engine-crash tests |
| DNS leak | DNS rules compile into the same runtime and unavailable detours reject | multi-server failover and routed leak tests |
| VPN loop | every libbox socket uses `VpnService.protect()` and the app package is excluded | network-change and IPv6-only tests |
| Secret leak at rest | AES-256-GCM, Android Keystore, `noBackupFilesDir`, redacted main config | Android instrumentation test across upgrade |
| Secret leak in logs | structured user-safe errors and credential-key redaction | fuzz imported errors and native logs |
| Export compromise | default JSON export removes secrets | password-encrypted full backup remains M1 |
| Malicious subscription | no automatic subscriptions in current build | preview, allow-listing and merge rules remain M1 |
| Native binary replacement | pinned version and SHA-256 checks before every build | SBOM and reproducible native builds remain |
| MITM | native TLS/certificate verification is retained | certificate-pin editors and negative server tests |
| Weak legacy protocol | unavailable until an audited adapter exists; warning required | per-protocol restrictions and physical legacy lab |
| Update replacement | Android package signature, published SHA-256, manual installer handoff | signed release manifest remains |
| Root abuse | no root runtime in the current build | explicit command preview and confirmation before M8 |

## Trust boundaries

* Android Keystore protects the local encryption key.
* The app-private files directory protects routing structure.
* `noBackupFilesDir` protects encrypted secret payloads from Android Backup.
* sing-box and the TCP/TLS compatibility executable are pinned native
  dependencies and are not downloaded at runtime.
* Imported profiles, files, QR codes and future subscriptions are untrusted
  input and must be validated before replacing active configuration.

## Security response

Report vulnerabilities through the repository security policy. Diagnostic
exports must be explicitly initiated by the user and must not contain packet
payloads, access UUIDs, passwords, private keys, PSK, cookies or subscription
URLs.

