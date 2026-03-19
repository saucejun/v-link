package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// PunchNotify 告知对端公网地址，帮助双方开始直连探测。

public final class PunchNotify implements ControlMessage {
    private final NodeId peerNodeId;
    private final int peerPublicIp;
    private final int peerPublicPort;
    private final int peerVirtualIp;

    public PunchNotify(NodeId peerNodeId, int peerPublicIp, int peerPublicPort, int peerVirtualIp) {
        this.peerNodeId = peerNodeId;
        this.peerPublicIp = peerPublicIp;
        this.peerPublicPort = peerPublicPort;
        this.peerVirtualIp = peerVirtualIp;
    }

    public NodeId peerNodeId() {
        return peerNodeId;
    }

    public int peerPublicIp() {
        return peerPublicIp;
    }

    public int peerPublicPort() {
        return peerPublicPort;
    }

    public int peerVirtualIp() {
        return peerVirtualIp;
    }

    @Override
    public MessageType type() {
        return MessageType.PUNCH_NOTIFY;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(peerNodeId.toBytes());
        out.writeInt(peerPublicIp);
        out.writeShort(peerPublicPort);
        out.writeInt(peerVirtualIp);
    }

    public static PunchNotify decode(ByteBuf in) {
        byte[] peerBytes = new byte[16];
        in.readBytes(peerBytes);
        int peerPublicIp = in.readInt();
        int peerPublicPort = in.readUnsignedShort();
        int peerVirtualIp = in.readInt();
        return new PunchNotify(NodeId.fromBytes(peerBytes), peerPublicIp, peerPublicPort, peerVirtualIp);
    }
}
