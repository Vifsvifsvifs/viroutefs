# ViRouteFS 0.14.0-beta.15

- Keeps a short, bounded in-memory trail of native OpenVPN messages for each active profile.
- Shows the relevant handshake or transport message when OpenVPN is retrying and therefore still reports `Connecting` instead of a terminal error.
- Redacts credential, token, cookie, key, PIN, and one-time-code values before any native diagnostic is shown.
- Does not write the captured OpenVPN diagnostic trail to disk and clears it when network control stops.
- Includes all automatic VPNGate, guided setup, routing, Android Back, and structured OpenVPN status fixes from beta.13 and beta.14.
