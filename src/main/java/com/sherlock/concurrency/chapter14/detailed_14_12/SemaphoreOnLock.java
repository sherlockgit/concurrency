package com.sherlock.concurrency.chapter14.detailed_14_12;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用 Lock 实现的计数信号量。
 *
 * <p>这是《Java 并发编程实战》中的 14.12。</p>
 *
 * <p>这个类演示了如何用 {@link ReentrantLock} 和 {@link Condition}
 * 实现一个简化版的计数信号量。</p>
 *
 * <p>注意：这不是 {@link java.util.concurrent.Semaphore} 的真实实现。
 * JDK 中的 Semaphore 是基于 AbstractQueuedSynchronizer 实现的。</p>
 *
 * <p>计数信号量可以理解成一组许可：</p>
 *
 * <p>1. {@link #acquire()} 获取一个许可；如果没有许可，就阻塞等待；</p>
 * <p>2. {@link #release()} 释放一个许可；如果有线程正在等待许可，就唤醒其中一个。</p>
 */
@ThreadSafe
public class SemaphoreOnLock {

    /**
     * 保护 permits 的显式锁。
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 等待“有可用许可”的条件队列。
     *
     * <p>条件谓词是：</p>
     *
     * <pre>
     * permits > 0
     * </pre>
     */
    private final Condition permitsAvailable = lock.newCondition();

    /**
     * 当前可用许可数量。
     *
     * <p>由 lock 保护。</p>
     */
    private int permits;

    public SemaphoreOnLock(int initialPermits) {
        if (initialPermits < 0) {
            throw new IllegalArgumentException("initialPermits must not be negative");
        }
        this.permits = initialPermits;
    }

    /**
     * 获取一个许可。
     *
     * <p>入口协议：必须存在可用许可，也就是 {@code permits > 0}。</p>
     *
     * <p>如果没有许可，线程进入 {@code permitsAvailable} 条件队列等待。
     * 当其他线程调用 {@link #release()} 增加许可后，会通过 {@code signal}
     * 唤醒一个等待线程。</p>
     *
     * @throws InterruptedException 等待许可期间被中断
     */
    public void acquire() throws InterruptedException {
        lock.lock();
        try {
            /*
             * await 必须放在 while 中。
             *
             * await 返回只表示“可以重新检查条件了”，不表示 permits 一定大于 0。
             */
            while (permits <= 0) {
                /*
                 * await 会释放 lock，让 release 有机会进入并增加 permits。
                 * 被 signal 唤醒后，线程必须重新拿到 lock，才会从 await 返回。
                 */
                permitsAvailable.await();
            }

            /*
             * 走到这里说明当前持有 lock，并且 permits > 0。
             * 消耗一个许可。
             */
            permits--;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 释放一个许可。
     *
     * <p>出口协议：释放许可会让 {@code permits > 0} 这个条件可能变成 true。
     * 因此需要通知正在等待许可的线程。</p>
     */
    public void release() {
        lock.lock();
        try {
            permits++;

            /*
             * 每次 release 只增加一个许可，通常只需要唤醒一个等待线程。
             *
             * 这里可以使用 signal，而不是 signalAll。
             * 因为 permitsAvailable 这个条件队列中只等待一种条件：permits > 0。
             */
            permitsAvailable.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 返回当前可用许可数。
     *
     * <p>这个方法只用于示例断言。</p>
     */
    public int availablePermits() {
        lock.lock();
        try {
            return permits;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testAcquireBlocksUntilRelease();
        testSignalWakesOneWaiter();

        System.out.println("14.12 semaphore on lock passed");
    }

    /**
     * 验证：没有许可时 acquire 会阻塞；release 后等待线程可以继续执行。
     */
    private static void testAcquireBlocksUntilRelease() throws Exception {
        final SemaphoreOnLock semaphore = new SemaphoreOnLock(0);
        final CountDownLatch acquirerStarted = new CountDownLatch(1);
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread acquirer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    acquirerStarted.countDown();
                    semaphore.acquire();
                    acquired.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "semaphore-single-acquirer");

        acquirer.start();
        acquirerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(acquirer.isAlive(), "acquirer should wait while no permit is available");
        assertEquals(0, acquired.get(), "acquired count before release");

        semaphore.release();
        acquirer.join(2000);

        assertNoFailure(failure);
        assertFalse(acquirer.isAlive(), "acquirer should finish after release");
        assertEquals(1, acquired.get(), "acquired count after release");
        assertEquals(0, semaphore.availablePermits(), "permit should be consumed");
    }

    /**
     * 验证：一次 release 增加一个许可，只需要唤醒一个等待线程。
     */
    private static void testSignalWakesOneWaiter() throws Exception {
        final SemaphoreOnLock semaphore = new SemaphoreOnLock(0);
        final CountDownLatch waitersStarted = new CountDownLatch(2);
        final AtomicInteger acquired = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread first = newAcquireThread(semaphore, waitersStarted, acquired, failure,
                "semaphore-first-waiter");
        Thread second = newAcquireThread(semaphore, waitersStarted, acquired, failure,
                "semaphore-second-waiter");

        first.start();
        second.start();
        waitersStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertEquals(0, acquired.get(), "both waiters should still be blocked");

        semaphore.release();
        waitUntilAcquiredCount(acquired, 1);
        assertEquals(1, acquired.get(), "one release should let one waiter pass");

        semaphore.release();
        first.join(2000);
        second.join(2000);

        assertNoFailure(failure);
        assertFalse(first.isAlive(), "first waiter should finish");
        assertFalse(second.isAlive(), "second waiter should finish");
        assertEquals(2, acquired.get(), "both waiters should pass after two releases");
        assertEquals(0, semaphore.availablePermits(), "all permits should be consumed");
    }

    private static Thread newAcquireThread(final SemaphoreOnLock semaphore,
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
        long deadline = System.currentTimeMillis() + 2000L;
        while (acquired.get() < expected && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
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
