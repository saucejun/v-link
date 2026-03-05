package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

public final class QueryPeerResponse implements ControlMessage {
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_NOT_FOUND = 1;

    private final byte status;
    private final NodeId targetId;
    private final int publicIp;
    private final int publicPort;
    private final int virtualIp;
    private final long lastSeenEpochSec;
    private final boolean relayRequired;

    public QueryPeerResponse(
        byte status,
        NodeId targetId,
        int publicIp,
        int publicPort,
        int virtualIp,
        long lastSeenEpochSec,
        boolean relayRequired
    ) {
        this.status = status;
        this.targetId = targetId;
        this.publicIp = publicIp;
        this.publicPort = publicPort;
        this.virtualIp = virtualIp;
        this.lastSeenEpochSec = lastSeenEpochSec;
        this.relayRequired = relayRequired;
    }

    public byte status() {
        return status;
    }

    public NodeId targetId() {
        return targetId;
    }

    public int publicIp() {
        return publicIp;
    }

    public int publicPort() {
        return publicPort;
    }

    public int virtualIp() {
        return virtualIp;
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
        out.writeBytes(targetId.toBytes());
        out.writeInt(publicIp);
        out.writeShort(publicPort);
        out.writeInt(virtualIp);
        out.writeInt((int) lastSeenEpochSec);
        out.writeByte(relayRequired ? 1 : 0);
    }

    public static QueryPeerResponse decode(ByteBuf in) {
        byte status = in.readByte();
        byte[] targetIdBytes = new byte[16];
        in.readBytes(targetIdBytes);
        int publicIp = in.readInt();
        int publicPort = in.readUnsignedShort();
        int virtualIp = in.readInt();
        long lastSeenEpochSec = in.readUnsignedInt();
        boolean relayRequired = in.readByte() != 0;
        return new QueryPeerResponse(
            status,
            NodeId.fromBytes(targetIdBytes),
            publicIp,
            publicPort,
            virtualIp,
            lastSeenEpochSec,
            relayRequired
        );
    }
}

