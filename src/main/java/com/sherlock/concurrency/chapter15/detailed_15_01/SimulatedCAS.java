package com.sherlock.concurrency.chapter15.detailed_15_01;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 用 synchronized 模拟 CAS 操作。
 *
 * <p>这是《Java 并发编程实战》中的 15.1。</p>
 *
 * <p>CAS 是 Compare-And-Swap 的缩写，可以理解为：</p>
 *
 * <p>1. 先读取变量当前的旧值；</p>
 * <p>2. 判断旧值是否等于调用方期望的值；</p>
 * <p>3. 如果相等，就把变量改成新值；</p>
 * <p>4. 如果不相等，说明变量已经被别的线程改过，本次更新失败。</p>
 *
 * <p>真正的 CAS 通常由 CPU 指令直接支持，属于无锁算法的基础原语。
 * 本例没有使用 CPU 的 CAS 指令，而是用 synchronized 把整个比较和替换过程保护起来，
 * 从而模拟出相同的原子语义。</p>
 */
@ThreadSafe
public class SimulatedCAS {

    /**
     * 被 CAS 保护的值。
     *
     * <p>这里没有写 volatile，因为所有访问都发生在 synchronized 方法中。
     * synchronized 同时提供互斥性和可见性。</p>
     */
    private int value;

    public SimulatedCAS() {
        this(0);
    }

    public SimulatedCAS(int initialValue) {
        this.value = initialValue;
    }

    /**
     * 获取当前值。
     *
     * <p>读取也加锁，是为了保证读到的是最新值。</p>
     */
    public synchronized int get() {
        return value;
    }

    /**
     * 比较并交换。
     *
     * <p>这是本例最核心的方法。整个方法被 synchronized 保护，
     * 因此同一时刻只能有一个线程执行下面的逻辑：</p>
     *
     * <p>1. 读取当前旧值 oldValue；</p>
     * <p>2. 如果 oldValue 等于 expectedValue，就把 value 改成 newValue；</p>
     * <p>3. 无论是否替换成功，都返回最开始读到的 oldValue。</p>
     *
     * <p>注意：这个方法返回的是“操作前看到的旧值”，不是 boolean。
     * 调用方可以通过 oldValue 是否等于 expectedValue 来判断更新是否成功。</p>
     */
    public synchronized int compareAndSwap(int expectedValue, int newValue) {
        int oldValue = value;
        if (oldValue == expectedValue) {
            value = newValue;
        }
        return oldValue;
    }

    /**
     * 比较并设置。
     *
     * <p>这是 compareAndSwap 的 boolean 版本：</p>
     *
     * <p>1. 如果返回 true，表示当前值等于 expectedValue，并且已经被改成 newValue；</p>
     * <p>2. 如果返回 false，表示当前值不等于 expectedValue，没有发生修改。</p>
     *
     * <p>这里在 synchronized 方法中继续调用 synchronized 方法没有问题，
     * 因为 Java 的内置锁是可重入锁。</p>
     */
    public synchronized boolean compareAndSet(int expectedValue, int newValue) {
        return expectedValue == compareAndSwap(expectedValue, newValue);
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) {
        testCompareAndSwapSuccess();
        testCompareAndSwapFailure();
        testCompareAndSet();

        System.out.println("15.1 simulated CAS passed");
    }

    /**
     * 验证：期望值和当前值相等时，compareAndSwap 会更新成功。
     */
    private static void testCompareAndSwapSuccess() {
        SimulatedCAS cas = new SimulatedCAS(10);

        int oldValue = cas.compareAndSwap(10, 20);

        assertEquals(10, oldValue, "compareAndSwap should return old value");
        assertEquals(20, cas.get(), "value should be updated when expected value matches");
    }

    /**
     * 验证：期望值和当前值不相等时，compareAndSwap 不会修改当前值。
     */
    private static void testCompareAndSwapFailure() {
        SimulatedCAS cas = new SimulatedCAS(10);

        int oldValue = cas.compareAndSwap(99, 20);

        assertEquals(10, oldValue, "compareAndSwap should still return old value");
        assertEquals(10, cas.get(), "value should not change when expected value mismatches");
    }

    /**
     * 验证：compareAndSet 把 compareAndSwap 的旧值判断转换成 boolean。
     */
    private static void testCompareAndSet() {
        SimulatedCAS cas = new SimulatedCAS(10);

        assertTrue(cas.compareAndSet(10, 20), "compareAndSet should return true on success");
        assertEquals(20, cas.get(), "value should be updated after successful compareAndSet");

        assertFalse(cas.compareAndSet(10, 30), "compareAndSet should return false on failure");
        assertEquals(20, cas.get(), "value should stay unchanged after failed compareAndSet");
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

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
