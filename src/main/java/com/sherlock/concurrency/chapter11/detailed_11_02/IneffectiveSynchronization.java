package com.sherlock.concurrency.chapter11.detailed_11_02;

import java.util.concurrent.CountDownLatch;

/**
 * 没有效果的同步。
 *
 * <p>这是《Java 并发编程实战》中的 11.2。</p>
 *
 * <p>这个例子展示一种常见误区：
 * 方法里确实写了 synchronized，但同步使用的锁不是共享锁，
 * 因此不能保护共享状态。</p>
 *
 * <p>同步要生效，必须满足一个条件：
 * 所有线程访问同一份共享状态时，必须竞争同一把锁。</p>
 *
 * <p>如果每次调用方法都创建一个新的锁对象，
 * 那么不同线程进入方法时拿到的是不同锁。
 * 它们并不会互斥，仍然可以同时修改共享变量。</p>
 */
public class IneffectiveSynchronization {

    /**
     * 多个线程共同修改的共享状态。
     */
    private int count;

    /**
     * 错误示例：同步在局部新建的对象上。
     *
     * <p>这个 synchronized 没有效果。
     * 因为 lock 是局部变量，每次调用都会 new 一个新的对象。
     * 不同线程不会竞争同一把锁，所以 count++ 仍然是并发执行的。</p>
     */
    public void ineffectiveIncrement() {
        Object lock = new Object();
        synchronized (lock) {
            count++;
        }
    }

    /**
     * 正确示例：同步在当前对象上。
     *
     * <p>所有线程调用这个方法时，竞争的都是同一个 IneffectiveSynchronization 实例的对象锁。
     * 因此 count++ 可以被互斥保护。</p>
     */
    public synchronized void effectiveIncrement() {
        count++;
    }

    public int getCount() {
        return count;
    }

    private void reset() {
        count = 0;
    }

    /**
     * 简单演示。
     *
     * <p>由于并发错误具有时序依赖，错误示例不是每次都一定丢失更新。
     * 但在足够多线程和足够多循环下，通常能看到 ineffectiveIncrement 的结果小于期望值。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        IneffectiveSynchronization demo = new IneffectiveSynchronization();

        int threads = 8;
        int iterations = 200000;
        int expected = threads * iterations;

        runTest(threads, iterations, new RunnableFactory() {
            @Override
            public Runnable create(final IneffectiveSynchronization target,
                                   final CountDownLatch startGate,
                                   final CountDownLatch doneGate,
                                   final int loopCount) {
                return new Runnable() {
                    @Override
                    public void run() {
                        awaitQuietly(startGate);
                        for (int i = 0; i < loopCount; i++) {
                            target.ineffectiveIncrement();
                        }
                        doneGate.countDown();
                    }
                };
            }
        }, demo);

        System.out.println("ineffective expected=" + expected
                + ", actual=" + demo.getCount());

        demo.reset();

        runTest(threads, iterations, new RunnableFactory() {
            @Override
            public Runnable create(final IneffectiveSynchronization target,
                                   final CountDownLatch startGate,
                                   final CountDownLatch doneGate,
                                   final int loopCount) {
                return new Runnable() {
                    @Override
                    public void run() {
                        awaitQuietly(startGate);
                        for (int i = 0; i < loopCount; i++) {
                            target.effectiveIncrement();
                        }
                        doneGate.countDown();
                    }
                };
            }
        }, demo);

        System.out.println("effective expected=" + expected
                + ", actual=" + demo.getCount());
    }

    private static void runTest(int threads,
                                int iterations,
                                RunnableFactory runnableFactory,
                                IneffectiveSynchronization target)
            throws InterruptedException {
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threads);

        for (int i = 0; i < threads; i++) {
            Thread thread = new Thread(
                    runnableFactory.create(target, startGate, doneGate, iterations),
                    "increment-thread-" + i);
            thread.start();
        }

        startGate.countDown();
        doneGate.await();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private interface RunnableFactory {

        Runnable create(IneffectiveSynchronization target,
                        CountDownLatch startGate,
                        CountDownLatch doneGate,
                        int loopCount);
    }
}
