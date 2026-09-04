#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

for cmd in gh git base64; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "ERROR: '$cmd' is required."; exit 1; }
done

gh auth status >/dev/null 2>&1 || { echo "ERROR: GitHub CLI is not authenticated. Run: gh auth login"; exit 1; }
OWNER="$(gh api user --jq .login)"
REPO="${APPBLOCKER_GITHUB_REPO:-AppBlocker}"
FULL="$OWNER/$REPO"
KEYSTORE="$HOME/.android/debug.keystore"

if [ ! -s "$KEYSTORE" ]; then
  echo "ERROR: $KEYSTORE was not found."
  echo "This exact key is required so GitHub-built APKs can update the AppBlocker already installed on your phone."
  exit 2
fi

echo "[1/7] Configuring in-app GitHub updater for $FULL..."
CONFIG="$ROOT/android/app/src/main/res/values/update_config.xml"
python3 - "$CONFIG" "$OWNER" "$REPO" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); owner=sys.argv[2]; repo=sys.argv[3]
s=p.read_text()
import re
s=re.sub(r'<string name="github_owner">.*?</string>', f'<string name="github_owner">{owner}</string>', s)
s=re.sub(r'<string name="github_repo">.*?</string>', f'<string name="github_repo">{repo}</string>', s)
p.write_text(s)
PY

echo "[2/7] Preparing Git repository..."
if [ ! -d .git ]; then
  git init
  git branch -M main
fi
git config user.name "AppBlocker Release Bot"
git config user.email "$OWNER@users.noreply.github.com"

echo "[3/7] Creating GitHub repository if needed..."
if ! gh repo view "$FULL" >/dev/null 2>&1; then
  gh repo create "$FULL" --public --description "AppBlocker Android client and self-hosted dashboard" >/dev/null
fi
if git remote get-url origin >/dev/null 2>&1; then
  git remote set-url origin "https://github.com/$FULL.git"
else
  git remote add origin "https://github.com/$FULL.git"
fi

echo "[4/7] Uploading signing key to encrypted GitHub Actions secrets..."
KEYSTORE_B64="$(base64 -w0 "$KEYSTORE")"
gh secret set APPBLOCKER_KEYSTORE_BASE64 --repo "$FULL" --body "$KEYSTORE_B64"
gh secret set APPBLOCKER_KEYSTORE_PASSWORD --repo "$FULL" --body "android"
gh secret set APPBLOCKER_KEY_ALIAS --repo "$FULL" --body "androiddebugkey"
gh secret set APPBLOCKER_KEY_PASSWORD --repo "$FULL" --body "android"
unset KEYSTORE_B64

echo "[5/7] Committing and pushing v1.3.0..."
git add .
if ! git diff --cached --quiet; then
  git commit -m "AppBlocker v1.3.0 - GitHub automatic updates"
fi
git push -u origin main

VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' android/app/build.gradle.kts | head -1)"
TAG="v$VERSION"
if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Tag $TAG already exists locally."
else
  git tag -a "$TAG" -m "AppBlocker $TAG"
fi
if gh release view "$TAG" --repo "$FULL" >/dev/null 2>&1; then
  echo "GitHub release $TAG already exists; skipping tag push."
else
  git push origin "$TAG"
fi

echo "[6/7] Waiting for GitHub Actions release build..."
RUN_ID=""
for _ in $(seq 1 30); do
  RUN_ID="$(gh run list --repo "$FULL" --workflow release-apk.yml --limit 10 --json databaseId,headBranch,event --jq '.[] | select(.headBranch=="'"$TAG"'" or .headBranch=="'"${TAG#v}"'") | .databaseId' | head -1 || true)"
  if [ -n "$RUN_ID" ]; then break; fi
  sleep 4
done
if [ -z "$RUN_ID" ]; then
  RUN_ID="$(gh run list --repo "$FULL" --workflow release-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
fi
if [ -n "$RUN_ID" ]; then
  gh run watch "$RUN_ID" --repo "$FULL" --exit-status
else
  echo "WARNING: Could not locate the workflow run automatically. Check GitHub Actions in $FULL."
fi

echo "[7/7] Downloading the first GitHub-built APK..."
mkdir -p release
if gh release view "$TAG" --repo "$FULL" >/dev/null 2>&1; then
  gh release download "$TAG" --repo "$FULL" --pattern '*.apk' --dir release --clobber
fi

echo
echo "GitHub automatic updates are configured."
echo "Repository: https://github.com/$FULL"
echo "Releases:   https://github.com/$FULL/releases"
echo
echo "ONE-TIME STEP: install the v1.3.0 APK from the GitHub release over your existing app."
echo "After that, future releases are detected inside the phone app automatically."
