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
 *
 * * *
 * 无限创建线程的不足
 *
 * 1.线程生命周期的开销非常高。线程的创建与销毁并不是没有代价的。根据平台的不同，实际的开销也有所不同，
 * * 但线程的创建过程都会需要时间，延迟处理的请求，并且需要JVM和操作系统提供一些辅助操作。如果请求
 * * 的到达率非常高且请求的处理过程是轻量级的，例如大多数服务器应用程序就是这种情况，那么为每个请
 * * 求创建一个新线程将消耗大量的计算资源。
 *
 * 2.资源消耗。活跃的线程会消耗系统资源，尤其是内存。如果可运行的线程数量多于可用处理器的数量，那么
 * * 有些线程将闲置。大量空闲的线程会占用许多内存，给垃圾回收器带来压力，而且大量线程在竟争CPU资
 * * 源时还将产生其他的性能开销。如果你已经拥有足够多的线程使所有CPU保持忙碌状态，那么再创建更多
 * * 的线程反而会降低性能。
 *
 * 3.稳定性。在可创建线程的数量上存在一个限制。这个限制值将随着平台的不同而不同，并且受多个因素制约，
 * * 包括JVM的启动参数、Thread构造函数中请求的栈大小，以及底层操作系统对线程的限制等。如果破坏了这
 * * 些限制，那么很可能抛出OutOfMemoryError异常，要想从这种错误中恢复过来是非常危险的，更简单的
 * * 办法是通过构造程序来避免超出这些限制。
 *
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
