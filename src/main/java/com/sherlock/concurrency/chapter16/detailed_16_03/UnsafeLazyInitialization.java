package com.sherlock.concurrency.chapter16.detailed_16_03;

import com.sherlock.concurrency.annoations.NotThreadSafe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 不安全的延迟初始化。
 *
 * <p>这是《Java 并发编程实战》中的 16.3。</p>
 *
 * <p>看起来很自然的“为空时才创建”并不是线程安全的。多个线程可能同时看到
 * resource == null，随后各自创建 Resource 并互相覆盖对 resource 的赋值。</p>
 *
 * <p>此外，resource 既不是 volatile，也没有通过锁来保护。即使某线程已经
 * 写入 resource，另一个线程也没有 Happens-Before 关系保证能及时看到该引用，
 * 更没有安全发布 Resource 内普通字段的保证。</p>
 */
@NotThreadSafe
public class UnsafeLazyInitialization {

    /**
     * 这是书中示例的关键：普通静态引用，未使用 volatile，也未受锁保护。
     */
    private static Resource resource;

    /**
     *
     * <p>这是故意错误的实现。</p>
     *
     * <p>“检查 resource 是否为空”和“创建并赋值”不是一个原子操作。
     * 两个线程可能同时通过 if 判断，并分别执行 new Resource()。</p>
     */
    public static Resource getInstance() {
        if (resource == null) {
            resource = new Resource(); // 不安全发布
        }
        return resource;
    }

    /**
     * 仅用于运行示例。
     *
     * <p>生产代码不应通过这种方式重置单例。</p>
     */
    private static void resetForDemo() {
        resource = null;
        Resource.resetConstructionCount();
    }

    /**
     * 被延迟创建的资源。
     */
    static class Resource {

        /**
         * 为演示“可能构造多次”而设置的计数器。
         * 它不参与 getInstance 的同步，也不会修复其中的竞态。
         */
        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        private final int id = CONSTRUCTIONS.incrementAndGet();

        /**
         * 普通字段，用于强调 Resource 本身也没有被安全发布。
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
     * 演示入口。
     *
     * <p>多个线程由同一个起跑信号同时调用 getInstance。运行时可能观察到创建多个
     * Resource 的现象，但竞态是否在某一次运行中暴露取决于调度，未观察到不代表安全。</p>
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
            }, "unsafe-lazy-caller-" + i);
            caller.start();
        }

        if (!ready.await(1, TimeUnit.SECONDS)) {
            throw new AssertionError("demo threads did not become ready");
        }
        start.countDown();

        if (!finished.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("demo threads did not finish");
        }

        System.out.println("16.3 unsafe lazy initialization demo");
        System.out.println("constructed Resource count = " + Resource.getConstructionCount());
        System.out.println("Resource ids returned to callers = " + returnedResourceIds);
        System.out.println("initialization markers observed = " + observedMarkers);
        System.out.println("A count greater than 1 demonstrates the check-then-act race.");
        System.out.println("A count of 1 in one run does NOT make this implementation thread-safe.");
    }
}
