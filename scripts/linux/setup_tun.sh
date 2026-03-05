#!/usr/bin/env bash
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root: sudo $0 ..."
  exit 1
fi

# Usage:
#   sudo ./scripts/linux/setup_tun.sh <tun_name> <virtual_ip_cidr> <mtu> <overlay_cidr> [owner_user]
# Example:
#   sudo ./scripts/linux/setup_tun.sh vlink0 10.10.0.2/24 1400 10.10.0.0/24 ec2-user

TUN_NAME=${1:-vlink0}
VIRTUAL_IP_CIDR=${2:-10.10.0.2/24}
MTU=${3:-1400}
OVERLAY_CIDR=${4:-10.10.0.0/24}
OWNER_USER=${5:-}

modprobe tun || true

if ip link show "$TUN_NAME" >/dev/null 2>&1; then
  ip link set dev "$TUN_NAME" down || true
  ip tuntap del dev "$TUN_NAME" mode tun || true
fi

if [[ -n "$OWNER_USER" ]]; then
  ip tuntap add dev "$TUN_NAME" mode tun user "$OWNER_USER"
else
  ip tuntap add dev "$TUN_NAME" mode tun
fi

ip addr replace "$VIRTUAL_IP_CIDR" dev "$TUN_NAME"
ip link set dev "$TUN_NAME" mtu "$MTU" up
ip route replace "$OVERLAY_CIDR" dev "$TUN_NAME"

sysctl -w net.ipv4.ip_forward=1 >/dev/null
sysctl -w net.ipv4.conf.all.rp_filter=0 >/dev/null
sysctl -w net.ipv4.conf.default.rp_filter=0 >/dev/null

echo "[setup_tun] tun=$TUN_NAME ip=$VIRTUAL_IP_CIDR mtu=$MTU route=$OVERLAY_CIDR owner=${OWNER_USER:-root}"
echo "[setup_tun] Notes:"
echo "  - If edge runs as non-root, pass owner_user so Java can attach to this TUN name."
echo "  - Persist sysctl in /etc/sysctl.d/99-vlink.conf if needed."
