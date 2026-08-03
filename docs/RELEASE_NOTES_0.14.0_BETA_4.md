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
* The Routes page now loads and labels installed applications off the main UI
  thread and reuses a process-local cache.

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
