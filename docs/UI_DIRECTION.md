# UI direction

## Navigation direction for 0.6.4-alpha

The main bottom navigation is:

- Networks / Сети
- Routes / Маршруты
- DNS
- FS
- Settings / Настройки

Home / Главная is no longer a bottom tab. Its useful overview text moved into **Settings → Help / Справка**.

The VPN-facing user concept is now **Networks / Сети** because ViRouteFS should be useful even without external VPN profiles: local network analysis, Flow Scanner visibility, route planning, DNS policy planning, and diagnostics remain useful.

## Placeholder policy

Normal UI should not present future work as clickable, configured, or real. Prefer empty states such as:

- No network profiles yet.
- No routes configured yet.
- No DNS policies configured yet.

Demo/sample material is allowed only when clearly labeled as Demo and not mixed with real configuration.

## Icon direction

Future app icon direction:

- Black/red aggressive theme.
- Stylized `V`.
- Sharp, technical look.
- Avoid a generic VPN shield look.
- Match the AMOLED black/red app theme.

Do not add low-quality generated raster artwork. If adaptive icon XML is adjusted before final artwork, keep it minimal and vector/XML based.
