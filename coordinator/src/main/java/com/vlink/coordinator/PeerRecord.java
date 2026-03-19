package com.vlink.coordinator;

import com.vlink.common.protocol.NodeId;
import java.net.InetSocketAddress;

public final class PeerRecord {
    private final NodeId nodeId;
    private final int virtualIp;
    private volatile InetSocketAddress publicEndpoint;
    private volatile long lastSeenEpochSec;

    public PeerRecord(NodeId nodeId, int virtualIp, InetSocketAddress publicEndpoint, long lastSeenEpochSec) {
        this.nodeId = nodeId;
        this.virtualIp = virtualIp;
        this.publicEndpoint = publicEndpoint;
        this.lastSeenEpochSec = lastSeenEpochSec;
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public int virtualIp() {
        return virtualIp;
    }

    public InetSocketAddress publicEndpoint() {
        return publicEndpoint;
    }

    public long lastSeenEpochSec() {
        return lastSeenEpochSec;
    }

    public void refresh(InetSocketAddress endpoint, long timestampSec) {
        this.publicEndpoint = endpoint;
        this.lastSeenEpochSec = timestampSec;
    }
}
