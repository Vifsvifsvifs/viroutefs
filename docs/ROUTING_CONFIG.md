# Routing configuration

ViRouteFS `0.4.0-alpha` replaces hardcoded route samples with editable local configuration.

## Storage

The app stores routing configuration as app-private JSON in internal storage. It does not request external storage permission, write exported files, upload config, or sync config to a cloud service.

On app start ViRouteFS loads the saved config if it is valid. If the file is missing or corrupted, the app falls back to defaults and shows a readable error so the user can reset or import a known-good config.

## JSON shape

Clipboard export includes:

- `version`;
- `profiles`;
- `dnsPolicies`;
- `rules`.

Import validates the JSON and keeps the existing config when import fails.

## Tunnel profiles

A tunnel profile contains:

- `id`;
- `name`;
- `type`;
- `description`;
- `enabled`;
- `mockOnly`;
- optional `platformNotes`.

Supported types are Direct, Block, Xray mock, Hysteria2 mock, OpenVPN mock, WireGuard mock, and Socks5 mock. Every non-Direct/Block profile is a mock profile and shows this limitation:

> Профиль пока не подключает реальный тоннель. Он используется для симуляции маршрутов.

## Route rules

A route rule contains:

- `id`;
- `name`;
- `type`: `APP_GROUP`, `APP`, `DOMAIN`, `CIDR`, or `DEFAULT`;
- `targetProfileId`;
- optional `dnsPolicyId`;
- `priority`;
- `matchers`;
- optional platform-neutral `appMatchers`;
- `enabled`;
- explanation fields.

Lower priority numbers win. Disabled rules are ignored. Disabled target profiles are not selected; the route engine falls back to a safe available Direct profile when possible. Exactly one enabled `DEFAULT` rule should be active.

## Default config

Defaults include Direct, Block, Xray Germany, Hysteria2 NL, OpenVPN Work, and SOCKS5 Work VM profiles. Rules cover banking/government/payment apps, Telegram, YouTube/media domains, work CIDRs, GitLab/Jira/Confluence/`*.corp`, blocked example domains, and a Direct default.

## Editing UI

The **Маршруты** screen lets users create, edit, enable/disable, and delete route profiles and route rules. Profile deletion is blocked while active rules depend on the profile. Rules can edit name, type, matchers, target profile, DNS policy, priority, and enabled state.

## Scenarios

Scenario buttons reset/apply predefined local configs:

- Работа и личное;
- Медиа через быстрый тоннель;
- Банки напрямую;
- Безопасный дефолт.

Each scenario explains what it changes before the user applies it.

## 0.4.1-alpha compact Routes model

Routes are presented to users as assignments from matchers to connection profiles:

- apps;
- sites/domains;
- IP/CIDR.

The UI groups matchers by profile instead of showing every raw rule in one technical list. Advanced fields such as priority, reason, technical details and recommended action are still part of the local model, but the compact editor fills sensible defaults for user-added rules.

The configuration now also carries a default/main profile id and optional per-profile DNS policy links. Existing exported JSON keeps compatibility through defaults for missing fields.

Real VPN routing is not implemented in 0.4.1-alpha; route decisions remain simulation/configuration behavior.
