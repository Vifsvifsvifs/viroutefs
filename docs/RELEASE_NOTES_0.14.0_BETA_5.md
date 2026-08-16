# ViRouteFS 0.14.0-beta.5

## Optional VPNGate catalog

* Added a separate, manually loaded catalog of free volunteer VPNGate OpenVPN
  servers. No request is made merely by opening the VPN screen.
* The screen shows country, host, IP, reported ping and speed, session count,
  uptime, operator and the server's advertised log-retention type.
* Search, country filters, ping/speed sorting and a bounded local cache keep the
  catalog usable without adding background activity or telemetry.
* Added two explicit selection modes. Manual mode imports one disabled server.
  Automatic mode excludes the locally detected/editable home-country code,
  prepares four low-ping foreign servers, enables them, and makes their
  existing sing-box URL-test group the default route.
* The automatic group performs real HTTPS health and delay checks every 60
  seconds. New connections use the available member with the lowest current
  delay; `System` is never added as an implicit fallback.
* A selected server is converted by the existing conservative `.ovpn` importer.
  Unknown directives and external commands are never executed, and every new
  VPNGate profile stays disabled until the user reviews and enables it.
* The catalog response is limited to 4 MiB and 512 servers; an individual
  decoded profile is limited to 256 KiB. HTTPS redirects are not followed.

## Safety and project status

* The UI explicitly warns that VPNGate relays are operated by third-party
  volunteers and may change availability or logging policy at any time.
* Updated the zapret2 engineering audit to upstream v1.0.4. The upstream
  nfqws2 path still requires kernel packet interception through NFQUEUE and
  explicit root privileges. This beta does not request root and does not
  present a non-working zapret2 switch or binary as integrated; a separate
  optional root module is planned without removing the existing ByeDPI route.

## Included previous beta.4 changes

* Per-profile app routing, inverted bypass mode, higher-priority profile CIDR
  networks, exclusive app assignment and selected apps pinned to the top.
* Local hosts-file editing, faster Routes opening, Android Quick Settings tile,
  corrected Android TUN TCP forwarding and two-request profile delay checks.
* Clickable voluntary-support link and locally generated QR code, explicitly
  described as support rather than a purchase.
