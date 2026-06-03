# UI direction

## Navigation direction for 0.6.5-alpha

The main bottom navigation is:

- Networks / Сети
- Routes / Маршруты
- DNS
- FS
- Settings / Настройки

Home / Главная is no longer a bottom tab. Its useful overview text moved into **Settings → Help / Справка**.

The VPN-facing user concept is now **Networks / Сети** because ViRouteFS should be useful even without external VPN profiles: local network analysis, Flow Scanner visibility, route planning, DNS policy planning, and diagnostics remain useful.

## Product UI principles

Normal UI should not present future work as clickable, configured, or real. Do not show fake placeholders, fake VPN profiles, fake DNS tunnels, TEST-NET controls, or feature buttons before the feature exists. Keep the main UI compact; put longer explanations in Settings → Help / Справка.

Prefer honest empty/default states such as:

- Apps without rules use System route.
- Приложения без правил идут через маршрут Система.
- Uses Android system DNS.
- Использует системный DNS Android.

Demo/sample material is allowed only when clearly labeled as Demo and not mixed with real configuration.

## Icon direction

Final app icon direction for this alpha:

- Aggressive black/red stylized `V`.
- AMOLED black background with deep red/crimson foreground and accents.
- Sharp, angular, blade-like, technical, and adult.
- Not childish.
- Not soft/friendly.
- Not a generic VPN shield.
- Should feel like network control, route scanner, flow analyzer, and red-black technical tool.

Do not add low-quality generated raster artwork or external binary design files. Prefer adaptive icon XML/vector assets.
