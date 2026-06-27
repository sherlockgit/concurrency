package com.sherlock.concurrency.chapter08.detailed_08_07;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 自定义线程基类。
 *
 * <p>这是《Java 并发编程实战》中的 8.7。
 * 虽然你这次问的是 8.6，但 8.6 直接依赖本类，
 * 所以这里一并补上，保持示例链条完整且可编译。</p>
 *
 * <p>这个类展示了一个自定义线程基类通常会集中处理的几类事情：</p>
 *
 * <p>1. 统一线程命名；</p>
 * <p>2. 统一设置未捕获异常处理器；</p>
 * <p>3. 统计已创建线程数和当前存活线程数；</p>
 * <p>4. 在调试模式下输出线程创建/退出日志。</p>
 */
public class MyAppThread extends Thread {

    /**
     * 默认线程名前缀。
     */
    public static final String DEFAULT_NAME = "MyAppThread";

    /**
     * 是否启用生命周期调试日志。
     */
    private static volatile boolean debugLifecycle = false;

    /**
     * 已创建线程总数。
     */
    private static final AtomicInteger created = new AtomicInteger();

    /**
     * 当前存活线程数。
     */
    private static final AtomicInteger alive = new AtomicInteger();

    /**
     * 用于输出线程级日志。
     */
    private static final Logger log = Logger.getAnonymousLogger();

    public MyAppThread(Runnable runnable) {
        this(runnable, DEFAULT_NAME);
    }

    /**
     * 创建一个自定义线程。
     *
     * <p>线程名采用 “前缀-序号” 的形式；
     * 同时统一挂上未捕获异常处理器，避免后台线程异常悄悄丢失。</p>
     *
     * @param runnable 线程要执行的任务
     * @param name 线程名前缀
     */
    public MyAppThread(Runnable runnable, String name) {
        super(runnable, name + "-" + created.incrementAndGet());
        setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                log.log(Level.SEVERE, "UNCAUGHT in thread " + t.getName(), e);
            }
        });
    }

    /**
     * 线程运行入口。
     *
     * <p>这里在真正执行业务任务前后维护 alive 计数，
     * 并在调试模式下输出生命周期日志。</p>
     */
    @Override
    public void run() {
        boolean debug = debugLifecycle;
        if (debug) {
            log.log(Level.FINE, "Created " + getName());
        }
        try {
            alive.incrementAndGet();
            super.run();
        } finally {
            alive.decrementAndGet();
            if (debug) {
                log.log(Level.FINE, "Exiting " + getName());
            }
        }
    }

    public static int getThreadsCreated() {
        return created.get();
    }

    public static int getThreadsAlive() {
        return alive.get();
    }

    public static boolean getDebug() {
        return debugLifecycle;
    }

    public static void setDebug(boolean debug) {
        debugLifecycle = debug;
    }
}
