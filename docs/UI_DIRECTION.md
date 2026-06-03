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

Normal UI should not present future work as clickable, configured, or real. Do not show fake placeholders, fake VPN profiles, fake DNS tunnels, TEST-NET controls, or feature buttons before the feature exists. Keep the main UI compact; Settings → Help / Справка is collapsed by default, and long help text must stay behind explicit expanders/details instead of appearing as a wall of text in the initial Settings view.

Prefer honest empty/default states such as:

- Apps without rules use System route.
- Приложения без правил идут через маршрут Система.
- Uses Android system DNS.
- Использует системный DNS Android.

Demo/sample material is allowed only when clearly labeled as Demo and not mixed with real configuration.

## Icon direction

Final app icon direction for this alpha:

- Minimalist black/red stylized `V`.
- Very dark background with a central red angular `V`.
- Subtle LAN/network motif integrated into the `V` with two or three small node dots and a thin connecting line.
- Sharp, clean, technical, and readable at small launcher sizes.
- Not childish, horror/aggressive, a cartoon face, or a generic VPN shield.
- Keep enough adaptive-icon padding so round, themed/monochrome, and legacy fallbacks stay visually consistent.

Do not add low-quality generated raster artwork or external binary design files. Prefer adaptive icon XML/vector assets.
