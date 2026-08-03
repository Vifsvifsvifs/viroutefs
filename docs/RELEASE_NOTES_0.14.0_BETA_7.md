# ViRouteFS 0.14.0-beta.7

This beta expands the optional root layer. Root remains entirely optional:
ordinary Android VPN routing, DNS, profiles, VPNGate, ByeDPI and the regular
Flow Scanner continue to work without it.

## New root tools

* **Application firewall** applies local IPv4/IPv6 UID rules for all traffic,
  direct Wi-Fi, direct cellular traffic, or VPN/TUN interfaces. Shared Android
  UIDs are merged and system/self UIDs are protected from accidental rules.
* **Emergency network guard** can lock traffic to VPN interfaces and block
  direct DNS, DoT, DoQ and IPv6 paths with separate ViRouteFS chains.
* **Root socket snapshot** reads bounded `/proc/net` metadata and maps UIDs to
  installed applications. It does not read payloads or decrypt TLS.
* **Local PCAP** uses reproducibly built tcpdump 4.99.6/libpcap 1.10.6. Capture
  modes are fixed, duration is at most 60 seconds, the packet count is capped,
  files stay private until the user exports them through Android's file picker,
  and arbitrary shell/BPF input is not accepted.
* **VPN tethering** discovers hotspot/USB/Bluetooth and tunnel interfaces
  instead of accepting interface names from text. IPv4 clients are routed to
  the active ViRouteFS VPN; direct fallback and downstream IPv6 are blocked.
* **Root automation** controls one owned module by network type, screen state
  and time window in a visible foreground service. It does not start at boot.

## System WireGuard

* A separate **System WireGuard (root)** screen uses the official WireGuard
  Android tunnel library `1.0.20260102` and the kernel module when present.
* The ordinary sing-box/VpnService WireGuard path remains available without
  root and is never silently replaced.
* Saved ViRouteFS profiles are converted to wg-quick text and validated by the
  official parser. Selected-app mode becomes `IncludedApplications`; bypass
  mode becomes `ExcludedApplications`.
* Full-tunnel profiles require an enabled Custom DNS policy containing a plain
  IPv4/IPv6 server. The app refuses to start a full kernel tunnel without this
  protection instead of silently leaking DNS.
* Only a fixed command allow-list can run as root. ViRouteFS deliberately does
  not use the upstream persistent root shell that changes Magisk policy flags.
* The recovery configuration is encrypted with Android Keystore and retained
  until an addressed `wg-quick down` is confirmed. Active profiles cannot be
  deleted from their editor.

## Recovery and supply-chain checks

* General root recovery waits for automation to relinquish its module and
  stops System WireGuard before clearing the remaining ViRouteFS-owned rules.
* tcpdump/libpcap and all three WireGuard native commands are arm64 and have
  16 KiB ELF load alignment. CI verifies pinned hashes, APK signing,
  zip alignment, license files and the release manifest.
* The exact GPL-2.0 wireguard-tools source archive is attached to the release.

## Verification boundary

The full unit-test suite, Android lint, signed release build, APK signature,
native hashes and 16 KiB alignment are release gates. Runtime behavior on a
physical KernelSU/Magisk/APatch phone remains explicitly unverified until the
device is connected again. No root module installs boot persistence.
