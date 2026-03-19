package com.vlink.edge;

public final class EdgeMain {
    private EdgeMain() {
    }

    public static void main(String[] args) throws Exception {   //EdgeMain类是边缘节点的入口点，包含main方法，负责解析命令行参数，创建EdgeClient实例，并启动客户端，同时注册一个关闭钩子以确保在程序终止时正确关闭客户端资源
        EdgeConfig config = EdgeConfig.fromArgs(args);
        EdgeClient client = new EdgeClient(config);
        Runtime.getRuntime().addShutdownHook(new Thread(client::stop));
        client.start();
        client.block();
    }
}

