package com.sherlock.concurrency.chapter06.detailed_06_04;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 基于线程池的Web服务器
 * * *
 * 使用线程池的Web服务器。
 * 相比于“每个任务一个线程”的模型，线程池通过复用固定数量的线程来处理所有请求，
 * 避免了频繁创建和销毁线程的开销，并且可以限制并发请求数量，防止资源耗尽。
 */
class TaskExecutionWebServer {
    // 线程池大小：同时处理的并发请求数上限
    private static final int NTHREADS = 100;

    // 创建一个固定大小的线程池，核心线程数 = 最大线程数 = NTHREADS
    // 使用 Executor 接口而非具体类型，便于切换不同的执行策略
    private static final Executor exec = Executors.newFixedThreadPool(NTHREADS);

    public static void main(String[] args) throws IOException {
        // 创建 ServerSocket，监听 80 端口（需要管理员权限）
        ServerSocket socket = new ServerSocket(80);

        while (true) {
            // 阻塞等待客户端连接
            final Socket connection = socket.accept();

            // 定义处理请求的任务
            Runnable task = new Runnable() {
                public void run() {
                    handleRequest(connection);
                }
            };

            // 将任务提交给线程池执行，而不是直接创建新线程
            // 线程池会使用空闲线程来执行该任务，如果没有空闲线程则任务在队列中等待
            exec.execute(task);
        }
    }

    /**
     * 处理客户端请求（示例占位实现）
     * @param connection 与客户端的Socket连接
     */
    private static void handleRequest(Socket connection) {
        // 实际业务逻辑：读取HTTP请求、处理、返回响应、关闭连接
    }
}
