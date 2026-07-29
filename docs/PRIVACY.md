# Privacy / Конфиденциальность

## Русский

ViRouteFS работает локально на устройстве. В приложении нет рекламы,
аналитических SDK, скрытой телеметрии и автоматической отправки журналов.

Приложение обрабатывает только данные, необходимые для локальной маршрутизации:
пользовательские профили, правила, DNS-настройки, список установленных
приложений, их значки и метаданные сетевых соединений. Содержимое пакетов по
умолчанию не сохраняется.

Пароли, access UUID, cookie, PSK, приватные ключи и расширенные конфигурации с
учётными данными, включая XHTTP `extra`, шифруются AES-256-GCM. Ключ хранится в
Android Keystore, а зашифрованный файл исключён из Android Backup. Временная
конфигурация Xray-core доступна только приложению, создаётся при запуске и
удаляется при остановке процесса.

Экспорт или диагностический отчёт создаётся только по явному действию
пользователя. ViRouteFS не предоставляет собственные VPN-серверы и не передаёт
данные владельцу проекта.

Полная резервная копия `.vrfs` содержит конфигурацию и секреты только внутри
AES-256-GCM ciphertext. Ключ не сохраняется: он формируется из введённого
пользователем пароля через PBKDF2-HMAC-SHA256 с уникальной случайной солью.
Перед импортом файл расшифровывается в памяти, проходит структурную и нативную
проверку и не заменяет текущие настройки без отдельного нажатия. Очищенный JSON
не содержит секретов и предназначен для диагностики, а не полного
восстановления.

Ручной CSV-экспорт Flow Scanner содержит только видимые метаданные выбранных
соединений. Содержимое пакетов, HTTPS, сообщения, файлы и пароли в него не
включаются.

Камера запрашивается только после явного нажатия «Сканировать QR-код» и работает
только пока открыт экран сканирования. Кадры распознаются локальной библиотекой,
закрываются сразу после обработки, не сохраняются и не отправляются. Найденный
текст сначала попадает в предпросмотр со скрытыми секретами; конфигурация не
изменяется до отдельного подтверждения импорта.

Полный URL подписки может содержать токен и поэтому хранится отдельно под
AES-256-GCM с ключом Android Keystore. В обычном JSON остаётся только маркер.
Подписка запрашивается вручную обычным HTTPS GET; приложение не добавляет к
запросу список приложений, маршруты, статистику или идентификатор пользователя.
Ответ не сохраняется как журнал и сначала показывается в замаскированном
предпросмотре. Фонового обновления без отдельного согласия нет.

## English

ViRouteFS works locally on the device. It contains no advertising, analytics
SDK, hidden telemetry, or automatic log upload.

The app processes only data required for local routing: user profiles, rules,
DNS settings, the installed-app list, local app icons, and network connection
metadata. Packet payloads are not stored by default.

Passwords, access UUIDs, cookies, PSKs, private keys, and advanced
configurations containing credentials, including XHTTP `extra`, are encrypted
with AES-256-GCM. The key is held by Android Keystore and the encrypted file is
excluded from Android Backup. The temporary Xray-core configuration is
app-private, created on process start, and removed on process stop.

An export or diagnostic report is created only after an explicit user action.
ViRouteFS provides no VPN servers of its own and sends no data to the project
owner.

The complete `.vrfs` backup stores configuration and secrets only inside
AES-256-GCM ciphertext. Its key is not stored and is derived from the
user-entered password with PBKDF2-HMAC-SHA256 and a unique random salt. Import
decrypts in memory, performs structural and native validation, and does not
replace current settings without a separate explicit action. The redacted JSON
contains no secrets and is intended for diagnostics, not complete recovery.

The manual Flow Scanner CSV export contains only visible metadata for the
selected connections. It does not include packet payloads, HTTPS contents,
messages, files, or passwords.

Camera access is requested only after the user explicitly selects “Scan QR
code” and is active only while the scanner screen is open. Frames are decoded
by a local library, closed immediately after processing, never stored, and
never uploaded. Decoded text first enters the secret-masking preview; the
configuration is not changed until the user separately confirms the import.

A complete subscription URL may contain an access token and is therefore kept
separately under AES-256-GCM with an Android Keystore key. The regular JSON
contains only a marker. A subscription is fetched manually with an ordinary
HTTPS GET; the app does not attach the installed-app list, routes, statistics,
or a user identifier. The response is not retained as a log and first appears
in the secret-masking preview. There is no background refresh without separate
consent.
