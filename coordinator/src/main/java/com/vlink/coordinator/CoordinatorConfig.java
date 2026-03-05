package com.vlink.coordinator;

import com.vlink.common.protocol.NodeId;
import java.util.HashMap;
import java.util.Map;

public final class CoordinatorConfig {
    private final String bindHost;
    private final int bindPort;
    private final String psk;
    private final NodeId coordinatorId;
    private final int peerTimeoutSec;
    private final int cleanupIntervalSec;

    public CoordinatorConfig(String bindHost, int bindPort, String psk, int peerTimeoutSec, int cleanupIntervalSec) {
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.psk = psk;
        this.peerTimeoutSec = peerTimeoutSec;
        this.cleanupIntervalSec = cleanupIntervalSec;
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

    public static CoordinatorConfig fromArgs(String[] args) {
        Map<String, String> flags = parseFlags(args);
        String bindHost = flags.getOrDefault("bind", "0.0.0.0");
        int bindPort = Integer.parseInt(flags.getOrDefault("port", "40000"));
        String psk = flags.getOrDefault("psk", "change-this-psk");
        int peerTimeoutSec = Integer.parseInt(flags.getOrDefault("peerTimeoutSec", "30"));
        int cleanupIntervalSec = Integer.parseInt(flags.getOrDefault("cleanupIntervalSec", "5"));
        return new CoordinatorConfig(bindHost, bindPort, psk, peerTimeoutSec, cleanupIntervalSec);
    }

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
