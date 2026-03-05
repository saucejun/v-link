package com.vlink.common.net;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.nio.NioEventLoopGroup;

public final class EventLoopResources {
    private final EventLoopGroup eventLoopGroup;
    private final Class<? extends DatagramChannel> channelClass;
    private final boolean epollEnabled;

    private EventLoopResources(EventLoopGroup eventLoopGroup, Class<? extends DatagramChannel> channelClass, boolean epollEnabled) {
        this.eventLoopGroup = eventLoopGroup;
        this.channelClass = channelClass;
        this.epollEnabled = epollEnabled;
    }

    public static EventLoopResources create(int threads) {
        if (isLinux() && Epoll.isAvailable()) {
            return new EventLoopResources(new EpollEventLoopGroup(threads), EpollDatagramChannel.class, true);
        }
        return new EventLoopResources(new NioEventLoopGroup(threads), NioDatagramChannel.class, false);
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("linux");
    }

    public EventLoopGroup eventLoopGroup() {
        return eventLoopGroup;
    }

    public Class<? extends DatagramChannel> channelClass() {
        return channelClass;
    }

    public boolean epollEnabled() {
        return epollEnabled;
    }
}
