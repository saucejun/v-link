package com.vlink.common.protocol;

import com.vlink.common.protocol.msg.DataPacket;
import com.vlink.common.protocol.msg.HeartbeatRequest;
import com.vlink.common.protocol.msg.HeartbeatResponse;
import com.vlink.common.protocol.msg.PunchNotify;
import com.vlink.common.protocol.msg.PunchRequest;
import com.vlink.common.protocol.msg.QueryPeerRequest;
import com.vlink.common.protocol.msg.QueryPeerResponse;
import com.vlink.common.protocol.msg.RegisterRequest;
import com.vlink.common.protocol.msg.RegisterResponse;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public final class PacketCodec {
    private PacketCodec() {
    }

    public static byte[] encode(ControlMessage message) {
        ByteBuf out = Unpooled.buffer();
        try {
            out.writeByte(message.type().code());
            message.encode(out);
            byte[] bytes = new byte[out.readableBytes()];
            out.readBytes(bytes);
            return bytes;
        } finally {
            out.release();
        }
    }

    public static ControlMessage decode(byte[] payload) {
        ByteBuf in = Unpooled.wrappedBuffer(payload);
        try {
            MessageType type = MessageType.fromCode(in.readByte());
            switch (type) {
                case REGISTER_REQ:
                    return RegisterRequest.decode(in);
                case REGISTER_RESP:
                    return RegisterResponse.decode(in);
                case HEARTBEAT_REQ:
                    return HeartbeatRequest.decode(in);
                case HEARTBEAT_RESP:
                    return HeartbeatResponse.decode(in);
                case QUERY_PEER_REQ:
                    return QueryPeerRequest.decode(in);
                case QUERY_PEER_RESP:
                    return QueryPeerResponse.decode(in);
                case PUNCH_REQUEST:
                    return PunchRequest.decode(in);
                case PUNCH_NOTIFY:
                    return PunchNotify.decode(in);
                case DATA_PACKET:
                    return DataPacket.decode(in);
                default:
                    throw new IllegalArgumentException("Unsupported type: " + type);
            }
        } finally {
            in.release();
        }
    }
}
