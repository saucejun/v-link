#!/usr/bin/env bash
set -euo pipefail

echo "== OS =="
cat /etc/os-release || true

echo
uname -a

echo
echo "== Kernel features =="
if [[ -e /dev/net/tun ]]; then
  echo "/dev/net/tun exists"
else
  echo "/dev/net/tun missing (try: modprobe tun)"
fi

if lsmod | grep -q '^tun\b'; then
  echo "tun module loaded"
else
  echo "tun module not loaded"
fi

echo
echo "== Tools =="
for c in ip ss tcpdump iptables nft firewall-cmd sestatus getenforce; do
  if command -v "$c" >/dev/null 2>&1; then
    echo "$c: OK"
  else
    echo "$c: MISSING"
  fi
done

echo
echo "== Java =="
java -version || true

echo
echo "== Firewall/SELinux =="
if command -v systemctl >/dev/null 2>&1; then
  systemctl is-active firewalld 2>/dev/null || true
fi
if command -v sestatus >/dev/null 2>&1; then
  sestatus || true
elif command -v getenforce >/dev/null 2>&1; then
  getenforce || true
fi
