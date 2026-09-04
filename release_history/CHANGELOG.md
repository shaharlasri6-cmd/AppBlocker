# AppBlocker Release History

## v1.2.1
- Fixed pairing completion on Android: approval now immediately starts the foreground sync service.
- Installed-app inventory is uploaded immediately after pairing instead of waiting for another resume/reopen cycle.
- Added visible pairing-approved / syncing feedback on the phone.
- Pairing status polling interval reduced to 2 seconds for faster completion.
- Android versionCode 8 / versionName 1.2.1.

## v1.2.0
- Removed Device Admin and all uninstall-resistance behavior from Android.
- Removed uninstall passwords, removal authorization, and related server/dashboard API.
- Removed Device Admin status/tamper checks from the dashboard and phone.
- Renamed the Android app display label to "Device Protection Configuration and Application Usage Management Service" without impersonating an Android system component.
- The app continues to exclude its own package from the blockable application inventory.
- Android versionCode 7; Java/Kotlin target remains 17.

# AppBlocker v1.1.3

## Fixes
- Fixed the final two unresolved `text` references in `MainActivity.kt` after the `textColor` rename.
- Kept Java and Kotlin JVM targets aligned on Java 17.
- Increased Android `versionCode` to 6 so this build can update the installed application.
- Updated the release APK name to `AppBlocker-v1.1.3-debug.apk`.

## v1.3.0 - GitHub automatic updates
- Added GitHub Releases based in-app update detection.
- Added automatic background update checks every six hours while the protection service is active.
- Added update notifications and a Software updates card in the Android app.
- Added one-tap APK download and Android installer handoff.
- Added GitHub Actions workflow that builds and publishes a signed APK for every v* tag.
- Added one-time setup-github-updates.sh to create/configure the GitHub repository and signing secrets.
- Added publish-github-release.sh for future releases.
- GitHub CI reuses the existing local Android debug signing key so the first GitHub build can update the already-installed app without uninstalling it.
- Android versionCode 9 / versionName 1.3.0.
