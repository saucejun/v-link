package com.vlink.coordinator;

import com.vlink.common.protocol.NodeId;
import com.vlink.common.protocol.msg.RegisterResponse;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class PeerRegistry {
    public static final class RegistrationResult {
        private final byte status;
        private final PeerRecord record;
        private final String reason;

        private RegistrationResult(byte status, PeerRecord record, String reason) {
            this.status = status;
            this.record = record;
            this.reason = reason;
        }

        public static RegistrationResult ok(PeerRecord record) {
            return new RegistrationResult(RegisterResponse.STATUS_OK, record, "");
        }

        public static RegistrationResult duplicate(PeerRecord record, String reason) {
            return new RegistrationResult(RegisterResponse.STATUS_DUPLICATE_NODE_ID, record, reason);
        }

        public static RegistrationResult ipConflict(String reason) {
            return new RegistrationResult(RegisterResponse.STATUS_IP_ALLOCATION_CONFLICT, null, reason);
        }

        public byte status() {
            return status;
        }

        public PeerRecord record() {
            return record;
        }

        public String reason() {
            return reason;
        }
    }

    private final ConcurrentMap<NodeId, PeerRecord> peersByNodeId = new ConcurrentHashMap<NodeId, PeerRecord>();
    private final ConcurrentMap<NodeId, Integer> virtualIpByNodeId = new ConcurrentHashMap<NodeId, Integer>();
    private final ConcurrentMap<NodeId, InetSocketAddress> endpointByNodeId = new ConcurrentHashMap<NodeId, InetSocketAddress>();
    private final ConcurrentMap<Integer, NodeId> nodeByVirtualIp = new ConcurrentHashMap<Integer, NodeId>();
    private final int vipRangeStart;
    private final int vipRangeEnd;
    private final AtomicInteger nextVipCandidate;

    public PeerRegistry(int vipRangeStart, int vipRangeEnd) {
        if (vipRangeStart <= 0 || vipRangeEnd <= 0 || vipRangeStart > vipRangeEnd) {
            throw new IllegalArgumentException("Invalid VIP range: " + vipRangeStart + "-" + vipRangeEnd);
        }
        this.vipRangeStart = vipRangeStart;
        this.vipRangeEnd = vipRangeEnd;
        this.nextVipCandidate = new AtomicInteger(vipRangeStart);
    }

    public synchronized RegistrationResult register(
        NodeId nodeId,
        InetSocketAddress endpoint,
        long nowSec,
        int duplicateWindowSec
    ) {
        PeerRecord existing = peersByNodeId.get(nodeId);
        if (existing != null) {
            InetSocketAddress previousEndpoint = endpointByNodeId.get(nodeId);
            boolean endpointChanged = previousEndpoint != null && !previousEndpoint.equals(endpoint);
            boolean stillActive = nowSec - existing.lastSeenEpochSec() <= Math.max(duplicateWindowSec, 1);
            if (endpointChanged && stillActive) {
                return RegistrationResult.duplicate(existing, "active endpoint already exists");
            }

            existing.refresh(endpoint, nowSec);
            endpointByNodeId.put(nodeId, endpoint);
            if (!bindMapping(existing)) {
                return RegistrationResult.ipConflict("mapping invalid for existing node");
            }
            return RegistrationResult.ok(existing);
        }

        int allocatedVip = allocateVirtualIp(nodeId);
        if (allocatedVip == 0) {
            return RegistrationResult.ipConflict("virtual ip pool exhausted or conflicting");
        }

        PeerRecord created = new PeerRecord(nodeId, allocatedVip, endpoint, nowSec);
        peersByNodeId.put(nodeId, created);
        endpointByNodeId.put(nodeId, endpoint);
        virtualIpByNodeId.put(nodeId, allocatedVip);
        return RegistrationResult.ok(created);
    }

    public PeerRecord refresh(NodeId nodeId, InetSocketAddress endpoint, long nowSec) {
        PeerRecord existing = peersByNodeId.get(nodeId);
        if (existing != null) {
            existing.refresh(endpoint, nowSec);
            endpointByNodeId.put(nodeId, endpoint);
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

    public InetSocketAddress connectionFor(NodeId nodeId) {
        return endpointByNodeId.get(nodeId);
    }

    public Integer virtualIpFor(NodeId nodeId) {
        return virtualIpByNodeId.get(nodeId);
    }

    public boolean hasValidMapping(NodeId nodeId) {
        PeerRecord record = peersByNodeId.get(nodeId);
        if (record == null) {
            return false;
        }
        return isMappingConsistent(record);
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
                endpointByNodeId.remove(nodeId);
                virtualIpByNodeId.remove(nodeId, removed.virtualIp());
                nodeByVirtualIp.remove(removed.virtualIp(), nodeId);
            }
        }
        return expired.size();
    }

    private boolean bindMapping(PeerRecord record) {
        NodeId nodeId = record.nodeId();
        int virtualIp = record.virtualIp();
        NodeId mappedNode = nodeByVirtualIp.putIfAbsent(virtualIp, nodeId);
        if (mappedNode != null && !mappedNode.equals(nodeId)) {
            return false;
        }
        virtualIpByNodeId.put(nodeId, virtualIp);
        if (record.publicEndpoint() != null) {
            endpointByNodeId.put(nodeId, record.publicEndpoint());
        }
        return true;
    }

    private boolean isMappingConsistent(PeerRecord record) {
        NodeId mappedNode = nodeByVirtualIp.get(record.virtualIp());
        Integer mappedVip = virtualIpByNodeId.get(record.nodeId());
        InetSocketAddress endpoint = endpointByNodeId.get(record.nodeId());
        return mappedNode != null
            && mappedNode.equals(record.nodeId())
            && mappedVip != null
            && mappedVip.intValue() == record.virtualIp()
            && endpoint != null;
    }

    private int allocateVirtualIp(NodeId nodeId) {
        int rangeSize = vipRangeEnd - vipRangeStart + 1;
        for (int i = 0; i < rangeSize; i++) {
            int candidate = nextVipCandidate.getAndUpdate(current -> current >= vipRangeEnd ? vipRangeStart : current + 1);
            NodeId currentOwner = nodeByVirtualIp.putIfAbsent(candidate, nodeId);
            if (currentOwner == null || currentOwner.equals(nodeId)) {
                return candidate;
            }
        }
        return 0;
    }
}
