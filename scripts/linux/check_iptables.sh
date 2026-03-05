#!/usr/bin/env bash
set -euo pipefail

echo "[filter INPUT/FORWARD]"
iptables -S INPUT || true
iptables -S FORWARD || true

echo
echo "[nat POSTROUTING]"
iptables -t nat -S POSTROUTING || true

echo
echo "[nft ruleset]"
if command -v nft >/dev/null 2>&1; then
  nft list ruleset || true
else
  echo "nft not installed"
fi

echo
echo "[firewalld]"
if command -v firewall-cmd >/dev/null 2>&1; then
  firewall-cmd --state || true
  firewall-cmd --list-ports || true
else
  echo "firewall-cmd not installed"
fi

echo
echo "[SELinux]"
if command -v sestatus >/dev/null 2>&1; then
  sestatus || true
elif command -v getenforce >/dev/null 2>&1; then
  getenforce || true
else
  echo "SELinux tools not installed"
fi
