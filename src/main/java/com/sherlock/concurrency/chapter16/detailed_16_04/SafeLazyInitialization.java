package com.sherlock.concurrency.chapter16.detailed_16_04;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程安全的延迟初始化。
 *
 * <p>这是《Java 并发编程实战》中的 16.4。</p>
 *
 * <p>16.3 的问题在于：检查 resource 是否为空和创建 Resource 之间没有同步。
 * 本例把 getInstance 声明为 static synchronized，使这两个步骤处于同一个临界区。</p>
 */
@ThreadSafe
public class SafeLazyInitialization {

    /**
     * 不必声明为 volatile。
     *
     * <p>因为所有读取和写入都只能通过 synchronized getInstance 完成，
     * 并由 SafeLazyInitialization.class 的监视器锁保护。</p>
     */
    private static Resource resource;

    /**
     * 获取唯一的 Resource 实例。
     *
     * <p>static synchronized 等价于：</p>
     *
     * <pre>
     * synchronized (SafeLazyInitialization.class) {
     *     // 方法体
     * }
     * </pre>
     *
     * <p>同一时刻只有一个线程可以执行 if 判断和创建操作。
     * 第一个线程创建实例并退出方法后，释放类监视器锁；后续线程获取同一把锁时，
     * 必须能看到 resource 的最新值。</p>
     */
    public static synchronized Resource getInstance() {
        if (resource == null) {
            resource = new Resource();
        }
        return resource;
    }

    /**
     * 仅供 main 中的独立演示使用。
     *
     * <p>真实单例通常不应支持重置；这里同步重置，以免破坏 getInstance 的锁保护约定。</p>
     */
    private static synchronized void resetForDemo() {
        resource = null;
        Resource.resetConstructionCount();
    }

    /**
     * 被延迟创建的资源。
     */
    static class Resource {

        /**
         * 只用于验证构造次数。它不参与 resource 的线程安全实现。
         */
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        private final int id = CONSTRUCTIONS.incrementAndGet();

        /**
         * 普通字段。通过 getInstance 的锁发布后，读取者也能正确看到它的值。
         */
        private int initializationMarker;

        Resource() {
            initializationMarker = 42;
        }

        int getId() {
            return id;
        }

        int getInitializationMarker() {
            return initializationMarker;
        }

        static int getConstructionCount() {
            return CONSTRUCTIONS.get();
        }

        static void resetConstructionCount() {
            CONSTRUCTIONS.set(0);
        }
    }

    /**
     * 并发验证入口。
     */
    public static void main(String[] args) throws Exception {
        final int threadCount = 32;
        resetForDemo();

        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(threadCount);
        final Set<Integer> returnedResourceIds = ConcurrentHashMap.<Integer>newKeySet();
        final Set<Integer> observedMarkers = ConcurrentHashMap.<Integer>newKeySet();

        for (int i = 0; i < threadCount; i++) {
            Thread caller = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();

                        Resource instance = getInstance();
                        returnedResourceIds.add(instance.getId());
                        observedMarkers.add(instance.getInitializationMarker());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("demo thread was interrupted", e);
                    } finally {
                        finished.countDown();
                    }
                }
            }, "safe-lazy-caller-" + i);
            caller.start();
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS), "demo threads did not become ready");
        start.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "demo threads did not finish");

        assertEquals(1, Resource.getConstructionCount(), "Resource should be constructed once");
        assertEquals(1, returnedResourceIds.size(), "all callers should receive the same instance");
        assertTrue(observedMarkers.size() == 1 && observedMarkers.contains(42),
                "all callers should observe the initialized state");

        System.out.println("16.4 safe lazy initialization passed");
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
