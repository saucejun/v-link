package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import com.vlink.common.protocol.NodeId;
import io.netty.buffer.ByteBuf;

// DataPacket 是数据面帧，承载虚拟 IP 包及 seq/ack/flags。

public final class DataPacket implements ControlMessage {
    public static final byte FLAG_ACK = 0x01;
    public static final byte FLAG_RELAY = 0x02;
    public static final byte FLAG_PROBE = 0x04;

    private final NodeId srcNodeId;
    private final NodeId dstNodeId;
    private final int srcVirtualIp;
    private final int dstVirtualIp;
    private final int seq;
    private final int ack;
    private final byte flags;
    private final byte[] payload;

    public DataPacket(
        NodeId srcNodeId,
        NodeId dstNodeId,
        int srcVirtualIp,
        int dstVirtualIp,
        int seq,
        int ack,
        byte flags,
        byte[] payload
    ) {
        this.srcNodeId = srcNodeId;
        this.dstNodeId = dstNodeId;
        this.srcVirtualIp = srcVirtualIp;
        this.dstVirtualIp = dstVirtualIp;
        this.seq = seq;
        this.ack = ack;
        this.flags = flags;
        this.payload = payload == null ? new byte[0] : payload.clone();
    }

    public NodeId srcNodeId() {
        return srcNodeId;
    }

    public NodeId dstNodeId() {
        return dstNodeId;
    }

    public int srcVirtualIp() {
        return srcVirtualIp;
    }

    public int dstVirtualIp() {
        return dstVirtualIp;
    }

    public int seq() {
        return seq;
    }

    public int ack() {
        return ack;
    }

    public byte flags() {
        return flags;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public boolean isAck() {
        return (flags & FLAG_ACK) != 0;
    }

    public boolean isRelay() {
        return (flags & FLAG_RELAY) != 0;
    }

    public boolean isProbe() {
        return (flags & FLAG_PROBE) != 0;
    }

    @Override
    public MessageType type() {
        return MessageType.DATA_PACKET;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeBytes(srcNodeId.toBytes());
        out.writeBytes(dstNodeId.toBytes());
        out.writeInt(srcVirtualIp);
        out.writeInt(dstVirtualIp);
        out.writeInt(seq);
        out.writeInt(ack);
        out.writeByte(flags);
        out.writeShort(payload.length);
        out.writeBytes(payload);
    }

    public static DataPacket decode(ByteBuf in) {
        byte[] srcBytes = new byte[16];
        byte[] dstBytes = new byte[16];
        in.readBytes(srcBytes);
        in.readBytes(dstBytes);
        int srcVirtualIp = in.readInt();
        int dstVirtualIp = in.readInt();
        int seq = in.readInt();
        int ack = in.readInt();
        byte flags = in.readByte();
        int payloadLen = in.readUnsignedShort();
        byte[] payload = new byte[payloadLen];
        in.readBytes(payload);
        return new DataPacket(
            NodeId.fromBytes(srcBytes),
            NodeId.fromBytes(dstBytes),
            srcVirtualIp,
            dstVirtualIp,
            seq,
            ack,
            flags,
            payload
        );
    }
}
