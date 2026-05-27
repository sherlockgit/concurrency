package com.sherlock.concurrency.chapter05.detailed_05_17;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用 ConcurrentHashMap 替换 HashMap
 * *
 * Memoizer2比 Memoizerl有着更好的并发行为:多线程可以并发地使用它。但它在作为缓存时仍然存
 * * 在一些不足--当两个线程同时调用compute时存在一个漏洞，可能会导致计算得到相同的值。在使用
 * * memoization的情况下，这只会带来低效，因为缓存的作用是避免相同的数据被计算多次。但对于更
 * * 通用的缓存机制来说，这种情况将更为糟糕。对于只提供单次初始化的对象缓存来说，这个漏洞就会带来安全风险。
 * * * *
 * 记忆包装器（版本2）：使用 ConcurrentHashMap 替代 synchronized 方法，
 * 允许多个线程并发读取缓存，但在缓存未命中时仍然存在“重复计算”的问题。
 *
 * 优点：读操作无锁，提高了并发度。
 * 缺点：当多个线程同时请求同一个尚未计算过的参数时，它们可能都会执行 compute，
 *       导致重复计算，浪费资源。
 *
 * @param <A> 输入参数类型
 * @param <V> 计算结果类型
 */
public class Memoizer2<A, V> implements Computable<A, V> {
    // 使用线程安全的 ConcurrentHashMap 作为缓存
    private final Map<A, V> cache = new ConcurrentHashMap<>();

    // 底层的实际计算逻辑
    private final Computable<A, V> c;

    /**
     * 构造函数
     * @param c 具体的计算策略对象
     */
    public Memoizer2(Computable<A, V> c) {
        this.c = c;
    }

    /**
     * 计算或从缓存中获取结果。
     * 线程安全：ConcurrentHashMap 保证内部操作的原子性，
     * 但“先检查后执行”的组合操作（get -> null -> compute -> put）不是原子的，
     * 可能导致两个线程同时发现缓存为空，重复执行计算。
     *
     * @param arg 输入参数
     * @return 计算结果（可能来自缓存，也可能实时计算）
     * @throws InterruptedException 若底层计算抛出中断异常
     */
    public V compute(A arg) throws InterruptedException {
        // 尝试从缓存中快速获取结果（非阻塞）
        V result = cache.get(arg);
        if (result == null) {
            // 缓存未命中，执行实际计算（可能很耗时）
            result = c.compute(arg);
            // 将计算结果放入缓存
            // 注意：如果多个线程同时到达此处，它们都会执行 compute 并多次 put（覆盖）
            cache.put(arg, result);
        }
        return result;
    }
}
