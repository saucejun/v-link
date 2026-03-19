package com.vlink.common.protocol;

import io.netty.buffer.ByteBuf;

// ControlMessage 是所有协议消息的统一接口。

public interface ControlMessage {
    MessageType type();

    void encode(ByteBuf out);
}

