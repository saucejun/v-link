package com.vlink.common.protocol;

import java.net.InetAddress;
import java.net.UnknownHostException;

// IpCodec 提供 IPv4 字符串、整数与 InetAddress 之间的转换。

public final class IpCodec {
    private IpCodec() {
    }

    public static int ipv4ToInt(String ipv4) {
        String[] segments = ipv4.split("\\.");
        if (segments.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4: " + ipv4);
        }
        int value = 0;
        for (String segment : segments) {
            int parsed = Integer.parseInt(segment);
            value = (value << 8) | (parsed & 0xff);
        }
        return value;
    }

    public static String intToIpv4(int value) {
        return (value >> 24 & 0xff) + "." + (value >> 16 & 0xff) + "." + (value >> 8 & 0xff) + "." + (value & 0xff);
    }

    public static int inetAddressToInt(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Only IPv4 is supported in this prototype");
        }
        return ((bytes[0] & 0xff) << 24)
            | ((bytes[1] & 0xff) << 16)
            | ((bytes[2] & 0xff) << 8)
            | (bytes[3] & 0xff);
    }

    public static InetAddress intToInetAddress(int value) {
        byte[] bytes = new byte[] {
            (byte) ((value >> 24) & 0xff),
            (byte) ((value >> 16) & 0xff),
            (byte) ((value >> 8) & 0xff),
            (byte) (value & 0xff)
        };
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException ex) {
            throw new IllegalStateException("Invalid IPv4 bytes", ex);
        }
    }
}

