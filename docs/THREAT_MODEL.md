# ViRouteFS threat model

ViRouteFS is a local network router. It does not provide public VPN servers,
decrypt HTTPS, upload traffic or run offensive security checks.

| Threat | Primary control | Remaining validation |
|---|---|---|
| Traffic leak | one TUN, no implicit `System` fallback, failed routes map to Block | IPv4/IPv6 device matrix and engine-crash tests |
| Group failover leak | automatic groups contain only explicit members; selector changes use the app-local command socket; no-member state never invents `System` | physical failover, simultaneous round-robin and network-change matrix |
| DNS leak | DNS rules compile into the same runtime; unavailable detours reject; ordered fallback never substitutes System; endpoint hostname bootstrap is explicit | physical routed leak and fallback tests remain |
| VPN loop | sing-box platform protection plus app-UID exclusion for local child processes | Xray/ByeDPI network-change and IPv6-only tests |
| Secret leak at rest | AES-256-GCM, Android Keystore, `noBackupFilesDir`, redacted main config | Android instrumentation test across upgrade |
| Secret leak in logs | structured user-safe errors and credential-key redaction | fuzz imported errors and native logs |
| Export compromise | default JSON export removes secrets | password-encrypted full backup remains M1 |
| Malicious subscription | manual HTTPS only; public-address preflight on every redirect; 2 MiB/512-profile bounds; duplicate-key/alias-limited YAML; masked preview; new profiles disabled; stable route-preserving merge | physical provider tests, DNS-rebinding hardening and optional background-consent design remain |
| Native binary replacement | pinned version and SHA-256 checks before every build | SBOM and reproducible native builds remain |
| MITM | native TLS/certificate verification is retained | certificate-pin editors and negative server tests |
| Weak legacy protocol | unavailable until an audited adapter exists; warning required | per-protocol restrictions and physical legacy lab |
| Update replacement | Android package signature, published SHA-256, manual installer handoff | signed release manifest remains |
| Root abuse | no root runtime in the current build | explicit command preview and confirmation before M8 |

## Trust boundaries

* Android Keystore protects the local encryption key.
* The app-private files directory protects routing structure.
* `noBackupFilesDir` protects encrypted secret payloads from Android Backup.
* sing-box, Xray-core and the TCP/TLS compatibility executable are pinned native
  dependencies and are not downloaded at runtime.
* Imported profiles, files, QR codes and subscriptions are untrusted
  input and must be validated before replacing active configuration.

## Security response

Report vulnerabilities through the repository security policy. Diagnostic
exports must be explicitly initiated by the user and must not contain packet
payloads, access UUIDs, passwords, private keys, PSK, cookies or subscription
URLs.

