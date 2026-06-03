# ViRouteFS routing vision

ViRouteFS is a human-readable traffic routing and diagnostics system. The product goal is simple:

> Show where traffic goes, explain why, and help fix the route.

ViRouteFS is not only an Android VPN app. The route model is intentionally platform-neutral so future Android, Linux, and Windows implementations can share the same concepts: profiles, rules, DNS policy, explanations, diagnostics, and leak warnings.

## Architecture direction

Android should eventually use one `VpnService` as the local traffic entry point. Future routing logic can then decide which internal outbound tunnel should handle each flow. Linux and Windows can later map the same model to their own platform routing mechanisms.

Planned outbound profile types include System, Block, Xray, Hysteria2, OpenVPN, WireGuard, and SOCKS5. System is the built-in default Android/system path inside ViRouteFS for unmatched apps; Block is fail-closed behavior. Legacy `Direct` ids/types may exist only for saved-config compatibility. Xray, Hysteria2, OpenVPN, WireGuard, and SOCKS5 profiles remain future/mock concepts until they route traffic safely.

## Route rules

The route engine uses clear, human-readable rules. Rules can match traffic by app group, app/text matcher, domain name, CIDR range, and a default fallback. Lower priority numbers win, disabled rules are ignored, and the enabled `DEFAULT` rule sends unmatched apps to System. Explicit matched rules are exclusive: unavailable selected profiles fail closed / Block and never silently fall back to another profile.

Each rule explains which tunnel profile it selects, which DNS policy applies as metadata, why it matched, the priority, mock status, leak risk, and what the user should do if the result is unexpected.

## DNS policy is part of a route

A route is not only a tunnel. A route also needs DNS policy, explanation, diagnostics, and leak warnings.

DNS policy is stored and shown as configuration metadata only. If no DNS is configured, the model uses Android system DNS through ViRouteFS policy and must not silently substitute public resolvers. Real DNS routing is not implemented. The UI warns when DNS policy and selected profile could leak or conflict.

## Route simulator and diagnostics

The route simulator and route diagnostics now use the saved local routing configuration instead of hardcoded samples. Diagnostics still perform real DNS/TCP/TLS/HTTP checks only after the user presses a button, and those checks use the current Android connection rather than the simulated route.

## Safety and privacy

ViRouteFS remains local-first:

- no telemetry;
- no analytics;
- no ads or tracking SDKs;
- no cloud upload;
- no background checks;
- no automatic packet capture;
- no offensive security features.
