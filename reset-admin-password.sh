#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"

printf 'New AppBlocker admin password (8+ chars): '
IFS= read -r -s PW1
echo
printf 'Repeat password: '
IFS= read -r -s PW2
echo

if [ "$PW1" != "$PW2" ]; then
  echo "ERROR: passwords do not match. Nothing changed."
  exit 1
fi
if [ ${#PW1} -lt 8 ]; then
  echo "ERROR: password must be at least 8 characters. Nothing changed."
  exit 1
fi

python3 "$ROOT/backend/server.py" --set-password "$PW1"
python3 "$ROOT/backend/server.py" --verify-password "$PW1"

systemctl --user restart appblocker-server.service
sleep 1

APPBLOCKER_TEST_PASSWORD="$PW1" python3 - <<'PY'
import json, os, sys, urllib.error, urllib.request
pw=os.environ['APPBLOCKER_TEST_PASSWORD']
req=urllib.request.Request(
    'http://127.0.0.1:8787/api/admin/login',
    data=json.dumps({'password':pw}).encode('utf-8'),
    headers={'Content-Type':'application/json'}, method='POST')
try:
    with urllib.request.urlopen(req, timeout=5) as r:
        body=json.load(r)
        if r.status != 200 or not body.get('token'):
            raise RuntimeError(f'unexpected response: HTTP {r.status}')
except Exception as e:
    print(f'ERROR: password was saved locally, but live website login test failed: {e}')
    print('Run: systemctl --user status appblocker-server.service --no-pager -l')
    sys.exit(2)
print('LIVE LOGIN TEST OK')
PY

unset PW1 PW2
printf '\nPassword reset completed and verified against the running server.\n'
printf 'Open: http://127.0.0.1:8787\n'
printf 'If the page was already open, press Ctrl+Shift+R once before signing in.\n'
