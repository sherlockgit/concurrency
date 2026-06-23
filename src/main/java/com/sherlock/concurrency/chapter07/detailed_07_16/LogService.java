package com.sherlock.concurrency.chapter07.detailed_07_16;

import com.sherlock.concurrency.annoations.Recommend;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 使用 ExecutorService 的日志服务。
 *
 * <p>这是《Java 并发编程实战》中的 7.16。
 * 与 7.13、7.14、7.15 相比，这一版不再手动维护“日志线程 + 队列 + 关闭协议”，
 * 而是把这些通用的任务调度与生命周期管理职责交给 {@link ExecutorService}。</p>
 *
 * <p>这里选择单线程线程池 {@link Executors#newSingleThreadExecutor()}，
 * 这样既能保证：</p>
 *
 * <p>1. 所有日志写出操作串行执行，不会出现多个线程并发写同一个 Writer；</p>
 * <p>2. 任务提交顺序就是日志写出顺序；</p>
 * <p>3. 关闭、等待任务完成、拒绝新任务等机制全部复用 ExecutorService 的现成语义。</p>
 *
 * <p>因此，7.16 的核心思想是：
 * 如果底层问题本质上已经是“任务的提交、执行与关闭”，
 * 那么优先考虑用 ExecutorService 表达，而不是自己手写一套线程协调协议。</p>
 */
@Recommend
public class LogService {

    /**
     * 单线程日志执行器。
     *
     * <p>每条日志都会被包装成一个小任务提交到这个执行器中，
     * 由它顺序执行真正的 writer.println(...) 操作。</p>
     */
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    /**
     * 最终执行日志输出的 writer。
     *
     * <p>由于所有写操作都被单线程 executor 串行化，
     * 因此这里不需要再为 writer 增加额外同步。</p>
     */
    private final PrintWriter writer;

    /**
     * 构造日志服务。
     *
     * @param writer 底层日志输出目标
     */
    public LogService(Writer writer) {
        this.writer = new PrintWriter(writer, true);
    }

    /**
     * 启动日志服务。
     *
     * <p>与 7.13/7.15 不同，这里实际上不需要显式启动后台线程。
     * 单线程 executor 会在第一次提交任务时按需创建工作线程。
     * 保留 start() 方法只是为了保持与前几版日志服务接口风格一致。</p>
     */
    public void start() {
        // no-op
    }

    /**
     * 关闭日志服务。
     *
     * <p>关闭步骤如下：</p>
     *
     * <p>1. 调用 {@link ExecutorService#shutdown()}，拒绝后续新任务；</p>
     * <p>2. 调用 {@link ExecutorService#awaitTermination(long, TimeUnit)}，
     *    等待已经提交的日志任务执行完；</p>
     * <p>3. 无论等待过程是否正常结束，最终都关闭底层 writer。</p>
     *
     * <p>这比 7.15 的手写 reservations 协议更简洁，
     * 因为“哪些任务已经被接受、哪些任务还未执行完”这些状态，
     * 已经由 ExecutorService 内部负责维护了。</p>
     *
     * @throws InterruptedException 如果当前调用 stop() 的线程在等待终止时被中断
     */
    public void stop() throws InterruptedException {
        try {
            exec.shutdown();
            exec.awaitTermination(10, TimeUnit.SECONDS);
        } finally {
            writer.close();
        }
    }

    /**
     * 提交一条日志消息。
     *
     * <p>这里并不直接写日志，而是把“写出这条日志”封装成一个小任务交给 executor。</p>
     *
     * <p>如果日志服务已经开始关闭，那么 executor 会拒绝新任务，
     * 从而抛出 {@link RejectedExecutionException}。
     * 对于“因为正在关闭而被拒绝”的情况，这里选择静默忽略；
     * 因为此时调用者再提交日志，本来就不能保证它会被写出。</p>
     *
     * <p>如果不是因为关闭而被拒绝，则说明出现了意外情况，应继续向外抛出异常。</p>
     *
     * @param msg 待写出的日志消息
     */
    public void log(final String msg) {
        try {
            exec.execute(new Runnable() {
                @Override
                public void run() {
                    writer.println(msg);
                }
            });
        } catch (RejectedExecutionException e) {
            if (!exec.isShutdown()) {
                throw e;
            }
        }
    }
}
