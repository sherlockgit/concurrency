package com.sherlock.concurrency.chapter13.detailed_13_07;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 使用读写锁包装 Map。
 *
 * <p>这是《Java 并发编程实战》中的 13.7。</p>
 *
 * <p>这个类演示了读写锁的典型用法：
 * 对只读操作使用读锁，对修改操作使用写锁。</p>
 *
 * <p>如果读操作很多、写操作较少，那么多个读线程可以并发读取同一个 Map；
 * 只有写入时才需要独占访问。相比所有操作都使用同一把互斥锁，
 * 读写锁可能在读多写少场景中提供更好的并发性。</p>
 *
 * <p>这个示例没有直接实现 {@link Map} 接口。
 * 主要原因是完整实现 Map 需要处理 {@code keySet}、{@code values}、
 * {@code entrySet} 等集合视图，而这些视图如果直接暴露出去，
 * 可能绕过读写锁访问底层 Map，破坏线程安全。</p>
 */
public class ReadWriteMap<K, V> {

    /**
     * 被读写锁保护的底层 Map。
     *
     * <p>所有访问都必须通过本类方法进行，不能把这个对象直接暴露给外部。</p>
     */
    private final Map<K, V> map;

    /**
     * 读写锁。
     */
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 读锁：保护只读操作。
     */
    private final Lock readLock = lock.readLock();

    /**
     * 写锁：保护修改操作。
     */
    private final Lock writeLock = lock.writeLock();

    public ReadWriteMap(Map<K, V> map) {
        if (map == null) {
            throw new IllegalArgumentException("map must not be null");
        }
        this.map = map;
    }

    /**
     * 放入或替换一个键值对。
     *
     * <p>这是修改操作，必须获得写锁。
     * 写锁是独占的：持有写锁时，其他读线程和写线程都不能访问 Map。</p>
     */
    public V put(K key, V value) {
        writeLock.lock();
        try {
            return map.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 删除一个键。
     *
     * <p>删除会修改 Map 结构，因此必须获得写锁。</p>
     */
    public V remove(Object key) {
        writeLock.lock();
        try {
            return map.remove(key);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 批量写入。
     *
     * <p>整个批量操作在同一把写锁保护下完成。
     * 这样其他线程不会看到 putAll 执行到一半时的中间状态。</p>
     */
    public void putAll(Map<? extends K, ? extends V> values) {
        writeLock.lock();
        try {
            map.putAll(values);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 清空 Map。
     *
     * <p>清空是修改操作，因此必须获得写锁。</p>
     */
    public void clear() {
        writeLock.lock();
        try {
            map.clear();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 根据 key 读取 value。
     *
     * <p>这是只读操作，因此使用读锁。
     * 多个读线程可以同时持有读锁并发读取。</p>
     */
    public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 当前键值对数量。
     */
    public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断 Map 是否为空。
     */
    public boolean isEmpty() {
        readLock.lock();
        try {
            return map.isEmpty();
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断是否包含指定 key。
     */
    public boolean containsKey(Object key) {
        readLock.lock();
        try {
            return map.containsKey(key);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 判断是否包含指定 value。
     */
    public boolean containsValue(Object value) {
        readLock.lock();
        try {
            return map.containsValue(value);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 返回当前 Map 的快照。
     *
     * <p>这不是官方 13.7 中的方法。
     * 本地示例加上它，是为了演示如何安全返回集合内容：
     * 不能直接返回底层 Map，否则外部可以绕过锁修改它。</p>
     */
    public Map<K, V> snapshot() {
        readLock.lock();
        try {
            return new HashMap<K, V>(map);
        } finally {
            readLock.unlock();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        final ReadWriteMap<String, Integer> readWriteMap =
                new ReadWriteMap<String, Integer>(new HashMap<String, Integer>());

        assertTrue(readWriteMap.isEmpty(), "new map should be empty");
        readWriteMap.put("base", 1);
        assertEquals(Integer.valueOf(1), readWriteMap.get("base"), "base value");

        Map<String, Integer> batch = new HashMap<String, Integer>();
        batch.put("a", 10);
        batch.put("b", 20);
        readWriteMap.putAll(batch);
        assertTrue(readWriteMap.containsKey("a"), "map should contain key a");
        assertTrue(readWriteMap.containsValue(20), "map should contain value 20");

        final int writerCount = 2;
        final int readerCount = 4;
        final int iterations = 1000;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(writerCount + readerCount);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < writerCount; i++) {
            final int writerIndex = i;
            Thread writer = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();
                        for (int j = 0; j < iterations; j++) {
                            readWriteMap.put("writer-" + writerIndex + "-" + j, j);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        endGate.countDown();
                    }
                }
            }, "read-write-map-writer-" + i);

            writer.start();
        }

        for (int i = 0; i < readerCount; i++) {
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        startGate.await();
                        for (int j = 0; j < iterations; j++) {
                            readWriteMap.get("base");
                            readWriteMap.containsKey("a");
                            readWriteMap.size();
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        endGate.countDown();
                    }
                }
            }, "read-write-map-reader-" + i);

            reader.start();
        }

        startGate.countDown();
        endGate.await();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        int expectedSize = 3 + writerCount * iterations;
        assertEquals(Integer.valueOf(expectedSize), Integer.valueOf(readWriteMap.size()), "final size");

        Integer removed = readWriteMap.remove("base");
        assertEquals(Integer.valueOf(1), removed, "removed value");
        assertFalse(readWriteMap.containsKey("base"), "base should be removed");

        Map<String, Integer> snapshot = readWriteMap.snapshot();
        assertEquals(Integer.valueOf(readWriteMap.size()), Integer.valueOf(snapshot.size()), "snapshot size");

        readWriteMap.clear();
        assertTrue(readWriteMap.isEmpty(), "map should be empty after clear");

        System.out.println("13.7 read/write map passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
