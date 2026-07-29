# ViRouteFS 0.14.0-beta.1

This beta combines profile import, advanced route constraints, IPv6 matching
and ordered DNS servers into one installable block.

## Profile import

* One screen accepts VLESS, VMess, Trojan, Shadowsocks, Hysteria2, TUIC,
  SOCKS5 and HTTP(S) links, OpenVPN text/files, and sing-box JSON
  outbounds/endpoints.
* v2rayNG-style VLESS/XHTTP URIs are imported as a separate Xray profile,
  disabled by default and with UUID/path/header/extra values masked in preview.
* Android text sharing and supported deep links open the same safe preview.
* Nothing is saved before an explicit preview and confirmation.
* Passwords, UUIDs, tokens and private-key fields are masked in the preview.
* Exact duplicates can be skipped, replaced or saved as a copy.
* Every imported profile starts disabled and must be reviewed before use.
* Input files are bounded to 2 MiB and are processed locally.
* OpenVPN profiles have dedicated username/password fields and separate local
  pickers for a CA certificate, client certificate and client private key.
  Client certificate/key pairing is validated before the native runtime check.

## Routing and DNS

* App, domain and IP/CIDR rules can be restricted to TCP or UDP.
* Rules support exact destination ports and ranges such as
  `443, 8000-8100`.
* IP and CIDR validation and route simulation support IPv4 and IPv6.
* Custom DNS policies store multiple servers in explicit priority order and
  keep the selected outbound detour.
* The first valid DNS server is primary. Automatic health-based failover is
  not claimed as complete in this release.
* The runtime DNS output uses current cache and timeout fields and no longer
  emits the deprecated independent-cache option.

## Xray/XHTTP runtime

* A pinned MPL-2.0 Xray-core arm64 process handles VLESS/XHTTP without creating
  a second Android VPN.
* sing-box remains the only TUN owner and routes only explicitly selected flows
  to an app-private localhost SOCKS endpoint for each active Xray profile.
* Missing or failed Xray endpoints stay fail-closed.
* VLESS UUID and XHTTP extra JSON are kept in the encrypted Android Keystore
  secret store. The generated Xray runtime file is owner-only, excluded from
  backup and removed on stop.
* The exact Xray commit, binary SHA-256, MPL-2.0 text and reproducible build
  script are included in the source tree.

## Safety and status

* Existing configurations migrate without turning new constraints on.
* Invalid or unavailable route targets still map to `Block`, never silently to
  `System`.
* This release does not raise any external VPN protocol to `DeviceVerified`.
  Each still needs a real server/device matrix.
