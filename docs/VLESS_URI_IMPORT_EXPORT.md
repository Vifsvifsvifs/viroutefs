# VLESS URI import/export (0.8.5-alpha)

ViRouteFS 0.8.5-alpha can import and export `vless://` profile URIs as local configuration only.

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

## Config-only limitation

VLESS runtime is not implemented in this release. Importing or saving a VLESS profile does **not** connect to the server, test server reachability, forward packets, write packets back to TUN, proxy DNS, or upload any data. VLESS profiles remain route-preview/configuration placeholders until a future runtime implementation is added.

## Privacy and identifiers

The app masks UUIDs in summaries and previews so casual logs and UI text do not reveal the full connection identifier. The full UUID is still stored locally because it is required to recreate a usable VLESS URI.

Export is an explicit user action only. The UI warns: “Exported VLESS URI contains connection identifiers. Share it carefully.” The exported URI contains connection identifiers, including the UUID and any parameters the user saved. Treat exported URIs as sensitive and share them only with trusted recipients.
