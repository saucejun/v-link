package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// RegisterRequest 是边缘节点向协调节点发起注册的请求消息。

public final class RegisterRequest implements ControlMessage {
    private final NodeId nodeId;
    private final int listenPort;

    public RegisterRequest(NodeId nodeId, int listenPort) {
        this.nodeId = nodeId;
        this.listenPort = listenPort;
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public int listenPort() {
        return listenPort;
    }

    @Override
    public MessageType type() {
        return MessageType.REGISTER_REQ;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(nodeId.toBytes());
        out.writeShort(listenPort);
    }

    public static RegisterRequest decode(ByteBuf in) {
        byte[] nodeIdBytes = new byte[16];
        in.readBytes(nodeIdBytes);
        NodeId nodeId = NodeId.fromBytes(nodeIdBytes);
        int listenPort = in.readUnsignedShort();
        return new RegisterRequest(nodeId, listenPort);
    }
}
