package com.sherlock.concurrency.chapter06.detailed_06_01;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 串行的Web服务器
 * * *
 * 单线程的Web服务器。
 * 这种实现方式每次只能处理一个请求，后续请求必须等待当前请求处理完毕。
 * 适用于演示或极低负载的场景，但生产环境不可用。
 */
class SingleThreadWebServer {
    public static void main(String[] args) throws IOException {
        // 创建ServerSocket，监听80端口（HTTP默认端口）
        // 注意：在Unix/Linux系统上绑定1024以下端口通常需要root权限
        ServerSocket socket = new ServerSocket(80);

        // 无限循环，持续接受客户端连接
        while (true) {
            // accept() 方法会阻塞，直到有客户端连接到达
            // 返回一个Socket对象，表示与客户端的连接
            Socket connection = socket.accept();

            // 同步处理请求：在handleRequest方法执行完毕之前，
            // 无法接受新的客户端连接
            handleRequest(connection);
        }
    }

    /**
     * 处理客户端请求的实际逻辑（示例）。
     * 此处应包含读取HTTP请求、生成响应、关闭连接等操作。
     * @param connection 与客户端的Socket连接
     */
    private static void handleRequest(Socket connection) {
        // 实际处理请求的代码（例如读取数据、返回HTTP响应）
        // 为简化示例，这里没有具体实现
    }
}
