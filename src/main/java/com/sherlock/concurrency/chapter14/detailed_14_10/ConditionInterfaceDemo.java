package com.sherlock.concurrency.chapter14.detailed_14_10;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Condition 接口的基本用法。
 *
 * <p>这是《Java 并发编程实战》中的 14.10。</p>
 *
 * <p>官方 14.10 指向的是 {@link Condition} 接口本身。
 * 这个本地示例把接口扩展成一个可运行的小例子，用来说明：</p>
 *
 * <p>1. {@code Condition.await()} 类似 {@code Object.wait()}；</p>
 * <p>2. {@code Condition.signalAll()} 类似 {@code Object.notifyAll()}；</p>
 * <p>3. 调用 {@code await/signal/signalAll} 前，必须先持有创建该 Condition 的锁；</p>
 * <p>4. 一个 {@link Lock} 可以创建多个 {@link Condition}，从而拥有多个独立条件队列。</p>
 */
public class ConditionInterfaceDemo {

    /**
     * 使用 Condition 实现的简单闸门。
     *
     * <p>这个类和 14.9 的 ThreadGate 类似，但同步机制从：</p>
     *
     * <pre>
     * synchronized + wait + notifyAll
     * </pre>
     *
     * <p>换成了：</p>
     *
     * <pre>
     * ReentrantLock + Condition.await + Condition.signalAll
     * </pre>
     */
    @ThreadSafe
    private static class ConditionGate {

        /**
         * 显式锁。
         *
         * <p>Condition 不是独立存在的，它总是由某一把 Lock 创建出来。</p>
         */
        private final Lock lock = new ReentrantLock();

        /**
         * 等待“门已经打开”这个条件的条件队列。
         *
         * <p>调用 {@link Lock#newCondition()} 相当于创建了一个专门的等待队列。
         * 和内置锁不同，一个 Lock 可以创建多个 Condition。</p>
         */
        private final Condition opened = lock.newCondition();

        /**
         * 门是否打开。
         *
         * <p>由 lock 保护。</p>
         */
        private boolean open;

        /**
         * 示例统计：调用 signalAll 的次数。
         *
         * <p>由 lock 保护。</p>
         */
        private int signalAllCalls;

        /**
         * 关闭门。
         */
        public void close() {
            lock.lock();
            try {
                open = false;
            } finally {
                lock.unlock();
            }
        }

        /**
         * 打开门，并唤醒等待“门打开”的线程。
         */
        public void open() {
            lock.lock();
            try {
                open = true;
                signalAllCalls++;

                /*
                 * signalAll 必须在持有 lock 时调用。
                 *
                 * 它不会立即释放锁，只是把等待在 opened 条件队列中的线程转移出来，
                 * 让它们之后有机会重新竞争 lock。
                 */
                opened.signalAll();
            } finally {
                lock.unlock();
            }
        }

        /**
         * 等待门打开。
         *
         * @throws InterruptedException 等待期间被中断
         */
        public void awaitOpen() throws InterruptedException {
            /*
             * 这里使用 lockInterruptibly，是为了让“等待获取锁”这个阶段也能响应中断。
             * 如果已经拿到锁，后面的 opened.await 也能响应中断。
             */
            lock.lockInterruptibly();
            try {
                /*
                 * await 也必须放在 while 中。
                 *
                 * await 返回只表示“可以重新检查条件了”，不表示 open 一定为 true。
                 */
                while (!open) {
                    /*
                     * await 会释放 lock，并让当前线程进入 opened 条件队列。
                     * 被 signalAll 唤醒后，线程必须重新拿到 lock，才会从 await 返回。
                     */
                    opened.await();
                }
            } finally {
                lock.unlock();
            }
        }

        /**
         * 限时等待门打开。
         *
         * <p>这个方法展示 {@link Condition#awaitNanos(long)} 的典型用法：
         * 每次 awaitNanos 返回后，把剩余时间保存下来，继续放到 while 条件中判断。</p>
         *
         * @param time 最大等待时间
         * @param unit 时间单位
         * @return 如果门在超时前打开，返回 true；否则返回 false
         * @throws InterruptedException 等待期间被中断
         */
        public boolean awaitOpen(long time, TimeUnit unit) throws InterruptedException {
            long nanos = unit.toNanos(time);

            lock.lockInterruptibly();
            try {
                while (!open) {
                    if (nanos <= 0L) {
                        return false;
                    }
                    nanos = opened.awaitNanos(nanos);
                }
                return true;
            } finally {
                lock.unlock();
            }
        }

        public int getSignalAllCalls() {
            lock.lock();
            try {
                return signalAllCalls;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final ConditionGate gate = new ConditionGate();
        final CountDownLatch waiterStarted = new CountDownLatch(1);
        final AtomicInteger passed = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        /*
         * 第一段：门默认关闭，线程应该阻塞在 Condition.await 中。
         */
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waiterStarted.countDown();
                    gate.awaitOpen();
                    passed.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "condition-gate-waiter");

        waiter.start();
        waiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(waiter.isAlive(), "waiter should wait while gate is closed");

        gate.open();
        waiter.join(2000);

        assertNoFailure(failure);
        assertFalse(waiter.isAlive(), "waiter should pass after gate is opened");
        assertEquals(1, passed.get(), "passed count");
        assertEquals(1, gate.getSignalAllCalls(), "signalAll calls after open");

        /*
         * 第二段：重新关门后，限时等待应该在超时后返回 false。
         */
        gate.close();
        boolean openedBeforeTimeout = gate.awaitOpen(100, TimeUnit.MILLISECONDS);
        assertFalse(openedBeforeTimeout, "timed await should return false after timeout");

        /*
         * 第三段：门保持打开时，awaitOpen 应该立即返回。
         */
        gate.open();
        assertTrue(gate.awaitOpen(100, TimeUnit.MILLISECONDS),
                "timed await should return true when gate is open");

        System.out.println("14.10 condition interface demo passed");
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
