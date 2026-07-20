package com.sherlock.concurrency.chapter14.detailed_14_13;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * AQS 中获取和释放操作的标准形式。
 *
 * <p>这是《Java 并发编程实战》中的 14.13。</p>
 *
 * <p>14.13 本身是一个模板型代码片段，用来说明 AQS 的基本工作方式：</p>
 *
 * <pre>
 * 获取操作：
 * while (同步状态不允许获取) {
 *     如果当前线程还没有入队，就加入等待队列；
 *     可能阻塞当前线程；
 * }
 * 如果当前线程曾经入队，就从队列中移除；
 * 返回获取成功；
 *
 * 释放操作：
 * 更新同步状态；
 * 如果新的状态可能让等待线程继续执行，就唤醒一个或多个等待线程；
 * </pre>
 *
 * <p>这个本地示例使用 AQS 实现一个简单的一次性闸门：</p>
 *
 * <p>1. 闸门关闭时，调用 {@link AqsGate#await()} 的线程会在 AQS 队列中等待；</p>
 * <p>2. 调用 {@link AqsGate#open()} 后，AQS 会唤醒等待线程；</p>
 * <p>3. 闸门打开后，后续线程可以直接通过。</p>
 */
public class AqsAcquireReleasePattern {

    /**
     * 使用 AQS 实现的一次性闸门。
     *
     * <p>这个类的语义类似 CountDownLatch(1)：一旦打开，就不会再关闭。</p>
     */
    @ThreadSafe
    private static class AqsGate {

        private final Sync sync = new Sync();

        /**
         * 等待闸门打开。
         *
         * <p>这里调用的是 AQS 提供的获取方法。
         * 如果 {@link Sync#tryAcquireShared(int)} 返回负数，AQS 会负责把当前线程入队并阻塞。</p>
         *
         * @throws InterruptedException 等待期间被中断
         */
        public void await() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 限时等待闸门打开。
         *
         * @param time 最大等待时间
         * @param unit 时间单位
         * @return 超时前闸门打开则返回 true，否则返回 false
         * @throws InterruptedException 等待期间被中断
         */
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return sync.tryAcquireSharedNanos(1, unit.toNanos(time));
        }

        /**
         * 打开闸门。
         *
         * <p>这里调用的是 AQS 提供的释放方法。
         * 如果 {@link Sync#tryReleaseShared(int)} 返回 true，
         * AQS 会认为这次释放可能让等待线程继续执行，于是唤醒队列中的后继线程。</p>
         */
        public void open() {
            sync.releaseShared(1);
        }

        public boolean isOpen() {
            return sync.isOpen();
        }

        /**
         * AQS 子类只需要定义“获取是否成功”和“释放是否可能唤醒等待线程”。
         *
         * <p>排队、阻塞、唤醒、中断和超时等通用细节，都由 AQS 处理。</p>
         */
        private static class Sync extends AbstractQueuedSynchronizer {

            private static final int CLOSED = 0;

            private static final int OPEN = 1;

            /**
             * 尝试获取共享同步状态。
             *
             * <p>返回值的含义：</p>
             *
             * <p>1. 负数：获取失败，AQS 会让线程进入等待队列；</p>
             * <p>2. 零或正数：获取成功，线程可以继续执行。</p>
             *
             * <p>本例中，state 为 OPEN 时表示闸门已打开，线程可以通过；
             * state 为 CLOSED 时表示闸门关闭，线程需要等待。</p>
             */
            @Override
            protected int tryAcquireShared(int ignored) {
                return getState() == OPEN ? 1 : -1;
            }

            /**
             * 尝试释放共享同步状态。
             *
             * <p>返回值的含义是：这次释放是否可能让等待线程继续执行。</p>
             *
             * <p>如果成功把状态从 CLOSED 改成 OPEN，就返回 true。
             * AQS 看到 true 后，会执行唤醒等待线程的逻辑。</p>
             *
             * <p>如果闸门本来已经打开，则返回 false。
             * 因为这次 open 没有产生新的状态转换，也就不需要额外唤醒。</p>
             */
            @Override
            protected boolean tryReleaseShared(int ignored) {
                return compareAndSetState(CLOSED, OPEN);
            }

            boolean isOpen() {
                return getState() == OPEN;
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testAcquireBlocksUntilRelease();
        testTimedAcquire();
        testOpenedGatePassesImmediately();

        System.out.println("14.13 AQS acquire/release pattern passed");
    }

    /**
     * 验证：闸门关闭时 await 会阻塞；open 后等待线程被 AQS 唤醒。
     */
    private static void testAcquireBlocksUntilRelease() throws Exception {
        final AqsGate gate = new AqsGate();
        final CountDownLatch waiterStarted = new CountDownLatch(1);
        final AtomicInteger passed = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waiterStarted.countDown();
                    gate.await();
                    passed.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "aqs-gate-waiter");

        waiter.start();
        waiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(waiter.isAlive(), "waiter should block while gate is closed");
        assertEquals(0, passed.get(), "passed count before open");

        gate.open();
        waiter.join(2000);

        assertNoFailure(failure);
        assertFalse(waiter.isAlive(), "waiter should pass after open");
        assertEquals(1, passed.get(), "passed count after open");
        assertTrue(gate.isOpen(), "gate should be open");
    }

    /**
     * 验证：AQS 的限时获取方法会在超时后返回 false。
     */
    private static void testTimedAcquire() throws Exception {
        AqsGate gate = new AqsGate();

        boolean opened = gate.await(100, TimeUnit.MILLISECONDS);
        assertFalse(opened, "timed await should return false when gate stays closed");

        gate.open();
        assertTrue(gate.await(100, TimeUnit.MILLISECONDS),
                "timed await should return true after gate opens");
    }

    /**
     * 验证：闸门已经打开后，后续线程不会入队阻塞，而是直接通过。
     */
    private static void testOpenedGatePassesImmediately() throws Exception {
        AqsGate gate = new AqsGate();
        gate.open();
        gate.await();
        assertTrue(gate.isOpen(), "gate should remain open");
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
