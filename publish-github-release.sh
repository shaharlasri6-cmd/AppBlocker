#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"
command -v gh >/dev/null || { echo "ERROR: gh is required"; exit 1; }
OWNER="$(gh api user --jq .login)"
REPO="$(python3 - <<'PY'
import re
s=open('android/app/src/main/res/values/update_config.xml').read()
m=re.search(r'<string name="github_repo">(.*?)</string>',s)
print(m.group(1) if m else 'AppBlocker')
PY
)"
FULL="$OWNER/$REPO"
VERSION="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' android/app/build.gradle.kts | head -1)"
TAG="v$VERSION"

git add .
if ! git diff --cached --quiet; then
  git commit -m "AppBlocker $TAG"
fi
git push origin main
if ! git rev-parse "$TAG" >/dev/null 2>&1; then git tag -a "$TAG" -m "AppBlocker $TAG"; fi
git push origin "$TAG"
echo "Published $TAG. GitHub Actions will build and attach the APK automatically."
echo "https://github.com/$FULL/actions"
