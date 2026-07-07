package com.sherlock.concurrency.chapter12.detailed_12_04;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 适合测试使用的中等质量随机数生成器。
 *
 * <p>这是《Java 并发编程实战》中的 12.4。</p>
 *
 * <p>第 12 章后续会做并发压力测试，例如多个生产者和消费者同时操作有界缓存。
 * 这些测试中经常需要生成一些伪随机数据。</p>
 *
 * <p>如果所有测试线程共享同一个随机数生成器，那么随机数生成器本身可能变成竞争热点。
 * 这样测试结果就会受到随机数生成器同步开销的影响，而不是只反映被测并发类的性能。</p>
 *
 * <p>{@code XorShift} 的思路是：
 * 每个测试线程可以创建自己的随机数生成器实例，
 * 这样随机数状态 {@link #x} 就被限制在当前线程内，
 * 不需要加锁，也不会和其他线程竞争。</p>
 *
 * <p>它不是密码学安全随机数生成器，也不适合安全场景。
 * 它只是一个简单、快速、足够用于并发测试数据生成的伪随机数生成器。</p>
 */
public class XorShift {

    /**
     * 用来给默认构造器生成不同种子的全局序列。
     *
     * <p>这里使用 {@link AtomicInteger}，是为了让多个线程同时创建 XorShift 实例时，
     * 能够安全地拿到不同的偏移值，减少不同实例使用相同 seed 的概率。</p>
     */
    private static final AtomicInteger seq = new AtomicInteger(8862213);

    /**
     * 当前随机数状态。
     *
     * <p>这个字段不是线程安全的。
     * 设计意图是每个线程使用自己的 XorShift 实例，而不是多个线程共享同一个实例。</p>
     */
    private int x = -1831433054;

    public XorShift(int seed) {
        x = seed;
    }

    /**
     * 使用当前时间和全局递增序列生成种子。
     */
    public XorShift() {
        this((int) System.nanoTime() + seq.getAndAdd(129));
    }

    /**
     * 生成下一个伪随机 int。
     *
     * <p>XorShift 算法通过异或和移位快速扰动内部状态。
     * 每次调用都会修改 {@link #x}，并返回修改后的状态。</p>
     *
     * @return 下一个伪随机数
     */
    public int next() {
        x ^= x << 6;
        x ^= x >>> 21;
        x ^= x << 7;
        return x;
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) {
        XorShift random = new XorShift(12345);

        for (int i = 0; i < 5; i++) {
            System.out.println(random.next());
        }
    }
}
