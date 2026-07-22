package com.sherlock.concurrency.chapter15.detailed_15_02;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter15.detailed_15_01.SimulatedCAS;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 CAS 实现的非阻塞计数器。
 *
 * <p>这是《Java 并发编程实战》中的 15.2。</p>
 *
 * <p>这个例子建立在 15.1 的 {@link SimulatedCAS} 之上。
 * 真实 JVM 中通常不会用 synchronized 模拟 CAS，而是使用 CPU 提供的 CAS 指令。
 * 这里复用 SimulatedCAS，是为了把“CAS 重试循环”的算法结构讲清楚。</p>
 *
 * <p>所谓非阻塞计数器，核心不是“没有循环”，而是：</p>
 *
 * <p>1. 线程不会因为获取互斥锁而挂起；</p>
 * <p>2. 如果本次 CAS 失败，说明有其他线程已经成功推进了状态；</p>
 * <p>3. 当前线程重新读取新值，再尝试更新即可。</p>
 */
@ThreadSafe
public class CasCounter {

    /**
     * 保存计数值的 CAS 变量。
     *
     * <p>在真实代码中，可以直接使用 {@link java.util.concurrent.atomic.AtomicInteger}
     * 或 {@link java.util.concurrent.atomic.AtomicLong}。</p>
     */
    private final SimulatedCAS value;

    public CasCounter() {
        this(0);
    }

    public CasCounter(int initialValue) {
        this.value = new SimulatedCAS(initialValue);
    }

    /**
     * 获取当前计数值。
     */
    public int getValue() {
        return value.get();
    }

    /**
     * 计数器加一。
     *
     * <p>这是本例最核心的方法。它的流程可以读成：</p>
     *
     * <p>1. 先读取当前值 v；</p>
     * <p>2. 计算出目标新值 v + 1；</p>
     * <p>3. 只有当 value 当前仍然等于 v 时，才把它改成 v + 1；</p>
     * <p>4. 如果 CAS 失败，说明读取 v 之后，已经有别的线程修改了 value，
     * 因此重新读取并重试。</p>
     *
     * <p>这个 do-while 循环也叫 CAS 重试循环。</p>
     */
    public int increment() {
        int oldValue;
        int newValue;

        do {
            oldValue = value.get();
            newValue = oldValue + 1;

            /*
             * compareAndSwap 返回的是“执行 CAS 时看到的旧值”。
             * 如果返回值仍然等于 oldValue，说明 CAS 成功；
             * 如果不相等，说明当前值已经被其他线程改过，需要重试。
             */
        } while (oldValue != value.compareAndSwap(oldValue, newValue));

        return newValue;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSingleThreadIncrement();
        testConcurrentIncrement();

        System.out.println("15.2 CAS counter passed");
    }

    /**
     * 验证：单线程下，每次 increment 都会返回递增后的值。
     */
    private static void testSingleThreadIncrement() {
        CasCounter counter = new CasCounter(10);

        assertEquals(10, counter.getValue(), "initial value");
        assertEquals(11, counter.increment(), "value after first increment");
        assertEquals(12, counter.increment(), "value after second increment");
        assertEquals(12, counter.getValue(), "final value");
    }

    /**
     * 验证：多线程并发调用 increment，不会丢失更新。
     */
    private static void testConcurrentIncrement() throws Exception {
        final CasCounter counter = new CasCounter();
        final int threadCount = 4;
        final int incrementsPerThread = 1000;
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

                        for (int j = 0; j < incrementsPerThread; j++) {
                            counter.increment();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }
            }, "cas-counter-worker-" + i);

            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertNoFailure(failure);
        assertEquals(threadCount * incrementsPerThread, counter.getValue(),
                "concurrent increments should not lose updates");
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
}
