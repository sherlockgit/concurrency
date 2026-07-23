package com.sherlock.concurrency.chapter15.detailed_15_04;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter15.PseudoRandom;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用 ReentrantLock 保护 seed 的伪随机数生成器。
 *
 * <p>这是《Java 并发编程实战》中的 15.4。</p>
 *
 * <p>这个类和前面的 CAS 计数器形成对比：</p>
 *
 * <p>1. CAS 计数器通过 CAS 重试循环修改共享状态；</p>
 * <p>2. 这个随机数生成器通过 ReentrantLock 保护共享状态；</p>
 * <p>3. 两者的目标都是保证“读取旧值、计算新值、写回新值”这组动作不会被并发破坏。</p>
 */
@ThreadSafe
public class ReentrantLockPseudoRandom extends PseudoRandom {

    /**
     * 保护 seed 的锁。
     *
     * <p>只要访问 seed，就必须先持有这把锁。</p>
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 伪随机数生成器的当前种子。
     *
     * <p>seed 不是 volatile，因为所有读写都发生在 lock/unlock 之间。
     * ReentrantLock 的释放和获取能提供内存可见性。</p>
     */
    private int seed;

    public ReentrantLockPseudoRandom(int seed) {
        this.seed = seed;
    }

    /**
     * 生成下一个伪随机数。
     *
     * <p>整个方法的临界区是：</p>
     *
     * <p>1. 读取旧 seed；</p>
     * <p>2. 根据旧 seed 计算新 seed；</p>
     * <p>3. 把新 seed 写回字段。</p>
     *
     * <p>如果不加锁，多个线程可能同时读到同一个旧 seed，
     * 然后计算出相同的新 seed，导致随机序列推进次数丢失。</p>
     */
    @Override
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }

        lock.lock();
        try {
            int currentSeed = seed;
            seed = calculateNext(currentSeed);

            int remainder = currentSeed % n;

            /*
             * 这段写法贴近书中的示例。
             * 它把负余数转换成正数，并把余数 0 映射成 n，
             * 因此返回范围是 1 到 n，而不是 0 到 n - 1。
             */
            return remainder > 0 ? remainder : remainder + n;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 当前 seed。
     *
     * <p>这个方法不是书中示例的核心 API，只用于本地断言。</p>
     */
    int currentSeedForTest() {
        lock.lock();
        try {
            return seed;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSameSeedProducesSameSequence();
        testConcurrentCallsAdvanceSeedSafely();

        System.out.println("15.4 ReentrantLock pseudo random passed");
    }

    /**
     * 验证：相同初始 seed 会产生相同序列。
     */
    private static void testSameSeedProducesSameSequence() {
        ReentrantLockPseudoRandom first = new ReentrantLockPseudoRandom(17);
        ReentrantLockPseudoRandom second = new ReentrantLockPseudoRandom(17);

        for (int i = 0; i < 20; i++) {
            int firstValue = first.nextInt(1000);
            int secondValue = second.nextInt(1000);

            assertEquals(firstValue, secondValue, "same seed should produce same sequence");
            assertTrue(firstValue >= 1 && firstValue <= 1000,
                    "value should be in range 1..n");
        }
    }

    /**
     * 验证：并发调用 nextInt 时，seed 的推进不会丢失。
     *
     * <p>如果没有锁保护，多个线程可能同时基于同一个旧 seed 计算新 seed，
     * 最终 seed 推进次数就会小于实际调用次数。</p>
     */
    private static void testConcurrentCallsAdvanceSeedSafely() throws Exception {
        final int initialSeed = 31;
        final int threadCount = 4;
        final int callsPerThread = 1000;
        final ReentrantLockPseudoRandom random = new ReentrantLockPseudoRandom(initialSeed);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();

                        for (int j = 0; j < callsPerThread; j++) {
                            int value = random.nextInt(1000);
                            if (value < 1 || value > 1000) {
                                throw new AssertionError("value out of range: " + value);
                            }
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }
            }, "lock-pseudo-random-worker-" + i);

            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertNoFailure(failure);

        int expectedSeed = initialSeed;
        for (int i = 0; i < threadCount * callsPerThread; i++) {
            expectedSeed = random.calculateNext(expectedSeed);
        }

        assertEquals(expectedSeed, random.currentSeedForTest(),
                "seed should advance once per nextInt call");
    }

    private static void assertNoFailure(AtomicReference<Throwable> failure) {
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
