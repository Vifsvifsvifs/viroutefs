# Alpha APK signing

ViRouteFS alpha APKs are built by GitHub Actions. Android only allows an APK to update an installed app when both APKs use the same `applicationId` and the same signing key.

ViRouteFS keeps the Android package stable as `dev.vifs.viroutefs`. The second requirement is the signing key: random local or CI debug keys are different from machine to machine, so APKs signed with random debug keys usually cannot update over each other.

## Stable alpha signing model

ViRouteFS supports optional stable alpha signing through GitHub Secrets:

- If all alpha signing environment variables are present, Gradle signs the debug alpha APK with the stable alpha key.
- If the variables are missing, Gradle falls back to normal Android debug signing so local developer builds still work.
- The alpha signing key must **not** be committed to the repository.
- Keystore files such as `*.jks`, `*.keystore`, `*.p12`, and `*.pfx` are ignored by Git.

After the project switches from random debug signing to stable alpha signing, users who installed an older randomly signed APK must uninstall it once. After installing the first APK signed with the stable alpha key, future APKs signed with the same key should update over it.


## Published alpha updates

Published alpha APKs are distributed through GitHub Releases with attached APK assets, such as `ViRouteFS-0.6.13-alpha.apk`. GitHub Releases are the official alpha APK channel. The in-app updater can find a newer GitHub Release, show recent release history in Settings → Updates, let the user manually download an APK asset, and open Android's system package installer. The downloader writes to a temporary cache file first, validates file presence and size, then moves it to a final `.apk` filename before the FileProvider install handoff. ViRouteFS does not silently install APKs; Android asks the user to confirm installation and enforces package signature compatibility for updates.

Because published alpha APKs use the stable alpha signing model when CI secrets are configured, future stable alpha updates should install over previous alpha builds that use the same `applicationId` and signing key. Android may show an unverified app warning because this APK is installed outside Google Play. This is a system warning for sideloaded APKs. ViRouteFS cannot suppress system install warnings, and users must confirm installation in Android system UI. If a user installed an older randomly-signed debug APK, Android may reject an update from the stable alpha-signed release APK. In that case, the user may need to uninstall the older debug APK once, then install the GitHub Releases APK and continue updating from Releases afterward.

## Generate an alpha keystore

Run this locally and keep the output private:

```bash
keytool -genkeypair \
  -v \
  -keystore viroutefs-alpha.jks \
  -alias viroutefs-alpha \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Choose strong passwords and store them securely. Do not commit `viroutefs-alpha.jks`.

## Base64 encode for GitHub Secrets

On Linux:

```bash
base64 -w 0 viroutefs-alpha.jks
```

On macOS:

```bash
base64 -i viroutefs-alpha.jks | tr -d '\n'
```

## Required GitHub Secrets

Set these repository secrets in GitHub:

- `VIROUTEFS_ALPHA_KEYSTORE_BASE64` — base64 encoded contents of `viroutefs-alpha.jks`.
- `VIROUTEFS_ALPHA_KEYSTORE_PASSWORD` — keystore password.
- `VIROUTEFS_ALPHA_KEY_ALIAS` — key alias, for example `viroutefs-alpha`.
- `VIROUTEFS_ALPHA_KEY_PASSWORD` — key password.

The Android CI workflow decodes `VIROUTEFS_ALPHA_KEYSTORE_BASE64` into `$RUNNER_TEMP/viroutefs-alpha.jks`, exports the Gradle signing environment variables, and then runs:

```bash
gradle :app:assembleDebug --stacktrace
```

If these secrets are not configured, the workflow still builds an APK with default debug signing, but update-over-install will not be stable across CI machines or changed debug keys.
