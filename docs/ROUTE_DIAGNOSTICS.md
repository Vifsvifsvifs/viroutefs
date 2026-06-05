# Route diagnostics

ViRouteFS `0.4.0-alpha` keeps user-triggered route diagnostics and connects route selection to the saved editable routing configuration.

## What the user enters

On the **Маршруты** screen the user can enter:

- target domain, IP, URL, app keyword, package-like name, process-like name, or executable-like name;
- port;
- optional SNI / server name.

The route engine first selects a simulated route from local config. Then diagnostics run over the current Android network only.

## Current-network limitation

Route diagnostics do not send traffic through the selected profile yet. The app shows this limitation directly in the UI:

> В этой версии диагностика выполняется через текущее подключение Android. Выбранный маршрут пока симулируется.

The selected route can include a mock profile and DNS policy warning, but real VPN routing and real DNS routing are not implemented yet.

## What is included in a report

A copied or shared report is plain text and includes:

- ViRouteFS version;
- input target;
- host and port used by diagnostics;
- optional SNI / server name;
- selected simulated route, where unmatched traffic uses System and explicit matched rules are exclusive;
- matched rule and priority context;
- DNS policy metadata;
- plain-language route explanation;
- mock profile and DNS leak warnings;
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

Route diagnostics in `0.4.0-alpha` do **not** prove whether a future VPN route works, because real VPN routing is not implemented yet.

Current limitations:

- non-System/Block tunnels are mock profiles only;
- Xray is not implemented;
- Hysteria2 is not implemented;
- OpenVPN is not implemented;
- WireGuard is not implemented;
- SOCKS5 proxying is not implemented;
- packet capture is not implemented;
- Android `VpnService` routing is not connected to the route engine;
- DNS policy is metadata only;
- diagnostics use the current Android network, not a selected mock tunnel.

## Safety and privacy

Route diagnostics are local-first and user-controlled: no background checks, telemetry, analytics, ad tracking, cloud upload, automatic report export, packet capture, scanners, or offensive security features.

## Runtime observation controls in 0.8.3-alpha

VPN Runtime observation remains a local, opt-in read/drop preview. Packets read from the safe TUN preview are counted and dropped; ViRouteFS does not forward packets, write packets back to TUN, proxy DNS, implement VLESS, implement SOCKS5 forwarding, persist packet summaries, or upload telemetry/cloud logs.

The packet inspector has safety controls for heavy or sensitive testing:

- **Pause packet inspector** freezes the summary list by preventing new in-memory summaries from being appended. Runtime counters may continue to update while the service stays active and stable.
- **Clear packet list** empties the current in-memory summaries without resetting counters. Because summaries are not persisted, there is no on-disk packet history to delete.

The UI keeps only the latest 50 metadata-only summaries, debounces list publication to avoid excessive Compose updates under traffic, shows the last packet-list update timestamp, and displays "No packets observed yet" when the in-memory list is empty.
