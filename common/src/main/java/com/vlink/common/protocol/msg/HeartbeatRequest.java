package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

public final class HeartbeatRequest implements ControlMessage {
    private final NodeId nodeId;
    private final long seq;
    private final long rxBytes;
    private final long txBytes;

    public HeartbeatRequest(NodeId nodeId, long seq, long rxBytes, long txBytes) {
        this.nodeId = nodeId;
        this.seq = seq;
        this.rxBytes = rxBytes;
        this.txBytes = txBytes;
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public long seq() {
        return seq;
    }

    @Override
    public MessageType type() {
        return MessageType.HEARTBEAT_REQ;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(nodeId.toBytes());
        out.writeLong(seq);
        out.writeLong(rxBytes);
        out.writeLong(txBytes);
    }

    public static HeartbeatRequest decode(ByteBuf in) {
        byte[] nodeIdBytes = new byte[16];
        in.readBytes(nodeIdBytes);
        NodeId nodeId = NodeId.fromBytes(nodeIdBytes);
        long seq = in.readLong();
        long rxBytes = in.readLong();
        long txBytes = in.readLong();
        return new HeartbeatRequest(nodeId, seq, rxBytes, txBytes);
    }
}

