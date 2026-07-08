package com.sherlock.concurrency.chapter12.detailed_12_09;

import com.sherlock.concurrency.chapter12.detailed_12_08.TestingThreadFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 验证线程池是否会扩展到预期大小。
 *
 * <p>这是《Java 并发编程实战》中的 12.9。</p>
 *
 * <p>12.8 提供了一个测试用的 {@link TestingThreadFactory}，
 * 它会记录线程池到底创建了多少个线程。</p>
 *
 * <p>12.9 使用这个工厂来测试固定大小线程池：
 * 当提交的任务足够多，并且已有任务一直占用工作线程时，
 * 线程池应该持续创建工作线程，直到达到固定线程池的大小。</p>
 */
public class ThreadPoolExpansionTest {

    /**
     * 固定线程池的大小。
     *
     * <p>测试目标就是验证线程池最终创建了这么多个工作线程。</p>
     */
    private static final int MAX_SIZE = 10;

    /**
     * 提交任务数量。
     *
     * <p>这里提交远多于线程池大小的任务，是为了给线程池足够压力。
     * 前 MAX_SIZE 个任务会占住工作线程，后续任务会进入队列等待。</p>
     */
    private static final int TASK_COUNT = 10 * MAX_SIZE;

    /**
     * 等待线程池扩展时的轮询次数。
     */
    private static final int MAX_RETRIES = 20;

    /**
     * 每次轮询之间的等待时间。
     */
    private static final long RETRY_INTERVAL_MILLIS = 100;

    /**
     * 测试用线程工厂。
     *
     * <p>线程池每创建一个工作线程，都会调用这个工厂的
     * {@link TestingThreadFactory#newThread(Runnable)} 方法，
     * 因此 {@link TestingThreadFactory#numCreated} 就能记录线程创建数量。</p>
     */
    private final TestingThreadFactory threadFactory = new TestingThreadFactory();

    /**
     * 测试线程池扩展。
     *
     * <p>注意：这里必须使用 {@code Executors.newFixedThreadPool(MAX_SIZE, threadFactory)}。
     * 如果只调用 {@code Executors.newFixedThreadPool(MAX_SIZE)}，
     * 线程池会使用 JDK 默认线程工厂，测试中的 {@code threadFactory.numCreated}
     * 就永远不会增加。</p>
     */
    public void testPoolExpansion() throws InterruptedException {
        ExecutorService exec = Executors.newFixedThreadPool(MAX_SIZE, threadFactory);

        try {
            /*
             * 提交一批长期阻塞的任务。
             *
             * 每个任务启动后都会 sleep 很久，不会很快结束。
             * 这样第一个任务占住第一个工作线程，第二个任务占住第二个工作线程，
             * 线程池为了继续处理后续任务，就会继续创建工作线程，直到达到 MAX_SIZE。
             */
            for (int i = 0; i < TASK_COUNT; i++) {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(Long.MAX_VALUE);
                        } catch (InterruptedException e) {
                            /*
                             * 测试结束时 shutdownNow 会中断这些阻塞任务。
                             * 这里恢复中断状态，然后让任务自然退出。
                             */
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }

            /*
             * 等待线程池有时间创建工作线程。
             *
             * 并发测试不要假设提交任务后线程一定立刻创建完成。
             * 所以这里最多等待 20 次，每次 100ms。
             */
            for (int i = 0;
                 i < MAX_RETRIES && threadFactory.numCreated.get() < MAX_SIZE;
                 i++) {
                Thread.sleep(RETRY_INTERVAL_MILLIS);
            }

            assertEquals(MAX_SIZE, threadFactory.numCreated.get(), "created thread count");
        } finally {
            exec.shutdownNow();
            exec.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        ThreadPoolExpansionTest test = new ThreadPoolExpansionTest();
        test.testPoolExpansion();
        System.out.println("12.9 thread pool expansion test passed");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
