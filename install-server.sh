#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
IFS= read -r -s -p "Choose AppBlocker admin password (8+ chars): " PW; echo
if [ ${#PW} -lt 8 ]; then echo "Password too short"; exit 1; fi
python3 "$ROOT/backend/server.py" --set-password "$PW"
mkdir -p "$HOME/.config/systemd/user"
cat > "$HOME/.config/systemd/user/appblocker-server.service" <<EOF
[Unit]
Description=AppBlocker local dashboard and API
After=network.target
[Service]
Type=simple
WorkingDirectory=$ROOT
ExecStart=/usr/bin/python3 $ROOT/backend/server.py --host 0.0.0.0 --port 8787
Restart=on-failure
RestartSec=3
[Install]
WantedBy=default.target
EOF
systemctl --user daemon-reload
systemctl --user enable --now appblocker-server.service
IP=$(hostname -I | awk '{print $1}')
echo ""
echo "AppBlocker is running."
echo "Dashboard on this computer: http://127.0.0.1:8787"
echo "Phone server address (same Wi-Fi): http://$IP:8787"
