package com.sherlock.concurrency.chapter13.detailed_13_04;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 带时间预算的加锁。
 *
 * <p>这是《Java 并发编程实战》中的 13.4。</p>
 *
 * <p>本例展示 {@link Lock#tryLock(long, TimeUnit)} 的一个典型用途：
 * 整个操作有一个总时间预算，等待锁只能使用其中的一部分。
 * 如果等待锁用掉了太多时间，即使最终拿到锁，也可能没有足够时间完成真正的操作。</p>
 */
public class TimedLocking {

    /**
     * 保护共享线路的锁。
     *
     * <p>可以把 shared line 想象成一条一次只能被一个线程使用的共享通信线路。</p>
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 尝试在共享线路上发送消息。
     *
     * <p>timeout 是整个操作的时间预算，而不是单纯等待锁的时间。</p>
     *
     * <p>因此要先估算发送消息本身需要多久：
     * 如果总预算是 100ms，预计发送需要 30ms，
     * 那么最多只能花 70ms 等锁。
     * 如果等锁超过 70ms，即使拿到锁，也可能无法在 100ms 内完成整个发送操作。</p>
     *
     * @return true 表示在时间预算内完成发送，false 表示无法完成
     */
    public boolean trySendOnSharedLine(String message,
                                       long timeout,
                                       TimeUnit unit)
            throws InterruptedException {
        long timeoutNanos = unit.toNanos(timeout);
        long estimatedSendNanos = estimatedNanosToSend(message);
        long nanosToLock = timeoutNanos - estimatedSendNanos;

        /*
         * 如果预计发送时间已经超过总预算，就没有必要等待锁。
         */
        if (nanosToLock <= 0) {
            return false;
        }

        /*
         * 只把“剩余预算”用于等待锁。
         *
         * tryLock 会在以下两种情况返回：
         * 1. 在超时前获得锁，返回 true；
         * 2. 等到超时仍然没有获得锁，返回 false。
         */
        if (!lock.tryLock(nanosToLock, TimeUnit.NANOSECONDS)) {
            return false;
        }

        try {
            return sendOnSharedLine(message);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 模拟发送消息。
     *
     * <p>真实示例里这里会执行 I/O。
     * 本地示例用 sleep 模拟发送耗时。</p>
     */
    private boolean sendOnSharedLine(String message) throws InterruptedException {
        TimeUnit.NANOSECONDS.sleep(estimatedNanosToSend(message));
        return true;
    }

    /**
     * 估算发送消息需要的时间。
     *
     * <p>官方示例直接返回 {@code message.length()}。
     * 本地示例为了让运行结果更容易观察，把每个字符估算为 1ms。</p>
     */
    long estimatedNanosToSend(String message) {
        return TimeUnit.MILLISECONDS.toNanos(message.length());
    }

    /**
     * 持有共享线路一段时间，用于演示等待锁超时。
     */
    private void holdSharedLine(long time, TimeUnit unit) throws InterruptedException {
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
        final TimedLocking timedLocking = new TimedLocking();

        boolean enoughBudget = timedLocking.trySendOnSharedLine(
                "hello",
                50,
                TimeUnit.MILLISECONDS);
        assertTrue(enoughBudget, "send should succeed when budget is enough");

        boolean notEnoughBudget = timedLocking.trySendOnSharedLine(
                "this-message-is-too-long",
                5,
                TimeUnit.MILLISECONDS);
        assertFalse(notEnoughBudget, "send should fail when send cost exceeds budget");

        Thread holder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    timedLocking.holdSharedLine(300, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "shared-line-holder");

        holder.start();
        TimeUnit.MILLISECONDS.sleep(50);

        boolean lockTimeout = timedLocking.trySendOnSharedLine(
                "short",
                100,
                TimeUnit.MILLISECONDS);
        assertFalse(lockTimeout, "send should fail when lock cannot be acquired within budget");

        holder.join();

        System.out.println("13.4 timed locking passed");
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
