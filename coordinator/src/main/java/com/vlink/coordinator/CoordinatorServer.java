package com.vlink.coordinator;

import com.vlink.common.net.EventLoopResources;
import com.vlink.common.protocol.ControlMessage;
import com.vlink.common.protocol.EnvelopeCodec;
import com.vlink.common.protocol.EnvelopeCodec.DecodedEnvelope;
import com.vlink.common.protocol.IpCodec;
import com.vlink.common.protocol.msg.DataPacket;
import com.vlink.common.protocol.msg.HeartbeatRequest;
import com.vlink.common.protocol.msg.HeartbeatResponse;
import com.vlink.common.protocol.msg.PunchNotify;
import com.vlink.common.protocol.msg.PunchRequest;
import com.vlink.common.protocol.msg.QueryPeerRequest;
import com.vlink.common.protocol.msg.QueryPeerResponse;
import com.vlink.common.protocol.msg.RegisterRequest;
import com.vlink.common.protocol.msg.RegisterResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class CoordinatorServer {
    private static final int DUPLICATE_REGISTER_WINDOW_SEC = 5;

    private final CoordinatorConfig config;
    private final EventLoopResources eventLoopResources;
    private final PeerRegistry peerRegistry;
    private Channel channel;

    public CoordinatorServer(CoordinatorConfig config) {
        this.config = config;
        this.eventLoopResources = EventLoopResources.create(1);
        this.peerRegistry = new PeerRegistry(config.vipPoolStart(), config.vipPoolEnd());
    }

    public void start() throws InterruptedException {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopResources.eventLoopGroup())
            .channel(eventLoopResources.channelClass())
            .handler(new ChannelInitializer<Channel>() {
                @Override
                protected void initChannel(Channel ch) {
                    ch.pipeline().addLast(new CoordinatorInboundHandler());
                }
            });

        ChannelFuture future = bootstrap.bind(config.bindHost(), config.bindPort()).sync();
        channel = future.channel();

        channel.eventLoop().scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                long nowSec = Instant.now().getEpochSecond();
                int removed = peerRegistry.removeExpired(nowSec, config.peerTimeoutSec());
                if (removed > 0) {
                    System.out.println("[coordinator] removed stale peers=" + removed);
                }
            }
        }, config.cleanupIntervalSec(), config.cleanupIntervalSec(), TimeUnit.SECONDS);

        System.out.println("[coordinator] started on " + config.bindHost() + ":" + config.bindPort()
            + ", io=" + (eventLoopResources.epollEnabled() ? "epoll" : "nio")
            + ", peerTimeout=" + config.peerTimeoutSec() + "s"
            + ", vipPool=" + IpCodec.intToIpv4(config.vipPoolStart()) + "-" + IpCodec.intToIpv4(config.vipPoolEnd()));
    }

    public void block() throws InterruptedException {
        if (channel != null) {
            channel.closeFuture().sync();
        }
    }

    public void stop() {
        if (channel != null) {
            channel.close();
        }
        eventLoopResources.eventLoopGroup().shutdownGracefully();
    }

    private final class CoordinatorInboundHandler extends ChannelInboundHandlerAdapter {
        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            DatagramPacket packet = (DatagramPacket) msg;
            try {
                byte[] bytes = new byte[packet.content().readableBytes()];
                packet.content().readBytes(bytes);
                DecodedEnvelope decoded = EnvelopeCodec.decode(config.psk(), bytes);
                ControlMessage inbound = decoded.message();
                InetSocketAddress sender = packet.sender();
                long nowSec = Instant.now().getEpochSecond();

                if (inbound instanceof RegisterRequest) {
                    onRegister(ctx, sender, (RegisterRequest) inbound, nowSec);
                } else if (inbound instanceof HeartbeatRequest) {
                    onHeartbeat(ctx, sender, (HeartbeatRequest) inbound, nowSec);
                } else if (inbound instanceof QueryPeerRequest) {
                    onQueryPeer(ctx, sender, (QueryPeerRequest) inbound, nowSec);
                } else if (inbound instanceof PunchRequest) {
                    onPunchRequest(ctx, sender, (PunchRequest) inbound, nowSec);
                } else if (inbound instanceof DataPacket) {
                    onRelayData(ctx, sender, (DataPacket) inbound, nowSec);
                }
            } catch (Exception ex) {
                System.err.println("[coordinator] packet process error: " + ex.getMessage());
            } finally {
                packet.release();
            }
        }

        private void onRegister(ChannelHandlerContext ctx, InetSocketAddress sender, RegisterRequest request, long nowSec) {
            PeerRegistry.RegistrationResult result = peerRegistry.register(
                request.nodeId(),
                sender,
                nowSec,
                DUPLICATE_REGISTER_WINDOW_SEC
            );
            int assignedVip = result.record() == null ? 0 : result.record().virtualIp();
            RegisterResponse response = new RegisterResponse(result.status(), assignedVip, config.peerTimeoutSec());
            send(ctx, sender, response);

            if (result.status() == RegisterResponse.STATUS_OK) {
                System.out.println("[coordinator] register node=" + request.nodeId().shortText()
                    + " pub=" + sender.getAddress().getHostAddress() + ":" + sender.getPort()
                    + " assignedVip=" + IpCodec.intToIpv4(assignedVip));
                return;
            }

            System.err.println("[coordinator] register rejected node=" + request.nodeId().shortText()
                + " pub=" + sender.getAddress().getHostAddress() + ":" + sender.getPort()
                + " status=" + registerStatusText(result.status())
                + " reason=" + result.reason());
        }

        private void onHeartbeat(ChannelHandlerContext ctx, InetSocketAddress sender, HeartbeatRequest request, long nowSec) {
            PeerRecord existing = peerRegistry.refresh(request.nodeId(), sender, nowSec);
            byte status = existing == null ? HeartbeatResponse.STATUS_UNKNOWN : HeartbeatResponse.STATUS_OK;
            HeartbeatResponse response = new HeartbeatResponse(status, request.seq(), config.peerTimeoutSec());
            send(ctx, sender, response);
        }

        private void onQueryPeer(ChannelHandlerContext ctx, InetSocketAddress requester, QueryPeerRequest request, long nowSec) {
            PeerRecord target = peerRegistry.find(request.targetNodeId());
            QueryPeerResponse response;
            if (target == null) {
                response = buildQueryErrorResponse(QueryPeerResponse.STATUS_NOT_FOUND, request.targetNodeId());
                send(ctx, requester, response);
                return;
            }
            if (nowSec - target.lastSeenEpochSec() > config.peerTimeoutSec()) {
                response = buildQueryErrorResponse(QueryPeerResponse.STATUS_OFFLINE, request.targetNodeId());
                send(ctx, requester, response);
                return;
            }
            if (!peerRegistry.hasValidMapping(target.nodeId())) {
                response = buildQueryErrorResponse(QueryPeerResponse.STATUS_MAPPING_INVALID, request.targetNodeId());
                send(ctx, requester, response);
                return;
            }

            InetSocketAddress targetEndpoint = peerRegistry.connectionFor(target.nodeId());
            if (targetEndpoint == null) {
                response = buildQueryErrorResponse(QueryPeerResponse.STATUS_MAPPING_INVALID, request.targetNodeId());
                send(ctx, requester, response);
                return;
            }
            InetAddress address = targetEndpoint.getAddress();
            response = new QueryPeerResponse(
                QueryPeerResponse.STATUS_OK,
                target.nodeId(),
                IpCodec.inetAddressToInt(address),
                targetEndpoint.getPort(),
                target.virtualIp(),
                target.lastSeenEpochSec(),
                false
            );
            send(ctx, requester, response);
        }

        private QueryPeerResponse buildQueryErrorResponse(byte status, com.vlink.common.protocol.NodeId targetNodeId) {
            return new QueryPeerResponse(
                status,
                targetNodeId,
                0,
                0,
                0,
                0,
                true
            );
        }

        private void onPunchRequest(ChannelHandlerContext ctx, InetSocketAddress sender, PunchRequest request, long nowSec) {
            PeerRecord requester = peerRegistry.refresh(request.requesterId(), sender, nowSec);
            if (requester == null || !peerRegistry.hasValidMapping(request.requesterId())) {
                System.err.println("[coordinator] punch requester invalid requester=" + request.requesterId().shortText());
                return;
            }

            PeerRecord target = peerRegistry.find(request.targetNodeId());
            if (target == null) {
                System.err.println("[coordinator] punch target not found target=" + request.targetNodeId().shortText());
                return;
            }
            if (nowSec - target.lastSeenEpochSec() > config.peerTimeoutSec()) {
                System.err.println("[coordinator] punch target offline target=" + request.targetNodeId().shortText());
                return;
            }
            if (!peerRegistry.hasValidMapping(target.nodeId())) {
                System.err.println("[coordinator] punch target mapping invalid target=" + request.targetNodeId().shortText());
                return;
            }

            InetSocketAddress targetEndpoint = peerRegistry.connectionFor(target.nodeId());
            InetSocketAddress requesterEndpoint = peerRegistry.connectionFor(requester.nodeId());
            if (targetEndpoint == null || requesterEndpoint == null) {
                System.err.println("[coordinator] punch endpoint missing requester=" + requester.nodeId().shortText()
                    + " target=" + target.nodeId().shortText());
                return;
            }

            PunchNotify notifyToTarget = new PunchNotify(
                requester.nodeId(),
                IpCodec.inetAddressToInt(requesterEndpoint.getAddress()),
                requesterEndpoint.getPort(),
                requester.virtualIp()
            );
            send(ctx, targetEndpoint, notifyToTarget);

            PunchNotify notifyToRequester = new PunchNotify(
                target.nodeId(),
                IpCodec.inetAddressToInt(targetEndpoint.getAddress()),
                targetEndpoint.getPort(),
                target.virtualIp()
            );
            send(ctx, requesterEndpoint, notifyToRequester);
        }

        private void onRelayData(ChannelHandlerContext ctx, InetSocketAddress sender, DataPacket packet, long nowSec) {
            if (!packet.isRelay()) {
                return;
            }

            peerRegistry.refresh(packet.srcNodeId(), sender, nowSec);

            PeerRecord target = peerRegistry.find(packet.dstNodeId());
            if (target == null && packet.dstVirtualIp() != 0) {
                target = peerRegistry.findByVirtualIp(packet.dstVirtualIp());
            }
            if (target == null) {
                System.err.println("[coordinator] relay target not found dstNode=" + packet.dstNodeId().shortText());
                return;
            }
            if (nowSec - target.lastSeenEpochSec() > config.peerTimeoutSec()) {
                System.err.println("[coordinator] relay target offline dstNode=" + packet.dstNodeId().shortText());
                return;
            }
            if (!peerRegistry.hasValidMapping(target.nodeId())) {
                System.err.println("[coordinator] relay mapping invalid dstNode=" + packet.dstNodeId().shortText());
                return;
            }

            InetSocketAddress targetEndpoint = peerRegistry.connectionFor(target.nodeId());
            if (targetEndpoint == null) {
                System.err.println("[coordinator] relay endpoint missing dstNode=" + packet.dstNodeId().shortText());
                return;
            }

            send(ctx, targetEndpoint, packet);
            if (!packet.isAck() && !packet.isProbe()) {
                System.out.println("[coordinator] relay packet src=" + packet.srcNodeId().shortText()
                    + " dst=" + packet.dstNodeId().shortText() + " seq=" + packet.seq());
            }
        }

        private void send(ChannelHandlerContext ctx, InetSocketAddress destination, ControlMessage message) {
            byte[] encoded = EnvelopeCodec.encode(config.psk(), message, config.coordinatorId(), Instant.now().getEpochSecond());
            ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(encoded), destination));
        }

        private String registerStatusText(byte status) {
            if (status == RegisterResponse.STATUS_OK) {
                return "OK";
            }
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
    }
}
