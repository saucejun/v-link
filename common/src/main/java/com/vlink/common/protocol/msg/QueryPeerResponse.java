package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// QueryPeerResponse 返回目标 peer 的公网端点和状态。

public final class QueryPeerResponse implements ControlMessage {
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_NOT_FOUND = 1;
    public static final byte STATUS_OFFLINE = 2;
    public static final byte STATUS_MAPPING_INVALID = 3;

    private final byte status;
    private final NodeId targetNodeId;
    private final int publicIp;
    private final int publicPort;
    private final int targetVirtualIp;
    private final long lastSeenEpochSec;
    private final boolean relayRequired;

    public QueryPeerResponse(
        byte status,
        NodeId targetNodeId,
        int publicIp,
        int publicPort,
        int targetVirtualIp,
        long lastSeenEpochSec,
        boolean relayRequired
    ) {
        this.status = status;
        this.targetNodeId = targetNodeId;
        this.publicIp = publicIp;
        this.publicPort = publicPort;
        this.targetVirtualIp = targetVirtualIp;
        this.lastSeenEpochSec = lastSeenEpochSec;
        this.relayRequired = relayRequired;
    }

    public byte status() {
        return status;
    }

    public NodeId targetNodeId() {
        return targetNodeId;
    }

    public int publicIp() {
        return publicIp;
    }

    public int publicPort() {
        return publicPort;
    }

    public int targetVirtualIp() {
        return targetVirtualIp;
    }

    public long lastSeenEpochSec() {
        return lastSeenEpochSec;
    }

    public boolean relayRequired() {
        return relayRequired;
    }

    @Override
    public MessageType type() {
        return MessageType.QUERY_PEER_RESP;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeByte(status);
        out.writeBytes(targetNodeId.toBytes());
        out.writeInt(publicIp);
        out.writeShort(publicPort);
        out.writeInt(targetVirtualIp);
        out.writeInt((int) lastSeenEpochSec);
        out.writeByte(relayRequired ? 1 : 0);
    }

    public static QueryPeerResponse decode(ByteBuf in) {
        byte status = in.readByte();
        byte[] targetIdBytes = new byte[16];
        in.readBytes(targetIdBytes);
        int publicIp = in.readInt();
        int publicPort = in.readUnsignedShort();
        int targetVirtualIp = in.readInt();
        long lastSeenEpochSec = in.readUnsignedInt();
        boolean relayRequired = in.readByte() != 0;
        return new QueryPeerResponse(
            status,
            NodeId.fromBytes(targetIdBytes),
            publicIp,
            publicPort,
            targetVirtualIp,
            lastSeenEpochSec,
            relayRequired
        );
    }
}
