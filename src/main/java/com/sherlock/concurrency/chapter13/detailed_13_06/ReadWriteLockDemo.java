package com.sherlock.concurrency.chapter13.detailed_13_06;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * ReadWriteLock 接口的基本用法。
 *
 * <p>这是《Java 并发编程实战》中的 13.6。</p>
 *
 * <p>13.6 在书中对应的是 {@link ReadWriteLock} 接口说明。
 * 读写锁把一把锁拆成两种视图：</p>
 *
 * <p>1. {@link ReadWriteLock#readLock()}：读锁；</p>
 * <p>2. {@link ReadWriteLock#writeLock()}：写锁。</p>
 *
 * <p>读写锁的核心规则是：</p>
 *
 * <p>1. 多个读线程可以同时持有读锁；</p>
 * <p>2. 写线程持有写锁时，其他读线程和写线程都不能进入；</p>
 * <p>3. 有读线程持有读锁时，写线程通常要等待所有读锁释放；</p>
 * <p>4. 因此它适合“读多写少”的共享数据结构。</p>
 */
public class ReadWriteLockDemo {

    /**
     * 实际使用的读写锁实现。
     *
     * <p>{@link ReentrantReadWriteLock} 是最常用的 {@link ReadWriteLock} 实现。</p>
     */
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * 读锁。
     *
     * <p>所有只读取 {@link #values} 的方法都应该获得读锁。
     * 读锁之间可以共享，所以多个读线程能并发执行。</p>
     */
    private final Lock readLock = readWriteLock.readLock();

    /**
     * 写锁。
     *
     * <p>所有修改 {@link #values} 的方法都应该获得写锁。
     * 写锁是独占的，会排斥其他读锁和写锁。</p>
     */
    private final Lock writeLock = readWriteLock.writeLock();

    /**
     * 被读写锁保护的共享状态。
     */
    private final Map<String, String> values = new HashMap<String, String>();

    /**
     * 写入或更新一个值。
     */
    public void put(String key, String value) {
        writeLock.lock();
        try {
            values.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 读取一个值。
     */
    public String get(String key) {
        readLock.lock();
        try {
            return values.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回当前数据快照。
     *
     * <p>这里返回的是拷贝，而不是内部 Map 本身。
     * 如果直接返回内部 Map，就会让受保护的可变状态逃逸。</p>
     */
    public Map<String, String> snapshot() {
        readLock.lock();
        try {
            return new HashMap<String, String>(values);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 在持有读锁期间等待，用于演示多个读锁可以同时存在。
     */
    private String slowRead(String key,
                            CountDownLatch readersReady,
                            CountDownLatch releaseReaders,
                            AtomicInteger activeReaders,
                            AtomicInteger maxConcurrentReaders)
            throws InterruptedException {
        readLock.lock();
        try {
            int readers = activeReaders.incrementAndGet();
            updateMax(maxConcurrentReaders, readers);
            readersReady.countDown();

            /*
             * 故意让读线程停在读锁内部。
             *
             * 如果读锁之间互斥，那么多个读线程无法同时停在这里；
             * 如果读锁可以共享，maxConcurrentReaders 就会大于 1。
             */
            releaseReaders.await();
            return values.get(key);
        } finally {
            activeReaders.decrementAndGet();
            readLock.unlock();
        }
    }

    private static void updateMax(AtomicInteger maxConcurrentReaders, int readers) {
        while (true) {
            int current = maxConcurrentReaders.get();
            if (readers <= current) {
                return;
            }
            if (maxConcurrentReaders.compareAndSet(current, readers)) {
                return;
            }
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        final ReadWriteLockDemo store = new ReadWriteLockDemo();
        store.put("name", "sherlock");
        assertEquals("sherlock", store.get("name"), "initial value");

        final int readerCount = 3;
        final CountDownLatch readersReady = new CountDownLatch(readerCount);
        final CountDownLatch releaseReaders = new CountDownLatch(1);
        final CountDownLatch readersDone = new CountDownLatch(readerCount);
        final AtomicInteger activeReaders = new AtomicInteger(0);
        final AtomicInteger maxConcurrentReaders = new AtomicInteger(0);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < readerCount; i++) {
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        String value = store.slowRead(
                                "name",
                                readersReady,
                                releaseReaders,
                                activeReaders,
                                maxConcurrentReaders);
                        assertEquals("sherlock", value, "value read by reader");
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        readersDone.countDown();
                    }
                }
            }, "read-lock-reader-" + i);

            reader.start();
        }

        /*
         * 等到所有读线程都进入读锁保护区。
         * 如果读锁不能共享，这里就等不到 readerCount 个读线程全部 ready。
         */
        assertTrue(readersReady.await(2, TimeUnit.SECONDS), "all readers should hold read lock together");
        assertTrue(maxConcurrentReaders.get() > 1, "read lock should allow concurrent readers");

        final AtomicBoolean writerFinished = new AtomicBoolean(false);
        Thread writer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    store.put("name", "watson");
                    writerFinished.set(true);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "write-lock-writer");

        writer.start();

        /*
         * 写线程应该被已有读锁挡住。
         */
        TimeUnit.MILLISECONDS.sleep(100);
        assertFalse(writerFinished.get(), "writer should wait while read locks are held");

        releaseReaders.countDown();
        readersDone.await();
        writer.join(2000);

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        assertTrue(writerFinished.get(), "writer should finish after readers release read locks");
        assertEquals("watson", store.get("name"), "updated value");
        assertEquals("watson", store.snapshot().get("name"), "snapshot value");

        System.out.println("maxConcurrentReaders=" + maxConcurrentReaders.get());
        System.out.println("13.6 read/write lock demo passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(String expected, String actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
