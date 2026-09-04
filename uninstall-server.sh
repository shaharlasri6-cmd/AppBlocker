#!/usr/bin/env bash
set -e
systemctl --user disable --now appblocker-server.service 2>/dev/null || true
rm -f "$HOME/.config/systemd/user/appblocker-server.service"
systemctl --user daemon-reload
printf 'Server service removed. Project/data directory was NOT deleted.\n'
