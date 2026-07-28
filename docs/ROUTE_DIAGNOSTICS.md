# Route diagnostics

Route diagnostics has two separate jobs:

1. explain which saved rule, profile and DNS policy would match the supplied app/domain/IP;
2. run optional DNS/TCP/TLS/HTTP checks through the current physical Android network.

The first job uses the same local policy model that is compiled into sing-box. The second job does not prove the selected VPN path because the ViRouteFS app process is excluded from its own TUN.

## Report contents

- normalized input;
- matched rule and priority;
- selected profile;
- selected DNS policy;
- fail-closed warnings;
- results of requested network checks;
- plain-language recommendation;
- technical details behind an expander.

Reports are kept only for the current app session and can be copied or shared only by explicit action.

## End-to-end route test

Use a separate test application with an explicit rule, enable the VPN, verify external IP/DNS from that application, then disable the selected profile and confirm that traffic is blocked. A successful in-app TCP or TLS check alone is not sufficient.
