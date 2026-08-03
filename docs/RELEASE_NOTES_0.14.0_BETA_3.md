# ViRouteFS 0.14.0-beta.3

## Xray/XHTTP compatibility

* Fixed the bundled Xray 26.6.1 launch command to use the current `run -c`
  syntax.
* Legacy v2rayNG XHTTP profiles containing `allowInsecure: true` are migrated
  before Xray starts. ViRouteFS obtains the presented leaf certificate once,
  stores its SHA-256 as a private no-backup TOFU pin, and supplies
  `pinnedPeerCertSha256` for that exact pinned certificate.
* VLESS share links preserve the current `pcs` and `vcn` certificate options.
* Xray startup errors remain fail-closed and retain the exact bounded,
  secret-masked engine reason for the user.
* An asynchronous Android VPN-router health failure now retains the last
  secret-masked sing-box reason instead of falling back to a generic readiness
  message.

## Import and connection diagnostics

* The add-VPN sheet now has one direct **Paste from clipboard and recognize**
  action. The existing preview still masks secrets and imports profiles
  disabled.
* Every user VPN card has **Test delay** with separate configuration, server,
  and routed-tunnel results plus a prominent current millisecond value after a
  successful routed HTTPS measurement.
* The tunnel stage sends one HTTPS request through a loopback-only HTTP proxy
  routed to the exact active profile outbound, including XHTTP profiles that
  pass through the local Xray SOCKS endpoint. It does not silently test through
  `System`, and starting network control does not trigger this request.
* Disabled, unrouted, malformed, UDP-only, and inactive-runtime cases explain
  which stage could not run and what the user needs to change.

## Privacy and safety

* Tests are manual. There is no telemetry or upload of configuration, logs, or
  certificate material.
* Certificate pins and temporary Xray runtime files stay in app-private
  no-backup storage.
* A failed profile test never changes the selected route and does not add a
  fallback to the ordinary phone connection.
