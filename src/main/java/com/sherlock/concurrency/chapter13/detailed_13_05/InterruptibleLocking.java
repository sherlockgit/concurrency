package com.sherlock.concurrency.chapter13.detailed_13_05;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 可中断的锁获取操作。
 *
 * <p>这是《Java 并发编程实战》中的 13.5。</p>
 *
 * <p>{@link Lock#lockInterruptibly()} 与 {@link Lock#lock()} 的区别是：
 * 如果线程正在等待锁，并且此时被中断，那么 {@code lockInterruptibly}
 * 会抛出 {@link InterruptedException}，让线程有机会退出等待。</p>
 *
 * <p>这对于支持取消、服务关闭、避免线程长时间卡死非常有用。</p>
 */
public class InterruptibleLocking {

    /**
     * 保护共享线路的锁。
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 在共享线路上发送消息。
     *
     * <p>如果当前锁被其他线程持有，调用线程会等待。
     * 但由于这里使用的是 {@code lockInterruptibly()}，
     * 等待期间如果调用线程被中断，就会抛出 {@link InterruptedException}。</p>
     */
    public boolean sendOnSharedLine(String message) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            return cancellableSendOnSharedLine(message);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 模拟可取消的发送操作。
     *
     * <p>真实场景中这里可能是一个可中断的 I/O 或长时间操作。
     * 本地示例用 sleep 模拟，并保留 {@code throws InterruptedException}，
     * 表示发送过程本身也支持取消。</p>
     */
    private boolean cancellableSendOnSharedLine(String message) throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(message.length());
        return true;
    }

    /**
     * 持有锁一段时间，用于制造另一个线程等待锁的场景。
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
        final InterruptibleLocking locking = new InterruptibleLocking();
        final CountDownLatch holderHasLock = new CountDownLatch(1);
        final AtomicBoolean interruptedWhileWaiting = new AtomicBoolean(false);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread holder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    /*
                     * 先手动拿到锁，确保 waiter 之后会卡在 lockInterruptibly 上。
                     */
                    locking.lock.lock();
                    try {
                        holderHasLock.countDown();
                        TimeUnit.MILLISECONDS.sleep(500);
                    } finally {
                        locking.lock.unlock();
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "shared-line-holder");

        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    holderHasLock.await();
                    locking.sendOnSharedLine("hello");
                    failure.compareAndSet(null, new AssertionError("waiter should have been interrupted"));
                } catch (InterruptedException expected) {
                    /*
                     * 预期路径：
                     * waiter 正在等待 lockInterruptibly 获取锁时，被主线程中断。
                     */
                    interruptedWhileWaiting.set(true);
                    Thread.currentThread().interrupt();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "interruptible-lock-waiter");

        holder.start();
        waiter.start();

        holderHasLock.await();
        TimeUnit.MILLISECONDS.sleep(100);
        waiter.interrupt();

        waiter.join(1000);
        holder.join();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        assertFalse(waiter.isAlive(), "waiter should exit after interruption");
        assertTrue(interruptedWhileWaiting.get(), "waiter should observe interruption while waiting for lock");

        boolean sent = locking.sendOnSharedLine("ok");
        assertTrue(sent, "send should succeed after lock is free");

        System.out.println("13.5 interruptible locking passed");
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
