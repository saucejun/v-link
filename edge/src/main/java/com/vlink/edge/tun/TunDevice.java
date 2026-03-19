package com.vlink.edge.tun;

import java.io.Closeable;
import java.io.IOException;

// TunDevice 抽象了 TUN 设备读写接口，屏蔽平台差异。

public interface TunDevice extends Closeable {
    String name();

    int read(byte[] buffer) throws IOException;

    void write(byte[] packet, int offset, int length) throws IOException;
}
