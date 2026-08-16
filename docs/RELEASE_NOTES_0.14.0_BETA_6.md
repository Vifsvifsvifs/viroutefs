# ViRouteFS 0.14.0-beta.6

## Optional root centre

* Added a separate root centre that detects KernelSU Next, KernelSU, Magisk and
  APatch, but never invokes `su` merely because the app or screen is opened.
* Root remains optional. The normal Android VpnService, profiles, routes, DNS,
  VPNGate and Flow Scanner continue to work without it.
* A root request is made only after an explicit user action. The initial probe
  is read-only and reports iptables, IPv6, NFQUEUE queue-bypass, nftables,
  tcpdump, traffic control, conntrack and kernel WireGuard availability.

## Connection adaptation root module

* Added the disabled-by-default **Адаптация соединений (root)** module based on
  pinned upstream zapret2/nfqws2 v1.0.4. ByeDPI remains available separately.
* The module is not presented as a VPN profile. It is explicitly described as
  packet adaptation without a remote tunnel, encryption or IP hiding.
* The official Android arm64 binary, standard Lua files and MIT notice are
  pinned by archive and per-file SHA-256. Gradle and the runtime both verify the
  selected artifacts before use.
* Startup requires IPv4 and IPv6 iptables plus NFQUEUE `--queue-bypass`. It
  queues only outgoing TCP 80/443 and QUIC UDP 443 in the namespaced
  `VIROUTEFS_Z2_OUT` chains.
* Recovery state is persisted before mutation. A failed start automatically
  kills only the verified app-private process and removes only its two chains.
  Manual module stop leaves future root modules untouched.
* The root centre also provides an emergency cleanup for all ViRouteFS-owned
  root chains and processes. It never flushes the global iptables or nftables
  ruleset.

## Verification boundary

* Unit tests, Kotlin compilation, lint, APK assembly, native hashes and APK
  contents are checked in CI.
* The root runtime is intentionally marked experimental and not device-verified
  until the physical KernelSU Next phone is reconnected and the grant, deny,
  IPv4, IPv6, QUIC, network-change, process-crash and recovery matrix passes.
* No persistent boot module or automatic root start is installed in this beta.

## Included beta.5 functionality

* VPNGate manual selection and automatic foreign low-latency group with live
  HTTPS URL tests and failover.
* Per-profile app/CIDR routing, hosts entries, faster Routes page and selected
  applications pinned to the top.
* Clickable voluntary-support link and working local QR code.
