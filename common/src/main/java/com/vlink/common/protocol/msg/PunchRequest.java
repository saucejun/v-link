package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// PunchRequest 请求协调节点通知双方进行 NAT 打洞。

public final class PunchRequest implements ControlMessage {
    private final NodeId requesterId;
    private final NodeId targetNodeId;

    public PunchRequest(NodeId requesterId, NodeId targetNodeId) {
        this.requesterId = requesterId;
        this.targetNodeId = targetNodeId;
    }

    public NodeId requesterId() {
        return requesterId;
    }

    public NodeId targetNodeId() {
        return targetNodeId;
    }

    @Override
    public MessageType type() {
        return MessageType.PUNCH_REQUEST;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(requesterId.toBytes());
        out.writeBytes(targetNodeId.toBytes());
    }

    public static PunchRequest decode(ByteBuf in) {
        byte[] requester = new byte[16];
        byte[] target = new byte[16];
        in.readBytes(requester);
        in.readBytes(target);
        return new PunchRequest(NodeId.fromBytes(requester), NodeId.fromBytes(target));
    }
}
