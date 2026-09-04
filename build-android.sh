#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="$HOME/.local/share/appblocker-android-sdk"
TOOLS_ZIP="$HOME/.cache/appblocker-commandlinetools.zip"
GRADLE_ZIP="$HOME/.cache/appblocker-gradle-8.9-bin.zip"
GRADLE_HOME="$HOME/.local/share/appblocker-gradle-8.9"
mkdir -p "$HOME/.cache" "$SDK/cmdline-tools" "$HOME/.local/share" "$ROOT/release"

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[1/5] Downloading Android command-line tools..."
  curl -fL "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip" -o "$TOOLS_ZIP"
  rm -rf "$SDK/cmdline-tools/latest" "$SDK/cmdline-tools/cmdline-tools"
  unzip -q "$TOOLS_ZIP" -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
else
  echo "[1/5] Android command-line tools already installed; reusing them."
fi
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"

echo "[2/5] Checking Android SDK licenses..."
yes | "$SDKMANAGER" --sdk_root="$SDK" --licenses >/dev/null || true

echo "[3/5] Checking Android SDK platform/build tools..."
if [ -d "$SDK/platforms/android-35" ] && [ -d "$SDK/build-tools/35.0.0" ] && [ -d "$SDK/platform-tools" ]; then
  echo "Android SDK 35 already installed; reusing it."
else
  "$SDKMANAGER" --sdk_root="$SDK" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
fi

if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  echo "[4/5] Downloading Gradle 8.9..."
  curl -fL "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -o "$GRADLE_ZIP"
  rm -rf "$GRADLE_HOME" "$HOME/.local/share/gradle-8.9"
  unzip -q "$GRADLE_ZIP" -d "$HOME/.local/share"
  mv "$HOME/.local/share/gradle-8.9" "$GRADLE_HOME"
else
  echo "[4/5] Gradle 8.9 already installed; reusing it."
fi

echo "sdk.dir=$SDK" > "$ROOT/android/local.properties"
echo "[5/5] Building APK..."
(cd "$ROOT/android" && "$GRADLE_HOME/bin/gradle" --no-daemon clean assembleDebug)
cp "$ROOT/android/app/build/outputs/apk/debug/app-debug.apk" "$ROOT/release/AppBlocker-v2.0.0-debug.apk"
echo ""
echo "APK ready: $ROOT/release/AppBlocker-v2.0.0-debug.apk"
