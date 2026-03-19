# v-link Architecture (M2/M3)

## Components
- `coordinator`: registration, heartbeat tracking, punch notify, relay forwarding.
- `edge`: TUN device IO, encrypted UDP dataplane, direct/relay switch, stop-and-wait.
- `common`: protocol codecs and crypto.

## Datapath
1. Edge reads IP packet from TUN.
2. Resolve destination virtual IP via runtime cache (`virtualIp -> nodeId`), learned from coordinator query responses.
3. Control-plane lookup and punch use `targetNodeId` (never `targetVirtualIp`).
4. Send encrypted `DATA_PACKET` over UDP:
   - preferred: direct peer endpoint.
   - fallback: coordinator relay.
5. Receiver decrypts and writes payload back to local TUN.

## Direct/Relay State Machine
- `INIT`: no endpoint yet.
- `PROBING`: query peer + coordinator punch notify + direct probe packets.
- `DIRECT`: direct UDP path established.
- `RELAY`: coordinator relay mode after direct timeout/retry fail.

Transitions:
- `INIT -> PROBING`: first outbound packet.
- `PROBING -> DIRECT`: receive direct packet/ack/probe.
- `PROBING -> RELAY`: probing timeout or retransmit exhausted.
- `RELAY -> DIRECT`: later probe succeeds.

## Reliability (simplified)
- stop-and-wait per peer.
- fields: `seq/ack`.
- retransmit on RTO timeout.
- max retry reached => fallback relay.
- receiver dedup cache prevents repeated write into TUN.
