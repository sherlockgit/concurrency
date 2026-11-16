package com.sherlock.concurrency.chapter16.detailed_16_06;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 延迟初始化占位类模式（Initialization-on-demand holder idiom）。
 *
 * <p>这是《Java 并发编程实战》中的 16.6。</p>
 *
 * <p>外部类 ResourceFactory 被初始化时，不会初始化静态内部类 ResourceHolder。
 * 只有第一次执行 getResource() 并访问 ResourceHolder.resource 时，JVM 才初始化
 * ResourceHolder。类初始化的“至多一次”和安全发布保证，使该模式同时具备延迟性和线程安全性。</p>
 */
@ThreadSafe
public class ResourceFactory {

    /**
     * 静态内部占位类。
     *
     * <p>ResourceHolder 的初始化由 JVM 单独管理，不会因为外部类已经加载而立即发生。
     * 第一次主动读取 resource 时，JVM 会执行 resource = new Resource()，
     * 并把构造好的对象安全发布给所有后续调用者。</p>
     */
    private static class ResourceHolder {

        /**
         * 书中的关键字段：不需要 volatile，也不需要显式 synchronized。
         * 本例不会在初始化后修改它。
         */
        public static Resource resource = new Resource();
    }

    /**
     * 按需获取 Resource。
     *
     * <p>调用此方法前，ResourceHolder 尚未初始化；首次访问其静态字段会触发 JVM
     * 对 ResourceHolder 的类初始化。后续调用只返回已经创建好的同一实例。</p>
     */
    public static Resource getResource() {
        return ResourceFactory.ResourceHolder.resource;
    }

    /**
     * 演示用资源。
     */
    static class Resource {

        /**
         * 仅用于验证延迟创建和构造次数，不参与发布机制。
         */
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        private final int id = CONSTRUCTIONS.incrementAndGet();

        /**
         * 普通字段，会随着 ResourceHolder 的类初始化被安全发布。
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
     */
    public static void main(String[] args) throws Exception {
        /*
         * Resource 类可以被引用，但只要尚未访问 ResourceHolder.resource，
         * Resource 实例就还没有被创建。
         */
        assertEquals(0, Resource.getConstructionCount(),
                "Resource should not be created before the first getResource call");

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
            }, "resource-holder-caller-" + i);
            caller.start();
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS), "demo threads did not become ready");
        start.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "demo threads did not finish");

        assertEquals(1, Resource.getConstructionCount(), "Resource should be constructed once");
        assertEquals(1, returnedResourceIds.size(), "all callers should receive the same instance");
        assertTrue(observedMarkers.size() == 1 && observedMarkers.contains(42),
                "all callers should observe the initialized state");

        System.out.println("16.6 initialization-on-demand holder passed");
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
