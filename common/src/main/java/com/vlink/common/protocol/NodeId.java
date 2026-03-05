package com.vlink.common.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

public final class NodeId {
    private final UUID uuid;

    public NodeId(UUID uuid) {
        this.uuid = uuid;
    }

    public static NodeId fromBytes(byte[] bytes) {
        if (bytes.length != ProtocolConstants.NODE_ID_LEN) {
            throw new IllegalArgumentException("NodeId bytes length must be 16");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new NodeId(new UUID(buffer.getLong(), buffer.getLong()));
    }

    public static NodeId fromStableString(String raw) {
        return new NodeId(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)));
    }

    public static NodeId fromUuidString(String raw) {
        return new NodeId(UUID.fromString(raw));
    }

    public UUID asUuid() {
        return uuid;
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.NODE_ID_LEN);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public String shortText() {
        String full = uuid.toString().replace("-", "");
        return full.substring(0, 12);
    }

    @Override
    public String toString() {
        return uuid.toString();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NodeId)) {
            return false;
        }
        NodeId nodeId = (NodeId) other;
        return Arrays.equals(toBytes(), nodeId.toBytes());
    }

    @Override
    public int hashCode() {
        return uuid.hashCode();
    }
}

