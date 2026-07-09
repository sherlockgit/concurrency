package com.sherlock.concurrency.chapter13.detailed_13_01;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lock 接口的基本用法。
 *
 * <p>这是《Java 并发编程实战》中的 13.1。</p>
 *
 * <p>13.1 在书中对应的是 {@link Lock} 接口说明。
 * {@code Lock} 与 {@code synchronized} 一样，都可以用来保护共享状态，
 * 但 {@code Lock} 提供了更灵活的加锁方式。</p>
 *
 * <p>常见能力包括：</p>
 *
 * <p>1. {@link Lock#lock()}：普通加锁，拿不到锁就一直等待；</p>
 * <p>2. {@link Lock#tryLock()}：尝试加锁，拿不到锁就立即返回 false；</p>
 * <p>3. {@link Lock#tryLock(long, TimeUnit)}：限时等待锁；</p>
 * <p>4. {@link Lock#lockInterruptibly()}：等待锁时可以响应中断；</p>
 * <p>5. {@link Lock#unlock()}：显式释放锁。</p>
 */
public class LockInterfaceDemo {

    /**
     * 实际使用的 Lock 实现。
     *
     * <p>{@link ReentrantLock} 是最常用的 {@link Lock} 实现。
     * “Reentrant” 表示可重入：同一个线程已经持有锁时，可以再次获得同一把锁。</p>
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 被锁保护的共享状态。
     */
    private int value;

    /**
     * 普通加锁方式。
     *
     * <p>这是 {@code Lock} 最基础的用法，对应 {@code synchronized} 的互斥能力。</p>
     *
     * <p>注意：调用 {@code lock.lock()} 后，必须在 {@code finally} 中调用
     * {@code lock.unlock()}。否则如果临界区抛出异常，锁就可能永远无法释放。</p>
     */
    public void increment() {
        lock.lock();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试加锁。
     *
     * <p>{@code tryLock()} 不会一直等待。
     * 如果当前锁空闲，就获得锁并返回 true；
     * 如果当前锁已被其他线程持有，就立即返回 false。</p>
     *
     * <p>这种方式适合“拿不到锁就做别的事”或者“避免死锁”的场景。</p>
     *
     * @return 是否成功完成递增
     */
    public boolean tryIncrement() {
        if (!lock.tryLock()) {
            return false;
        }

        try {
            value++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 限时等待锁。
     *
     * <p>这个方法最多等待指定时间。
     * 如果在超时前获得锁，就执行递增并返回 true；
     * 如果超时还没有获得锁，就返回 false。</p>
     *
     * <p>它比 {@link #tryIncrement()} 更灵活：
     * 不是完全不等，也不是无限等待，而是在响应性和成功率之间做折中。</p>
     *
     * @return 是否成功完成递增
     */
    public boolean tryIncrementWithin(long timeout, TimeUnit unit) throws InterruptedException {
        if (!lock.tryLock(timeout, unit)) {
            return false;
        }

        try {
            value++;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 可中断地等待锁。
     *
     * <p>{@code lockInterruptibly()} 与 {@code lock()} 的区别是：
     * 线程在等待锁期间如果被中断，会抛出 {@link InterruptedException}，
     * 而不是继续无限等待。</p>
     *
     * <p>这在取消任务、关闭服务、避免长时间卡住时非常有用。</p>
     */
    public void incrementInterruptibly() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            value++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 读取共享状态。
     *
     * <p>读操作也要加锁，因为 {@link #value} 是共享可变状态。
     * 加锁不仅保证互斥，也保证内存可见性。</p>
     */
    public int getValue() {
        lock.lock();
        try {
            return value;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 持有锁一段时间，用于演示 tryLock 和 lockInterruptibly。
     */
    private void holdLock(long time, TimeUnit unit) throws InterruptedException {
        lock.lock();
        try {
            unit.sleep(time);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final LockInterfaceDemo demo = new LockInterfaceDemo();

        demo.increment();
        assertEquals(1, demo.getValue(), "value after increment");

        boolean trySuccess = demo.tryIncrement();
        assertTrue(trySuccess, "tryIncrement should succeed when lock is free");
        assertEquals(2, demo.getValue(), "value after tryIncrement");

        Thread holder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    demo.holdLock(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "lock-holder");

        holder.start();
        TimeUnit.MILLISECONDS.sleep(50);

        boolean timeoutSuccess = demo.tryIncrementWithin(100, TimeUnit.MILLISECONDS);
        assertFalse(timeoutSuccess, "tryIncrementWithin should timeout while lock is held");

        holder.join();

        demo.incrementInterruptibly();
        assertEquals(3, demo.getValue(), "value after incrementInterruptibly");

        System.out.println("13.1 lock interface demo passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
