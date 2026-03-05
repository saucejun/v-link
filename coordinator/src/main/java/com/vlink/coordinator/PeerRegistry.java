package com.vlink.coordinator;

import com.vlink.common.protocol.NodeId;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class PeerRegistry {
    private final ConcurrentMap<NodeId, PeerRecord> peersByNodeId = new ConcurrentHashMap<NodeId, PeerRecord>();
    private final ConcurrentMap<Integer, NodeId> nodeByVirtualIp = new ConcurrentHashMap<Integer, NodeId>();

    public PeerRecord upsert(NodeId nodeId, int virtualIp, InetSocketAddress endpoint, long nowSec) {
        PeerRecord record = peersByNodeId.compute(nodeId, (key, existing) -> {
            if (existing == null) {
                return new PeerRecord(nodeId, virtualIp, endpoint, nowSec);
            }
            existing.refresh(endpoint, nowSec);
            return existing;
        });
        nodeByVirtualIp.put(virtualIp, nodeId);
        return record;
    }

    public PeerRecord refresh(NodeId nodeId, InetSocketAddress endpoint, long nowSec) {
        PeerRecord existing = peersByNodeId.get(nodeId);
        if (existing != null) {
            existing.refresh(endpoint, nowSec);
        }
        return existing;
    }

    public PeerRecord find(NodeId nodeId) {
        return peersByNodeId.get(nodeId);
    }

    public PeerRecord findByVirtualIp(int virtualIp) {
        NodeId nodeId = nodeByVirtualIp.get(virtualIp);
        return nodeId == null ? null : peersByNodeId.get(nodeId);
    }

    public int removeExpired(long nowSec, int timeoutSec) {
        List<NodeId> expired = new ArrayList<NodeId>();
        for (Map.Entry<NodeId, PeerRecord> entry : peersByNodeId.entrySet()) {
            if (nowSec - entry.getValue().lastSeenEpochSec() > timeoutSec) {
                expired.add(entry.getKey());
            }
        }
        for (NodeId nodeId : expired) {
            PeerRecord removed = peersByNodeId.remove(nodeId);
            if (removed != null) {
                nodeByVirtualIp.remove(removed.virtualIp(), nodeId);
            }
        }
        return expired.size();
    }
}
