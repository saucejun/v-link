package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// QueryPeerRequest 用于查询目标 peer 的公网地址信息。

public final class QueryPeerRequest implements ControlMessage {
    private final NodeId requesterId;
    private final NodeId targetNodeId;

    public QueryPeerRequest(NodeId requesterId, NodeId targetNodeId) {
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
        return MessageType.QUERY_PEER_REQ;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(requesterId.toBytes());
        out.writeBytes(targetNodeId.toBytes());
    }

    public static QueryPeerRequest decode(ByteBuf in) {
        byte[] requester = new byte[16];
        byte[] target = new byte[16];
        in.readBytes(requester);
        in.readBytes(target);
        return new QueryPeerRequest(NodeId.fromBytes(requester), NodeId.fromBytes(target));
    }
}
