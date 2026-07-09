package com.sherlock.concurrency.chapter12.detailed_12_13;

import com.sherlock.concurrency.chapter12.detailed_12_12.TimedPutTakeTest;

import java.util.concurrent.TimeUnit;

/**
 * TimedPutTakeTest 的驱动程序。
 *
 * <p>这是《Java 并发编程实战》中的 12.13。</p>
 *
 * <p>12.12 只是给一次生产者-消费者测试加上计时。
 * 12.13 则把它扩展成一个小型基准测试驱动程序：
 * 使用不同的缓冲区容量和不同的生产者/消费者数量反复运行，
 * 观察参数变化对吞吐量的影响。</p>
 */
public class TimedPutTakeTestDriver {

    /**
     * 每个生产者或消费者执行的操作次数。
     *
     * <p>官方示例中是 100000。
     * 本地示例调小到 20000，避免每次运行等待过久。</p>
     */
    private static final int TRIALS_PER_THREAD = 5000;

    /**
     * 缓冲区容量测试范围。
     *
     * <p>官方示例按 1、10、100、1000 递增。
     * 本地示例保留同样的增长方式。</p>
     */
    private static final int[] CAPACITIES = {1, 10, 100, 1000};

    /**
     * 生产者/消费者对数测试范围。
     *
     * <p>官方示例一直测到 128 对。
     * 本地示例测到 16 对，避免在普通开发机器上产生过长运行时间和过多线程。</p>
     */
    private static final int[] PAIRS = {1, 2, 4, 8};

    /**
     * 每组参数重复运行次数。
     *
     * <p>官方示例每组参数运行两次，中间 sleep 一段时间。
     * 第一轮通常更容易受到 JIT 预热、类加载、线程创建等因素影响；
     * 第二轮结果一般更接近稳定状态。</p>
     */
    private static final int RUNS_PER_CASE = 2;

    /**
     * 运行不同参数组合的计时测试。
     */
    public void run() throws InterruptedException {
        System.out.println("trialsPerThread=" + TRIALS_PER_THREAD);

        for (int capacity : CAPACITIES) {
            System.out.println("Capacity: " + capacity);

            for (int pairs : PAIRS) {
                System.out.print("Pairs: " + pairs);

                for (int run = 1; run <= RUNS_PER_CASE; run++) {
                    TimedPutTakeTest test =
                            new TimedPutTakeTest(capacity, pairs, TRIALS_PER_THREAD);

                    long elapsedNanos = test.test(false);
                    long itemCount = (long) pairs * TRIALS_PER_THREAD;
                    long nsPerItem = elapsedNanos / itemCount;

                    System.out.print("\trun" + run + "=" + nsPerItem + " ns/item");

                    /*
                     * 给 JVM、操作系统调度器和 GC 一点缓冲时间，
                     * 减少前一轮测试对后一轮测试的干扰。
                     */
                    TimeUnit.MILLISECONDS.sleep(100);
                }

                System.out.println();
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        TimedPutTakeTestDriver driver = new TimedPutTakeTestDriver();
        driver.run();
        System.out.println("12.13 timed put/take driver passed");
    }
}
