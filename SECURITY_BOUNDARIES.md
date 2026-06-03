# Security Boundaries

ViRouteFS is a defensive, local-first Android network toolkit. It should explain routes, DNS behavior, and flow diagnostics in a way normal users can understand.

The guiding principle is:

> Risk detected, exploitation not performed.

## Allowed and implemented direction

Current and planned work may include:

- Local VPN permission flow.
- Safe TUN preview.
- Developer-only TEST-NET route preview using `203.0.113.0/24`.
- Route diagnostics.
- DNS diagnostics.
- Flow Scanner explanations.
- Local-only logs and user-controlled exports.
- Clear explanations, technical details, and recommended actions for diagnostic results.

## Explicitly not included

ViRouteFS must not include offensive or hidden surveillance behavior, including:

- WPS brute force.
- Wi-Fi cracking.
- Deauth attacks.
- Evil twin attacks.
- Credential theft.
- Exploit automation.
- Stealth scanning.
- Hidden interception.
- Packet payload logging without explicit user action.
- Router admin brute force.
- Hidden telemetry, analytics, tracking SDKs, ads, or automatic cloud upload.

## User-control requirements

- Diagnostics should be manually started by the user.
- Logs and future PCAP exports must stay local unless the user explicitly exports them.
- Any future packet capture feature must be explicit, visible, and documented before use.
- Any future root-only feature must be clearly labeled and optional.


## Routing isolation boundary

When network control is off, Android works normally and ViRouteFS does not control traffic. When network control is on in the product model, all traffic enters ViRouteFS: unmatched apps use the built-in System / Система route, matched app/domain/IP rules use only the selected profile, and unavailable selected profiles fail closed / Block. System is an internal default route, not bypass. Full runtime enforcement is a future routing-engine task and must be implemented without hidden interception, payload logging, telemetry, ads, tracking, or cloud upload. See docs/ROUTING_POLICY.md.
