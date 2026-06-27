package com.sherlock.concurrency.chapter08.detailed_08_06;

import com.sherlock.concurrency.chapter08.detailed_08_07.MyAppThread;

import java.util.concurrent.ThreadFactory;

/**
 * 自定义线程工厂。
 *
 * <p>这是《Java 并发编程实战》中的 8.6。
 * 它本身非常小，核心作用只有一个：
 * 把“创建线程”的职责统一交给 {@link MyAppThread} 这个自定义线程基类。</p>
 *
 * <p>换句话说，8.6 要表达的重点不是复杂逻辑，
 * 而是“ThreadFactory 可以成为线程定制策略的统一入口”。</p>
 *
 * <p>调用方只需要知道：</p>
 *
 * <p>1. 我要一个线程工厂；</p>
 * <p>2. 它创建出来的线程会自动带上统一的命名规则、异常处理器、生命周期统计等行为；</p>
 * <p>3. 而这些线程行为细节，都封装在 {@link MyAppThread} 里。</p>
 */
public class MyThreadFactory implements ThreadFactory {

    /**
     * 线程池/线程组的逻辑名称前缀。
     *
     * <p>实际线程名会由 {@link MyAppThread} 在此基础上追加编号。</p>
     */
    private final String poolName;

    public MyThreadFactory(String poolName) {
        this.poolName = poolName;
    }

    /**
     * 创建一个新的应用线程。
     *
     * <p>这里没有直接返回普通 {@link Thread}，
     * 而是返回一个带有统一行为约束的 {@link MyAppThread}。</p>
     *
     * @param runnable 要在线程中执行的任务
     * @return 新创建的自定义线程
     */
    @Override
    public Thread newThread(Runnable runnable) {
        return new MyAppThread(runnable, poolName);
    }
}
