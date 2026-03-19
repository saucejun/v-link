package com.vlink.edge;

import com.vlink.common.protocol.NodeId;
import com.vlink.common.protocol.msg.DataPacket;
import java.net.InetSocketAddress;
import java.util.ArrayDeque;
import java.util.Deque;

// PeerSession 表示一个对端会话状态，记录路径模式和在途包信息。

final class PeerSession {
    enum LinkMode {
        INIT,
        PROBING,
        DIRECT,
        RELAY
    }

    final NodeId peerId;
    int peerVirtualIp;
    InetSocketAddress publicEndpoint;

    LinkMode mode;
    long probingStartedAtMs;
    long lastProbeSentAtMs;
    long lastQueryAtMs;

    int nextSeq;
    Inflight inflight;
    final Deque<byte[]> outboundQueue;

    PeerSession(NodeId peerId, int peerVirtualIp, boolean forceRelay) {
        this.peerId = peerId;
        this.peerVirtualIp = peerVirtualIp;
        this.mode = forceRelay ? LinkMode.RELAY : LinkMode.INIT;
        this.nextSeq = 1;
        this.outboundQueue = new ArrayDeque<byte[]>();
    }

    boolean hasEndpoint() {
        return publicEndpoint != null;
    }

    boolean probingActive() {
        return mode == LinkMode.PROBING || probingStartedAtMs > 0;
    }

    static final class Inflight {
        final DataPacket packet;
        boolean viaRelay;
        long lastSentAtMs;
        int retries;

        Inflight(DataPacket packet, boolean viaRelay, long lastSentAtMs) {
            this.packet = packet;
            this.viaRelay = viaRelay;
            this.lastSentAtMs = lastSentAtMs;
            this.retries = 0;
        }
    }
}
