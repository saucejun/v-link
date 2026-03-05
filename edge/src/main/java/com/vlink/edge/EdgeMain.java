package com.vlink.edge;

public final class EdgeMain {
    private EdgeMain() {
    }

    public static void main(String[] args) throws Exception {
        EdgeConfig config = EdgeConfig.fromArgs(args);
        EdgeClient client = new EdgeClient(config);
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        client.start();
        client.block();
    }
}

