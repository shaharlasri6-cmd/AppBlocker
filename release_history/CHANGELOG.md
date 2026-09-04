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

## v1.3.1
- Fixed GitHub Actions Android SDK setup failure (exit code 127 / sdkmanager not found).
- Added android-actions/setup-android before installing SDK 35 packages.
- Updated checkout/setup-java actions to current major versions.
- Kept existing signing secrets and release flow unchanged.

## v1.3.2 - Dashboard device lifecycle controls
- Added a dashboard connection-loss alert when a paired phone has not synchronized for 2 minutes.
- The alert explains that the app may have been removed/stopped, or that the phone/server connection may be unavailable.
- Added automatic dashboard refresh every 15 seconds so connection-loss alerts appear without manual refresh.
- Added an authenticated Delete device action in Device settings.
- Deleting a device removes its server-side app inventory, policies, usage records, pairing records, settings, and uploaded block images.
- Deleting a device from the dashboard does not uninstall anything from the phone.
- Android time-limit tracking now uses a reliable foreground-app resolver based on UsageEvents with Accessibility fallback.
- Accessibility window noise no longer overwrites the timed foreground application.
- Time-limit reached notifications are now independent of warning thresholds.
- Warning notifications use a fresh high-importance channel and no longer fire several thresholds at once.
- The phone status page now shows whether notification permission is enabled.
- Notification permission problems are reported in the dashboard protection log.
- Android versionCode 11 / versionName 1.3.2.

## v2.0.0

- Removed the web dashboard completely.
- Removed the Python backend, SQLite server database, pairing, device tokens and heartbeat.
- AppBlocker now works entirely on the Android phone.
- Added first-run management password creation.
- Every local configuration change requires the management password.
- Added local application list with search and optional system-app visibility.
- Added Unrestricted, Always blocked and Time limited application modes.
- Added fully local general-block and time-limit images.
- Added local time-limit warning thresholds.
- Preserved Accessibility + Usage Access blocking logic.
- AppBlocker permanently excludes its own package from blocking.
- GitHub Releases remain the only update channel.
- Android versionCode 20 / versionName 2.0.0.
