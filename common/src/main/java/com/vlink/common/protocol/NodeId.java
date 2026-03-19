package com.vlink.common.protocol;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

// NodeId 封装节点标识，统一 UUID 与字节表示的转换。

public final class NodeId {
    private final UUID uuid;

    public NodeId(UUID uuid) {
        this.uuid = uuid;
    }

    public static NodeId fromBytes(byte[] bytes) {  // 从 16 字节数组创建 NodeId。
        if (bytes.length != ProtocolConstants.NODE_ID_LEN) {
            throw new IllegalArgumentException("NodeId bytes length must be 16");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes); 
        return new NodeId(new UUID(buffer.getLong(), buffer.getLong()));
    }

    public static NodeId fromStableString(String raw) { // 从任意字符串创建稳定的 NodeId（通过 UUID5）。
        return new NodeId(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)));
    }

    public static NodeId fromUuidString(String raw) {   // 从标准 UUID 字符串创建 NodeId。
        return new NodeId(UUID.fromString(raw));
    }

    public UUID asUuid() {  
        return uuid;
    }

    public byte[] toBytes() {   // 转换 NodeId 为 16 字节数组。
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolConstants.NODE_ID_LEN);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public String shortText() { // 获取 NodeId 的短文本表示（UUID 去掉连字符后的前 12 字符）。
        String full = uuid.toString().replace("-", "");
        return full.substring(0, 12);
    }

    @Override
    public String toString() {  // 获取 NodeId 的标准 UUID 字符串表示。
        return uuid.toString();
    }

    @Override
    public boolean equals(Object other) {   // NodeId 相等性基于字节表示的完全相等。
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

