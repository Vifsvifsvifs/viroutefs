# VLESS URI import/export and TCP reachability (0.8.6-alpha)

ViRouteFS 0.8.6-alpha can import and export `vless://` profile URIs as local configuration and can run an explicit manual TCP reachability probe for the saved host and port.

## What is supported

The importer parses the following URI parts and stores them in the local VLESS profile form:

- UUID from `vless://uuid@...`
- server host and port
- profile name from the URI fragment (`#name`)
- transport placeholder from `type=tcp`, `type=ws`, or `type=grpc`
- `security` values `none`, `tls`, or `reality`
- `encryption`
- `flow`
- `sni`
- `fp`
- `pbk`
- `sid`
- `path`
- host header from `host=`
- `alpn` when present
- gRPC service name from `serviceName=` when present

The parser validates UUID syntax and requires a port in the `1..65535` range. Unsupported schemes, unsupported transport placeholders, unsupported security values, missing hosts, invalid UUIDs, and invalid ports are rejected with user-visible errors.

## Manual TCP reachability probe

0.8.6-alpha adds a **user-triggered only** “Test TCP reachability” action on the VLESS profile screen. The probe opens a plain TCP socket to the profile host and port with a timeout, then closes the socket immediately after a successful connect.

The probe reports these outcomes: reachable, timeout, refused, DNS/host error, or validation error. The app also summarizes readiness as Not tested, TCP reachable, or Last test failed.

This is TCP reachability only. It does **not** send any bytes, does **not** send the UUID or credentials, does **not** perform TLS, does **not** perform REALITY, does **not** perform a VLESS handshake, does **not** connect using VLESS, does **not** forward packets, does **not** write packets back to TUN, and does **not** proxy DNS.

Recent manual results are stored locally in app no-backup storage, newest first, capped at 20 entries per profile. The history stores profile id/name snapshot, host, port, timestamp, result state, message, and elapsed time only; it does not store UUID.

## Runtime limitation

VLESS runtime is not implemented in this release. Importing or saving a VLESS profile does **not** auto-connect to the server, forward packets, write packets back to TUN, proxy DNS, or upload any data. VLESS profiles remain route-preview/configuration placeholders until a future runtime implementation is added.

## Privacy and identifiers

The app masks UUIDs in summaries and previews so casual logs and UI text do not reveal the full connection identifier. The full UUID is still stored locally because it is required to recreate a usable VLESS URI.

Export is an explicit user action only. The UI warns: “Exported VLESS URI contains connection identifiers. Share it carefully.” The exported URI contains connection identifiers, including the UUID and any parameters the user saved. Treat exported URIs as sensitive and share them only with trusted recipients.
