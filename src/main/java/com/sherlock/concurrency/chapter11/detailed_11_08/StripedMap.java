package com.sherlock.concurrency.chapter11.detailed_11_08;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 使用锁分段的基于哈希的 Map。
 *
 * <p>这是《Java 并发编程实战》中的 11.8。</p>
 *
 * <p>11.6 和 11.7 讲的是“锁分解”：
 * 如果一个类里有多个彼此独立的状态变量，可以用不同的锁分别保护它们。</p>
 *
 * <p>11.8 进一步讲“锁分段”（lock striping）：
 * 如果一个数据结构内部有很多相似的组成部分，例如哈希表中的很多桶，
 * 不一定要给每个桶都配一把锁，也不一定要整张表只用一把锁。
 * 可以准备一组锁，让多个桶共享其中一把锁。</p>
 *
 * <p>本类的同步策略是：</p>
 *
 * <p>{@code buckets[n]} 由 {@code locks[n % N_LOCKS]} 保护。</p>
 *
 * <p>这样做的效果是：</p>
 *
 * <p>1. 访问不同分段的线程可以并发执行；</p>
 * <p>2. 锁数量固定，不会因为桶数量变大而无限增加；</p>
 * <p>3. 相比整张表一把锁，锁竞争更少；</p>
 * <p>4. 相比每个桶一把锁，内存和管理成本更低。</p>
 *
 * <p>这个类是为了展示锁分段思想，不是完整的生产级 Map。
 * 真实项目应优先使用 {@link java.util.concurrent.ConcurrentHashMap}。</p>
 */
@ThreadSafe
public class StripedMap {

    /**
     * 分段锁数量。
     *
     * <p>桶数量可以很多，但锁数量固定为 16。
     * 多个桶会映射到同一把锁。</p>
     */
    private static final int N_LOCKS = 16;

    /**
     * 哈希桶数组。
     *
     * <p>第 n 个桶由 locks[n % N_LOCKS] 保护。</p>
     */
    private final Node[] buckets;

    /**
     * 分段锁数组。
     */
    private final Object[] locks;

    /**
     * 单链表节点。
     *
     * <p>每个桶里保存一条链表，用于处理哈希冲突。</p>
     */
    private static class Node {

        Node next;

        Object key;

        Object value;

        Node(Object key, Object value, Node next) {
            this.key = key;
            this.value = value;
            this.next = next;
        }
    }

    public StripedMap(int numBuckets) {
        if (numBuckets <= 0) {
            throw new IllegalArgumentException("numBuckets must be positive");
        }

        buckets = new Node[numBuckets];
        locks = new Object[N_LOCKS];
        for (int i = 0; i < N_LOCKS; i++) {
            locks[i] = new Object();
        }
    }

    /**
     * 根据 key 计算桶下标。
     *
     * <p>官方示例使用 {@code Math.abs(key.hashCode() % buckets.length)}。
     * 这里使用按位与去掉符号位，避免 {@code Integer.MIN_VALUE} 取绝对值仍为负数的问题。</p>
     */
    private int hash(Object key) {
        if (key == null) {
            throw new NullPointerException("key");
        }
        return (key.hashCode() & 0x7fffffff) % buckets.length;
    }

    /**
     * 根据桶下标选择保护锁。
     */
    private Object lockForBucket(int hash) {
        return locks[hash % N_LOCKS];
    }

    /**
     * 查找 key 对应的 value。
     *
     * <p>只需要锁住 key 所在桶对应的分段锁。
     * 其他线程如果访问的是不同分段，可以并发执行。</p>
     */
    public Object get(Object key) {
        int hash = hash(key);
        synchronized (lockForBucket(hash)) {
            for (Node node = buckets[hash]; node != null; node = node.next) {
                if (node.key.equals(key)) {
                    return node.value;
                }
            }
        }
        return null;
    }

    /**
     * 插入或更新 key 对应的 value。
     *
     * <p>官方清单只展示了 get 和 clear。
     * 这里补充 put，方便直接运行和观察锁分段 Map 的行为。</p>
     */
    public void put(Object key, Object value) {
        int hash = hash(key);
        synchronized (lockForBucket(hash)) {
            for (Node node = buckets[hash]; node != null; node = node.next) {
                if (node.key.equals(key)) {
                    node.value = value;
                    return;
                }
            }

            buckets[hash] = new Node(key, value, buckets[hash]);
        }
    }

    /**
     * 清空整张表。
     *
     * <p>清空时需要访问所有桶。
     * 这里逐个桶获取对应的分段锁，然后清空该桶。
     * 因为多个桶可能共享同一把锁，所以同一把锁可能会被重复获取多次。</p>
     */
    public void clear() {
        for (int i = 0; i < buckets.length; i++) {
            synchronized (lockForBucket(i)) {
                buckets[i] = null;
            }
        }
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        StripedMap map = new StripedMap(32);

        map.put("alice", "Shanghai");
        map.put("bob", "Beijing");
        map.put("alice", "Hangzhou");

        System.out.println(map.get("alice"));
        System.out.println(map.get("bob"));
        System.out.println(map.get("carol"));

        map.clear();
        System.out.println(map.get("alice"));
    }
}
