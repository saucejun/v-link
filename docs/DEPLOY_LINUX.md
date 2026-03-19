# DEPLOY_LINUX (Ubuntu 20.04+ / Alibaba Cloud Linux 3 OpenAnolis)

## 0. Compatibility Summary
- Supported runtime targets:
  - Ubuntu 20.04+
  - Alibaba Cloud Linux 3.2104 U12.3 (OpenAnolis Edition), kernel 5.10.x, x86_64
- Java requirement: JDK 17+
- Network stack requirement: UDP reachable ports and `/dev/net/tun`

## 1. Distro-dependent vs Java-only
### 1.1 Distro-dependent parts
- Netty native transport loading:
  - Linux uses epoll if available (`Epoll.isAvailable()`), else fallback NIO.
  - Depends on glibc/native libs and packaging.
- TUN device provisioning:
  - `/dev/net/tun`, `tun` kernel module, `ip tuntap` behavior, permissions (`CAP_NET_ADMIN`/owner user).
- Firewall stack:
  - firewalld (common on OpenAnolis/RHEL family)
  - iptables/nftables backend differences.
- SELinux:
  - Enforcing mode may block operations depending on host policy.
- sysctl/iproute2 tools:
  - `ip`, `sysctl`, rp_filter and forwarding settings.
- Package manager:
  - Ubuntu: apt
  - OpenAnolis/Alibaba Cloud Linux 3: dnf (or yum compatibility)

### 1.2 Pure Java (distro-agnostic)
- Protocol serialization/deserialization
- AES-128-GCM encryption envelope
- Coordinator control-plane logic
- Edge stop-and-wait reliability logic
- Relay and NAT probe state machine logic

## 2. Install Dependencies
Use script (auto detects apt/dnf/yum):
```bash
./scripts/linux/install_deps.sh
```

Manual commands:

### Ubuntu 20.04+
```bash
sudo apt-get update
sudo apt-get install -y openjdk-17-jdk iproute2 iptables tcpdump net-tools curl ca-certificates procps
```

### Alibaba Cloud Linux 3 / OpenAnolis
```bash
sudo dnf install -y \
  java-17-openjdk-devel iproute iptables iptables-services tcpdump net-tools \
  curl ca-certificates procps-ng policycoreutils policycoreutils-python-utils firewalld
```

## 3. Host Compatibility Quick Check
```bash
./scripts/linux/check_host_compat.sh
```

## 4. Coordinator Host (OpenAnolis example)
### 4.1 Firewall allow UDP
```bash
sudo ./scripts/linux/configure_firewall.sh 40000
```

### 4.2 SELinux check
```bash
sestatus || getenforce
```
If SELinux is Enforcing and traffic is blocked, first verify firewalld/iptables rules and logs before changing SELinux policy.

### 4.3 Start coordinator
```bash
cat > coordinator.env <<'EOF'
PSK=demo-psk
BIND=0.0.0.0
PORT=40000
PEER_TIMEOUT_SEC=30
CLEANUP_INTERVAL_SEC=5
VIP_POOL_START=172.10.0.2
VIP_POOL_END=172.10.0.254
EOF

./scripts/linux/run_coordinator.sh ./coordinator.env
```

### 4.4 Minimal verification command list
Start:
```bash
./scripts/linux/run_coordinator.sh ./coordinator.env
```

Listen port check:
```bash
ss -lunp | grep 40000 || netstat -lunp | grep 40000
```

Packet capture:
```bash
sudo tcpdump -ni any 'udp port 40000'
```

Log keywords:
- `[coordinator] started on ... io=epoll|nio`
- `[coordinator] register node=`
- `[coordinator] relay packet`
- `[coordinator] removed stale peers=`

## 5. Edge Hosts (both Ubuntu/OpenAnolis)
### 5.1 Setup TUN
Edge-A:
```bash
sudo ./scripts/linux/setup_tun.sh vlink0 10.10.0.2/24 1400 172.10.0.0/24 <edge_user>
```

Edge-B:
```bash
sudo ./scripts/linux/setup_tun.sh vlink0 10.10.0.3/24 1400 172.10.0.0/24 <edge_user>
```

`<edge_user>` optional. If edge runs non-root, set this so TUN is created with matching owner.

### 5.2 Open firewall for edge UDP ports
```bash
sudo ./scripts/linux/configure_firewall.sh 41001 41002
```

### 5.3 Run edge
Edge-A:
```bash
cat > edge-a.env <<'EOF'
NODE_ID=edge-a
BIND=0.0.0.0
BIND_PORT=41001
COORDINATOR_HOST=<COORD_PUBLIC_IP>
COORDINATOR_PORT=40000
PSK=demo-psk
TUN_MODE=linux
TUN_NAME=vlink0
PEERS=edge-b
FORCE_RELAY=false
EOF
./scripts/linux/run_edge.sh ./edge-a.env
```

Edge-B:
```bash
cat > edge-b.env <<'EOF'
NODE_ID=edge-b
BIND=0.0.0.0
BIND_PORT=41002
COORDINATOR_HOST=<COORD_PUBLIC_IP>
COORDINATOR_PORT=40000
PSK=demo-psk
TUN_MODE=linux
TUN_NAME=vlink0
PEERS=edge-a
FORCE_RELAY=false
EOF
./scripts/linux/run_edge.sh ./edge-b.env
```

Notes:
- Edge no longer accepts `VIRTUAL_IP`; coordinator assigns it during register.
- Keep edge startup order stable if you rely on deterministic sequential VIP allocation from the pool.

## 6. Validation (DIRECT + RELAY)
### 6.1 Ping overlay
On edge-a:
```bash
ping -c 4 10.10.0.3
```

On edge-b:
```bash
ping -c 4 10.10.0.2
```

### 6.2 DIRECT mode evidence
Expected edge logs:
- `[edge] register status=OK assignedVip=...`
- `[edge] path switch -> DIRECT ...`
- `[edge] TX DIRECT seq=...`

### 6.3 RELAY mode evidence
Set one side `FORCE_RELAY=true` then restart edge.
Expected logs:
- edge: `[edge] TX RELAY seq=...`
- coordinator: `[coordinator] relay packet ...`

## 7. tcpdump Filters
Overlay ICMP on TUN:
```bash
sudo tcpdump -ni vlink0 'icmp and (host 10.10.0.2 or host 10.10.0.3)'
```

Underlay UDP (control + data):
```bash
sudo tcpdump -ni any 'udp and (port 40000 or port 41001 or port 41002)'
```

## 8. Optional TUN Permission Workaround
If `/dev/net/tun` or ioctl permission is restricted:
1. Pre-create TUN with owner user via `setup_tun.sh ... <edge_user>`.
2. Run edge with same `--tunName` and non-root account.
3. If still blocked, run edge with CAP_NET_ADMIN/root in controlled environment.
