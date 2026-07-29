# UI direction

## Navigation direction for 0.13.0-beta.1

The main bottom navigation is:

- Control / Контроль
- Routes / Маршруты
- Scanner / Сканер
- More / Ещё

DNS, Tools and Settings are full screens opened from **More / Ещё**. They do not occupy the bottom bar because they are not primary daily destinations.

The primary concept is **Control / Контроль** because the app can start without an external VPN profile. `System` uses the phone's normal mobile-data or Wi-Fi connection, and explicit rules override it only for selected apps or addresses.

## Product UI principles

Normal UI should not present future work as clickable, configured, or real. Do not show fake placeholders, fake VPN profiles, fake DNS tunnels, TEST-NET controls, or feature buttons before the feature exists. Keep the main UI compact; Settings → Help / Справка is collapsed by default, and long help text must stay behind explicit expanders/details instead of appearing as a wall of text in the initial Settings view.

Prefer honest empty/default states such as:

- Apps without rules use System route.
- Приложения без правил идут через маршрут Система.
- Uses Android system DNS.
- Использует системный DNS Android.

Demo/sample material is allowed only when clearly labeled as Demo and not mixed with real configuration.

## Icon direction

Final app icon direction for this alpha and later launcher-surface cleanup:

- Minimalist black/red stylized `V`.
- Very dark background with a central red angular `V`.
- White branching/network-like lines around or behind the `V`, using a small number of node dots and thin connecting lines.
- Sharp, clean, technical, and readable at small launcher sizes.
- Not childish, horror/aggressive, a cartoon face, or a generic VPN shield.
- Keep enough adaptive-icon padding so round, installer/package parser, file manager APK preview, and legacy fallbacks stay visually consistent. Launcher resources use unique `ic_viroutefs_launcher_0614*` names so updated installs cannot fall back to stale cached `ic_launcher` assets; old `ic_launcher` resources should remain absent or visually identical if reintroduced for compatibility.

Do not add low-quality generated raster artwork or external binary design files. Prefer adaptive icon XML/vector assets.

Launcher icon resource audit for `0.6.15-alpha`: AndroidManifest `android:icon` and `android:roundIcon` point to `@mipmap/ic_viroutefs_launcher_0614*`; adaptive icon XML uses the shared dark background and red `V` foreground without a launcher monochrome layer. ViRouteFS intentionally does not provide Android themed launcher icon metadata for now because some launchers recolor it and make the launcher icon inconsistent with installer/APK preview. Full-color launcher icon rendering is preferred for alpha branding consistency. Notification small icons should use a separate notification-only resource. No runtime VPN/TUN packet forwarding, updater logic, telemetry, analytics, ads, tracking, cloud upload, background update checks, APK auto-download, or silent install behavior changed as part of the icon cleanup.
