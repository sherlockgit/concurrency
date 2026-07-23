package com.sherlock.concurrency.chapter15.detailed_15_03;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 用 CAS 维护多个变量之间的不变式。
 *
 * <p>这是《Java 并发编程实战》中的 15.3。</p>
 *
 * <p>这个例子要解决的问题是：<code>lower <= upper</code>。
 * 如果 lower 和 upper 分开用两个原子变量保存，那么它们之间的不变式就很难同时维护。
 * 书里的做法是把它们封装进一个不可变对象，再用一个 AtomicReference 整体替换。</p>
 */
@ThreadSafe
public class CasNumberRange {

    /**
     * 不可变范围对象。
     *
     * <p>lower 和 upper 必须一起满足 lower <= upper。
     * 一旦构造完成，就不再修改。</p>
     */
    private static class IntPair {
        final int lower;
        final int upper;

        IntPair(int lower, int upper) {
            if (lower > upper) {
                throw new IllegalArgumentException("lower cannot be greater than upper");
            }
            this.lower = lower;
            this.upper = upper;
        }
    }

    /**
     * 原子持有整个范围对象。
     *
     * <p>更新范围时，不是分别修改 lower/upper，
     * 而是构造一个新的 IntPair，然后一次性 CAS 替换掉旧对象。</p>
     */
    private final AtomicReference<IntPair> values = new AtomicReference<IntPair>(new IntPair(0, 0));

    public int getLower() {
        return values.get().lower;
    }

    public int getUpper() {
        return values.get().upper;
    }

    /**
     * 设置下界。
     *
     * <p>更新流程如下：</p>
     *
     * <p>1. 读取当前范围快照 oldValue；</p>
     * <p>2. 检查新的 lower 是否仍然不大于 oldValue.upper；</p>
     * <p>3. 构造新的 IntPair；</p>
     * <p>4. CAS 替换成功则结束，失败则说明有别的线程改过范围，重新读取重试。</p>
     */
    public void setLower(int lower) {
        for (;;) {
            IntPair oldValue = values.get();
            if (lower > oldValue.upper) {
                throw new IllegalArgumentException("lower cannot be greater than upper");
            }

            IntPair newValue = new IntPair(lower, oldValue.upper);
            if (values.compareAndSet(oldValue, newValue)) {
                return;
            }
        }
    }

    /**
     * 设置上界。
     */
    public void setUpper(int upper) {
        for (;;) {
            IntPair oldValue = values.get();
            if (upper < oldValue.lower) {
                throw new IllegalArgumentException("upper cannot be less than lower");
            }

            IntPair newValue = new IntPair(oldValue.lower, upper);
            if (values.compareAndSet(oldValue, newValue)) {
                return;
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSingleThreadInvariantChecks();
        testConcurrentSafeUpdates();

        System.out.println("15.3 CAS number range passed");
    }

    /**
     * 验证：单线程下可以正常修改范围，越界修改会被拒绝。
     */
    private static void testSingleThreadInvariantChecks() {
        CasNumberRange range = new CasNumberRange();

        range.setUpper(10);
        range.setLower(1);
        assertEquals(1, range.getLower(), "lower after setLower");
        assertEquals(10, range.getUpper(), "upper after setUpper");

        boolean lowerFailed = false;
        try {
            range.setLower(20);
        } catch (IllegalArgumentException expected) {
            lowerFailed = true;
        }
        assertTrue(lowerFailed, "setting lower above upper should fail");

        boolean upperFailed = false;
        try {
            range.setUpper(0);
        } catch (IllegalArgumentException expected) {
            upperFailed = true;
        }
        assertTrue(upperFailed, "setting upper below lower should fail");

        assertEquals(1, range.getLower(), "lower should remain unchanged");
        assertEquals(10, range.getUpper(), "upper should remain unchanged");
    }

    /**
     * 验证：并发安全更新不会破坏不变式。
     *
     * <p>一个线程只负责降低 lower，另一个线程只负责提高 upper。
     * 由于每次更新都先检查再 CAS，因此最终范围仍然满足 lower <= upper。</p>
     */
    private static void testConcurrentSafeUpdates() throws Exception {
        final CasNumberRange range = new CasNumberRange();
        range.setUpper(20);
        range.setLower(10);

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread lowerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ready.countDown();
                    start.await();

                    for (int i = 9; i >= 0; i--) {
                        range.setLower(i);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "cas-range-lower");

        Thread upperThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ready.countDown();
                    start.await();

                    for (int i = 21; i <= 30; i++) {
                        range.setUpper(i);
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "cas-range-upper");

        lowerThread.start();
        upperThread.start();
        ready.await();
        start.countDown();
        lowerThread.join();
        upperThread.join();

        assertNoFailure(failure);
        assertTrue(range.getLower() <= range.getUpper(), "invariant lower <= upper should hold");
        assertEquals(0, range.getLower(), "final lower");
        assertEquals(30, range.getUpper(), "final upper");
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
