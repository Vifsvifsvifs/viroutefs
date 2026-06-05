# ViRouteFS diagnostics

ViRouteFS `0.4.0-alpha` includes real basic network diagnostics and route diagnostics that explain results in plain Russian: what happened, technical details, elapsed time when available, which simulated route and DNS policy were selected, and what the user should check next.

## User-triggered only

Diagnostics run only after the user presses a check button in the app.

ViRouteFS does not perform automatic background checks, background scanning, telemetry, analytics, ad tracking, cloud upload of logs or diagnostic results, packet capture, port scanning, vulnerability checks, brute force, or exploit automation.

Users should check only their own resources or networks where they have permission.

## DNS lookup

The DNS screen performs a real lookup after the user presses **Проверить**.

Supported record types are `A` and `AAAA`. The UI keeps domain, DNS server, and record type fields. Current limitation: ViRouteFS uses Android's system DNS resolver. The custom DNS server field and routing DNS policies are kept for the product flow, explanation, and leak-risk checks, but missing DNS is shown as Android system DNS and is not silently replaced with public resolvers. Direct querying of selected DNS servers and per-route DNS enforcement will be connected later.

## TCP check

The Tools screen includes a TCP section with host, port, and timeout fields. After the user presses **Проверить TCP**, ViRouteFS attempts to open one TCP socket to the selected host and port. This is a single user-requested connection check, not a scanner.

## TLS/SNI check

The Tools screen includes a TLS/SNI section with host, port, and SNI/server-name fields. After the user presses **Проверить TLS**, ViRouteFS opens a TLS connection and uses SNI when possible. When available, the result includes protocol, cipher, certificate subject, issuer, validity dates, and elapsed time.

## HTTP/HTTPS check

The Tools screen includes an HTTP section with a URL field. After the user presses **Проверить HTTP**, ViRouteFS performs a simple HTTP `HEAD` request and follows a small number of safe `http`/`https` redirects.

## Route diagnostics

The Routes screen includes **Диагностика маршрута**. After the user presses **Проверить маршрут**, ViRouteFS uses the saved editable `RouteEngine` configuration to select a simulated route, show the matched rule, priority, selected profile, DNS policy, leak warnings, and mock limitations, then runs current-network diagnostics.

The report can be copied to the clipboard or sent through the Android share sheet only by explicit user action. The last five reports are kept in memory for the current app session only.

See [ROUTE_DIAGNOSTICS.md](ROUTE_DIAGNOSTICS.md) for details.

## VPN and route simulator status

Real VPN routing is still not implemented. Xray, Hysteria2, OpenVPN, WireGuard, SOCKS5, packet capture, and true custom DNS routing are also not implemented in this milestone.

The Route Simulator and selected Route Diagnostics tunnel remain simulation-only. They are UX and configuration models for explaining future routing decisions, not a real routing engine connected to Android VPN traffic.

## Live route decision preview in 0.8.2-alpha

The local packet inspector now derives an observation-only route decision preview from each in-memory `PacketSummary`. The preview reuses the existing route model to show the matched rule name, selected profile, and selected profile type beside packet summaries.

This preview does not enable runtime forwarding: packets are not forwarded, written back to TUN, proxied through SOCKS5, sent through VLESS-like engines, DNS-proxied, persisted, uploaded, or logged with payload data. SOCKS5 and other remote/mock runtime profiles are shown with explicit runtime-forwarding warnings.
