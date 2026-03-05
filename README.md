# v-link

Lightweight P2P virtual LAN with coordinator + edge nodes.

## Modules
- `common`: protocol, crypto, net utils
- `coordinator`: control plane + relay
- `edge`: real/mock TUN + dataplane
- `docs`: deployment and protocol docs
- `scripts`: windows/linux run scripts

## Current stage
- Linux real TUN (`/dev/net/tun` via JNA)
- UDP direct probe + fallback relay
- stop-and-wait reliability (`seq/ack`, retry, dedup)

## Docs
- `docs/architecture.md`
- `docs/protocol.md`
- `docs/DEPLOY_LINUX.md`
