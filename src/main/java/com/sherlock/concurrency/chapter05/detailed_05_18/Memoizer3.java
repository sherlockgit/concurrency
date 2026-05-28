package com.sherlock.concurrency.chapter05.detailed_05_18;


import java.util.Map;
import java.util.concurrent.*;

/**
 * 基于 FutureTask 的 Memoizing封装器
 * * *
 * Memoizer3的实现几乎是完美的:它表现出了非常好的并发性(基本上是源于ConcurrentHashMap高效的并发性)，
 * * 若结果已经计算出来，那么将立即返回。如果其他线程正在计算该结果，那么新到的线程将一直等待这个结果被
 * * 计算出来。它只有一个缺陷，即仍然存在两个线程计算出相同值的漏洞。这个漏洞的发生概率要远小于Memoizer2
 * * 中发生的概率，但由于compute方法中的if代码块仍然是非原子(nonatomic)的“先检查再执行”操作，因此两
 * * 个线程仍有可能在同一时间内调用compute来计算相同的值，即二者都没有在缓存中找到期望的值，因此都开始计算。
 * * *
 * 记忆包装器（版本3）：使用 ConcurrentHashMap 缓存 Future 对象，
 * 确保对于同一个参数，只有一个线程执行实际计算，其他线程等待该计算结果。
 *
 * 相比 Memoizer2，它解决了“重复计算”的问题，但仍有细微的竞争条件：
 * 如果两个线程几乎同时调用 compute 且缓存中都没有对应的 Future，
 * 它们可能会都创建自己的 FutureTask 并放入缓存（后放入的会覆盖先放入的），
 * 导致仍然可能重复计算。这个问题将在后续版本（Memoizer）中使用原子操作解决。
 *
 * @param <A> 输入参数类型
 * @param <V> 计算结果类型
 */
public class Memoizer3<A, V> implements Computable<A, V> {
    // 缓存：键为输入参数，值为 Future 对象（代表异步计算任务）
    private final Map<A, Future<V>> cache = new ConcurrentHashMap<>();
    // 底层的实际计算逻辑
    private final Computable<A, V> c;

    public Memoizer3(Computable<A, V> c) {
        this.c = c;
    }

    @Override
    public V compute(final A arg) throws InterruptedException {
        // 尝试从缓存中获取该参数对应的 Future
        Future<V> f = cache.get(arg);
        if (f == null) {
            // 缓存未命中：创建一个 Callable 任务，封装实际计算
            Callable<V> eval = new Callable<V>() {
                public V call() throws InterruptedException {
                    return c.compute(arg);
                }
            };
            // 将 Callable 包装为 FutureTask（同时是 Future 和 Runnable）
            FutureTask<V> ft = new FutureTask<>(eval);
            f = ft;
            // 将 FutureTask 放入缓存（注意：此时尚未运行）
            cache.put(arg, ft);
            // 立即运行该任务（在当前线程中执行计算）
            ft.run(); // 这里会调用 c.compute(arg)，可能耗时较长
        }
        try {
            // 获取计算结果：如果任务尚未完成，get() 会阻塞直到完成
            return f.get();
        } catch (ExecutionException e) {
            // 如果计算过程抛出异常，则重新抛出清理后的异常
            throw launderThrowable(e.getCause());
        }
    }

    /**
     * 将底层抛出的异常清理为运行时异常或 Error，
     * 避免不必要的手动处理。
     * @param t 被包装的 Throwable
     * @return 永远不会正常返回，总是抛出异常
     */
    private RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else
            throw new IllegalStateException("Not unchecked", t);
    }
}
