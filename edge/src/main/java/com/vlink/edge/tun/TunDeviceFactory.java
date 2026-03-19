package com.vlink.edge.tun;

import com.vlink.edge.EdgeConfig;
import java.io.IOException;

// TunDeviceFactory 按运行模式创建 Linux 真 TUN 或 Mock TUN。

public final class TunDeviceFactory {
    private TunDeviceFactory() {
    }

    public static TunDevice create(EdgeConfig config) throws IOException {
        String mode = config.tunMode().toLowerCase();
        if ("auto".equals(mode)) {
            mode = isLinux() ? "linux" : "mock";
        }

        if ("linux".equals(mode)) {
            return new LinuxTunDevice(config.tunName());
        }
        
        throw new IllegalArgumentException("Unsupported tun mode: " + config.tunMode());
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux");
    }
}
