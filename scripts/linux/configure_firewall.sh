#!/usr/bin/env bash
set -euo pipefail

# Open UDP ports for coordinator/edge.
# Usage: sudo ./scripts/linux/configure_firewall.sh 40000 41001 41002

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root"
  exit 1
fi

PORTS=("$@")
if [[ ${#PORTS[@]} -eq 0 ]]; then
  PORTS=(40000 41001 41002)
fi

if command -v firewall-cmd >/dev/null 2>&1 && systemctl is-active --quiet firewalld; then
  for p in "${PORTS[@]}"; do
    firewall-cmd --add-port="${p}/udp" --permanent
    firewall-cmd --add-port="${p}/udp"
  done
  firewall-cmd --reload
  echo "[configure_firewall] firewalld udp ports opened: ${PORTS[*]}"
  exit 0
fi

if command -v iptables >/dev/null 2>&1; then
  for p in "${PORTS[@]}"; do
    iptables -C INPUT -p udp --dport "$p" -j ACCEPT 2>/dev/null || \
      iptables -I INPUT -p udp --dport "$p" -j ACCEPT
  done
  echo "[configure_firewall] iptables udp ports accepted: ${PORTS[*]}"
  echo "[configure_firewall] Persist with your distro method (iptables-save/service)."
  exit 0
fi

echo "[configure_firewall] No firewalld/iptables found"
exit 1
