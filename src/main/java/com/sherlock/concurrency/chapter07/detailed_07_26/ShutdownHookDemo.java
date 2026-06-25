package com.sherlock.concurrency.chapter07.detailed_07_26;

import com.sherlock.concurrency.chapter07.detailed_07_16.LogService;

import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * 使用 shutdown hook 在 JVM 退出时关闭服务。
 *
 * <p>这是《Java 并发编程实战》中的 7.26。</p>
 *
 * <p>7.26 在书里更接近一个“用法片段”而不是完整示例，
 * 核心想说明的是：当某个后台服务的生命周期应该跟随整个 JVM 进程时，
 * 可以通过 {@link Runtime#addShutdownHook(Thread)} 注册一个关闭钩子，
 * 在 JVM 正常关闭时统一触发清理逻辑。</p>
 *
 * <p>这里沿用 7.16 的 {@link LogService} 作为被管理的后台服务，
 * 演示 shutdown hook 的典型职责：</p>
 *
 * <p>1. 进程退出时停止接收新任务；</p>
 * <p>2. 尽量等待已提交任务处理完成；</p>
 * <p>3. 释放底层 I/O 资源；</p>
 * <p>4. 避免把收尾责任散落到各个业务调用点。</p>
 *
 * <p>注意：shutdown hook 适合做“简短、线程安全、幂等”的清理工作。
 * 不应该在钩子里执行耗时很长、强依赖外部环境、或者彼此存在复杂顺序依赖的逻辑。</p>
 */
public class ShutdownHookDemo {

    /**
     * 实际需要在 JVM 退出时关闭的后台服务。
     */
    private final LogService logService;

    /**
     * 负责在 JVM 关闭阶段调用 {@link LogService#stop()} 的钩子线程。
     */
    private final Thread shutdownHook;

    /**
     * 生命周期状态位。
     *
     * <p>这里只做最小同步保护，避免重复启动或重复停止时产生不一致行为。</p>
     */
    private final Object lifecycleLock = new Object();

    private boolean started;
    private boolean stopped;

    public ShutdownHookDemo(Writer writer) {
        this.logService = new LogService(writer);
        this.shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                stopFromShutdownHook();
            }
        }, "log-service-shutdown-hook");
    }

    /**
     * 启动服务并向 JVM 注册关闭钩子。
     */
    public void start() {
        synchronized (lifecycleLock) {
            if (started) {
                return;
            }

            logService.start();
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            started = true;
        }
    }

    /**
     * 提交一条日志消息。
     *
     * <p>这里直接复用 7.16 中基于单线程 {@code ExecutorService} 的日志服务实现。</p>
     *
     * @param message 日志消息
     */
    public void log(String message) {
        logService.log(message);
    }

    /**
     * 手动停止服务。
     *
     * <p>如果应用不是通过 JVM 退出，而是由业务代码主动关闭服务，
     * 那么也应该把已经注册的 shutdown hook 移除，避免后续退出时重复执行同一套清理逻辑。</p>
     *
     * @throws InterruptedException 如果等待日志服务终止时当前线程被中断
     */
    public void stop() throws InterruptedException {
        synchronized (lifecycleLock) {
            if (!started || stopped) {
                return;
            }
            stopped = true;
        }

        removeShutdownHookIfPossible();
        logService.stop();
    }

    /**
     * shutdown hook 真正执行的收尾逻辑。
     *
     * <p>由于 hook 的 {@code run()} 不能抛出 checked exception，
     * 所以如果在等待服务停止时被中断，只能恢复中断状态并尽快结束。</p>
     */
    private void stopFromShutdownHook() {
        synchronized (lifecycleLock) {
            if (!started || stopped) {
                return;
            }
            stopped = true;
        }

        try {
            logService.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 尝试移除已经注册的 shutdown hook。
     *
     * <p>如果 JVM 已经进入关闭流程，{@link Runtime#removeShutdownHook(Thread)}
     * 会抛出 {@link IllegalStateException}，此时说明钩子已经来不及再移除，
     * 直接忽略即可。</p>
     */
    private void removeShutdownHookIfPossible() {
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        } catch (IllegalStateException ignored) {
            // JVM 已进入关闭阶段时无法移除 hook，此时允许它继续执行。
        }
    }

    /**
     * 简单演示 7.26 的运行方式。
     *
     * <p>由于 7.16 中的日志线程默认是非守护线程，
     * 如果什么都不做，main 方法结束后 JVM 不会直接退出。
     * 因此这里显式调用 {@link System#exit(int)} 来触发 shutdown hook，
     * 以便直观看到“JVM 关闭时统一停止服务”的效果。</p>
     */
    public static void main(String[] args) throws Exception {
        ShutdownHookDemo demo = new ShutdownHookDemo(
                new OutputStreamWriter(System.out, StandardCharsets.UTF_8));

        demo.start();
        demo.log("main: log service started");
        demo.log("main: shutdown hook has been registered");

        TimeUnit.MILLISECONDS.sleep(300);
        System.err.println("main: calling System.exit(0) to trigger shutdown hook");
        System.exit(0);
    }
}
