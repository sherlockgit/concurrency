package com.sherlock.concurrency.chapter06.detailed_06_08;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.logging.Logger;

/**
 * 支持生命周期的Web服务器。
 * 它使用ExecutorService（支持关闭）来管理线程池，并能够优雅地停止：
 * - 停止接受新连接
 * - 等待已提交的任务执行完成（或超时）
 * - 通过检查请求内容来触发关闭（例如收到特殊的“shutdown”命令）
 */
class LifecycleWebServer {
    // 日志记录器，用于记录异常信息
    private static final Logger log = Logger.getLogger(LifecycleWebServer.class.getName());

    // 线程池（具体配置在构造函数或初始化块中，例如 Executors.newFixedThreadPool(100)）
    private final ExecutorService exec = Executors.newFixedThreadPool(100);  // 假设已在构造函数中初始化

    /**
     * 启动服务器：绑定端口，循环接受请求，提交给线程池处理
     * @throws IOException 若绑定端口失败
     */
    public void start() throws IOException {
        ServerSocket socket = new ServerSocket(80);  // 监听80端口
        // 当线程池未被关闭时，持续接受新连接
        while (!exec.isShutdown()) {
            try {
                final Socket conn = socket.accept();      // 阻塞接受连接
                // 提交任务：处理该连接
                exec.execute(new Runnable() {
                    public void run() {
                        handleRequest(conn);
                    }
                });
            } catch (RejectedExecutionException e) {
                // 如果线程池已经关闭，提交任务时会抛出此异常
                if (!exec.isShutdown()) {
                    // 若线程池并未关闭却发生拒绝（例如队列满且达到最大线程数），记录日志
                    log.severe("任务提交被拒绝: " + e.getMessage());
                }
                // 如果已经关闭，则忽略（正常停止过程中的现象）
            }
        }
    }

    /**
     * 停止服务器：关闭线程池，不再接受新任务，等待已有任务完成
     */
    public void stop() {
        exec.shutdown();     // 优雅关闭，不再接受新任务，等待已有任务执行完毕
    }

    /**
     * 处理单个客户端连接
     * @param connection 客户端Socket
     */
    void handleRequest(Socket connection) {
        // 读取请求（示例方法，需自行实现）
        Request req = readRequest(connection);
        // 如果收到的请求是“关闭命令”，则触发服务器停止
        if (isShutdownRequest(req)) {
            stop();   // 注意：多个线程可能同时调用stop，但ExecutorService内部是线程安全的
        } else {
            // 正常分发请求到业务逻辑
            dispatchRequest(req);
        }
    }

    // ------------------ 以下为示例辅助方法（实际需具体实现） ------------------
    private Request readRequest(Socket connection) {
        // 从Socket输入流中解析HTTP请求（简化示例）
        return new Request();
    }

    private boolean isShutdownRequest(Request req) {
        // 判断是否为关闭服务器的特殊请求（如检查URI是否为 /shutdown）
        return false; // 示例永远返回false
    }

    private void dispatchRequest(Request req) {
        // 实际业务处理（返回静态文件、动态页面等）
    }

    // 示例内部类，表示一个请求
    private static class Request {
        // 包含请求行、头部、主体等信息
    }
}
