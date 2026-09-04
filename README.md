# AppBlocker 2.0

AppBlocker is now a fully local Android application blocker.

There is no web dashboard, backend server, pairing, device token, heartbeat, remote database, or LAN dependency.

## Local management

On first launch the phone owner creates a management password.

Every configuration change inside AppBlocker requires that password:
- application blocking rules
- time limits
- blocking images
- warning thresholds
- password changes
- software update installation

## Blocking modes

Each installed application can be:
- Unrestricted
- Always blocked
- Time limited (`allowed minutes / window minutes`)

AppBlocker's own package is permanently excluded from blocking.

## Images

The general block image and time-limit image are selected from the phone and copied into AppBlocker's private local storage.

## Required Android permissions

- Accessibility: foreground app detection and blocking
- Usage access: more reliable foreground detection and time accounting
- Notifications: time-limit warnings

## Updates

Software updates remain GitHub-based.

`publish-github-release.sh` publishes a version tag. GitHub Actions builds the signed APK and attaches it to the GitHub Release. The installed app checks GitHub Releases directly and can download future updates.

## Safety model

This remains a normal Android application. It does not root the phone, impersonate Android system components, prevent uninstall through OS mechanisms, or use destructive device-management behavior.
