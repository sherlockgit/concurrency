package com.sherlock.concurrency.chapter13.detailed_13_02;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用 ReentrantLock 保护对象状态。
 *
 * <p>这是《Java 并发编程实战》中的 13.2。</p>
 *
 * <p>13.2 在书中是一个片段，核心模式是：</p>
 *
 * <pre>
 * Lock lock = new ReentrantLock();
 *
 * lock.lock();
 * try {
 *     // 访问被 lock 保护的共享状态
 * } finally {
 *     lock.unlock();
 * }
 * </pre>
 *
 * <p>这和使用 {@code synchronized} 保护状态的目标相同：
 * 所有访问共享可变状态的代码，都必须先获得同一把锁。</p>
 */
public class GuardingStateWithReentrantLock {

    /**
     * 保护账户余额的显式锁。
     *
     * <p>这里使用接口类型 {@link Lock} 声明字段，
     * 实际对象是最常见的 {@link ReentrantLock}。</p>
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 被锁保护的共享可变状态。
     *
     * <p>只要读写 balance，就必须先获得 {@link #lock}。</p>
     */
    private int balance;

    public GuardingStateWithReentrantLock(int initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("initialBalance must not be negative");
        }
        this.balance = initialBalance;
    }

    /**
     * 存款。
     *
     * <p>修改 balance 是复合操作：
     * 读取旧值、计算新值、写回新值。
     * 多个线程同时执行时，必须用锁保护整个复合操作。</p>
     */
    public void deposit(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        lock.lock();
        try {
            balance += amount;
        } finally {
            /*
             * unlock 必须放在 finally 中。
             *
             * 如果临界区抛出异常，finally 仍然会执行，
             * 从而避免锁永远无法释放。
             */
            lock.unlock();
        }
    }

    /**
     * 取款。
     *
     * <p>检查余额和扣减余额必须放在同一个临界区中。
     * 如果检查和扣减之间没有同一把锁保护，就可能两个线程都看到余额足够，
     * 然后都扣款成功，导致余额被扣成负数。</p>
     *
     * @return 是否取款成功
     */
    public boolean withdraw(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }

        lock.lock();
        try {
            if (balance < amount) {
                return false;
            }
            balance -= amount;
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 查询余额。
     *
     * <p>读取共享状态也要加锁。
     * 加锁既能避免读到中间状态，也能建立内存可见性。</p>
     */
    public int getBalance() {
        lock.lock();
        try {
            return balance;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        final GuardingStateWithReentrantLock account =
                new GuardingStateWithReentrantLock(0);

        final int threadCount = 4;
        final int iterations = 10000;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();

                        for (int j = 0; j < iterations; j++) {
                            account.deposit(1);
                            boolean success = account.withdraw(1);
                            assertTrue(success, "withdraw should succeed after deposit");
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        endGate.countDown();
                    }
                }
            }, "account-worker-" + i);

            worker.start();
        }

        startGate.countDown();
        endGate.await();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        assertEquals(0, account.getBalance(), "final balance");
        System.out.println("13.2 guarding state with ReentrantLock passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
