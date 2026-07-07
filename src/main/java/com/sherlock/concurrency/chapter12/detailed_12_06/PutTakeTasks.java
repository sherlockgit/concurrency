package com.sherlock.concurrency.chapter12.detailed_12_06;

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
 * 生产者和消费者任务。
 *
 * <p>这是《Java 并发编程实战》中的 12.6。</p>
 *
 * <p>12.5 展示的是完整的生产者-消费者测试程序，
 * 12.6 重点展示其中真正执行并发操作的两个任务：{@link Producer} 和 {@link Consumer}。</p>
 *
 * <p>测试目标不是证明每个元素的精确顺序，而是证明：</p>
 *
 * <p>1. 所有生产者放入缓冲区的整数，最终都能被消费者取出；</p>
 * <p>2. 缓冲区没有丢失元素、重复返回元素、或者返回被破坏的元素；</p>
 * <p>3. 因此所有 put 的校验和应该等于所有 take 的校验和。</p>
 */
public class PutTakeTasks {

    /**
     * 等待屏障的最长时间。
     *
     * <p>书上的示例直接调用 {@code barrier.await()}。
     * 本地示例加上超时，是为了避免代码出错时测试线程永久挂住。</p>
     */
    private static final long TEST_TIMEOUT_SECONDS = 10;

    /**
     * 被测对象：使用信号量实现的有界缓冲区。
     */
    private final SemaphoreBoundedBuffer<Integer> buffer;

    /**
     * 每个生产者或者消费者执行的操作次数。
     */
    private final int nTrials;

    /**
     * 生产者和消费者的配对数量。
     *
     * <p>{@code nPairs = 4} 表示 4 个生产者和 4 个消费者。</p>
     */
    private final int nPairs;

    /**
     * 启动屏障和结束屏障。
     *
     * <p>参与方数量是 {@code nPairs * 2 + 1}：
     * nPairs 个生产者、nPairs 个消费者，再加上主测试线程。</p>
     */
    private final CyclicBarrier barrier;

    /**
     * 所有生产者放入数据的校验和。
     */
    private final AtomicInteger putSum = new AtomicInteger(0);

    /**
     * 所有消费者取出数据的校验和。
     */
    private final AtomicInteger takeSum = new AtomicInteger(0);

    /**
     * 保存工作线程中的第一个异常。
     *
     * <p>如果工作线程只是把异常抛出到自己的线程中，主测试线程不一定能感知到。
     * 因此这里显式记录异常，并在主测试线程完成等待后统一检查。</p>
     */
    private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

    public PutTakeTasks(int capacity, int nPairs, int nTrials) {
        this.buffer = new SemaphoreBoundedBuffer<Integer>(capacity);
        this.nTrials = nTrials;
        this.nPairs = nPairs;
        this.barrier = new CyclicBarrier(nPairs * 2 + 1);
    }

    /**
     * 启动生产者和消费者，并验证最终结果。
     */
    public void test() {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            for (int i = 0; i < nPairs; i++) {
                pool.execute(new Producer());
                pool.execute(new Consumer());
            }

            /*
             * 第一次 await：让所有任务都先准备好。
             *
             * 主线程、所有生产者、所有消费者都会在这里等待。
             * 当最后一个线程到达屏障时，所有线程才会一起继续执行。
             * 这样可以尽量避免某个生产者过早跑完，导致测试并发压力不足。
             */
            awaitBarrierUnchecked("start");

            /*
             * 第二次 await：等待所有任务都执行完 put/take 循环。
             *
             * 所有 Producer 和 Consumer 在 run 方法末尾也会等待这个屏障。
             * 主线程通过这次等待，确认它们都完成了自己的 nTrials 次操作。
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
     * <p>每个生产者会生成 {@code nTrials} 个伪随机整数，
     * 把它们放入有界缓冲区，并把这些整数累加到本地变量 {@code sum} 中。</p>
     *
     * <p>注意：它不是每放入一个元素就更新 {@link #putSum}，
     * 而是先在本地变量中累加，最后只调用一次 {@code getAndAdd}。
     * 这样可以减少多个生产者同时更新同一个 {@code AtomicInteger} 带来的竞争。</p>
     */
    private class Producer implements Runnable {

        @Override
        public void run() {
            try {
                /*
                 * 每个生产者使用自己的伪随机数生成器。
                 *
                 * 如果所有线程共享同一个 Random，那么 Random 自身可能变成竞争热点，
                 * 测试结果就会混入随机数生成器的同步开销，而不是单纯反映缓冲区的并发行为。
                 */
                XorShift random = new XorShift(hashCode() ^ (int) System.nanoTime());
                int sum = 0;

                /*
                 * 等待统一开始。
                 */
                awaitBarrier();

                /*
                 * 循环生产数据。
                 *
                 * 如果缓冲区已满，buffer.put 会阻塞；
                 * 如果实现有并发错误，这里可能出现丢数据、重复数据、死锁等问题。
                 */
                for (int i = nTrials; i > 0; --i) {
                    int value = random.next();
                    buffer.put(value);
                    sum += value;
                }

                /*
                 * 生产者完成后，把本线程生产过的所有值一次性加入总校验和。
                 */
                putSum.getAndAdd(sum);

                /*
                 * 等待统一结束，通知主测试线程当前生产者已经完成。
                 */
                awaitBarrier();
            } catch (Throwable t) {
                recordFailure(t);
            }
        }
    }

    /**
     * 消费者任务。
     *
     * <p>每个消费者会从缓冲区取出 {@code nTrials} 个整数，
     * 并把这些整数累加到本地变量 {@code sum} 中。</p>
     *
     * <p>和生产者一样，消费者也是最后只更新一次 {@link #takeSum}，
     * 用来降低校验和变量本身对测试造成的干扰。</p>
     */
    private class Consumer implements Runnable {

        @Override
        public void run() {
            try {
                int sum = 0;

                /*
                 * 等待统一开始。
                 *
                 * 生产者和消费者同时冲击同一个缓冲区，
                 * 才更容易暴露缓冲区实现中的并发问题。
                 */
                awaitBarrier();

                /*
                 * 循环消费数据。
                 *
                 * 如果缓冲区为空，buffer.take 会阻塞；
                 * 只要生产者最终放入了足够的数据，每个消费者都应该能取到 nTrials 个元素。
                 */
                for (int i = nTrials; i > 0; --i) {
                    sum += buffer.take();
                }

                /*
                 * 消费者完成后，把本线程取出的所有值一次性加入总校验和。
                 */
                takeSum.getAndAdd(sum);

                /*
                 * 等待统一结束，通知主测试线程当前消费者已经完成。
                 */
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

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) {
        PutTakeTasks test = new PutTakeTasks(10, 4, 10000);
        test.test();
        System.out.println("12.6 producer/consumer tasks test passed");
    }
}
