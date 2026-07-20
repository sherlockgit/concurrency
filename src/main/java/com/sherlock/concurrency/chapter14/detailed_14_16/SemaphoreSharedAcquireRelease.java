package com.sherlock.concurrency.chapter14.detailed_14_16;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * Semaphore 中 tryAcquireShared 和 tryReleaseShared 的核心思路。
 *
 * <p>这是《Java 并发编程实战》中的 14.16。</p>
 *
 * <p>官方 14.16 指向 JDK 中 Semaphore 的 AQS 实现源码。
 * 本地示例实现一个教学版信号量，只保留最核心的同步语义：</p>
 *
 * <p>1. AQS 的 state 表示当前剩余许可数；</p>
 * <p>2. acquire 时通过 CAS 从 state 中减去一个许可；</p>
 * <p>3. release 时通过 CAS 向 state 中加回一个许可；</p>
 * <p>4. 许可不够时，线程进入 AQS 的共享等待队列。</p>
 */
@ThreadSafe
public class SemaphoreSharedAcquireRelease {

    /**
     * 教学版信号量。
     *
     * <p>外部类只暴露业务语义：获取许可、释放许可、查看许可数。
     * 线程排队、阻塞、唤醒、中断处理都交给 AQS 完成。</p>
     */
    private static class TeachingSemaphore {

        private final Sync sync;

        TeachingSemaphore(int permits) {
            if (permits < 0) {
                throw new IllegalArgumentException("permits must not be negative");
            }
            this.sync = new Sync(permits);
        }

        /**
         * 获取一个许可。
         *
         * <p>如果当前没有可用许可，当前线程会进入 AQS 的共享等待队列。
         * 使用 interruptible 版本，是为了让等待线程能响应中断。</p>
         */
        public void acquire() throws InterruptedException {
            sync.acquireSharedInterruptibly(1);
        }

        /**
         * 尝试获取一个许可。
         *
         * <p>这个方法不会阻塞。返回 true 表示成功扣减了一个许可；
         * 返回 false 表示当前没有许可。</p>
         */
        public boolean tryAcquire() {
            return sync.tryAcquireOne();
        }

        /**
         * 释放一个许可。
         *
         * <p>释放成功后，AQS 会尝试唤醒共享等待队列中的线程。</p>
         */
        public void release() {
            sync.releaseShared(1);
        }

        /**
         * 当前剩余许可数。
         */
        public int availablePermits() {
            return sync.availablePermits();
        }

        /**
         * AQS 子类。
         *
         * <p>这个同步器使用 AQS 的 state 表示许可数：</p>
         *
         * <p>1. state > 0：还有许可，线程可以尝试获取；</p>
         * <p>2. state == 0：没有许可，新线程需要等待；</p>
         * <p>3. state 的修改必须通过 CAS 完成，避免多个线程同时扣减或增加许可时发生竞态。</p>
         */
        private static class Sync extends AbstractQueuedSynchronizer {

            Sync(int permits) {
                setState(permits);
            }

            boolean tryAcquireOne() {
                return tryAcquireShared(1) >= 0;
            }

            int availablePermits() {
                return getState();
            }

            /**
             * 尝试以共享模式获取许可。
             *
             * <p>AQS 对 tryAcquireShared 的返回值有约定：</p>
             *
             * <p>1. 返回负数：获取失败，当前线程需要入队等待；</p>
             * <p>2. 返回 0：获取成功，但没有剩余共享资源可以继续传播；</p>
             * <p>3. 返回正数：获取成功，并且可能还有剩余共享资源，后续等待线程也可以尝试获取。</p>
             *
             * <p>Semaphore 的获取逻辑就是从许可数中扣减 acquires。
             * 如果扣减后为负数，说明许可不够；否则通过 CAS 把新许可数写回 state。</p>
             */
            @Override
            protected int tryAcquireShared(int acquires) {
                for (;;) {
                    int available = getState();
                    int remaining = available - acquires;

                    /*
                     * remaining < 0 表示许可不够。
                     * 直接返回负数，AQS 会把当前线程放入共享等待队列。
                     */
                    if (remaining < 0) {
                        return remaining;
                    }

                    /*
                     * CAS 成功表示当前线程成功抢到了许可。
                     * 如果 CAS 失败，说明其他线程刚刚修改了许可数，需要重新读取 state 再试。
                     */
                    if (compareAndSetState(available, remaining)) {
                        return remaining;
                    }
                }
            }

            /**
             * 尝试以共享模式释放许可。
             *
             * <p>释放就是把许可数加回 state。由于可能有多个线程同时 release，
             * 所以这里同样必须使用 CAS 循环。</p>
             *
             * <p>返回 true 表示这次 release 可能让等待条件成立。
             * AQS 收到 true 后，会执行共享模式的传播唤醒逻辑，
             * 让等待队列中的线程重新尝试 tryAcquireShared。</p>
             */
            @Override
            protected boolean tryReleaseShared(int releases) {
                for (;;) {
                    int current = getState();
                    int next = current + releases;

                    if (next < current) {
                        throw new Error("maximum permit count exceeded");
                    }

                    if (compareAndSetState(current, next)) {
                        return true;
                    }
                }
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testAcquireConsumesPermit();
        testAcquireBlocksWhenNoPermits();
        testSharedReleaseWakesWaitersOnePermitAtATime();

        System.out.println("14.16 semaphore shared acquire/release passed");
    }

    /**
     * 验证：获取许可会减少 state，释放许可会增加 state。
     */
    private static void testAcquireConsumesPermit() throws Exception {
        TeachingSemaphore semaphore = new TeachingSemaphore(1);

        assertEquals(1, semaphore.availablePermits(), "initial permits");
        assertTrue(semaphore.tryAcquire(), "tryAcquire should succeed when permit exists");
        assertEquals(0, semaphore.availablePermits(), "permits after tryAcquire");
        assertFalse(semaphore.tryAcquire(), "tryAcquire should fail when no permit exists");

        semaphore.release();
        assertEquals(1, semaphore.availablePermits(), "permits after release");

        semaphore.acquire();
        assertEquals(0, semaphore.availablePermits(), "permits after blocking acquire");
    }

    /**
     * 验证：没有许可时，acquire 会让线程阻塞在 AQS 共享等待队列中。
     */
    private static void testAcquireBlocksWhenNoPermits() throws Exception {
        final TeachingSemaphore semaphore = new TeachingSemaphore(0);
        final CountDownLatch waiterStarted = new CountDownLatch(1);
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread waiter = newAcquiringThread(semaphore, waiterStarted, acquired, failure,
                "semaphore-single-waiter");

        waiter.start();
        waiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(waiter.isAlive(), "waiter should block when there is no permit");
        assertEquals(0, acquired.get(), "waiter should not acquire before release");

        semaphore.release();
        waiter.join(2000);

        assertNoFailure(failure);
        assertFalse(waiter.isAlive(), "waiter should wake up after release");
        assertEquals(1, acquired.get(), "waiter should acquire once");
        assertEquals(0, semaphore.availablePermits(), "permit should be consumed by waiter");
    }

    /**
     * 验证：共享模式下，每释放一个许可，最多让一个正在等待的 acquire 成功通过。
     */
    private static void testSharedReleaseWakesWaitersOnePermitAtATime() throws Exception {
        final TeachingSemaphore semaphore = new TeachingSemaphore(0);
        final CountDownLatch waitersStarted = new CountDownLatch(2);
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread first = newAcquiringThread(semaphore, waitersStarted, acquired, failure,
                "semaphore-first-waiter");
        Thread second = newAcquiringThread(semaphore, waitersStarted, acquired, failure,
                "semaphore-second-waiter");

        first.start();
        second.start();
        waitersStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertEquals(0, acquired.get(), "no waiter should acquire before release");

        /*
         * 第一次 release 只增加一个许可。
         * 因此两个等待线程中应该只有一个能通过 acquire，另一个仍然等待。
         */
        semaphore.release();
        waitUntilAcquiredCount(acquired, 1);
        assertEquals(0, semaphore.availablePermits(), "first released permit should be consumed");

        /*
         * 第二次 release 再增加一个许可，剩下的等待线程才能继续执行。
         */
        semaphore.release();
        first.join(2000);
        second.join(2000);

        assertNoFailure(failure);
        assertFalse(first.isAlive(), "first waiter should finish");
        assertFalse(second.isAlive(), "second waiter should finish");
        assertEquals(2, acquired.get(), "both waiters should acquire exactly once");
        assertEquals(0, semaphore.availablePermits(), "all released permits should be consumed");
    }

    private static Thread newAcquiringThread(final TeachingSemaphore semaphore,
                                             final CountDownLatch started,
                                             final AtomicInteger acquired,
                                             final AtomicReference<Throwable> failure,
                                             String name) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    started.countDown();
                    semaphore.acquire();
                    acquired.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, name);
    }

    private static void waitUntilAcquiredCount(AtomicInteger acquired, int expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (acquired.get() == expected) {
                return;
            }
            TimeUnit.MILLISECONDS.sleep(10);
        }
        throw new AssertionError("timed out waiting for acquired count " + expected
                + ", actual " + acquired.get());
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
