# DEPLOY_LINUX（Ubuntu 20.04+ / 阿里云 Linux 3 OpenAnolis）

## 0. 兼容性概览
- 支持的运行目标：
  - Ubuntu 20.04+
  - Alibaba Cloud Linux 3.2104 U12.3（OpenAnolis 版），内核 5.10.x，x86_64
- Java 要求：JDK 17+
- 网络栈要求：UDP 端口可达，且存在 `/dev/net/tun`

## 1. 发行版相关部分 vs 纯 Java 部分

### 1.1 与发行版相关的部分
- Netty Native Transport 加载：
  - Linux 上如果可用则使用 epoll（`Epoll.isAvailable()`），否则回退到 NIO。
  - 依赖 glibc / 本地库以及打包方式。
- TUN 设备创建与配置：
  - `/dev/net/tun`、`tun` 内核模块、`ip tuntap` 的行为、权限（`CAP_NET_ADMIN` / 设备所属用户）。
- 防火墙栈：
  - firewalld（在 OpenAnolis / RHEL 系常见）
  - iptables / nftables 后端差异。
- SELinux：
  - Enforcing 模式下，可能会因主机策略阻止某些操作。
- sysctl / iproute2 工具：
  - `ip`、`sysctl`、rp_filter 以及 forwarding 设置。
- 包管理器：
  - Ubuntu：`apt`
  - OpenAnolis / Alibaba Cloud Linux 3：`dnf`（或兼容 `yum`）

### 1.2 纯 Java 部分（与发行版无关）
- 协议序列化 / 反序列化
- AES-128-GCM 加密封装
- Coordinator 控制平面逻辑
- Edge 停等（stop-and-wait）可靠性逻辑
- 中继与 NAT 探测状态机逻辑

## 2. 安装依赖
使用脚本（自动检测 apt / dnf / yum）：

```bash
./scripts/linux/install_deps.sh
```

手动安装命令如下：

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

## 3. 主机兼容性快速检查

```bash
./scripts/linux/check_host_compat.sh
```

## 4. Coordinator 主机（OpenAnolis 示例）

### 4.1 防火墙放行 UDP

```bash
sudo ./scripts/linux/configure_firewall.sh 40000
```

### 4.2 SELinux 检查

```bash
sestatus || getenforce
```

如果 SELinux 处于 Enforcing 且流量被阻断，请先检查 firewalld / iptables 规则以及日志，再决定是否调整 SELinux 策略。

### 4.3 启动 coordinator

```bash
cat > coordinator.env <<'EOF'
PSK=demo-psk
BIND=0.0.0.0
PORT=40000
PEER_TIMEOUT_SEC=30
CLEANUP_INTERVAL_SEC=5
EOF

./scripts/linux/run_coordinator.sh ./coordinator.env
```

### 4.4 最小验证命令清单

启动：

```bash
./scripts/linux/run_coordinator.sh ./coordinator.env
```

检查监听端口：

```bash
ss -lunp | grep 40000 || netstat -lunp | grep 40000
```

抓包：

```bash
sudo tcpdump -ni any 'udp port 40000'
```

日志关键字：

* `[coordinator] started on ... io=epoll|nio`
* `[coordinator] register node=`
* `[coordinator] relay packet`
* `[coordinator] removed stale peers=`

## 5. Edge 主机（Ubuntu / OpenAnolis 均适用）

### 5.1 配置 TUN

Edge-A：

```bash
sudo ./scripts/linux/setup_tun.sh vlink0 172.20.0.2/24 1400 172.20.0.0/24 <edge_user>
```

Edge-B：

```bash
sudo ./scripts/linux/setup_tun.sh vlink0 172.20.0.3/24 1400 172.20.0.0/24 <edge_user>
```

`<edge_user>` 为可选项。如果 edge 进程以非 root 用户运行，请设置该参数，以便创建的 TUN 设备拥有匹配的属主。

### 5.2 为 Edge UDP 端口放行防火墙

```bash
sudo ./scripts/linux/configure_firewall.sh 41001 41002
```

### 5.3 运行 edge

Edge-A：

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

Edge-B：

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

## 6. 验证（DIRECT + RELAY）

### 6.1 Ping Overlay 网络

在 edge-a 上：

```bash
ping -c 4 172.20.0.3
```

在 edge-b 上：

```bash
ping -c 4 172.20.0.2
```

### 6.2 DIRECT 模式验证依据

预期 edge 日志：

* `[edge] path switch -> DIRECT ...`
* `[edge] TX DIRECT seq=...`

### 6.3 RELAY 模式验证依据

将其中一侧设置为 `FORCE_RELAY=true`，然后重启 edge。

预期日志：

* edge：`[edge] TX RELAY seq=...`
* coordinator：`[coordinator] relay packet ...`

## 7. tcpdump 过滤器

TUN 上的 Overlay ICMP：

```bash
sudo tcpdump -ni vlink0 'icmp and (host 172.20.0.2 or host 172.20.0.3)'
```

底层 UDP（控制面 + 数据面）：

```bash
sudo tcpdump -ni any 'udp and (port 40000 or port 41001 or port 41002)'
```

## 8. 可选的 TUN 权限绕过方案

如果 `/dev/net/tun` 或 ioctl 权限受限：

1. 通过 `setup_tun.sh ... <edge_user>` 预先创建 TUN，并指定属主用户。
2. 使用相同的 `--tunName` 和非 root 账号运行 edge。
3. 如果仍然受阻，请在受控环境中以 `CAP_NET_ADMIN` 或 root 权限运行 edge。


