package com.sherlock.concurrency.chapter12.detailed_12_11;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * 基于 CyclicBarrier 的计时器。
 *
 * <p>这是《Java 并发编程实战》中的 12.11。</p>
 *
 * <p>这个类通常作为 {@link CyclicBarrier} 的 barrier action 使用。
 * 所谓 barrier action，就是所有参与线程都到达屏障后，
 * 屏障在放行这些线程之前会自动执行的那段回调代码。</p>
 *
 * <p>这个计时器利用同一个屏障回调执行两次：</p>
 *
 * <p>1. 第一次所有线程到达屏障时，记录开始时间；</p>
 * <p>2. 第二次所有线程到达屏障时，记录结束时间；</p>
 * <p>3. 两次时间之差就是并发测试主体的运行耗时。</p>
 */
public class BarrierTimer implements Runnable {

    /**
     * 标记是否已经记录过开始时间。
     *
     * <p>第一次执行 {@link #run()} 时为 false，用来记录 startTime。
     * 第二次执行 {@link #run()} 时为 true，用来记录 endTime。</p>
     */
    private boolean started;

    /**
     * 开始时间，单位是纳秒。
     */
    private long startTime;

    /**
     * 结束时间，单位是纳秒。
     */
    private long endTime;

    /**
     * 屏障动作。
     *
     * <p>这个方法会被 {@link CyclicBarrier} 自动调用，而不是由测试代码直接调用。
     * 因此它也是一个回调。</p>
     *
     * <p>方法加 synchronized 是为了保证 started、startTime、endTime 的可见性和原子性。
     * 虽然 barrier action 一次只会由一个到达屏障的线程执行，
     * 但测试主线程后续会调用 {@link #getTime()} 读取这些字段，
     * 使用同一把锁能建立清晰的同步关系。</p>
     */
    @Override
    public synchronized void run() {
        long now = System.nanoTime();

        if (!started) {
            started = true;
            startTime = now;
        } else {
            endTime = now;
        }
    }

    /**
     * 清空计时器状态，方便重复使用。
     */
    public synchronized void clear() {
        started = false;
    }

    /**
     * 获取开始和结束之间的耗时。
     *
     * @return 纳秒级耗时
     */
    public synchronized long getTime() {
        return endTime - startTime;
    }

    /**
     * 简单运行入口。
     *
     * <p>这个演示创建 4 个工作线程和 1 个主线程共同参与屏障。
     * 第一次屏障记录开始时间，第二次屏障记录结束时间。</p>
     */
    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        final int workerCount = 4;
        final BarrierTimer timer = new BarrierTimer();
        final CyclicBarrier barrier = new CyclicBarrier(workerCount + 1, timer);

        for (int i = 0; i < workerCount; i++) {
            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        /*
                         * 等待统一开始。
                         *
                         * 当所有工作线程和主线程都到达这里时，
                         * barrier 会先执行 timer.run()，记录 startTime。
                         */
                        barrier.await();

                        /*
                         * 模拟测试主体。
                         */
                        TimeUnit.MILLISECONDS.sleep(100);

                        /*
                         * 等待统一结束。
                         *
                         * 当所有工作线程和主线程都到达这里时，
                         * barrier 会再次执行 timer.run()，记录 endTime。
                         */
                        barrier.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (BrokenBarrierException e) {
                        throw new AssertionError("barrier broken", e);
                    }
                }
            }, "barrier-worker-" + i);

            worker.start();
        }

        /*
         * 主线程参加第一次屏障：触发开始计时。
         */
        barrier.await();

        /*
         * 主线程参加第二次屏障：触发结束计时。
         */
        barrier.await();

        long elapsedNanos = timer.getTime();
        assertTrue(elapsedNanos > 0, "elapsed time should be positive");

        System.out.println("elapsedMillis=" + TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        System.out.println("12.11 barrier timer passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
