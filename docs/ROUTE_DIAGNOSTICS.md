# Route diagnostics

ViRouteFS `0.3.0-alpha` adds route diagnostics to the Russian **Маршруты** screen.

The feature answers three user questions in one report:

1. Which route would ViRouteFS select for this target?
2. Which mock rule caused that selection?
3. What do DNS, TCP, TLS/SNI, and HTTP checks show over the current Android network?

## Current behavior

Route diagnostics combine two different layers:

- **Simulated routing:** the existing in-memory `RouteEngine` selects one of the sample tunnel profiles and explains the matched rule.
- **Real current-network diagnostics:** when the user presses **Проверить маршрут**, ViRouteFS runs defensive DNS/TCP/TLS/HTTP checks through the device's current Android connection.

The app shows this limitation directly in the UI:

> В этой версии диагностика выполняется через текущее подключение Android. Выбранный маршрут пока симулируется.

## What is included in a report

A copied or shared report is plain text and includes:

- ViRouteFS version;
- input target;
- host and port used by diagnostics;
- optional SNI / server name;
- selected mock route;
- matched rule;
- plain-language route explanation;
- DNS result when the target looks like a domain;
- TCP result for the target and port;
- TLS/SNI result when port `443` is used or SNI is provided;
- HTTP result when the target is a URL or HTTPS can be inferred;
- final Russian summary;
- recommended next action;
- safety note;
- limitation note;
- elapsed time.

Reports are copied to the Android clipboard or sent to the Android share sheet only after explicit user action.

## In-memory history

The **Последние проверки** section keeps the last five route diagnostic reports for the current app session only.

ViRouteFS does not create files, does not use a database for this history, and does not persist route reports across app restarts.

## Limitations

Route diagnostics in `0.3.0-alpha` do **not** prove whether a future VPN route works, because real VPN routing is not implemented yet.

Current limitations:

- selected tunnels are mock profiles only;
- Xray is not implemented;
- OpenVPN is not implemented;
- packet capture is not implemented;
- Android `VpnService` routing is not connected to the route engine;
- diagnostics use the current Android network, not a selected mock tunnel.

For example, if a mock OpenVPN work route is selected but TCP fails through the current Android network, the report explains that this does not necessarily mean the future OpenVPN route is broken.

## Safety and privacy

Route diagnostics are local-first and user-controlled:

- no background checks;
- no telemetry;
- no analytics;
- no ad tracking;
- no cloud upload;
- no automatic report export;
- no packet capture;
- no scanners;
- no offensive security features.

Users should check only their own resources or networks where they have permission.
