package com.sherlock.concurrency.chapter12.detailed_12_08;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用于测试线程池的 ThreadFactory。
 *
 * <p>这是《Java 并发编程实战》中的 12.8。</p>
 *
 * <p>这个类本身不改变线程的执行逻辑，它只是包装了
 * {@link Executors#defaultThreadFactory()}，并在每次创建线程时记录创建次数。</p>
 *
 * <p>为什么测试线程池时需要它？</p>
 *
 * <p>因为线程池内部什么时候创建新线程，外部通常不容易直接观察。
 * 如果给线程池传入一个自定义 {@link ThreadFactory}，
 * 那么线程池每次需要新工作线程时，都会调用 {@link #newThread(Runnable)}。
 * 测试代码就可以通过 {@link #numCreated} 判断线程池到底创建了多少个线程。</p>
 *
 * <p>后续 12.9 会使用这个工厂来验证：当任务足够多时，
 * 固定大小线程池是否会扩展到预期的最大线程数。</p>
 */
public class TestingThreadFactory implements ThreadFactory {

    /**
     * 已创建线程的数量。
     *
     * <p>使用 {@link AtomicInteger} 是因为 {@code newThread} 可能被多个线程并发调用。
     * 即使当前测试中调用路径通常比较简单，测试工具类也应该尽量避免自己引入竞态条件。</p>
     */
    public final AtomicInteger numCreated = new AtomicInteger();

    /**
     * 真正负责创建线程的默认工厂。
     *
     * <p>我们不自己手写线程创建细节，而是委托给 JDK 默认实现。
     * 这样可以保留默认线程名称、线程组、daemon 标记、优先级等行为。</p>
     */
    private final ThreadFactory factory = Executors.defaultThreadFactory();

    /**
     * 创建一个新线程。
     *
     * <p>线程池需要新工作线程时，会调用这个方法。
     * 这里先把计数器加一，再把真正的线程创建工作交给默认工厂。</p>
     *
     * @param runnable 新线程要执行的任务
     * @return 新创建的线程
     */
    @Override
    public Thread newThread(Runnable runnable) {
        numCreated.incrementAndGet();
        return factory.newThread(runnable);
    }

    /**
     * 简单运行入口。
     *
     * <p>这里只演示 ThreadFactory 的计数能力。
     * 真正的线程池扩容测试在 12.9 中实现。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        TestingThreadFactory threadFactory = new TestingThreadFactory();

        Thread first = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getName() + " executed first task");
            }
        });

        Thread second = threadFactory.newThread(new Runnable() {
            @Override
            public void run() {
                System.out.println(Thread.currentThread().getName() + " executed second task");
            }
        });

        first.start();
        second.start();

        first.join();
        second.join();

        assertEquals(2, threadFactory.numCreated.get(), "created thread count");
        System.out.println("12.8 testing thread factory passed");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
