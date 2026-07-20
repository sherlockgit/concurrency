package com.sherlock.concurrency.chapter14.detailed_14_14;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 使用 AbstractQueuedSynchronizer 实现的一次性闭锁。
 *
 * <p>这是《Java 并发编程实战》中的 14.14。</p>
 *
 * <p>一次性闭锁可以理解成一扇只会打开一次的门：</p>
 *
 * <p>1. 初始状态下门是关闭的，调用 {@link #await()} 的线程会阻塞；</p>
 * <p>2. 调用 {@link #signal()} 后，门被永久打开，所有等待线程被放行；</p>
 * <p>3. 门一旦打开，就不会再关闭，后续调用 {@link #await()} 会立即返回。</p>
 *
 * <p>它和 {@link java.util.concurrent.CountDownLatch} 的 {@code count=1} 场景非常相似。
 * 这个例子的重点不是生产一个新的工具类，而是演示如何用 AQS 构建同步器。</p>
 */
@ThreadSafe
public class OneShotLatch {

    /**
     * 真正的同步逻辑封装在 AQS 子类中。
     *
     * <p>外部类只暴露业务语义：await 和 signal。
     * 排队、阻塞、唤醒、中断处理等通用逻辑交给 AQS。</p>
     */
    private final Sync sync = new Sync();

    /**
     * 等待闭锁打开。
     *
     * <p>如果闭锁还没有打开，当前线程会进入 AQS 的共享等待队列。
     * 如果闭锁已经打开，方法会立即返回。</p>
     *
     * @throws InterruptedException 等待期间被中断
     */
    public void await() throws InterruptedException {
        sync.acquireSharedInterruptibly(0);
    }

    /**
     * 打开闭锁。
     *
     * <p>这是一次性操作。调用后，AQS 的 state 会从 0 变成 1。
     * 所有已经在 {@link #await()} 中等待的线程，以及后续调用 {@link #await()} 的线程，
     * 都可以通过。</p>
     */
    public void signal() {
        sync.releaseShared(0);
    }

    /**
     * 当前闭锁是否已经打开。
     *
     * <p>这个方法不是官方示例的核心 API，只用于本地示例断言。</p>
     */
    public boolean isSignalled() {
        return sync.isSignalled();
    }

    /**
     * AQS 子类。
     *
     * <p>这个同步器使用 AQS 的 {@code state} 表示闭锁状态：</p>
     *
     * <p>1. {@code state == 0}：关闭，线程不能通过；</p>
     * <p>2. {@code state == 1}：打开，线程可以通过。</p>
     */
    private static class Sync extends AbstractQueuedSynchronizer {

        private static final int CLOSED = 0;

        private static final int OPEN = 1;

        /**
         * 尝试以共享模式获取同步状态。
         *
         * <p>AQS 约定：</p>
         *
         * <p>1. 返回负数表示获取失败，线程需要入队等待；</p>
         * <p>2. 返回零或正数表示获取成功，线程可以继续执行。</p>
         *
         * <p>本例中，如果 state 已经是 OPEN，说明闭锁已经打开，
         * 所以返回 1；否则返回 -1，让 AQS 把线程放入等待队列。</p>
         */
        @Override
        protected int tryAcquireShared(int ignored) {
            return getState() == OPEN ? 1 : -1;
        }

        /**
         * 尝试以共享模式释放同步状态。
         *
         * <p>释放操作就是“打开闭锁”：把 state 设置为 OPEN。</p>
         *
         * <p>返回 true 表示这次释放可能让等待线程继续执行。
         * AQS 看到 true 后，会负责唤醒共享等待队列中的线程。</p>
         */
        @Override
        protected boolean tryReleaseShared(int ignored) {
            setState(OPEN);
            return true;
        }

        boolean isSignalled() {
            return getState() == OPEN;
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final OneShotLatch latch = new OneShotLatch();
        final CountDownLatch waitersStarted = new CountDownLatch(2);
        final AtomicInteger passed = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread first = newAwaitingThread(latch, waitersStarted, passed, failure,
                "one-shot-latch-first");
        Thread second = newAwaitingThread(latch, waitersStarted, passed, failure,
                "one-shot-latch-second");

        first.start();
        second.start();
        waitersStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(first.isAlive(), "first waiter should block before signal");
        assertTrue(second.isAlive(), "second waiter should block before signal");
        assertEquals(0, passed.get(), "no thread should pass before signal");
        assertFalse(latch.isSignalled(), "latch should be closed before signal");

        latch.signal();
        first.join(2000);
        second.join(2000);

        assertNoFailure(failure);
        assertFalse(first.isAlive(), "first waiter should pass after signal");
        assertFalse(second.isAlive(), "second waiter should pass after signal");
        assertEquals(2, passed.get(), "both waiters should pass after signal");
        assertTrue(latch.isSignalled(), "latch should be open after signal");

        /*
         * 闭锁已经打开后，后续 await 不应该再阻塞。
         */
        latch.await();
        assertEquals(2, passed.get(), "main thread pass is not counted");

        /*
         * 多次 signal 也不会把闭锁重新关闭。
         */
        latch.signal();
        assertTrue(latch.isSignalled(), "latch should remain open after repeated signal");

        System.out.println("14.14 one-shot latch passed");
    }

    private static Thread newAwaitingThread(final OneShotLatch latch,
                                            final CountDownLatch started,
                                            final AtomicInteger passed,
                                            final AtomicReference<Throwable> failure,
                                            String name) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    started.countDown();
                    latch.await();
                    passed.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, name);
    }

    private static void assertNoFailure(AtomicReference<Throwable> failure) {
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
