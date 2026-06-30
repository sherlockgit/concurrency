package com.sherlock.concurrency.chapter10.detailed_10_07;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 打印死锁后的线程转储信息。
 *
 * <p>这是对《Java 并发编程实战》10.7 的本地补充。</p>
 *
 * <p>官方 10.7 不是源码，而是一段线程转储片段。
 * 这个 demo 用代码稳定制造一个和 10.1 类似的锁顺序死锁，
 * 然后通过 {@link ThreadMXBean} 打印出死锁线程的详细信息，
 * 方便观察线程 dump 中的关键信息。</p>
 *
 * <p>重点观察：</p>
 *
 * <p>1. 线程状态通常是 BLOCKED；</p>
 * <p>2. 每个线程正在等待哪把锁；</p>
 * <p>3. 每个线程当前已经持有哪些锁；</p>
 * <p>4. 两个线程之间是否形成“你等我，我等你”的等待环。</p>
 */
public class ThreadDumpAfterDeadlockDemo {

    private final Object left = new Object();

    private final Object right = new Object();

    public static void main(String[] args) throws InterruptedException {
        ThreadDumpAfterDeadlockDemo demo = new ThreadDumpAfterDeadlockDemo();
        demo.runDemo();
    }

    private void runDemo() throws InterruptedException {
        final CountDownLatch firstLocksAcquired = new CountDownLatch(2);

        Thread leftThenRight = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (left) {
                    firstLocksAcquired.countDown();
                    awaitQuietly(firstLocksAcquired);
                    synchronized (right) {
                        doSomething();
                    }
                }
            }
        }, "left-then-right");

        Thread rightThenLeft = new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (right) {
                    firstLocksAcquired.countDown();
                    awaitQuietly(firstLocksAcquired);
                    synchronized (left) {
                        doSomethingElse();
                    }
                }
            }
        }, "right-then-left");

        /*
         * 设置为 daemon，避免示例真的死锁后阻止 JVM 退出。
         * main 线程打印完线程转储信息后，进程可以自然结束。
         */
        leftThenRight.setDaemon(true);
        rightThenLeft.setDaemon(true);

        leftThenRight.start();
        rightThenLeft.start();

        printDeadlockedThreadDump();
    }

    private void doSomething() {
    }

    private void doSomethingElse() {
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 等待 JVM 识别死锁，并打印死锁线程的线程转储信息。
     */
    private static void printDeadlockedThreadDump() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (int i = 0; i < 20; i++) {
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreadIds != null) {
                ThreadInfo[] threadInfos =
                        threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);

                System.out.println("Found one Java-level deadlock:");
                System.out.println();

                for (ThreadInfo threadInfo : threadInfos) {
                    printThreadInfo(threadInfo);
                    System.out.println();
                }
                return;
            }

            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("deadlock not detected in time");
    }

    /**
     * 打印比 ThreadInfo.toString() 更聚焦的线程信息。
     *
     * <p>真实 jstack 输出会更长。这里保留最关键的几项：
     * 线程名、线程状态、正在等待的锁、锁持有者、当前线程已经持有的监视器锁。</p>
     */
    private static void printThreadInfo(ThreadInfo threadInfo) {
        System.out.println('"' + threadInfo.getThreadName() + '"');
        System.out.println("  Thread state = " + threadInfo.getThreadState());
        System.out.println("  Waiting for lock = " + threadInfo.getLockName());
        System.out.println("  Lock owner = " + threadInfo.getLockOwnerName());

        if (threadInfo.getLockedMonitors().length > 0) {
            System.out.println("  Locked monitors:");
            for (int i = 0; i < threadInfo.getLockedMonitors().length; i++) {
                System.out.println("    " + threadInfo.getLockedMonitors()[i]);
            }
        }

        StackTraceElement[] stackTrace = threadInfo.getStackTrace();
        if (stackTrace.length > 0) {
            System.out.println("  Top stack frame = " + stackTrace[0]);
        }
    }
}
