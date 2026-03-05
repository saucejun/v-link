# v-link Protocol Spec (M2/M3)

Transport: UDP + encrypted envelope (`AES-128-GCM`, PSK from CLI).

## SecureEnvelope
| Field | Size | Note |
|---|---:|---|
| magic | 2 | `0x564C` |
| version | 1 | `1` |
| flags | 1 | encrypted flag bit0=1 |
| messageType | 1 | enum code |
| cryptoSuite | 1 | `1` = AES-128-GCM |
| senderNodeId | 16 | UUID bytes |
| timestampSec | 4 | unix epoch sec |
| nonceLen | 1 | typically `12` |
| nonce | n | random nonce |
| cipherLen | 2 | ciphertext length |
| ciphertext | n | encrypted control/data payload |

Key derivation: `SHA-256(pskUtf8)[0..15]` -> AES-128 key.
AAD: `magic+version+messageType+senderNodeId+timestampSec+nonceLen+nonce`.

## Message Types
| Type | Code |
|---|---:|
| REGISTER_REQ | 1 |
| REGISTER_RESP | 2 |
| HEARTBEAT_REQ | 3 |
| HEARTBEAT_RESP | 4 |
| QUERY_PEER_REQ | 5 |
| QUERY_PEER_RESP | 6 |
| PUNCH_REQUEST | 7 |
| PUNCH_NOTIFY | 8 |
| DATA_PACKET | 9 |

## New M2/M3 Payloads
### PUNCH_REQUEST
- requesterNodeId(16)
- targetNodeId(16)

### PUNCH_NOTIFY
- peerNodeId(16)
- peerPublicIpV4(4)
- peerPublicPort(2)
- peerVirtualIpV4(4)

### DATA_PACKET
- srcNodeId(16)
- dstNodeId(16)
- srcVirtualIpV4(4)
- dstVirtualIpV4(4)
- seq(4)
- ack(4)
- flags(1)
- payloadLen(2)
- payload(payloadLen)

Flags:
- bit0 `ACK`
- bit1 `RELAY`
- bit2 `PROBE`
