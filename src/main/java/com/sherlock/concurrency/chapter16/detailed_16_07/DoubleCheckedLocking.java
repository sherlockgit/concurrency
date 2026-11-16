package com.sherlock.concurrency.chapter16.detailed_16_07;

import com.sherlock.concurrency.annoations.NotThreadSafe;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 双重检查锁定反模式。
 *
 * <p>这是《Java 并发编程实战》中的 16.7。</p>
 *
 * <p>本类故意保留错误实现：resource 没有声明为 volatile。
 * 因此某个线程可能在锁外看到非 null 的 resource，却没有看到 Resource 构造器
 * 对普通字段的全部写入。这是“不安全发布”问题。</p>
 *
 * <p>Java 5 及之后，只有将 resource 声明为 volatile，双重检查锁定才可以正确使用。
 * 16.7 中的代码没有 volatile，所以仍然是反模式。</p>
 */
@NotThreadSafe
public class DoubleCheckedLocking {

    /**
     * 故意不是 volatile，这是本例不安全的根源。
     */
    private static Resource resource;

    /**
     * 双重检查锁定。
     *
     * <p>第一次检查位于锁外：资源已经创建后，大多数调用直接返回，避免每次调用都加锁。</p>
     *
     * <p>第二次检查位于锁内：多个线程可能同时在锁外看到 null；第一个线程创建完成后，
     * 后面获得锁的线程必须再次检查，避免重复创建对象。</p>
     *
     * <p>注意：这里并没有“获取两次锁”。一次调用最多只会获取一次
     * DoubleCheckedLocking.class 的监视器锁；“双重”指的是对 resource == null
     * 进行了两次检查。</p>
     */
    public static Resource getInstance() {
        if (resource == null) {
            synchronized (DoubleCheckedLocking.class) {
                if (resource == null) {
                    resource = new Resource(); // 非 volatile 引用的不安全发布
                }
            }
        }
        return resource;
    }

    /**
     * 仅用于 main 中的独立演示。
     *
     * <p>真实单例不应提供重置操作。这里发生在启动工作线程之前，
     * 因而不会与 getInstance 并发执行。</p>
     */
    private static void resetForDemo() {
        resource = null;
        Resource.resetConstructionCount();
    }

    /**
     * 演示用资源。
     */
    static class Resource {

        private static final AtomicInteger CONSTRUCTIONS = new AtomicInteger();

        private final int id = CONSTRUCTIONS.incrementAndGet();

        /**
         * 故意使用普通非 final、非 volatile 字段。
         *
         * <p>在 resource 不安全发布的情况下，另一个线程理论上可能观察到默认值 0，
         * 而不是构造器写入的 42。该现象是否在某次运行中复现，取决于 JVM、处理器和时序。</p>
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
     * 并发演示入口。
     *
     * <p>此测试即使全部通过，也不能证明当前实现线程安全。
     * 内存模型错误往往依赖于特定处理器、JIT 优化和非常窄的时序窗口。</p>
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
            }, "double-checked-locking-caller-" + i);
            caller.start();
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS), "demo threads did not become ready");
        start.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "demo threads did not finish");

        System.out.println("16.7 double-checked locking demo finished");
        System.out.println("constructed Resource count = " + Resource.getConstructionCount());
        System.out.println("Resource ids returned to callers = " + returnedResourceIds);
        System.out.println("initialization markers observed = " + observedMarkers);
        System.out.println("A normal-looking result does NOT prove this non-volatile implementation is safe.");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
