package com.vlink.common.protocol;

public final class ProtocolConstants {
    public static final short MAGIC = (short) 0x564c; // "VL"
    public static final byte VERSION = 1;
    public static final int NONCE_LEN = 12;
    public static final int NODE_ID_LEN = 16;

    private ProtocolConstants() {
    }
}
