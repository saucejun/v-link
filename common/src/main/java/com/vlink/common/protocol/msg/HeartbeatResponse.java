package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;

// HeartbeatResponse 是协调节点对心跳的确认消息。

public final class HeartbeatResponse implements ControlMessage {
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_UNKNOWN = 1;

    private final byte status;
    private final long seqEcho;
    private final int ttlSec;

    public HeartbeatResponse(byte status, long seqEcho, int ttlSec) {
        this.status = status;
        this.seqEcho = seqEcho;
        this.ttlSec = ttlSec;
    }

    public byte status() {
        return status;
    }

    public long seqEcho() {
        return seqEcho;
    }

    @Override
    public MessageType type() {
        return MessageType.HEARTBEAT_RESP;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeByte(status);
        out.writeLong(seqEcho);
        out.writeShort(ttlSec);
    }

    public static HeartbeatResponse decode(ByteBuf in) {
        byte status = in.readByte();
        long seqEcho = in.readLong();
        int ttlSec = in.readUnsignedShort();
        return new HeartbeatResponse(status, seqEcho, ttlSec);
    }
}

