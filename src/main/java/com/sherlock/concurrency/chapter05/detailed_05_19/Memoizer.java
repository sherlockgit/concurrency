package com.sherlock.concurrency.chapter05.detailed_05_19;


import java.util.concurrent.*;

/**
 * Memoizer的最终实现
 * * *
 * 终极记忆包装器（最终版）：使用 ConcurrentHashMap 的原子操作 putIfAbsent，
 * 彻底解决了版本3中的竞争条件问题。确保对于同一个参数，绝对只有一个线程执行实际计算。
 * 同时处理了计算过程中可能出现的异常和取消操作，自动清理缓存中的无效条目。
 *
 * 1. 没有解决“缓存逾期”的问题
 * 逾期：指缓存中的条目经过一段时间后变得“陈旧”或“不再有效”（例如，数据源可能更新了，或业务要求每隔一段时间重新计算）。
 * 当前 Memoizer 中，只要 Future 成功完成，它就会永久保存在 cache 中。后续所有相同参数的调用都会直接返回之前的结果，永远不会重新计算。
 *
 * 解决方案：可以通过自定义 FutureTask 子类，为每个结果附加上“过期时间戳”。例如，在子类中记录创建时间或最后访问时间，然后由一个后台线程定期扫描 ConcurrentHashMap，移除那些过期的 Future 条目（并可能中断正在进行的计算）。这样就可以实现“时间驱动的缓存失效”。
 *
 * 2. 没有解决“缓存清理”的问题
 * 缓存清理：即使没有逾期（例如数据永远有效），如果参数空间极大（如用户查询字符串），缓存会无限增长，最终耗尽内存。需要一种策略来淘汰旧条目，比如 LRU（最近最少使用）、LFU 或 大小限制。
 * 当前 Memoizer 没有任何淘汰机制。ConcurrentHashMap 本身不会自动删除条目（除非显式 remove）。
 *
 * 解决方案：可以组合使用 LinkedHashMap 的“移除最旧条目”特性（但需要额外的同步），或者使用 Guava 的 CacheBuilder、Caffeine 等成熟的缓存库，它们支持基于大小、时间、引用等的淘汰策略。如果坚持手写，可以在 putIfAbsent 成功后，检查缓存大小是否超过阈值，并主动移除一些条目（例如用 ConcurrentHashMap 配合一个 ConcurrentLinkedQueue 记录访问顺序，但实现复杂且易错）。
 * * *
 * @param <A> 输入参数类型
 * @param <V> 计算结果类型
 */
public class Memoizer<A, V> implements Computable<A, V> {
    // 缓存：参数 -> 异步计算任务（Future）
    // 使用 ConcurrentHashMap 保证线程安全和高并发访问
    private final ConcurrentHashMap<A, Future<V>> cache = new ConcurrentHashMap<>();
    // 底层实际的计算逻辑（昂贵操作）
    private final Computable<A, V> c;

    public Memoizer(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(final A arg) throws InterruptedException {
        // 循环重试：可能在异常（取消）时需要重新尝试计算
        while (true) {
            // 1. 尝试从缓存中获取已有的 Future（代表正在计算或已完成的任务）
            Future<V> f = cache.get(arg);
            if (f == null) {
                // 2. 缓存未命中：创建一个新的计算任务
                Callable<V> eval = new Callable<V>() {
                    public V call() throws InterruptedException {
                        return c.compute(arg);
                    }
                };
                FutureTask<V> ft = new FutureTask<>(eval);

                // 3. 原子操作：只有当缓存中没有对应键时才放入 ft
                //    putIfAbsent 返回之前关联的值，如果没有则返回 null
                f = cache.putIfAbsent(arg, ft);
                if (f == null) {
                    // 当前线程成功成为该参数的计算者
                    f = ft;
                    // 立即运行计算（当前线程执行，也可委托给线程池）
                    ft.run();   // 调用 c.compute(arg)，可能阻塞或耗时
                }
                // 如果 f != null，说明另一个线程抢先插入了 Future，则使用那个 Future
            }

            // 4. 等待计算完成并获取结果
            try {
                return f.get();  // 阻塞直到结果可用
            } catch (CancellationException e) {
                // 如果任务被取消，则从缓存中移除此条目（清除无效状态）
                // 注意：使用 remove(arg, f) 确保只移除当前 Future，防止误删新插入的任务
                cache.remove(arg, f);
                // 重新循环，再次尝试计算（重新创建任务）
            } catch (ExecutionException e) {
                // 如果计算过程抛出异常，则清理并抛出未检查异常（launderThrowable）
                // 注意：这里原文有误 e.getClass() 应改为 e.getCause()
                throw launderThrowable(e.getCause());
            }
        }
    }

    /**
     * 将检查型异常转换为运行时异常或 Error，简化异常处理。
     * @param t 原始异常原因
     * @return 永远不会正常返回，总是抛出异常
     */
    private RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new IllegalStateException("未检查的异常", t);
    }
}
