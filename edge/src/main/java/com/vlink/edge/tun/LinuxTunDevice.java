package com.vlink.edge.tun;

import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LinuxTunDevice implements TunDevice {
    private static final int O_RDWR = 0x0002;
    private static final int IFF_TUN = 0x0001;
    private static final int IFF_NO_PI = 0x1000;
    private static final long TUNSETIFF = 0x400454caL;

    private final int fd;
    private final String name;
    private final AtomicBoolean closed;

    public LinuxTunDevice(String preferredName) throws IOException {
        this.closed = new AtomicBoolean(false);
        int openedFd = NativeLibC.INSTANCE.open("/dev/net/tun", O_RDWR);
        if (openedFd < 0) {
            throw ioError("open(/dev/net/tun)");
        }

        IfReq ifReq = new IfReq();
        byte[] rawName = preferredName.getBytes(StandardCharsets.US_ASCII);
        int copyLen = Math.min(rawName.length, ifReq.ifr_name.length - 1);
        System.arraycopy(rawName, 0, ifReq.ifr_name, 0, copyLen);
        ifReq.ifr_flags = (short) (IFF_TUN | IFF_NO_PI);
        ifReq.write();

        int rc = NativeLibC.INSTANCE.ioctl(openedFd, TUNSETIFF, ifReq.getPointer());
        if (rc < 0) {
            NativeLibC.INSTANCE.close(openedFd);
            throw ioError("ioctl(TUNSETIFF)");
        }

        ifReq.read();
        this.fd = openedFd;
        this.name = fromCString(ifReq.ifr_name);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        ensureOpen();
        Memory memory = new Memory(buffer.length);
        int read = NativeLibC.INSTANCE.read(fd, memory, buffer.length);
        if (read < 0) {
            throw ioError("read(tun)");
        }
        if (read > 0) {
            memory.read(0, buffer, 0, read);
        }
        return read;
    }

    @Override
    public void write(byte[] packet, int offset, int length) throws IOException {
        ensureOpen();
        if (offset < 0 || length < 0 || offset + length > packet.length) {
            throw new IllegalArgumentException("Invalid packet bounds");
        }
        Memory memory = new Memory(length);
        memory.write(0, packet, offset, length);
        int written = NativeLibC.INSTANCE.write(fd, memory, length);
        if (written < 0) {
            throw ioError("write(tun)");
        }
        if (written != length) {
            throw new IOException("Short write to TUN device: " + written + "/" + length);
        }
    }

    @Override
    public void close() throws IOException {
        if (closed.compareAndSet(false, true)) {
            int rc = NativeLibC.INSTANCE.close(fd);
            if (rc < 0) {
                throw ioError("close(tun)");
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (closed.get()) {
            throw new IOException("TUN device already closed");
        }
    }

    private static IOException ioError(String operation) {
        return new IOException(operation + " failed, errno=" + Native.getLastError());
    }

    private static String fromCString(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(Arrays.copyOf(bytes, len), StandardCharsets.US_ASCII);
    }

    @Structure.FieldOrder({"ifr_name", "ifr_flags", "ifr_pad"})
    public static final class IfReq extends Structure {
        public byte[] ifr_name = new byte[16];
        public short ifr_flags;
        public byte[] ifr_pad = new byte[22];
    }

    private interface NativeLibC extends Library {
        NativeLibC INSTANCE = Native.load("c", NativeLibC.class);

        int open(String path, int flags);

        int ioctl(int fd, long request, Pointer argp);

        int close(int fd);

        int read(int fd, Pointer buffer, int count);

        int write(int fd, Pointer buffer, int count);
    }
}
