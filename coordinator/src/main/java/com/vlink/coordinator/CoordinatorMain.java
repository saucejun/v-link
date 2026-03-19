package com.vlink.coordinator;

public final class CoordinatorMain {    //CoordinatorMain类是协调器的入口点，包含main方法，负责解析命令行参数，创建CoordinatorServer实例，并启动服务器，同时注册一个关闭钩子以确保在程序终止时正确关闭服务器资源
    private CoordinatorMain() { //私有构造函数，防止外部实例化
    }

    public static void main(String[] args) throws Exception {   //main方法，程序入口点，负责解析命令行参数，创建CoordinatorServer实例，并启动服务器，同时注册一个关闭钩子以确保在程序终止时正确关闭服务器资源
        CoordinatorConfig config = CoordinatorConfig.fromArgs(args);    //从命令行参数创建配置对象，解析命令行参数并初始化配置项
        CoordinatorServer server = new CoordinatorServer(config);   //创建协调器服务器实例，传入配置对象
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop)); //注册关闭钩子，在程序终止时调用服务器的stop方法，确保正确关闭服务器资源
        server.start(); //启动服务器，开始监听客户端请求
        server.block(); //阻塞当前线程，等待服务器关闭，保持程序运行状态，直到接收到关闭信号
    }
}
