package com.vlink.edge.tun;

import java.io.Closeable;
import java.io.IOException;

public interface TunDevice extends Closeable {
    String name();

    int read(byte[] buffer) throws IOException;

    void write(byte[] packet, int offset, int length) throws IOException;
}
