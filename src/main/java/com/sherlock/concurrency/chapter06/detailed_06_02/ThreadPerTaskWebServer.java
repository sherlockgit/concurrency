package com.sherlock.concurrency.chapter06.detailed_06_02;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 在Web服务器中为每个请求启动一个新的线程
 * * *
 * 每个任务启动一个线程的Web服务器。
 * 这种模型为每一个客户端连接都创建一个新的线程来处理请求，主线程可以立即返回接受下一个连接。
 * 优点：并发处理能力强，简单直观。
 * 缺点：线程创建和销毁开销大；大量线程会消耗内存和CPU调度资源，可能导致系统过载。
 */
class ThreadPerTaskWebServer {
    public static void main(String[] args) throws IOException {
        // 绑定80端口，监听HTTP请求（注意：1024以下端口通常需要管理员权限）
        ServerSocket socket = new ServerSocket(80);

        // 无限循环，持续接受客户端连接
        while (true) {
            // 阻塞等待客户端连接，返回与该客户端的Socket
            final Socket connection = socket.accept();

            // 定义一个任务：处理当前客户端请求
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };
            // 为每个请求创建一个新线程并启动，主线程立即返回继续accept
            new Thread(task).start();
        }
    }

    /**
     * 处理客户端请求的具体逻辑（示例占位）
     * @param connection 与客户端的Socket连接
     */
    private static void handleRequest(Socket connection) {
        // 实际处理请求（读取数据、发送响应、关闭连接等）
    }
}
