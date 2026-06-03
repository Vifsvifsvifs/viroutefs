# ViRouteFS third-party license planning — 0.6.7-alpha

ViRouteFS app code is GPL-3.0-or-later.

Every bundled engine, library, or binary must be recorded here before integration. This file is a planning document for future license review, not an actual bundled engine license list yet.

## Current state

No external VPN/proxy/DPI engine binaries are bundled yet.

Do not claim a component is bundled unless it is actually shipped in the app, APK, source tree, or build inputs. Before any engine or binary is added, update this table with the exact license, source URL, bundled status, notices, source obligations, and any compatibility notes.

## Planned candidates

| Component | Purpose | License | Bundled? yes/no | Source URL | Notes / obligations |
| --- | --- | --- | --- | --- | --- |
| OpenVPN3 Core | Planned OpenVPN-compatible engine candidate | To be audited; prefer MPL-2.0 path | no | TBD | Planned candidate, not bundled. Preserve MPL notices/source obligations if integrated. Do not embed OpenVPN 2.x GPLv2-only core into the GPL-3.0-or-later app. |
| ByeDPI-style implementation | Planned local DPI bypass profile candidate | To be audited | no | TBD | Planned candidate, not bundled. Must remain local, explicit, and fail closed if selected but unavailable. |
| SOCKS5 implementation | Planned first real outbound candidate | To be audited | no | TBD | Planned candidate, not bundled. Implementation license must be checked before embedding. |
| WireGuard userspace | Planned VPN profile candidate | To be audited; must be GPL-compatible | no | TBD | Planned candidate, not bundled. Prefer GPL-compatible userspace implementation; avoid embedding GPLv2-only wireguard-tools into the APK. |
| Hysteria2 | Planned external tunnel candidate | To be audited | no | TBD | Planned candidate, not bundled. Audit Android feasibility, native dependencies, maintenance, and security. |
| Xray-core | Planned Xray/VLESS/Reality/VMess/Trojan import and engine candidate | To be audited; MPL-2.0 style obligations expected | no | TBD | Planned candidate, not bundled. Preserve notices/source obligations if integrated. |
| sing-box | Planned possible engine/core candidate | To be audited; GPL-3.0-or-later compatibility expected | no | TBD | Planned candidate, not bundled. Large dependency and architecture decision before integration. |
