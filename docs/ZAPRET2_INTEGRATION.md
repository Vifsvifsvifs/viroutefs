# zapret2 root integration

This is an engineering and license record, not legal advice. The user-facing
feature is named **Connection adaptation (root)**. The upstream name remains
visible in technical details, source records, hashes and licenses.

## Pinned snapshot

- Upstream: https://github.com/bol-van/zapret2
- Release: `v1.0.4`
- Commit/tag target: `2c21faa80e1acb71ddceb8b49176f266b7d33f05`
- Upstream release archive SHA-256: `5760b6d41c09459fff00b4a6fec5437a471a00aac15f734723ede149cd26c709`
- Android arm64 `nfqws2` SHA-256: `2e1a0e950e0bc7189b5662e54fdd66d749d51215b167a647f15659554e7b4090`
- License: MIT, copyright 2016-2026 bol-van

`tools/fetch-zapret2.ps1` downloads the exact upstream release, verifies the
archive and every selected file, and installs the binary, standard Lua files
and MIT notice into the Android source tree. Gradle verifies the native binary
again before every build; the app verifies the binary and Lua hashes before a
root start.

## Product boundary

zapret2 is a packet-analysis and packet-transformation engine. Its `nfqws2`
program receives selected Linux NFQUEUE packets and runs Lua strategies. It is
not a VPN, does not provide a remote tunnel or encryption, and does not hide the
device IP address.

The ordinary ViRouteFS router remains a rootless Android `VpnService`. ByeDPI
remains available as the separate **TCP/TLS compatibility** route. The new
zapret2 path is not a tunnel profile and is never required for base VPN, route,
DNS or Flow Scanner operation.

## Root activation model

The root module is off by default and does not invoke `su` during application
startup or when the root centre is merely opened. The user must acknowledge the
risk and press the start button. ViRouteFS then:

1. requests root through the installed KernelSU, Magisk or APatch manager;
2. performs a read-only capability probe;
3. requires IPv4 and IPv6 iptables plus NFQUEUE `--queue-bypass` support;
4. verifies all bundled artifacts by SHA-256;
5. writes recovery state before changing the network stack;
6. starts only its pinned app-private `nfqws2` process;
7. creates only the `VIROUTEFS_Z2_OUT` chains and attaches them to `OUTPUT`;
8. rolls the process and both chains back when any step fails.

The queue uses `--queue-bypass`, so packets are accepted if the userspace
listener disappears. Generated packets are marked to prevent recapture. Manual
stop removes only this module. The root centre also exposes an emergency cleanup
that removes all ViRouteFS-owned root chains and processes without flushing the
device firewall or deleting unrelated rules.

The first strategy set is deliberately bounded to outgoing web traffic on TCP
80/443 and QUIC UDP 443. Arbitrary user shell text and arbitrary strategy
arguments are not accepted.

## Verification status

The source, artifact hashes, script generation, rollback namespace and Android
build are covered by local tests. The root runtime is **not device verified**
until the physical KernelSU Next phone is reconnected and the following matrix
passes: grant/deny, IPv4, IPv6, QUIC, network changes, process crash, manual stop,
emergency cleanup, coexistence with Android VpnService and reboot recovery.

No persistent boot module or automatic root start is installed before that
physical recovery test succeeds.
