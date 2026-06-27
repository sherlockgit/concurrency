package com.sherlock.concurrency.chapter08.detailed_08_04;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * 使用 Semaphore 对任务提交进行节流。
 *
 * <p>这是《Java 并发编程实战》中的 8.4。
 * 它解决的问题不是“线程池内部怎么执行任务”，而是“调用方一次最多允许有多少个任务正在运行或排队”。</p>
 *
 * <p>核心思路是：</p>
 *
 * <p>1. 在线程池外面再包一层 {@code BoundedExecutor}；</p>
 * <p>2. 每次提交任务前先获取一个信号量许可；</p>
 * <p>3. 任务执行结束后，无论正常还是异常，都在 finally 中归还许可；</p>
 * <p>4. 如果底层线程池拒绝该任务，也要把刚刚获取的许可还回去。</p>
 *
 * <p>这样就能限制“已经提交但尚未完成”的任务总数。</p>
 */
@ThreadSafe
public class BoundedExecutor {

    /**
     * 实际执行任务的底层执行器。
     */
    private final Executor exec;

    /**
     * 用来限制并发提交数量的信号量。
     *
     * <p>它的许可数表示：当前最多允许多少个任务处于“已提交但未完成”的状态。</p>
     */
    private final Semaphore semaphore;

    /**
     * 构造一个带有提交上限控制的执行器包装器。
     *
     * @param exec 底层执行器
     * @param bound 最大允许的在途任务数
     */
    public BoundedExecutor(Executor exec, int bound) {
        this.exec = exec;
        this.semaphore = new Semaphore(bound);
    }

    /**
     * 提交任务。
     *
     * <p>步骤如下：</p>
     *
     * <p>1. 先获取一个许可；如果没有许可，调用线程会阻塞等待；</p>
     * <p>2. 成功获取许可后，把真实任务再包一层提交给底层 executor；</p>
     * <p>3. 包装任务在 finally 中归还许可；</p>
     * <p>4. 如果底层执行器拒绝执行，则在 catch 中立即归还许可。</p>
     *
     * @param command 待执行任务
     * @throws InterruptedException 如果当前线程在等待许可时被中断
     */
    public void submitTask(final Runnable command) throws InterruptedException {
        semaphore.acquire();
        try {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        command.run();
                    } finally {
                        semaphore.release();
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            semaphore.release();
        }
    }
}
