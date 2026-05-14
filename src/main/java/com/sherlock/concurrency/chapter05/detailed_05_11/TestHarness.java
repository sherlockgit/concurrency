package com.sherlock.concurrency.chapter05.detailed_05_11;

import java.util.concurrent.CountDownLatch;

/**
 * 在计时测试中使用CountDownLatch来启动和停止线程
 * * *
 * 闭锁是一种同步工具类，可以延迟线程的进度直到其到达终止状态。闭锁的作用相当于一扇门:在闭锁到达结束状态之前，
 * * 这扇门一直是关闭的，并且没有任何线程能通过，当到达结束状态时，这扇门会打开并允许所有的线程通过。
 * * 当闭锁到达结束状态后，将不会再改变状态，因此这扇门将永远保持打开状态。闭锁可以用来确保某些活动直到其他活动都完成后才继续执行
 */
public class TestHarness {

    public long timeTasks(int nThreads, final Runnable task)
            throws InterruptedException {
        // 开始门闩，初始为1，用于让所有线程同时开始
        final CountDownLatch startGate = new CountDownLatch(1);
        // 结束门闩，初始为 nThreads，用于等待所有线程执行完毕
        final CountDownLatch endGate = new CountDownLatch(nThreads);

        // 创建并启动 nThreads 个工作线程
        for (int i = 0; i < nThreads; i++) {
            Thread t = new Thread() {
                public void run() {
                    try {
                        // 等待开始门闩打开，所有线程在此阻塞，确保同时启动
                        startGate.await();
                        try {
                            // 执行传入的任务
                            task.run();
                        } finally {
                            // 任务完成后（无论是否异常），将结束门闩计数减一
                            endGate.countDown();
                        }
                    } catch (InterruptedException ignored) {
                        // 忽略中断异常，线程直接退出
                    }
                }
            };
            t.start(); // 启动线程，但此时因 startGate 未打开，线程阻塞在 await()
        }

        // 记录开始时间（纳秒级）
        long start = System.nanoTime();
        // 打开开始门闩，所有线程同时开始执行任务
        startGate.countDown();
        // 等待结束门闩变为0，即所有线程执行完毕
        endGate.await();
        // 记录结束时间
        long end = System.nanoTime();

        // 返回总耗时（纳秒）
        return end - start;
    }

}
