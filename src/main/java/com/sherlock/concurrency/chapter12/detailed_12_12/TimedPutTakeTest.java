package com.sherlock.concurrency.chapter12.detailed_12_12;

import com.sherlock.concurrency.chapter12.detailed_12_01.SemaphoreBoundedBuffer;
import com.sherlock.concurrency.chapter12.detailed_12_04.XorShift;
import com.sherlock.concurrency.chapter12.detailed_12_11.BarrierTimer;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 BarrierTimer 计时的生产者-消费者测试。
 *
 * <p>这是《Java 并发编程实战》中的 12.12。</p>
 *
 * <p>12.5 的 {@code PutTakeTest} 只验证功能正确性：
 * 所有生产者放入的整数之和，最终应该等于所有消费者取出的整数之和。</p>
 *
 * <p>12.12 在这个基础上增加计时逻辑：
 * 使用 12.11 的 {@link BarrierTimer} 作为 {@link CyclicBarrier} 的 barrier action，
 * 让所有工作线程统一开始、统一结束，并统计测试主体的运行耗时。</p>
 */
public class TimedPutTakeTest {

    /**
     * 等待屏障的最长时间。
     *
     * <p>给屏障等待加超时，是为了避免测试出错时主线程永久挂住。</p>
     */
    private static final long TEST_TIMEOUT_SECONDS = 10;

    /**
     * 被测试的有界缓冲区。
     */
    private final SemaphoreBoundedBuffer<Integer> buffer;

    /**
     * 每个生产者或者消费者执行的次数。
     */
    private final int nTrials;

    /**
     * 生产者和消费者的配对数量。
     */
    private final int nPairs;

    /**
     * 屏障计时器。
     *
     * <p>它会被 {@link CyclicBarrier} 自动回调两次：
     * 第一次记录开始时间，第二次记录结束时间。</p>
     */
    private final BarrierTimer timer = new BarrierTimer();

    /**
     * 启动屏障和结束屏障。
     *
     * <p>参与方数量为 {@code nPairs * 2 + 1}：
     * nPairs 个生产者、nPairs 个消费者，再加上主测试线程。</p>
     *
     * <p>和 12.5 不同的是，这里给屏障传入了 {@code timer}。
     * 所有参与方都到达屏障时，屏障会先执行 {@code timer.run()}，然后再放行。</p>
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
     * 记录工作线程中的第一个失败。
     */
    private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

    public TimedPutTakeTest(int capacity, int nPairs, int nTrials) {
        this.buffer = new SemaphoreBoundedBuffer<Integer>(capacity);
        this.nTrials = nTrials;
        this.nPairs = nPairs;
        this.barrier = new CyclicBarrier(nPairs * 2 + 1, timer);
    }

    /**
     * 执行带计时的生产者-消费者测试。
     *
     * @return 测试主体耗时，单位是纳秒
     */
    public long test() {
        return test(true);
    }

    /**
     * 执行带计时的生产者-消费者测试。
     *
     * @param verbose 是否在当前方法中直接打印计时结果
     * @return 测试主体耗时，单位是纳秒
     */
    public long test(boolean verbose) {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            timer.clear();

            for (int i = 0; i < nPairs; i++) {
                pool.execute(new Producer());
                pool.execute(new Consumer());
            }

            /*
             * 第一次 await：统一起跑。
             *
             * 当主线程、所有生产者、所有消费者都到达这里时，
             * barrier 会执行 timer.run()，记录 startTime。
             * 之后所有线程才真正开始执行 put/take 循环。
             */
            awaitBarrierUnchecked("start");

            /*
             * 第二次 await：统一结束。
             *
             * 当所有工作线程都完成 put/take 循环并到达这里时，
             * 主线程也到达屏障，barrier 会再次执行 timer.run()，记录 endTime。
             */
            awaitBarrierUnchecked("finish");

            rethrowWorkerFailureIfAny();
            assertEquals(putSum.get(), takeSum.get(), "putSum should equal takeSum");

            long elapsedNanos = timer.getTime();
            long itemCount = (long) nPairs * nTrials;
            long nsPerItem = elapsedNanos / itemCount;
            long nsPerOperation = elapsedNanos / (itemCount * 2);

            if (verbose) {
                System.out.println("items=" + itemCount);
                System.out.println("elapsedNanos=" + elapsedNanos);
                System.out.println("nsPerItem=" + nsPerItem);
                System.out.println("nsPerPutOrTake=" + nsPerOperation);
            }

            return elapsedNanos;
        } finally {
            pool.shutdownNow();
            awaitTerminationQuietly(pool);
        }
    }

    /**
     * 生产者任务。
     *
     * <p>每个生产者生成 {@code nTrials} 个伪随机整数，
     * 放入缓冲区，并记录本线程生产值的局部校验和。</p>
     */
    private class Producer implements Runnable {

        @Override
        public void run() {
            try {
                XorShift random = new XorShift(hashCode() ^ (int) System.nanoTime());
                int sum = 0;

                awaitBarrier();
                for (int i = nTrials; i > 0; --i) {
                    int value = random.next();
                    buffer.put(value);
                    sum += value;
                }
                putSum.getAndAdd(sum);
                awaitBarrier();
            } catch (Throwable t) {
                recordFailure(t);
            }
        }
    }

    /**
     * 消费者任务。
     *
     * <p>每个消费者从缓冲区取出 {@code nTrials} 个整数，
     * 并记录本线程消费值的局部校验和。</p>
     */
    private class Consumer implements Runnable {

        @Override
        public void run() {
            try {
                int sum = 0;

                awaitBarrier();
                for (int i = nTrials; i > 0; --i) {
                    sum += buffer.take();
                }
                takeSum.getAndAdd(sum);
                awaitBarrier();
            } catch (Throwable t) {
                recordFailure(t);
            }
        }
    }

    private void awaitBarrier()
            throws InterruptedException, BrokenBarrierException, TimeoutException {
        barrier.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void awaitBarrierUnchecked(String phase) {
        try {
            awaitBarrier();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting at " + phase + " barrier", e);
        } catch (BrokenBarrierException e) {
            throw new AssertionError("barrier broken at " + phase, e);
        } catch (TimeoutException e) {
            throw new AssertionError("timeout while waiting at " + phase + " barrier", e);
        }
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

    private void awaitTerminationQuietly(ExecutorService pool) {
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) {
        TimedPutTakeTest test = new TimedPutTakeTest(10, 4, 10000);
        test.test();
        System.out.println("12.12 timed put/take test passed");
    }
}
