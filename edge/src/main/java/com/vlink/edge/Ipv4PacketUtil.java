package com.vlink.edge;

// Ipv4PacketUtil 提供最小 IPv4 包解析能力（当前只用到目标地址）。

public final class Ipv4PacketUtil {
    private Ipv4PacketUtil() {
    }

    public static boolean isIpv4(byte[] packet) {
        if (packet == null || packet.length < 20) {
            return false;
        }
        int version = (packet[0] >> 4) & 0x0f;
        return version == 4;
    }

    public static int destinationIp(byte[] packet) {
        if (!isIpv4(packet)) {
            throw new IllegalArgumentException("Not a valid IPv4 packet");
        }
        return ((packet[16] & 0xff) << 24)
            | ((packet[17] & 0xff) << 16)
            | ((packet[18] & 0xff) << 8)
            | (packet[19] & 0xff);
    }
}
