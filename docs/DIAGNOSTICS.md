# ViRouteFS diagnostics

ViRouteFS `0.3.0-alpha` includes real basic network diagnostics and route diagnostics that explain results in plain Russian: what happened, technical details, elapsed time when available, and what the user should check next.

## User-triggered only

Diagnostics run only after the user presses a check button in the app.

ViRouteFS does not perform:

- automatic background checks;
- background scanning;
- telemetry;
- analytics;
- ad tracking;
- cloud upload of logs or diagnostic results;
- packet capture;
- port scanning;
- vulnerability checks;
- brute force;
- exploit automation.

Users should check only their own resources or networks where they have permission.

## DNS lookup

The DNS screen performs a real lookup after the user presses **Проверить**.

Supported record types in this milestone:

- `A` for IPv4 addresses;
- `AAAA` for IPv6 addresses.

The UI keeps these fields:

- domain;
- DNS server;
- record type.

Current limitation: ViRouteFS uses Android's system DNS resolver in `0.3-alpha`. The custom DNS server field is kept for the product flow, but direct querying of the selected DNS server will be connected later. The app shows this limitation directly on the DNS screen.

Validation includes empty domains, invalid domain names, unsupported record types, DNS lookup failures, and timeouts.

## TCP check

The Tools screen includes a TCP section with host, port, and timeout fields. After the user presses **Проверить TCP**, ViRouteFS attempts to open one TCP socket to the selected host and port.

The result explains common outcomes:

- DNS failed;
- TCP connected;
- TCP timed out;
- connection refused;
- unknown error.

This is a single user-requested connection check, not a scanner.

## TLS/SNI check

The Tools screen includes a TLS/SNI section with host, port, and SNI/server-name fields. After the user presses **Проверить TLS**, ViRouteFS opens a TLS connection and uses SNI when possible.

When available, the result includes:

- handshake success or failure;
- TLS protocol;
- cipher suite;
- peer certificate subject;
- certificate issuer;
- certificate validity dates;
- elapsed time.

The plain-language explanation distinguishes likely certificate mismatch, expired or not-yet-valid certificates, timeouts, and connection failures where possible.

## HTTP/HTTPS check

The Tools screen includes an HTTP section with a URL field. After the user presses **Проверить HTTP**, ViRouteFS performs a simple HTTP `HEAD` request and follows a small number of safe `http`/`https` redirects.

The result can include:

- status code;
- final URL if redirected;
- content type;
- elapsed time;
- DNS/TCP/TLS/HTTP stage hints when failures are recognizable.

## Route diagnostics

The Routes screen includes **Диагностика маршрута**. After the user presses **Проверить маршрут**, ViRouteFS uses the existing `RouteEngine` to select a simulated route and then runs current-network diagnostics.

Depending on the target and fields, the combined report can include DNS, TCP, TLS/SNI, and HTTP results. The report can be copied to the clipboard or sent through the Android share sheet only by explicit user action. The last five reports are kept in memory for the current app session only.

See [ROUTE_DIAGNOSTICS.md](ROUTE_DIAGNOSTICS.md) for details.

## VPN and route simulator status

Real VPN routing is still not implemented. Xray, OpenVPN, and packet capture are also not implemented in this milestone.

The Route Simulator and selected Route Diagnostics tunnel remain mock/simulation-only. They are UX models for explaining future routing decisions, not a real routing engine connected to Android VPN traffic.
