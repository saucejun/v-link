package com.vlink.coordinator;

import com.vlink.common.protocol.IpCodec;
import com.vlink.common.protocol.NodeId;
import java.util.HashMap;
import java.util.Map;

// CoordinatorConfig 负责保存协调节点启动参数，
// 并支持从命令行参数解析出配置对象。
// CoordinatorConfig 保存协调节点运行参数并支持命令行解析。
public final class CoordinatorConfig {
    // UDP 监听地址，例如 0.0.0.0
    private final String bindHost;
    // UDP 监听端口
    private final int bindPort;
    // 预共享密钥（用于加密封包）
    private final String psk;
    // 协调节点自己的稳定 nodeId
    private final NodeId coordinatorId;
    // 节点离线超时时间（秒）
    private final int peerTimeoutSec;
    // 定时清理间隔（秒）
    private final int cleanupIntervalSec;
    // 自动分配虚拟 IP 池范围（闭区间）
    private final int vipPoolStart;
    private final int vipPoolEnd;

    public CoordinatorConfig(
        String bindHost,
        int bindPort,
        String psk,
        int peerTimeoutSec,
        int cleanupIntervalSec,
        int vipPoolStart,
        int vipPoolEnd
    ) {
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.psk = psk;
        this.peerTimeoutSec = peerTimeoutSec;
        this.cleanupIntervalSec = cleanupIntervalSec;
        this.vipPoolStart = vipPoolStart;
        this.vipPoolEnd = vipPoolEnd;
        this.coordinatorId = NodeId.fromStableString("coordinator@" + bindHost + ":" + bindPort);
    }

    public String bindHost() {
        return bindHost;
    }

    public int bindPort() {
        return bindPort;
    }

    public String psk() {
        return psk;
    }

    public NodeId coordinatorId() {
        return coordinatorId;
    }

    public int peerTimeoutSec() {
        return peerTimeoutSec;
    }

    public int cleanupIntervalSec() {
        return cleanupIntervalSec;
    }

    public int vipPoolStart() {
        return vipPoolStart;
    }

    public int vipPoolEnd() {
        return vipPoolEnd;
    }

    // 从 --key value 风格参数中构建配置。
    public static CoordinatorConfig fromArgs(String[] args) {
        Map<String, String> flags = parseFlags(args);
        String bindHost = flags.getOrDefault("bind", "0.0.0.0");
        int bindPort = Integer.parseInt(flags.getOrDefault("port", "40000"));
        String psk = flags.getOrDefault("psk", "change-this-psk");
        int peerTimeoutSec = Integer.parseInt(flags.getOrDefault("peerTimeoutSec", "30"));
        int cleanupIntervalSec = Integer.parseInt(flags.getOrDefault("cleanupIntervalSec", "5"));
        int vipPoolStart = IpCodec.ipv4ToInt(flags.getOrDefault("vipPoolStart", "10.10.0.2"));
        int vipPoolEnd = IpCodec.ipv4ToInt(flags.getOrDefault("vipPoolEnd", "10.10.255.254"));
        return new CoordinatorConfig(
            bindHost,
            bindPort,
            psk,
            peerTimeoutSec,
            cleanupIntervalSec,
            vipPoolStart,
            vipPoolEnd
        );
    }

    // 简单参数解析：支持 --key value；无 value 时默认 true。
    private static Map<String, String> parseFlags(String[] args) {
        Map<String, String> map = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!arg.startsWith("--")) {
                continue;
            }
            String key = arg.substring(2);
            String value = "true";
            if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                value = args[i + 1];
                i++;
            }
            map.put(key, value);
        }
        return map;
    }
}
