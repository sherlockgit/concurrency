package com.sherlock.concurrency.chapter14.detailed_14_15;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * 非公平 ReentrantLock 中 tryAcquire 的核心思路。
 *
 * <p>这是《Java 并发编程实战》中的 14.15。</p>
 *
 * <p>官方 14.15 指向 JDK 源码中的非公平 ReentrantLock 获取逻辑。
 * 本地示例实现一个教学版非公平可重入锁，只保留核心路径：</p>
 *
 * <p>1. 如果锁当前空闲，当前线程用 CAS 抢占锁；</p>
 * <p>2. 如果当前线程已经持有锁，则增加重入计数；</p>
 * <p>3. 如果锁被其他线程持有，则获取失败，由 AQS 负责排队和阻塞。</p>
 *
 * <p>所谓“非公平”，重点在第一步：
 * 即使 AQS 队列中已经有等待线程，新来的线程也可以先尝试 CAS 抢锁。
 * 如果 CAS 成功，它就插队成功。</p>
 */
public class NonfairReentrantLockTryAcquire {

    /**
     * 教学版非公平可重入锁。
     */
    @ThreadSafe
    private static class TeachingNonfairReentrantLock {

        private final Sync sync = new Sync();

        /**
         * 获取锁。
         *
         * <p>先走非公平快速路径：如果 state 是 0，就直接 CAS 抢锁。
         * CAS 失败或锁不空闲时，再进入 AQS 的 acquire 流程。</p>
         */
        public void lock() {
            sync.lock();
        }

        /**
         * 释放锁。
         */
        public void unlock() {
            sync.release(1);
        }

        public boolean isHeldByCurrentThread() {
            return sync.isHeldByCurrentThread();
        }

        public int getHoldCount() {
            return sync.getHoldCount();
        }

        private static class Sync extends AbstractQueuedSynchronizer {

            /**
             * 非公平锁的 lock 入口。
             *
             * <p>这一步体现“非公平”：新来的线程不先检查队列中是否有人等待，
             * 而是直接尝试把 state 从 0 改成 1。</p>
             */
            void lock() {
                Thread current = Thread.currentThread();

                if (compareAndSetState(0, 1)) {
                    /*
                     * CAS 成功，说明当前线程抢到了空闲锁。
                     * AQS 的 state 只表示同步状态，锁的拥有者需要单独记录。
                     */
                    setExclusiveOwnerThread(current);
                } else {
                    /*
                     * 快速抢占失败后，进入 AQS 的标准独占获取流程。
                     * AQS 会反复调用 tryAcquire；
                     * 如果仍然失败，就把线程加入等待队列并阻塞。
                     */
                    acquire(1);
                }
            }

            /**
             * 尝试获取锁。
             *
             * <p>这是 14.15 关注的核心。</p>
             *
             * <p>AQS 约定：返回 true 表示获取成功；返回 false 表示获取失败，
             * AQS 后续会决定是否让当前线程排队和阻塞。</p>
             */
            @Override
            protected boolean tryAcquire(int acquires) {
                Thread current = Thread.currentThread();
                int state = getState();

                /*
                 * 情况一：锁当前空闲。
                 *
                 * 非公平锁不会先问“队列里是否已经有人等待”，
                 * 而是直接 CAS 抢占。
                 */
                if (state == 0) {
                    if (compareAndSetState(0, acquires)) {
                        setExclusiveOwnerThread(current);
                        return true;
                    }
                    return false;
                }

                /*
                 * 情况二：锁已经被当前线程持有。
                 *
                 * 这就是“可重入”：同一个线程再次 lock 不会死锁，
                 * 而是把 state 当作重入计数加一。
                 */
                if (current == getExclusiveOwnerThread()) {
                    int nextState = state + acquires;
                    if (nextState < 0) {
                        throw new Error("maximum lock count exceeded");
                    }
                    setState(nextState);
                    return true;
                }

                /*
                 * 情况三：锁被其他线程持有。
                 *
                 * 当前线程不能获取锁，返回 false。
                 * 后续排队、阻塞和唤醒由 AQS 处理。
                 */
                return false;
            }

            /**
             * 尝试释放锁。
             *
             * <p>只有持有锁的线程才能释放锁。
             * 每次 unlock 会让重入计数减一，只有减到 0 时，锁才真正空闲。</p>
             */
            @Override
            protected boolean tryRelease(int releases) {
                int nextState = getState() - releases;

                if (Thread.currentThread() != getExclusiveOwnerThread()) {
                    throw new IllegalMonitorStateException("current thread does not hold lock");
                }

                boolean free = false;
                if (nextState == 0) {
                    free = true;
                    setExclusiveOwnerThread(null);
                }

                setState(nextState);
                return free;
            }

            boolean isHeldByCurrentThread() {
                return getExclusiveOwnerThread() == Thread.currentThread();
            }

            int getHoldCount() {
                return isHeldByCurrentThread() ? getState() : 0;
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testReentrantAcquireAndRelease();
        testOtherThreadWaitsUntilFullyReleased();

        System.out.println("14.15 nonfair ReentrantLock tryAcquire passed");
    }

    /**
     * 验证：同一个线程可以重入，unlock 到计数为 0 后才真正释放。
     */
    private static void testReentrantAcquireAndRelease() {
        TeachingNonfairReentrantLock lock = new TeachingNonfairReentrantLock();

        lock.lock();
        assertTrue(lock.isHeldByCurrentThread(), "current thread should hold lock");
        assertEquals(1, lock.getHoldCount(), "hold count after first lock");

        lock.lock();
        assertEquals(2, lock.getHoldCount(), "hold count after reentrant lock");

        lock.unlock();
        assertTrue(lock.isHeldByCurrentThread(), "lock should still be held after one unlock");
        assertEquals(1, lock.getHoldCount(), "hold count after one unlock");

        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread(), "lock should be free after full release");
        assertEquals(0, lock.getHoldCount(), "hold count after full release");
    }

    /**
     * 验证：其他线程必须等持有者把重入计数释放到 0 后才能获得锁。
     */
    private static void testOtherThreadWaitsUntilFullyReleased() throws Exception {
        final TeachingNonfairReentrantLock lock = new TeachingNonfairReentrantLock();
        final CountDownLatch waiterStarted = new CountDownLatch(1);
        final AtomicInteger acquiredByWaiter = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        lock.lock();
        lock.lock();

        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waiterStarted.countDown();
                    lock.lock();
                    try {
                        acquiredByWaiter.incrementAndGet();
                    } finally {
                        lock.unlock();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "nonfair-lock-waiter");

        waiter.start();
        waiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(waiter.isAlive(), "waiter should block while another thread holds lock");
        assertEquals(0, acquiredByWaiter.get(), "waiter should not acquire before release");

        /*
         * 第一次 unlock 只把重入计数从 2 减到 1，锁仍然归主线程持有。
         */
        lock.unlock();
        TimeUnit.MILLISECONDS.sleep(100);

        assertTrue(waiter.isAlive(), "waiter should still block while hold count is 1");
        assertEquals(0, acquiredByWaiter.get(), "waiter should not acquire before full release");

        /*
         * 第二次 unlock 把重入计数降到 0，tryRelease 返回 true，
         * AQS 才会唤醒等待队列中的线程。
         */
        lock.unlock();
        waiter.join(2000);

        assertNoFailure(failure);
        assertFalse(waiter.isAlive(), "waiter should acquire after full release");
        assertEquals(1, acquiredByWaiter.get(), "waiter should acquire exactly once");
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
