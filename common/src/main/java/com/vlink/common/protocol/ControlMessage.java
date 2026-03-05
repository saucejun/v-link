package com.vlink.common.protocol;

import io.netty.buffer.ByteBuf;

public interface ControlMessage {
    MessageType type();

    void encode(ByteBuf out);
}

