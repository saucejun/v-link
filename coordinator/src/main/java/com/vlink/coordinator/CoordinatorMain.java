package com.vlink.coordinator;

public final class CoordinatorMain {
    private CoordinatorMain() {
    }

    public static void main(String[] args) throws Exception {
        CoordinatorConfig config = CoordinatorConfig.fromArgs(args);
        CoordinatorServer server = new CoordinatorServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
        server.start();
        server.block();
    }
}
