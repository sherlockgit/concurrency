package com.sherlock.concurrency.chapter12.detailed_12_10;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 Thread.yield 产生更多线程交错。
 *
 * <p>这是《Java 并发编程实战》中的 12.10。</p>
 *
 * <p>书中的 12.10 是一个片段，核心思想是：
 * 在并发测试中，可以在一些容易发生竞态的位置插入 {@link Thread#yield()}，
 * 主动给调度器一个机会，让当前线程让出 CPU，从而增加不同线程交错执行的概率。</p>
 *
 * <p>注意：{@code Thread.yield()} 不是同步机制，也不是强制切换线程。
 * 它只是一个提示，JVM 和操作系统可以选择忽略它。
 * 因此它只能帮助测试更容易暴露问题，不能用来证明程序一定正确。</p>
 */
public class YieldInterleavingTest {

    /**
     * 并发执行的线程数。
     */
    private static final int THREAD_COUNT = 4;

    /**
     * 每个线程执行的递增次数。
     */
    private static final int ITERATIONS = 50000;

    /**
     * 计数器接口。
     *
     * <p>下面会提供一个非线程安全实现和一个线程安全实现，用来对比 yield 的作用。</p>
     */
    private interface Counter {
        void increment();

        int get();
    }

    /**
     * 非线程安全计数器。
     *
     * <p>{@code value++} 表面上是一行代码，但实际包含三步：</p>
     *
     * <p>1. 读取 value；</p>
     * <p>2. 计算 value + 1；</p>
     * <p>3. 写回 value。</p>
     *
     * <p>如果两个线程同时执行这三步，就可能发生丢失更新。</p>
     */
    private static class UnsafeCounter implements Counter {
        private int value;

        @Override
        public void increment() {
            /*
             * 先把共享变量读到局部变量。
             */
            int snapshot = value;

            /*
             * 在“读”和“写”之间插入 yield。
             *
             * 这会扩大竞争窗口：
             * 当前线程刚读到旧值，还没写回新值时，主动提示调度器可以切换到其他线程。
             * 如果其他线程也读到了同一个旧值，后面就可能互相覆盖，造成丢失更新。
             */
            Thread.yield();

            /*
             * 写回新值。
             *
             * 这里没有 synchronized、Lock 或 AtomicInteger 保护，
             * 所以多个线程写回时可能互相覆盖。
             */
            value = snapshot + 1;
        }

        @Override
        public int get() {
            return value;
        }
    }

    /**
     * 线程安全计数器。
     *
     * <p>这里故意也在同步代码中调用 {@code Thread.yield()}。
     * 即使发生线程切换，其他线程也无法同时进入 synchronized 方法修改 value，
     * 因此最终结果仍然应该正确。</p>
     */
    private static class SynchronizedCounter implements Counter {
        private int value;

        @Override
        public synchronized void increment() {
            int snapshot = value;
            Thread.yield();
            value = snapshot + 1;
        }

        @Override
        public synchronized int get() {
            return value;
        }
    }

    /**
     * 演示 yield 如何帮助测试产生更多线程交错。
     */
    public void testYieldCanGenerateMoreInterleavings() throws InterruptedException {
        int expected = THREAD_COUNT * ITERATIONS;

        int unsafeActual = runCounter(new UnsafeCounter());
        System.out.println("unsafe counter expected=" + expected + ", actual=" + unsafeActual);

        /*
         * 不把 unsafeActual < expected 写成硬断言。
         *
         * 原因是 Thread.yield 只是提示，不保证一定发生线程切换。
         * 在某些 JVM、操作系统或者机器负载下，非线程安全计数器也可能偶然得到正确结果。
         */
        if (unsafeActual == expected) {
            System.out.println("unsafe counter did not fail in this run; Thread.yield is only a hint");
        }

        int synchronizedActual = runCounter(new SynchronizedCounter());
        System.out.println("synchronized counter expected=" + expected + ", actual=" + synchronizedActual);

        /*
         * 同步版本必须正确。
         *
         * 这说明 yield 只能制造更多执行交错，不能替代正确的同步。
         */
        assertEquals(expected, synchronizedActual, "synchronized counter value");
    }

    /**
     * 多线程运行同一个计数器。
     */
    private int runCounter(final Counter counter) throws InterruptedException {
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(THREAD_COUNT);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < THREAD_COUNT; i++) {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        /*
                         * 所有线程先在起跑线上等待。
                         *
                         * 这样可以让它们尽量同时开始递增操作，
                         * 从而提高并发冲突出现的概率。
                         */
                        startGate.await();

                        for (int j = 0; j < ITERATIONS; j++) {
                            counter.increment();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        endGate.countDown();
                    }
                }
            }, "counter-worker-" + i);

            worker.start();
        }

        startGate.countDown();
        endGate.await();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        return counter.get();
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        YieldInterleavingTest test = new YieldInterleavingTest();
        test.testYieldCanGenerateMoreInterleavings();
        System.out.println("12.10 yield interleaving test passed");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
