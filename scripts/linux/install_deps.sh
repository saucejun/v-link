#!/usr/bin/env bash
set -euo pipefail

# Install baseline dependencies for Ubuntu 20.04+ and Alibaba Cloud Linux 3/OpenAnolis.

if command -v apt-get >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  sudo apt-get update
  sudo apt-get install -y \
    openjdk-17-jdk \
    iproute2 \
    iptables \
    tcpdump \
    net-tools \
    curl \
    ca-certificates \
    procps
  echo "[install_deps] Installed via apt-get"
  exit 0
fi

if command -v dnf >/dev/null 2>&1; then
  sudo dnf install -y \
    java-17-openjdk-devel \
    iproute \
    iptables \
    iptables-services \
    tcpdump \
    net-tools \
    curl \
    ca-certificates \
    procps-ng \
    policycoreutils \
    policycoreutils-python-utils \
    firewalld
  echo "[install_deps] Installed via dnf"
  exit 0
fi

if command -v yum >/dev/null 2>&1; then
  sudo yum install -y \
    java-17-openjdk-devel \
    iproute \
    iptables \
    iptables-services \
    tcpdump \
    net-tools \
    curl \
    ca-certificates \
    procps-ng \
    policycoreutils \
    policycoreutils-python-utils \
    firewalld
  echo "[install_deps] Installed via yum"
  exit 0
fi

echo "[install_deps] Unsupported distro: no apt-get/dnf/yum found"
exit 1
