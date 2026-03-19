package com.vlink.edge;

import com.vlink.common.net.EventLoopResources;
import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.EnvelopeCodec;
import com.vlink.common.protocol.EnvelopeCodec.DecodedEnvelope;
import com.vlink.common.protocol.IpCodec;
import com.vlink.common.protocol.NodeId;
import com.vlink.common.protocol.msg.DataPacket;
import com.vlink.common.protocol.msg.HeartbeatRequest;
import com.vlink.common.protocol.msg.HeartbeatResponse;
import com.vlink.common.protocol.msg.PunchNotify;
import com.vlink.common.protocol.msg.PunchRequest;
import com.vlink.common.protocol.msg.QueryPeerRequest;
import com.vlink.common.protocol.msg.QueryPeerResponse;
import com.vlink.common.protocol.msg.RegisterRequest;
import com.vlink.common.protocol.msg.RegisterResponse;
import com.vlink.edge.tun.TunDevice;
import com.vlink.edge.tun.TunDeviceFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramPacket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class EdgeClient {
    private static final int RETRANSMIT_TICK_MS = 200;
    private static final long VIP_RESOLVE_THROTTLE_MS = 1000;

    private final EdgeConfig config;
    private final EventLoopResources eventLoopResources;
    private final AtomicLong heartbeatSeq;
    private final InetSocketAddress coordinatorAddress;
    private final Map<NodeId, PeerSession> sessions;
    private final Map<Integer, NodeId> peerByVirtualIp;
    private final Map<NodeId, Integer> virtualIpByPeer;
    private final Map<Integer, Long> vipResolveRequestedAtMs;
    private final DedupCache dedupCache;

    private volatile boolean running;
    private volatile int assignedVirtualIp;
    private Channel channel;
    private TunDevice tunDevice;
    private Thread tunReaderThread;

    public EdgeClient(EdgeConfig config) {
        this.config = config;
        this.eventLoopResources = EventLoopResources.create(1);
        this.heartbeatSeq = new AtomicLong(1);
        this.coordinatorAddress = new InetSocketAddress(config.coordinatorHost(), config.coordinatorPort());
        this.sessions = new HashMap<NodeId, PeerSession>();
        this.peerByVirtualIp = new HashMap<Integer, NodeId>();
        this.virtualIpByPeer = new HashMap<NodeId, Integer>();
        this.vipResolveRequestedAtMs = new HashMap<Integer, Long>();
        this.dedupCache = new DedupCache();
        this.assignedVirtualIp = 0;
    }

    public void start() throws Exception {
        this.tunDevice = TunDeviceFactory.create(config);
        this.running = true;

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopResources.eventLoopGroup())
            .channel(eventLoopResources.channelClass())
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new EdgeInboundHandler());
                }
            });

        ChannelFuture bound = bootstrap.bind(config.bindHost(), config.bindPort()).sync();
        this.channel = bound.channel();

        for (NodeId knownPeer : config.knownPeers()) {
            sessions.put(knownPeer, new PeerSession(knownPeer, 0, config.forceRelay()));
        }

        System.out.println("[edge] started node=" + config.nodeName()
            + " bind=" + config.bindHost() + ":" + config.bindPort()
            + " tun=" + tunDevice.name()
            + " io=" + (eventLoopResources.epollEnabled() ? "epoll" : "nio")
            + " forceRelay=" + config.forceRelay());

        sendToCoordinator(new RegisterRequest(config.nodeId(), config.bindPort()));
        scheduleHeartbeat();
        scheduleStateTick();
        startTunReader();
    }

    public void block() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
        }
    }

    public void stop() {
        running = false;
        if (channel != null) {
            channel.close();
        }
        if (tunReaderThread != null) {
            tunReaderThread.interrupt();
        }
        if (tunDevice != null) {
            try {
                tunDevice.close();
            } catch (IOException ex) {
                System.err.println("[edge] failed to close tun: " + ex.getMessage());
            }
        }
        eventLoopResources.eventLoopGroup().shutdownGracefully();
    }

    private void scheduleHeartbeat() {
        channel.eventLoop().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long seq = heartbeatSeq.getAndIncrement();
                sendToCoordinator(new HeartbeatRequest(config.nodeId(), seq, 0, 0));
            }
        }, config.heartbeatIntervalSec(), config.heartbeatIntervalSec(), TimeUnit.SECONDS);
    }

    private void scheduleStateTick() {
        channel.eventLoop().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long nowMs = System.currentTimeMillis();
                for (PeerSession session : sessions.values()) {
                    driveProbe(session, nowMs);
                    driveRetransmit(session, nowMs);
                    trySendNext(session, nowMs);
                }
            }
        }, RETRANSMIT_TICK_MS, RETRANSMIT_TICK_MS, TimeUnit.MILLISECONDS);
    }

    private void startTunReader() {
        tunReaderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[Math.max(config.mtu() + 128, 2048)];
                while (running) {
                    try {
                        int read = tunDevice.read(buffer);
                        if (read <= 0) {
                            continue;
                        }
                        byte[] packet = Arrays.copyOf(buffer, read);
                        channel.eventLoop().execute(new Runnable() {
                            @Override
                            public void run() {
                                onTunPacket(packet);
                            }
                        });
                    } catch (IOException ex) {
                        if (running) {
                            System.err.println("[edge] tun read failed: " + ex.getMessage());
                        }
                        return;
                    }
                }
            }
        }, "tun-reader-" + config.nodeName());
        tunReaderThread.setDaemon(true);
        tunReaderThread.start();
    }

    private void onTunPacket(byte[] packet) {
        if (!Ipv4PacketUtil.isIpv4(packet)) {
            return;
        }
        if (assignedVirtualIp == 0) {
            return;
        }

        int dstVip = Ipv4PacketUtil.destinationIp(packet);
        NodeId targetPeer = peerByVirtualIp.get(dstVip);
        if (targetPeer == null) {
            requestVipResolve(dstVip, System.currentTimeMillis());
            return;
        }

        PeerSession session = sessionFor(targetPeer, dstVip);
        session.outboundQueue.offer(packet);

        if (session.mode == PeerSession.LinkMode.INIT && !config.forceRelay()) {
            beginProbe(session, true, System.currentTimeMillis());
        }
        trySendNext(session, System.currentTimeMillis());
    }

    private void requestVipResolve(int dstVip, long nowMs) {
        Long last = vipResolveRequestedAtMs.get(dstVip);
        if (last != null && nowMs - last.longValue() < VIP_RESOLVE_THROTTLE_MS) {
            return;
        }
        vipResolveRequestedAtMs.put(dstVip, nowMs);

        Set<NodeId> candidates = new HashSet<NodeId>();
        candidates.addAll(config.knownPeers());
        candidates.addAll(sessions.keySet());
        candidates.addAll(virtualIpByPeer.keySet());

        if (candidates.isEmpty()) {
            System.err.println("[edge] no known peers to resolve destination vip=" + IpCodec.intToIpv4(dstVip));
            return;
        }

        for (NodeId targetNodeId : candidates) {
            sendToCoordinator(new QueryPeerRequest(config.nodeId(), targetNodeId));
        }
    }

    private PeerSession sessionFor(NodeId peerId, int peerVirtualIp) {
        PeerSession session = sessions.get(peerId);
        if (session == null) {
            session = new PeerSession(peerId, peerVirtualIp, config.forceRelay());
            sessions.put(peerId, session);
        }
        if (peerVirtualIp > 0) {
            cachePeerVirtualIp(peerId, peerVirtualIp);
        }
        return session;
    }

    private void cachePeerVirtualIp(NodeId peerId, int peerVirtualIp) {
        if (peerVirtualIp <= 0) {
            return;
        }
        Integer previousVip = virtualIpByPeer.put(peerId, peerVirtualIp);
        if (previousVip != null && previousVip.intValue() != peerVirtualIp) {
            peerByVirtualIp.remove(previousVip, peerId);
        }

        NodeId previousPeer = peerByVirtualIp.put(peerVirtualIp, peerId);
        if (previousPeer != null && !previousPeer.equals(peerId)) {
            virtualIpByPeer.remove(previousPeer, peerVirtualIp);
            System.err.println("[edge] vip mapping replaced vip=" + IpCodec.intToIpv4(peerVirtualIp)
                + " oldPeer=" + previousPeer.shortText()
                + " newPeer=" + peerId.shortText());
        }

        vipResolveRequestedAtMs.remove(peerVirtualIp);
        PeerSession session = sessions.get(peerId);
        if (session != null) {
            session.peerVirtualIp = peerVirtualIp;
        }
    }

    private void beginProbe(PeerSession session, boolean strictMode, long nowMs) {
        if (config.forceRelay()) {
            return;
        }
        if (nowMs - session.lastQueryAtMs < 1000) {
            return;
        }

        session.lastQueryAtMs = nowMs;
        if (session.mode == PeerSession.LinkMode.INIT) {
            session.mode = PeerSession.LinkMode.PROBING;
        }
        if (session.mode == PeerSession.LinkMode.RELAY && strictMode) {
            session.mode = PeerSession.LinkMode.PROBING;
        }
        if (session.mode == PeerSession.LinkMode.PROBING && session.probingStartedAtMs == 0) {
            session.probingStartedAtMs = nowMs;
        }

        sendToCoordinator(new QueryPeerRequest(config.nodeId(), session.peerId));
        sendToCoordinator(new PunchRequest(config.nodeId(), session.peerId));

        if (session.hasEndpoint()) {
            sendProbe(session, nowMs);
        }
    }

    private void sendProbe(PeerSession session, long nowMs) {
        if (assignedVirtualIp == 0 || !session.hasEndpoint()) {
            return;
        }
        DataPacket probe = new DataPacket(
            config.nodeId(),
            session.peerId,
            assignedVirtualIp,
            session.peerVirtualIp,
            0,
            0,
            DataPacket.FLAG_PROBE,
            new byte[0]
        );
        sendDirect(probe, session.publicEndpoint);
        session.lastProbeSentAtMs = nowMs;
    }

    private void driveProbe(PeerSession session, long nowMs) {
        if (config.forceRelay()) {
            session.mode = PeerSession.LinkMode.RELAY;
            return;
        }

        if (session.mode == PeerSession.LinkMode.PROBING) {
            if (session.probingStartedAtMs == 0) {
                session.probingStartedAtMs = nowMs;
            }
            if (session.hasEndpoint() && nowMs - session.lastProbeSentAtMs >= 600) {
                sendProbe(session, nowMs);
            }
            if (nowMs - session.probingStartedAtMs >= config.probeTimeoutMs()) {
                session.probingStartedAtMs = 0;
                switchToRelay(session, "probe-timeout");
            }
            return;
        }

        if (session.mode == PeerSession.LinkMode.RELAY && nowMs - session.lastQueryAtMs >= config.relayProbeIntervalMs()) {
            beginProbe(session, false, nowMs);
        }
    }

    private void driveRetransmit(PeerSession session, long nowMs) {
        PeerSession.Inflight inflight = session.inflight;
        if (inflight == null) {
            return;
        }
        if (nowMs - inflight.lastSentAtMs < config.rtoMs()) {
            return;
        }

        if (inflight.retries < config.maxRetries()) {
            inflight.retries++;
            resendInflight(session, inflight, nowMs);
            return;
        }

        if (!inflight.viaRelay) {
            switchToRelay(session, "ack-timeout");
            DataPacket relayFrame = new DataPacket(
                inflight.packet.srcNodeId(),
                inflight.packet.dstNodeId(),
                inflight.packet.srcVirtualIp(),
                inflight.packet.dstVirtualIp(),
                inflight.packet.seq(),
                inflight.packet.ack(),
                (byte) (inflight.packet.flags() | DataPacket.FLAG_RELAY),
                inflight.packet.payload()
            );
            session.inflight = new PeerSession.Inflight(relayFrame, true, nowMs);
            sendRelay(relayFrame);
            System.out.println("[edge] TX RELAY seq=" + relayFrame.seq() + " dstNode=" + relayFrame.dstNodeId().shortText());
            return;
        }

        session.outboundQueue.poll();
        session.inflight = null;
        System.out.println("[edge] drop packet after retries peer=" + session.peerId.shortText());
    }

    private void resendInflight(PeerSession session, PeerSession.Inflight inflight, long nowMs) {
        inflight.lastSentAtMs = nowMs;
        if (inflight.viaRelay || session.mode == PeerSession.LinkMode.RELAY) {
            sendRelay(inflight.packet);
            return;
        }
        if (session.publicEndpoint != null) {
            sendDirect(inflight.packet, session.publicEndpoint);
            return;
        }
        switchToRelay(session, "endpoint-missing");
        DataPacket relay = new DataPacket(
            inflight.packet.srcNodeId(),
            inflight.packet.dstNodeId(),
            inflight.packet.srcVirtualIp(),
            inflight.packet.dstVirtualIp(),
            inflight.packet.seq(),
            inflight.packet.ack(),
            (byte) (inflight.packet.flags() | DataPacket.FLAG_RELAY),
            inflight.packet.payload()
        );
        session.inflight = new PeerSession.Inflight(relay, true, nowMs);
        sendRelay(relay);
    }

    private void trySendNext(PeerSession session, long nowMs) {
        if (session.inflight != null || session.outboundQueue.isEmpty()) {
            return;
        }
        if (assignedVirtualIp == 0 || session.peerVirtualIp == 0) {
            beginProbe(session, true, nowMs);
            return;
        }

        boolean viaRelay;
        if (config.forceRelay()) {
            session.mode = PeerSession.LinkMode.RELAY;
            viaRelay = true;
        } else if (session.mode == PeerSession.LinkMode.DIRECT && session.hasEndpoint()) {
            viaRelay = false;
        } else if (session.mode == PeerSession.LinkMode.RELAY) {
            viaRelay = true;
        } else if (session.mode == PeerSession.LinkMode.PROBING && session.hasEndpoint()) {
            viaRelay = false;
        } else {
            beginProbe(session, true, nowMs);
            return;
        }

        byte[] payload = session.outboundQueue.peek();
        int seq = session.nextSeq++;
        byte flags = viaRelay ? DataPacket.FLAG_RELAY : 0;
        DataPacket frame = new DataPacket(
            config.nodeId(),
            session.peerId,
            assignedVirtualIp,
            session.peerVirtualIp,
            seq,
            0,
            flags,
            payload
        );

        session.inflight = new PeerSession.Inflight(frame, viaRelay, nowMs);

        if (viaRelay) {
            sendRelay(frame);
            System.out.println("[edge] TX RELAY seq=" + seq + " dstNode=" + session.peerId.shortText());
        } else {
            sendDirect(frame, session.publicEndpoint);
            System.out.println("[edge] TX DIRECT seq=" + seq + " dstNode=" + session.peerId.shortText());
        }
    }

    private void onQueryPeerResponse(QueryPeerResponse response, long nowMs) {
        if (response.status() != QueryPeerResponse.STATUS_OK) {
            System.err.println("[edge] query target=" + response.targetNodeId().shortText()
                + " status=" + queryStatusText(response.status()));
            return;
        }

        cachePeerVirtualIp(response.targetNodeId(), response.targetVirtualIp());
        PeerSession session = sessionFor(response.targetNodeId(), response.targetVirtualIp());
        session.publicEndpoint = new InetSocketAddress(IpCodec.intToInetAddress(response.publicIp()), response.publicPort());

        if (!config.forceRelay()) {
            if (session.mode == PeerSession.LinkMode.INIT) {
                session.mode = PeerSession.LinkMode.PROBING;
            }
            if (session.mode == PeerSession.LinkMode.RELAY && session.probingStartedAtMs == 0) {
                session.probingStartedAtMs = nowMs;
            }
            if (session.mode == PeerSession.LinkMode.PROBING && session.probingStartedAtMs == 0) {
                session.probingStartedAtMs = nowMs;
            }
            sendProbe(session, nowMs);
        }
    }

    private void onPunchNotify(PunchNotify notify, long nowMs) {
        cachePeerVirtualIp(notify.peerNodeId(), notify.peerVirtualIp());
        PeerSession session = sessionFor(notify.peerNodeId(), notify.peerVirtualIp());
        session.publicEndpoint = new InetSocketAddress(IpCodec.intToInetAddress(notify.peerPublicIp()), notify.peerPublicPort());

        if (!config.forceRelay()) {
            if (session.mode == PeerSession.LinkMode.INIT || session.mode == PeerSession.LinkMode.RELAY) {
                session.mode = PeerSession.LinkMode.PROBING;
            }
            if (session.probingStartedAtMs == 0) {
                session.probingStartedAtMs = nowMs;
            }
            sendProbe(session, nowMs);
        }
    }

    private void onDataPacket(InetSocketAddress sender, DataPacket packet, long nowMs) {
        boolean dstNodeMatched = packet.dstNodeId().equals(config.nodeId());
        boolean dstVipMatched = assignedVirtualIp != 0 && packet.dstVirtualIp() == assignedVirtualIp;
        if (!dstNodeMatched && !dstVipMatched) {
            return;
        }

        cachePeerVirtualIp(packet.srcNodeId(), packet.srcVirtualIp());
        PeerSession session = sessionFor(packet.srcNodeId(), packet.srcVirtualIp());

        if (!packet.isRelay()) {
            session.publicEndpoint = sender;
            if (!config.forceRelay()) {
                switchToDirect(session, sender, "direct-rx");
            }
        }

        if (packet.isAck()) {
            onAckPacket(session, packet, sender, nowMs);
            return;
        }

        if (packet.isProbe()) {
            sendAck(session, packet.seq(), packet.isRelay(), sender);
            return;
        }

        if (!dedupCache.seen(packet.srcNodeId(), packet.seq())) {
            try {
                byte[] payload = packet.payload();
                tunDevice.write(payload, 0, payload.length);
            } catch (IOException ex) {
                System.err.println("[edge] tun write failed: " + ex.getMessage());
            }
        }

        sendAck(session, packet.seq(), packet.isRelay(), sender);
    }

    private void onAckPacket(PeerSession session, DataPacket ack, InetSocketAddress sender, long nowMs) {
        PeerSession.Inflight inflight = session.inflight;
        if (inflight == null) {
            return;
        }
        if (ack.ack() != inflight.packet.seq()) {
            return;
        }

        if (!ack.isRelay() && !config.forceRelay()) {
            switchToDirect(session, sender, "ack");
        }

        session.outboundQueue.poll();
        session.inflight = null;
        trySendNext(session, nowMs);
    }

    private void sendAck(PeerSession session, int ackSeq, boolean viaRelay, InetSocketAddress directTarget) {
        if (assignedVirtualIp == 0) {
            return;
        }
        byte flags = DataPacket.FLAG_ACK;
        if (viaRelay) {
            flags |= DataPacket.FLAG_RELAY;
        }
        DataPacket ack = new DataPacket(
            config.nodeId(),
            session.peerId,
            assignedVirtualIp,
            session.peerVirtualIp,
            0,
            ackSeq,
            flags,
            new byte[0]
        );
        if (viaRelay) {
            sendRelay(ack);
        } else {
            sendDirect(ack, directTarget);
        }
    }

    private void switchToRelay(PeerSession session, String reason) {
        if (session.mode != PeerSession.LinkMode.RELAY) {
            System.out.println("[edge] path switch DIRECT/PROBING -> RELAY peer=" + session.peerId.shortText() + " reason=" + reason);
        }
        session.mode = PeerSession.LinkMode.RELAY;
        session.probingStartedAtMs = 0;
    }

    private void switchToDirect(PeerSession session, InetSocketAddress endpoint, String reason) {
        session.publicEndpoint = endpoint;
        if (session.mode != PeerSession.LinkMode.DIRECT) {
            System.out.println("[edge] path switch -> DIRECT peer=" + session.peerId.shortText() + " reason=" + reason
                + " endpoint=" + endpoint.getAddress().getHostAddress() + ":" + endpoint.getPort());
        }
        session.mode = PeerSession.LinkMode.DIRECT;
        session.probingStartedAtMs = 0;
    }

    private void sendToCoordinator(ControlMessage message) {
        byte[] encoded = EnvelopeCodec.encode(config.psk(), message, config.nodeId(), Instant.now().getEpochSecond());
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(encoded), coordinatorAddress));
    }

    private void sendRelay(DataPacket packet) {
        sendToCoordinator(packet);
    }

    private void sendDirect(DataPacket packet, InetSocketAddress target) {
        if (target == null) {
            return;
        }
        byte[] encoded = EnvelopeCodec.encode(config.psk(), packet, config.nodeId(), Instant.now().getEpochSecond());
        channel.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(encoded), target));
    }

    private final class EdgeInboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            DatagramPacket datagram = (DatagramPacket) msg;
            try {
                byte[] bytes = new byte[datagram.content().readableBytes()];
                datagram.content().readBytes(bytes);
                DecodedEnvelope decoded = EnvelopeCodec.decode(config.psk(), bytes);
                ControlMessage inbound = decoded.message();
                long nowMs = System.currentTimeMillis();
                InetSocketAddress sender = datagram.sender();

                if (inbound instanceof RegisterResponse) {
                    onRegisterResponse((RegisterResponse) inbound);
                } else if (inbound instanceof HeartbeatResponse) {
                    HeartbeatResponse response = (HeartbeatResponse) inbound;
                    System.out.println("[edge] heartbeat ack seq=" + response.seqEcho() + " status=" + response.status());
                } else if (inbound instanceof QueryPeerResponse) {
                    onQueryPeerResponse((QueryPeerResponse) inbound, nowMs);
                } else if (inbound instanceof PunchNotify) {
                    onPunchNotify((PunchNotify) inbound, nowMs);
                } else if (inbound instanceof DataPacket) {
                    onDataPacket(sender, (DataPacket) inbound, nowMs);
                }
            } catch (Exception ex) {
                System.err.println("[edge] inbound decode error: " + ex.getMessage());
            } finally {
                datagram.release();
            }
        }
    }

    private void onRegisterResponse(RegisterResponse response) {
        if (response.status() == RegisterResponse.STATUS_OK) {
            assignedVirtualIp = response.assignedVirtualIp();
            System.out.println("[edge] register status=OK assignedVip=" + IpCodec.intToIpv4(assignedVirtualIp)
                + " ttl=" + response.ttlSec() + "s");
            return;
        }
        System.err.println("[edge] register failed status=" + registerStatusText(response.status()));
    }

    private String registerStatusText(byte status) {
        if (status == RegisterResponse.STATUS_DENIED) {
            return "DENIED";
        }
        if (status == RegisterResponse.STATUS_DUPLICATE_NODE_ID) {
            return "DUPLICATE_NODE_ID";
        }
        if (status == RegisterResponse.STATUS_IP_ALLOCATION_CONFLICT) {
            return "IP_ALLOCATION_CONFLICT";
        }
        return "UNKNOWN(" + status + ")";
    }

    private String queryStatusText(byte status) {
        if (status == QueryPeerResponse.STATUS_NOT_FOUND) {
            return "NODE_ID_NOT_FOUND";
        }
        if (status == QueryPeerResponse.STATUS_OFFLINE) {
            return "TARGET_OFFLINE";
        }
        if (status == QueryPeerResponse.STATUS_MAPPING_INVALID) {
            return "MAPPING_INVALID";
        }
        return "UNKNOWN(" + status + ")";
    }
}
