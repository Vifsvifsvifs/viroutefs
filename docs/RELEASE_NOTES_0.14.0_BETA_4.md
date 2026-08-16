# ViRouteFS 0.14.0-beta.4

## Android control and routing

* Added a toggleable **Network control** Quick Settings tile and an in-app
  Android 13+ prompt for adding it to the shade.
* Added per-VPN application selection with search, **Select all**, selected
  apps pinned to the top, and exclusive assignment across VPN profiles.
* Added inverted bypass mode: checked apps use `System`, unchecked apps use the
  selected default VPN. Profile IP/CIDR networks have higher priority than the
  app bypass list.
* Application and network assignments survive profile replacement and
  subscription refresh.

## DNS and responsiveness

* Added multiline hosts-file input accepting both `IP hostname` and
  `hostname IP`. Local hosts answers remain above every DNS policy.
* The Routes page no longer scans installed applications while opening. The
  local app catalog loads off the main UI thread only after an app-rule editor
  is opened and then reuses a process-local cache.

## Voluntary support

* Added the project owner's Sber HTTPS support link as a locally generated QR
  code. The same link opens when the QR or support button is tapped and can be
  copied manually.
* The screen explicitly labels the payment as voluntary support, not a purchase
  of features or digital benefits. ViRouteFS does not handle bank credentials
  or payment data.

## Physical Android fixes

* The Android TUN now uses the userspace gVisor stack. This fixes TCP flows that
  were accepted but did not complete on the tested Android 16 device while DNS
  and ICMP still worked.
* Profile delay checks reuse the connection for two routed HTTPS requests and
  display the lower current result, matching v2rayNG measurement behavior more
  closely.

## Privacy

* Installed application metadata, hosts entries, profile assignments, test
  results, and routing state stay on the device. No telemetry was added.
