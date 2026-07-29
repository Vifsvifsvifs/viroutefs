# Privacy / Конфиденциальность

## Русский

ViRouteFS работает локально на устройстве. В приложении нет рекламы,
аналитических SDK, скрытой телеметрии и автоматической отправки журналов.

Приложение обрабатывает только то, что нужно для локальной маршрутизации:
профили пользователя, правила, DNS-настройки, список установленных приложений,
иконки приложений и метаданные сетевых соединений. Содержимое пакетов по
умолчанию не сохраняется.

Пароли, access UUID, cookie, PSK, приватные ключи и расширенные конфигурации с
учётными данными шифруются AES-256-GCM. Ключ хранится в Android Keystore, а
зашифрованный файл исключён из Android Backup.

Экспорт или диагностический отчёт создаётся только по явному действию
пользователя. ViRouteFS не предоставляет собственные VPN-серверы и не передаёт
данные владельцам проекта.

## English

ViRouteFS works locally on the device. It contains no advertising, analytics
SDK, hidden telemetry, or automatic log upload.

The app processes only data required for local routing: user profiles, rules,
DNS settings, the installed-app list, local app icons, and network connection
metadata. Packet payloads are not stored by default.

Passwords, access UUIDs, cookies, PSKs, private keys, and advanced
configurations containing credentials are encrypted with AES-256-GCM. The key
is held by Android Keystore and the encrypted file is excluded from Android
Backup.

An export or diagnostic report is created only after an explicit user action.
ViRouteFS provides no VPN servers of its own and sends no data to the project
owner.

