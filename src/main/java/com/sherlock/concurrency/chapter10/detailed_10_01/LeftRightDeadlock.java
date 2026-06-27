package com.sherlock.concurrency.chapter10.detailed_10_01;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 简单的锁顺序死锁。
 *
 * <p>这是《Java 并发编程实战》中的 10.1。</p>
 *
 * <p>这个例子的核心问题是：
 * 两个方法获取同一组锁，但获取顺序相反。</p>
 *
 * <p>{@link #leftRight()} 的加锁顺序是：</p>
 * <p>1. 先获取 left；</p>
 * <p>2. 再获取 right。</p>
 *
 * <p>{@link #rightLeft()} 的加锁顺序是：</p>
 * <p>1. 先获取 right；</p>
 * <p>2. 再获取 left。</p>
 *
 * <p>如果线程 A 正在执行 leftRight，并且已经拿到 left；
 * 同时线程 B 正在执行 rightLeft，并且已经拿到 right；
 * 那么线程 A 会等待线程 B 释放 right，线程 B 会等待线程 A 释放 left。
 * 两个线程互相等待，谁也无法继续执行，这就是死锁。</p>
 *
 * <p>避免这类死锁的基本原则是：
 * 所有线程在获取多把锁时，都必须使用一致的锁顺序。</p>
 */
public class LeftRightDeadlock {

    /**
     * 第一把锁。
     */
    private final Object left = new Object();

    /**
     * 第二把锁。
     */
    private final Object right = new Object();

    /**
     * 先获取 left，再获取 right。
     */
    public void leftRight() {
        synchronized (left) {
            synchronized (right) {
                doSomething();
            }
        }
    }

    /**
     * 先获取 right，再获取 left。
     *
     * <p>这个方法和 {@link #leftRight()} 的加锁顺序相反，
     * 因此两个方法被不同线程并发调用时，存在死锁风险。</p>
     */
    public void rightLeft() {
        synchronized (right) {
            synchronized (left) {
                doSomethingElse();
            }
        }
    }

    void doSomething() {
    }

    void doSomethingElse() {
    }

    /**
     * 演示死锁检测。
     *
     * <p>为了稳定复现死锁，这里的演示线程没有直接调用 leftRight/rightLeft，
     * 而是在拿到第一把锁后用 CountDownLatch 等待对方也拿到第一把锁，
     * 然后再同时尝试获取第二把锁。</p>
     *
     * <p>两个演示线程被设置为 daemon。
     * 这样即使它们真的死锁了，main 线程检测并打印结果后，JVM 也能退出。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        final LeftRightDeadlock demo = new LeftRightDeadlock();
        final CountDownLatch firstLocksAcquired = new CountDownLatch(2);

        Thread leftThenRight = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (demo.left) {
                    firstLocksAcquired.countDown();
                    awaitQuietly(firstLocksAcquired);
                    synchronized (demo.right) {
                        demo.doSomething();
                    }
                }
            }
        }, "left-then-right");

        Thread rightThenLeft = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (demo.right) {
                    firstLocksAcquired.countDown();
                    awaitQuietly(firstLocksAcquired);
                    synchronized (demo.left) {
                        demo.doSomethingElse();
                    }
                }
            }
        }, "right-then-left");

        leftThenRight.setDaemon(true);
        rightThenLeft.setDaemon(true);

        leftThenRight.start();
        rightThenLeft.start();

        printDetectedDeadlock();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printDetectedDeadlock() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (int i = 0; i < 20; i++) {
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreadIds != null) {
                ThreadInfo[] threadInfos =
                        threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
                System.out.println("deadlock detected:");
                for (ThreadInfo threadInfo : threadInfos) {
                    System.out.println(threadInfo.getThreadName()
                            + " waiting for " + threadInfo.getLockName());
                }
                return;
            }

            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("deadlock not detected in time");
    }
}
