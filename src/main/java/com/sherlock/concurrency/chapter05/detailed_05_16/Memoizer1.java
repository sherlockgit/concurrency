package com.sherlock.concurrency.chapter05.detailed_05_16;

import java.util.HashMap;
import java.util.Map;

/**
 * 使用HashMap和同步机制来初始化缓存
 * * *
 * HashMap不是线程安全的，因此要确保两个线程不会同时访问HashMap，Memoizerl采用了一种保守的方法，
 * * 即对整个compute方法进行同步。这种方法能确保线程安全性，但会带来一个明显的可伸缩性问题:
 * * 每次只有一个线程能够执行compute。如果另一个线程正在计算结果，那么其他调用compute的线程
 * * 可能被阻塞很长时间。如果有多个线程在排队等待还未计算出的结果，那么compute方法的计算时间可
 * * 能比没有“记忆”操作的计算时间更长。这显然不是我们希望通过缓存获得的性能提升结果。
 *
 * * * *
 * 记忆包装器（版本1）：使用 HashMap 缓存计算结果，但整个 compute 方法加锁，导致并发性能差。
 * 线程安全，但同一时间只有一个线程能执行 compute，其他线程被阻塞。
 * @param <A> 输入参数类型
 * @param <V> 计算结果类型
 */
public class Memoizer1<A, V> implements Computable<A, V> {
    // 缓存容器，存储已经计算过的结果。@GuardedBy("this") 表示该字段受当前对象锁保护
    private final Map<A, V> cache = new HashMap<A, V>();

    // 底层的真正计算逻辑
    private final Computable<A, V> c;

    /**
     * 构造函数
     * @param c 具体的计算策略对象
     */
    public Memoizer1(Computable<A, V> c) {
        this.c = c;
    }

    /**
     * 计算或从缓存中获取结果。
     * synchronized 修饰整个方法，保证线程安全，但严重限制了并发度。
     * @param arg 输入参数
     * @return 计算结果（可能来自缓存，也可能实时计算）
     * @throws InterruptedException 若底层计算抛出中断异常
     */
    public synchronized V compute(A arg) throws InterruptedException {
        V result = cache.get(arg);   // 从缓存中获取
        if (result == null) {        // 缓存未命中
            result = c.compute(arg); // 执行实际计算（可能很慢）
            cache.put(arg, result);  // 将结果存入缓存
        }
        return result;
    }
}
