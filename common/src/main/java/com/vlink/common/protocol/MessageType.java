package com.vlink.common.protocol;

public enum MessageType {
    REGISTER_REQ((byte) 1),
    REGISTER_RESP((byte) 2),
    HEARTBEAT_REQ((byte) 3),
    HEARTBEAT_RESP((byte) 4),
    QUERY_PEER_REQ((byte) 5),
    QUERY_PEER_RESP((byte) 6),
    PUNCH_REQUEST((byte) 7),
    PUNCH_NOTIFY((byte) 8),
    DATA_PACKET((byte) 9);

    private final byte code;

    MessageType(byte code) {
        this.code = code;
    }

    public byte code() {
        return code;
    }

    public static MessageType fromCode(byte code) {
        for (MessageType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown message type: " + code);
    }
}
