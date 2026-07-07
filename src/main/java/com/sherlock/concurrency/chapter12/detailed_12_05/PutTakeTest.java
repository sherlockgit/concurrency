package com.sherlock.concurrency.chapter12.detailed_12_05;

import com.sherlock.concurrency.chapter12.detailed_12_01.SemaphoreBoundedBuffer;
import com.sherlock.concurrency.chapter12.detailed_12_04.XorShift;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * BoundedBuffer 的生产者-消费者测试程序。
 *
 * <p>这是《Java 并发编程实战》中的 12.5。</p>
 *
 * <p>12.2 和 12.3 测试的是比较小的边界条件：
 * 构造后是否为空、放满后是否为满、空缓冲区上的 take 是否会阻塞并响应中断。</p>
 *
 * <p>12.5 更进一步，使用多个生产者和多个消费者同时操作同一个有界缓存，
 * 用来验证它在并发压力下是否还能保持正确性。</p>
 *
 * <p>测试思路如下：</p>
 *
 * <p>1. 创建 nPairs 个生产者和 nPairs 个消费者；</p>
 * <p>2. 每个生产者放入 nTrials 个整数；</p>
 * <p>3. 每个消费者取出 nTrials 个整数；</p>
 * <p>4. 所有生产者放入的整数之和记录到 putSum；</p>
 * <p>5. 所有消费者取出的整数之和记录到 takeSum；</p>
 * <p>6. 如果缓冲区没有丢失、重复或篡改元素，那么最终 putSum 应该等于 takeSum。</p>
 *
 * <p>这个测试不会检查每个元素的精确顺序。
 * 有界缓存允许生产者和消费者并发交错执行，所以顺序不是重点。
 * 重点是：放进去的总数据，最终都应该被取出来。</p>
 */
public class PutTakeTest {

    /**
     * 等待所有测试线程到达屏障或结束的最大时间。
     *
     * <p>官方示例直接调用 barrier.await()。
     * 本地示例加上超时，避免被测类出问题时测试永久挂住。</p>
     */
    private static final long TEST_TIMEOUT_SECONDS = 10;

    /**
     * 被测试的有界缓存。
     */
    private final SemaphoreBoundedBuffer<Integer> buffer;

    /**
     * 每个生产者/消费者执行的次数。
     */
    private final int nTrials;

    /**
     * 生产者-消费者对数。
     */
    private final int nPairs;

    /**
     * 启动屏障和结束屏障。
     *
     * <p>参与方数量是 nPairs * 2 + 1：
     * nPairs 个生产者、nPairs 个消费者，再加上主测试线程。</p>
     */
    private final CyclicBarrier barrier;

    /**
     * 所有生产者放入值的校验和。
     */
    private final AtomicInteger putSum = new AtomicInteger(0);

    /**
     * 所有消费者取出值的校验和。
     */
    private final AtomicInteger takeSum = new AtomicInteger(0);

    /**
     * 工作线程中的第一个失败。
     *
     * <p>普通线程或线程池任务里的异常不会自动让主测试线程失败。
     * 因此这里显式记录工作线程异常，在主线程等待结束后统一检查。</p>
     */
    private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

    public PutTakeTest(int capacity, int nPairs, int nTrials) {
        this.buffer = new SemaphoreBoundedBuffer<Integer>(capacity);
        this.nTrials = nTrials;
        this.nPairs = nPairs;
        this.barrier = new CyclicBarrier(nPairs * 2 + 1);
    }

    /**
     * 执行生产者-消费者并发测试。
     */
    public void test() {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            for (int i = 0; i < nPairs; i++) {
                pool.execute(new Producer());
                pool.execute(new Consumer());
            }

            /*
             * 第一次 await：主线程等待所有生产者/消费者都准备好。
             * 所有线程都到达后，它们会几乎同时开始执行 put/take 循环。
             */
            awaitBarrierUnchecked("start");

            /*
             * 第二次 await：主线程等待所有生产者/消费者都完成。
             */
            awaitBarrierUnchecked("finish");

            rethrowWorkerFailureIfAny();
            assertEquals(putSum.get(), takeSum.get(), "putSum should equal takeSum");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 生产者任务。
     *
     * <p>官方 12.6 会专门展示 Producer/Consumer 类。
     * 为了让 12.5 文件单独可运行，这里先把它作为内部类保留。</p>
     */
    private class Producer implements Runnable {

        @Override
        public void run() {
            try {
                XorShift random = new XorShift(hashCode() ^ (int) System.nanoTime());
                int sum = 0;

                awaitBarrier("producer ready");
                for (int i = nTrials; i > 0; --i) {
                    int value = random.next();
                    buffer.put(value);
                    sum += value;
                }
                putSum.getAndAdd(sum);
                awaitBarrier("producer done");
            } catch (Throwable t) {
                recordFailure(t);
            }
        }
    }

    /**
     * 消费者任务。
     */
    private class Consumer implements Runnable {

        @Override
        public void run() {
            try {
                int sum = 0;

                awaitBarrier("consumer ready");
                for (int i = nTrials; i > 0; --i) {
                    sum += buffer.take();
                }
                takeSum.getAndAdd(sum);
                awaitBarrier("consumer done");
            } catch (Throwable t) {
                recordFailure(t);
            }
        }
    }

    private void awaitBarrier(String phase)
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void recordFailure(Throwable throwable) {
        failure.compareAndSet(null, throwable);
    }

    private void rethrowWorkerFailureIfAny() {
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private void awaitBarrierUnchecked(String phase) {
        try {
            awaitBarrier(phase);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting at " + phase + " barrier", e);
        } catch (BrokenBarrierException e) {
            throw new AssertionError("barrier broken at " + phase, e);
        } catch (TimeoutException e) {
            throw new AssertionError("timeout while waiting at " + phase + " barrier", e);
        }
    }

    /**
     * 简单测试入口。
     */
    public static void main(String[] args) {
        PutTakeTest test = new PutTakeTest(10, 4, 10000);
        test.test();
        System.out.println("12.5 put/take test passed");
    }
}
