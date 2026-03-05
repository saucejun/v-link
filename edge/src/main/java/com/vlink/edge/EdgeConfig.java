package com.vlink.edge;

import com.vlink.common.protocol.IpCodec;
import com.vlink.common.protocol.NodeId;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class EdgeConfig {
    private final NodeId nodeId;
    private final String nodeName;
    private final String coordinatorHost;
    private final int coordinatorPort;
    private final String bindHost;
    private final int bindPort;
    private final String psk;
    private final int virtualIp;
    private final long heartbeatIntervalSec;
    private final String tunMode;
    private final String tunName;
    private final String mockTunInputFile;
    private final String mockTunOutputFile;
    private final int mtu;
    private final int rtoMs;
    private final int maxRetries;
    private final int probeTimeoutMs;
    private final int relayProbeIntervalMs;
    private final boolean forceRelay;
    private final Map<NodeId, Integer> peerVirtualIps;
    private final Map<Integer, NodeId> vipToPeer;

    public EdgeConfig(
        NodeId nodeId,
        String nodeName,
        String coordinatorHost,
        int coordinatorPort,
        String bindHost,
        int bindPort,
        String psk,
        int virtualIp,
        long heartbeatIntervalSec,
        String tunMode,
        String tunName,
        String mockTunInputFile,
        String mockTunOutputFile,
        int mtu,
        int rtoMs,
        int maxRetries,
        int probeTimeoutMs,
        int relayProbeIntervalMs,
        boolean forceRelay,
        Map<NodeId, Integer> peerVirtualIps
    ) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.coordinatorHost = coordinatorHost;
        this.coordinatorPort = coordinatorPort;
        this.bindHost = bindHost;
        this.bindPort = bindPort;
        this.psk = psk;
        this.virtualIp = virtualIp;
        this.heartbeatIntervalSec = heartbeatIntervalSec;
        this.tunMode = tunMode;
        this.tunName = tunName;
        this.mockTunInputFile = mockTunInputFile;
        this.mockTunOutputFile = mockTunOutputFile;
        this.mtu = mtu;
        this.rtoMs = rtoMs;
        this.maxRetries = maxRetries;
        this.probeTimeoutMs = probeTimeoutMs;
        this.relayProbeIntervalMs = relayProbeIntervalMs;
        this.forceRelay = forceRelay;
        this.peerVirtualIps = Collections.unmodifiableMap(new HashMap<NodeId, Integer>(peerVirtualIps));

        Map<Integer, NodeId> reverse = new HashMap<Integer, NodeId>();
        for (Map.Entry<NodeId, Integer> entry : peerVirtualIps.entrySet()) {
            reverse.put(entry.getValue(), entry.getKey());
        }
        this.vipToPeer = Collections.unmodifiableMap(reverse);
    }

    public NodeId nodeId() {
        return nodeId;
    }

    public String nodeName() {
        return nodeName;
    }

    public String coordinatorHost() {
        return coordinatorHost;
    }

    public int coordinatorPort() {
        return coordinatorPort;
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

    public int virtualIp() {
        return virtualIp;
    }

    public long heartbeatIntervalSec() {
        return heartbeatIntervalSec;
    }

    public String tunMode() {
        return tunMode;
    }

    public String tunName() {
        return tunName;
    }

    public String mockTunInputFile() {
        return mockTunInputFile;
    }

    public String mockTunOutputFile() {
        return mockTunOutputFile;
    }

    public int mtu() {
        return mtu;
    }

    public int rtoMs() {
        return rtoMs;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public int probeTimeoutMs() {
        return probeTimeoutMs;
    }

    public int relayProbeIntervalMs() {
        return relayProbeIntervalMs;
    }

    public boolean forceRelay() {
        return forceRelay;
    }

    public Map<NodeId, Integer> peerVirtualIps() {
        return peerVirtualIps;
    }

    public NodeId peerByVirtualIp(int vip) {
        return vipToPeer.get(vip);
    }

    public Integer virtualIpByPeer(NodeId peerId) {
        return peerVirtualIps.get(peerId);
    }

    public static EdgeConfig fromArgs(String[] args) {
        Map<String, String> flags = parseFlags(args);
        String nodeName = flags.getOrDefault("id", "edge-a");
        NodeId nodeId = NodeId.fromStableString(nodeName);
        String coordinatorHost = flags.getOrDefault("coordinatorHost", "127.0.0.1");
        int coordinatorPort = Integer.parseInt(flags.getOrDefault("coordinatorPort", "40000"));
        String bindHost = flags.getOrDefault("bind", "0.0.0.0");
        int bindPort = Integer.parseInt(flags.getOrDefault("bindPort", "41000"));
        String psk = flags.getOrDefault("psk", "change-this-psk");
        int virtualIp = IpCodec.ipv4ToInt(flags.getOrDefault("virtualIp", "10.10.0.2"));
        long heartbeatIntervalSec = Long.parseLong(flags.getOrDefault("heartbeatSec", "5"));

        String tunMode = flags.getOrDefault("tunMode", "auto");
        String tunName = flags.getOrDefault("tunName", "vlink0");
        String mockTunInputFile = flags.getOrDefault("mockTunIn", "./mock-tun-in.bin");
        String mockTunOutputFile = flags.getOrDefault("mockTunOut", "./mock-tun-out.bin");

        int mtu = Integer.parseInt(flags.getOrDefault("mtu", "1400"));
        int rtoMs = Integer.parseInt(flags.getOrDefault("rtoMs", "700"));
        int maxRetries = Integer.parseInt(flags.getOrDefault("maxRetries", "4"));
        int probeTimeoutMs = Integer.parseInt(flags.getOrDefault("probeTimeoutMs", "4000"));
        int relayProbeIntervalMs = Integer.parseInt(flags.getOrDefault("relayProbeIntervalMs", "15000"));
        boolean forceRelay = Boolean.parseBoolean(flags.getOrDefault("forceRelay", "false"));

        Map<NodeId, Integer> peers = parsePeers(flags.getOrDefault("peers", ""));

        return new EdgeConfig(
            nodeId,
            nodeName,
            coordinatorHost,
            coordinatorPort,
            bindHost,
            bindPort,
            psk,
            virtualIp,
            heartbeatIntervalSec,
            tunMode,
            tunName,
            mockTunInputFile,
            mockTunOutputFile,
            mtu,
            rtoMs,
            maxRetries,
            probeTimeoutMs,
            relayProbeIntervalMs,
            forceRelay,
            peers
        );
    }

    private static Map<NodeId, Integer> parsePeers(String raw) {
        Map<NodeId, Integer> peers = new HashMap<NodeId, Integer>();
        if (raw == null || raw.trim().isEmpty()) {
            return peers;
        }

        String[] entries = raw.split(",");
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid --peers entry: " + trimmed);
            }
            String peerName = parts[0].trim();
            String peerVip = parts[1].trim();
            peers.put(NodeId.fromStableString(peerName), IpCodec.ipv4ToInt(peerVip));
        }
        return peers;
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
