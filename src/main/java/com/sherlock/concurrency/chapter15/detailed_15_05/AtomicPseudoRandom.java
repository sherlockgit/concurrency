package com.sherlock.concurrency.chapter15.detailed_15_05;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter15.PseudoRandom;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 AtomicInteger 保护 seed 的伪随机数生成器。
 *
 * <p>这是《Java 并发编程实战》中的 15.5。</p>
 *
 * <p>这个类和 15.4 的 ReentrantLock 版本形成对比：</p>
 *
 * <p>1. 15.4 用锁保护 seed 的读取、计算和写回；</p>
 * <p>2. 本例用 AtomicInteger 的 compareAndSet 保护 seed 的更新；</p>
 * <p>3. 如果 CAS 失败，说明其他线程已经推进了 seed，当前线程重新读取并重试。</p>
 */
@ThreadSafe
public class AtomicPseudoRandom extends PseudoRandom {

    /**
     * 使用 AtomicInteger 保存当前 seed。
     *
     * <p>AtomicInteger 内部使用 volatile 读写和 CAS，能保证可见性和原子更新。</p>
     */
    private final AtomicInteger seed;

    public AtomicPseudoRandom(int seed) {
        this.seed = new AtomicInteger(seed);
    }

    /**
     * 生成下一个伪随机数。
     *
     * <p>这个方法的核心是 CAS 重试循环：</p>
     *
     * <p>1. 读取当前 seed；</p>
     * <p>2. 根据当前 seed 计算 nextSeed；</p>
     * <p>3. 用 compareAndSet(currentSeed, nextSeed) 尝试更新；</p>
     * <p>4. 成功则返回，失败则说明 seed 已被其他线程改过，需要重试。</p>
     *
     * <p>和锁版本相比，这里不会因为获取锁失败而阻塞挂起线程。
     * 但如果竞争非常激烈，CAS 可能连续失败并反复重试。</p>
     */
    @Override
    public int nextInt(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n must be positive");
        }

        for (;;) {
            int currentSeed = seed.get();
            int nextSeed = calculateNext(currentSeed);

            if (seed.compareAndSet(currentSeed, nextSeed)) {
                int remainder = currentSeed % n;

                /*
                 * 这段写法贴近书中的示例。
                 * 返回范围是 1 到 n，而不是 JDK Random.nextInt(n) 的 0 到 n - 1。
                 */
                return remainder > 0 ? remainder : remainder + n;
            }
        }
    }

    /**
     * 当前 seed。
     *
     * <p>这个方法不是书中示例的核心 API，只用于本地断言。</p>
     */
    int currentSeedForTest() {
        return seed.get();
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSameSeedProducesSameSequence();
        testConcurrentCallsAdvanceSeedSafely();

        System.out.println("15.5 AtomicInteger pseudo random passed");
    }

    /**
     * 验证：相同初始 seed 会产生相同序列。
     */
    private static void testSameSeedProducesSameSequence() {
        AtomicPseudoRandom first = new AtomicPseudoRandom(17);
        AtomicPseudoRandom second = new AtomicPseudoRandom(17);

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
     * <p>CAS 失败并不表示算法失败，而是表示其他线程已经成功推进了 seed。
     * 当前线程重新读取新 seed 后再试，最终仍能保证每次 nextInt 都推进一次 seed。</p>
     */
    private static void testConcurrentCallsAdvanceSeedSafely() throws Exception {
        final int initialSeed = 31;
        final int threadCount = 4;
        final int callsPerThread = 1000;
        final AtomicPseudoRandom random = new AtomicPseudoRandom(initialSeed);
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
            }, "atomic-pseudo-random-worker-" + i);

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
