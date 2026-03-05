package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

public final class RegisterRequest implements ControlMessage {
    private final NodeId nodeId;
    private final int virtualIp;
    private final int listenPort;

    public RegisterRequest(NodeId nodeId, int virtualIp, int listenPort) {
        this.nodeId = nodeId;
        this.virtualIp = virtualIp;
        this.listenPort = listenPort;
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public int virtualIp() {
        return virtualIp;
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
        out.writeInt(virtualIp);
        out.writeShort(listenPort);
    }

    public static RegisterRequest decode(ByteBuf in) {
        byte[] nodeIdBytes = new byte[16];
        in.readBytes(nodeIdBytes);
        NodeId nodeId = NodeId.fromBytes(nodeIdBytes);
        int virtualIp = in.readInt();
        int listenPort = in.readUnsignedShort();
        return new RegisterRequest(nodeId, virtualIp, listenPort);
    }
}

