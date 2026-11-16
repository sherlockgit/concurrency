package com.sherlock.concurrency.chapter16.detailed_16_05;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 饿汉式初始化。
 *
 * <p>这是《Java 并发编程实战》中的 16.5。</p>
 *
 * <p>resource 会在 EagerInitialization 的类初始化阶段创建，而不是等到
 * 第一次调用 getResource() 时才创建。JVM 保证类初始化只会成功执行一次，
 * 并会把初始化期间的写入安全发布给之后使用该类的线程。</p>
 */
@ThreadSafe
public class EagerInitialization {

    /**
     * 书中的关键代码。
     *
     * <p>它不是 volatile，也没有由显式锁保护，但仍能安全发布：
     * 该赋值发生在 JVM 执行 EagerInitialization 的类初始化时。</p>
     *
     * <p>此字段后续不再被修改。如果允许其他线程在初始化后重新赋值，
     * 则仍然需要额外同步。</p>
     */
    private static Resource resource = new Resource();

    /**
     * 返回已经在类初始化阶段构造好的资源。
     *
     * <p>这个方法没有加锁，也不需要每次调用都读取 volatile 字段。
     * 它的安全性来自 JVM 的类初始化协议，而不是该方法本身的同步逻辑。</p>
     */
    public static Resource getResource() {
        return resource;
    }

    /**
     * 演示用资源。
     */
    static class Resource {

        /**
         * 仅用于验证资源只构造了一次，不参与 resource 的安全发布。
         */
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        private final int id = CONSTRUCTIONS.incrementAndGet();

        /**
         * 普通字段。JVM 的类初始化安全发布保证调用者能看到它的构造值。
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
    }

    /**
     * 并发验证入口。
     *
     * <p>注意：类初始化已经在 main 执行前完成，因此这不是用来“证明”JMM 的压力测试。
     * 它只是验证本例的结果：所有调用者都获得同一对象，并看到其初始化状态。</p>
     */
    public static void main(String[] args) throws Exception {
        final int threadCount = 32;
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

                        Resource instance = getResource();
                        returnedResourceIds.add(instance.getId());
                        observedMarkers.add(instance.getInitializationMarker());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("demo thread was interrupted", e);
                    } finally {
                        finished.countDown();
                    }
                }
            }, "eager-initialization-caller-" + i);
            caller.start();
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS), "demo threads did not become ready");
        start.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "demo threads did not finish");

        assertEquals(1, Resource.getConstructionCount(), "Resource should be constructed once");
        assertEquals(1, returnedResourceIds.size(), "all callers should receive the same instance");
        assertTrue(observedMarkers.size() == 1 && observedMarkers.contains(42),
                "all callers should observe the initialized state");

        System.out.println("16.5 eager initialization passed");
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
