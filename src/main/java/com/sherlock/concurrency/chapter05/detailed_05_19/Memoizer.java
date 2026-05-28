package com.sherlock.concurrency.chapter05.detailed_05_19;


import java.util.concurrent.*;

/**
 * 终极记忆包装器（最终版）：使用 ConcurrentHashMap 的原子操作 putIfAbsent，
 * 彻底解决了版本3中的竞争条件问题。确保对于同一个参数，绝对只有一个线程执行实际计算。
 * 同时处理了计算过程中可能出现的异常和取消操作，自动清理缓存中的无效条目。
 *
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
