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
    private final CoordinatorConfig config;
    private final EventLoopResources eventLoopResources;
    private final PeerRegistry peerRegistry;
    private Channel channel;

    public CoordinatorServer(CoordinatorConfig config) {
        this.config = config;
        this.eventLoopResources = EventLoopResources.create(1);
        this.peerRegistry = new PeerRegistry();
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
            + ", peerTimeout=" + config.peerTimeoutSec() + "s");
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
            peerRegistry.upsert(request.nodeId(), request.virtualIp(), sender, nowSec);
            RegisterResponse response = new RegisterResponse(RegisterResponse.STATUS_OK, request.virtualIp(), 30);
            send(ctx, sender, response);
            System.out.println("[coordinator] register node=" + request.nodeId().shortText()
                + " pub=" + sender.getAddress().getHostAddress() + ":" + sender.getPort()
                + " vip=" + IpCodec.intToIpv4(request.virtualIp()));
        }

        private void onHeartbeat(ChannelHandlerContext ctx, InetSocketAddress sender, HeartbeatRequest request, long nowSec) {
            PeerRecord existing = peerRegistry.refresh(request.nodeId(), sender, nowSec);
            byte status = existing == null ? HeartbeatResponse.STATUS_UNKNOWN : HeartbeatResponse.STATUS_OK;
            HeartbeatResponse response = new HeartbeatResponse(status, request.seq(), 30);
            send(ctx, sender, response);
        }

        private void onQueryPeer(ChannelHandlerContext ctx, InetSocketAddress requester, QueryPeerRequest request, long nowSec) {
            PeerRecord target = peerRegistry.find(request.targetId());
            if (target != null && nowSec - target.lastSeenEpochSec() > config.peerTimeoutSec()) {
                target = null;
            }

            QueryPeerResponse response;
            if (target == null) {
                response = new QueryPeerResponse(
                    QueryPeerResponse.STATUS_NOT_FOUND,
                    request.targetId(),
                    0,
                    0,
                    0,
                    0,
                    true
                );
            } else {
                InetAddress address = target.publicEndpoint().getAddress();
                response = new QueryPeerResponse(
                    QueryPeerResponse.STATUS_OK,
                    target.nodeId(),
                    IpCodec.inetAddressToInt(address),
                    target.publicEndpoint().getPort(),
                    target.virtualIp(),
                    target.lastSeenEpochSec(),
                    false
                );
            }
            send(ctx, requester, response);
        }

        private void onPunchRequest(ChannelHandlerContext ctx, InetSocketAddress sender, PunchRequest request, long nowSec) {
            PeerRecord requester = peerRegistry.refresh(request.requesterId(), sender, nowSec);
            if (requester == null) {
                requester = peerRegistry.find(request.requesterId());
            }
            PeerRecord target = peerRegistry.find(request.targetId());
            if (requester == null || target == null) {
                return;
            }

            PunchNotify notifyToTarget = new PunchNotify(
                requester.nodeId(),
                IpCodec.inetAddressToInt(requester.publicEndpoint().getAddress()),
                requester.publicEndpoint().getPort(),
                requester.virtualIp()
            );
            send(ctx, target.publicEndpoint(), notifyToTarget);

            PunchNotify notifyToRequester = new PunchNotify(
                target.nodeId(),
                IpCodec.inetAddressToInt(target.publicEndpoint().getAddress()),
                target.publicEndpoint().getPort(),
                target.virtualIp()
            );
            send(ctx, requester.publicEndpoint(), notifyToRequester);
        }

        private void onRelayData(ChannelHandlerContext ctx, InetSocketAddress sender, DataPacket packet, long nowSec) {
            if (!packet.isRelay()) {
                return;
            }

            peerRegistry.refresh(packet.srcNodeId(), sender, nowSec);

            PeerRecord target = peerRegistry.find(packet.dstNodeId());
            if (target == null) {
                target = peerRegistry.findByVirtualIp(packet.dstVirtualIp());
            }
            if (target == null) {
                return;
            }
            if (nowSec - target.lastSeenEpochSec() > config.peerTimeoutSec()) {
                return;
            }

            send(ctx, target.publicEndpoint(), packet);
            if (!packet.isAck() && !packet.isProbe()) {
                System.out.println("[coordinator] relay packet src=" + packet.srcNodeId().shortText()
                    + " dst=" + packet.dstNodeId().shortText() + " seq=" + packet.seq());
            }
        }

        private void send(ChannelHandlerContext ctx, InetSocketAddress destination, ControlMessage message) {
            byte[] encoded = EnvelopeCodec.encode(config.psk(), message, config.coordinatorId(), Instant.now().getEpochSecond());
            ctx.writeAndFlush(new DatagramPacket(Unpooled.wrappedBuffer(encoded), destination));
        }
    }
}
