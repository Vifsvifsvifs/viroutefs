# zapret2 integration decision

This record describes an engineering and license audit. It is not legal advice and does not claim that zapret2 is currently bundled or runtime-ready in ViRouteFS.

## Audited snapshot

- Upstream: https://github.com/bol-van/zapret2
- Release: `v1.0.4`
- Commit: `2c21faa80e1acb71ddceb8b49176f266b7d33f05`
- Published: 2026-07-31
- License file: `docs/LICENSE.txt`
- License: MIT, copyright 2016-2026 bol-van

The earlier https://github.com/bol-van/zapret repository declares zapret1 end-of-life and directs new development to zapret2.

## What zapret2 is

zapret2 is a packet-analysis and packet-transformation engine. Its `nfqws2` program receives selected packets from Linux NFQUEUE, classifies protocols and payloads, and runs Lua strategies that can pass, modify, drop, split or supplement packets.

It is not a VPN and does not provide a remote tunnel or encryption by itself.

## Android boundary

The upstream project can build an Android executable, but the normal traffic path still expects:

- NFQUEUE rules supplied by iptables or nftables;
- network administration and raw-packet capabilities;
- root-level control of the device network stack.

A normal Play-style Android application does not have those privileges. ViRouteFS also already owns the device VPN slot through one `VpnService`, so launching another VPN-based interceptor is not an option.

The physical Android 16 phone used for the 0.14.0 beta checks has KernelSU Next installed. The current ViRouteFS build has not requested or received `su` access yet, so a future root-only nfqws2 path must remain a separately enabled module and must not become a dependency of the base VPN router.

For these reasons, simply copying the upstream Android binary into the APK would create a non-working control and an unjustified security surface.

## Acceptable ViRouteFS integration path

A future rootless adapter must:

1. remain inside the existing ViRouteFS `VpnService`;
2. expose only packet or stream transformations that can be represented safely in the current userspace network stack;
3. avoid root, iptables, nftables and NFQUEUE requirements;
4. preserve per-app, domain, IP, CIDR and DNS route selection;
5. fail closed when the selected strategy cannot run;
6. include the upstream MIT notice, exact source revision, build script and artifact hash;
7. undergo physical-device tests for TCP, UDP/QUIC, IPv4, IPv6, battery use and network changes.

Full nfqws2 parity may be impossible without root because several strategies depend on raw packet injection and kernel interception. The adapter must not claim support for such strategies unless they are proven within the userspace runtime.

## Current decision

- `zapret2` is listed as **audited/planned**, not working.
- No zapret2 binary or Lua strategy is bundled in the current APK.
- The existing user-facing route is named **Совместимость TCP/TLS**.
- That route is currently implemented by the pinned MIT-licensed ByeDPI SOCKS engine, whose upstream name remains visible in licenses and technical details.
- A future root mode will keep ByeDPI available, use a separate neutral product label, identify zapret2 in technical/license details, request `su` only when the user explicitly enables it, and restore its own firewall state on failure or stop.
