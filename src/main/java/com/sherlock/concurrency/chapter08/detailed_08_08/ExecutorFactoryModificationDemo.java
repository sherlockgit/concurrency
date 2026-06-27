package com.sherlock.concurrency.chapter08.detailed_08_08;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 修改通过标准工厂创建的 Executor 的演示。
 *
 * <p>这个示例演示两种不同情况：</p>
 * <p>1. {@code newFixedThreadPool(...)} 返回的是具体的 {@link ThreadPoolExecutor}，可以强转并继续修改；</p>
 * <p>2. {@code newSingleThreadExecutor()} 返回的是包装器，不允许直接强转修改底层线程池配置。</p>
 */
public class ExecutorFactoryModificationDemo {

    public static void main(String[] args) {
        ExecutorService fixed = Executors.newFixedThreadPool(2);
        System.out.println("fixed class = " + fixed.getClass().getName());
        System.out.println("fixed instanceof ThreadPoolExecutor = " + (fixed instanceof ThreadPoolExecutor));

        if (fixed instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) fixed;
            pool.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
            System.out.println("fixed rejection policy changed to CallerRunsPolicy");
        }

        ExecutorService single = Executors.newSingleThreadExecutor();
        System.out.println("single class = " + single.getClass().getName());
        System.out.println("single instanceof ThreadPoolExecutor = " + (single instanceof ThreadPoolExecutor));

        try {
            ThreadPoolExecutor pool = (ThreadPoolExecutor) single;
            System.out.println(pool);
        } catch (ClassCastException e) {
            System.out.println("single-thread executor cannot be cast to ThreadPoolExecutor directly");
        }

        fixed.shutdown();
        single.shutdown();
    }
}
