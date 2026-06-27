package com.sherlock.concurrency.chapter08.detailed_08_05;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自定义 ThreadFactory 演示。
 *
 * <p>这个示例展示三件事：</p>
 * <p>1. 如何统一线程命名；</p>
 * <p>2. 如何为线程池中的所有线程统一设置 UncaughtExceptionHandler；</p>
 * <p>3. 为什么线程池层面的线程定制通常应该通过 ThreadFactory 完成。</p>
 */
public class CustomThreadFactoryDemo {

    public static void main(String[] args) throws InterruptedException {
        ThreadFactory factory = new LoggingThreadFactory("crawl-worker");
        ExecutorService executor = Executors.newFixedThreadPool(2, factory);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("running on " + Thread.currentThread().getName());
            }
        });

        executor.execute(new Runnable() {
            @Override
            public void run() {
                throw new RuntimeException("simulated task failure");
            }
        });

        executor.shutdown();
    }

    /**
     * 一个带日志和命名策略的线程工厂。
     *
     * <p>每次创建线程时都会：</p>
     * <p>1. 生成统一格式的线程名；</p>
     * <p>2. 设置为非守护线程；</p>
     * <p>3. 挂上统一的未捕获异常处理器。</p>
     */
    static class LoggingThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        LoggingThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, prefix + "-" + threadNumber.getAndIncrement());
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    System.err.println("uncaught in " + t.getName() + ": " + e.getMessage());
                }
            });
            return thread;
        }
    }
}
