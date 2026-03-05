package com.vlink.common.protocol.msg;

import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.MessageType;
import io.netty.buffer.ByteBuf;

public final class RegisterResponse implements ControlMessage {
    public static final byte STATUS_OK = 0;
    public static final byte STATUS_DENIED = 1;

    private final byte status;
    private final int assignedVirtualIp;
    private final int ttlSec;

    public RegisterResponse(byte status, int assignedVirtualIp, int ttlSec) {
        this.status = status;
        this.assignedVirtualIp = assignedVirtualIp;
        this.ttlSec = ttlSec;
    }

    public byte status() {
        return status;
    }

    public int assignedVirtualIp() {
        return assignedVirtualIp;
    }

    public int ttlSec() {
        return ttlSec;
    }

    @Override
    public MessageType type() {
        return MessageType.REGISTER_RESP;
    }

    @Override
    public void encode(ByteBuf out) {
        out.writeByte(status);
        out.writeInt(assignedVirtualIp);
        out.writeShort(ttlSec);
    }

    public static RegisterResponse decode(ByteBuf in) {
        byte status = in.readByte();
        int assignedVirtualIp = in.readInt();
        int ttlSec = in.readUnsignedShort();
        return new RegisterResponse(status, assignedVirtualIp, ttlSec);
    }
}

