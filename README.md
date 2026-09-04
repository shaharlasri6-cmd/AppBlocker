# AppBlocker 1.0

A zero-cost, self-hosted Android application blocker with a web-only policy dashboard.

## Safety model

AppBlocker never roots, factory-resets, flashes, reformats, deletes, encrypts, or edits other apps' data. It only observes foreground-app transitions and presents a blocking screen. It intentionally does **not** use Device Owner provisioning.

Because it is a normal Android app, Android still allows an owner with physical control to bypass protection through OS-level paths such as Safe Mode, ADB (if enabled), force-stop, or revoking Accessibility. AppBlocker reports/degrades on such conditions but cannot make them impossible without Device Owner/root.

## Cost

This edition is designed for **$0/month**. The backend/dashboard runs on your own Ubuntu computer using Python's standard library + SQLite. There is no paid cloud, domain, database, auth provider, storage provider, or push provider.

Trade-off: when your Ubuntu computer is off or the phone is away from your home network, remote policy changes do not synchronize. The Android phone continues enforcing the last cached policy offline. If you later want remote access over the internet, use your own free VPN/tunnel solution; it is deliberately not required by this release.

## Server install (Ubuntu)

```bash
cd AppBlocker
chmod +x install-server.sh uninstall-server.sh
./install-server.sh
```

The installer prints both the desktop dashboard URL and the phone's LAN server address.

## Android install

Run `./build-android.sh`. It locally downloads the free Android command-line SDK + Gradle into your user account, builds the APK, and places it at `release/AppBlocker-v1.0.0-debug.apk`. No Android Studio or sudo is required. Install that APK on the phone. The app asks only for the server address during initial pairing; policy controls are never available on the phone.

Then complete the setup buttons shown in the app:

- Accessibility service
- Usage access
- Battery/background exemption
- Optional Device Admin uninstall resistance
- Samsung: Never sleeping apps / disable automatic permission removal
- Xiaomi/Redmi/POCO: Autostart + Battery “No restrictions” + Recents lock when available

No step wipes or modifies existing phone data.

## Pairing

1. Start the server and open the dashboard.
2. Open AppBlocker on the phone.
3. Enter the server LAN URL, such as `http://192.168.1.20:8787`.
4. Tap **Create pairing code**.
5. Dashboard → **Pair device** → enter the code → **Approve pairing**.
6. The Android app automatically receives its device token and begins syncing.

## Dashboard features

- Connected-device health and tamper status
- Installed application inventory
- Unrestricted / Blocked / Time Limited rules
- Generic “X minutes every Y minutes” limits
- General-block and time-expired images
- Configurable notification thresholds enforced by the Android client
- Website-only uninstall authorization

## Time-limit semantics

v1.0 uses **fixed windows aligned to Unix time boundaries**. Example: `10 minutes every 60 minutes` resets at each clock-hour boundary. The device uses server time at last synchronization plus Android monotonic elapsed time, so simply changing the wall clock does not immediately grant extra time while offline.

## Uninstall authorization

Dashboard → Settings → **Authorize uninstall** creates a 15-minute device authorization. On the phone, the read-only status page then exposes a single **Prepare normal uninstall** action that removes Device Admin. Android's normal uninstall can then proceed.

This is intentionally honest: Device Admin adds friction but cannot block Safe Mode or ADB uninstall.

## Tests

```bash
python3 backend/tests.py
python3 backend/smoke_test.py
node --check web/app.js
```

## Data

All server data is stored under `data/` by default. `uninstall-server.sh` removes only the systemd user service and deliberately leaves project/data files untouched.
