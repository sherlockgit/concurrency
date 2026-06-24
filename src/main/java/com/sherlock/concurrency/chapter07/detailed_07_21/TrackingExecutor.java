package com.sherlock.concurrency.chapter07.detailed_07_21;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 能在关闭后跟踪“哪些任务在关闭过程中被取消”的 ExecutorService。
 *
 * <p>这是《Java 并发编程实战》中的 7.21。</p>
 *
 * <p>标准 {@link ExecutorService} 提供了 shutdown、shutdownNow、awaitTermination 等生命周期方法，
 * 但它有一个信息缺口：
 * 当线程池关闭后，我们通常能知道“哪些任务尚未开始就被从队列中移除”，
 * 却不容易知道“哪些任务已经开始执行，但在关闭过程中被中断取消了”。</p>
 *
 * <p>本类就是为了解决这个问题而设计的包装器：
 * 它把一个现有的 {@link ExecutorService} 包装起来，
 * 在不改变其基本行为的前提下，额外记录“在关闭过程中被取消的任务”。</p>
 *
 * <p>它的核心思路是：</p>
 *
 * <p>1. 对每个提交的任务再包一层 Runnable；</p>
 * <p>2. 在 finally 中检查：如果线程池已经关闭，并且当前执行线程带着中断状态结束，
 *    那么认为这个任务是在关闭过程中被取消的；</p>
 * <p>3. 把这样的任务记录到一个线程安全集合中；</p>
 * <p>4. 在线程池彻底终止后，通过 {@link #getCancelledTasks()} 取回这些任务。</p>
 *
 * <p>这种记录是“最佳努力（best-effort）”而非绝对精确：
 * 如果任务自己清除了中断状态，或者在中断到达与任务结束之间存在竞争窗口，
 * 结果可能不是 100% 完整。
 * 但对于像 7.22 WebCrawler 这类“希望保存未完成任务以便稍后恢复”的场景，
 * 它已经非常实用。</p>
 */
@ThreadSafe
public class TrackingExecutor extends AbstractExecutorService {

    /**
     * 被包装的实际线程池。
     *
     * <p>所有真正的任务执行、关闭、等待终止等动作，最终都委托给它完成。</p>
     */
    private final ExecutorService exec;

    /**
     * 在线程池关闭过程中被取消的任务集合。
     *
     * <p>使用同步包装的 Set，确保多个工作线程并发记录任务时是线程安全的。</p>
     */
    private final Set<Runnable> tasksCancelledAtShutdown =
            Collections.synchronizedSet(new HashSet<Runnable>());

    public TrackingExecutor(ExecutorService exec) {
        this.exec = exec;
    }

    /**
     * 平缓关闭：不再接收新任务，但让已提交任务继续执行。
     */
    @Override
    public void shutdown() {
        exec.shutdown();
    }

    /**
     * 立即关闭：尝试中断正在执行的任务，并返回尚未开始执行的任务列表。
     *
     * <p>这里仍然完全复用底层 ExecutorService 的语义。</p>
     *
     * @return 尚未开始执行的任务列表
     */
    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> tasks = exec.shutdownNow();
        List<Runnable> result = new ArrayList<>(tasks.size());
        for (Runnable task : tasks) {
            if (task instanceof SubmittedTask) {
                result.add(((SubmittedTask) task).getOriginalTask());
            } else {
                result.add(task);
            }
        }
        return result;
    }

    /**
     * 是否已进入关闭状态。
     *
     * @return true 表示已 shutdown 或 shutdownNow
     */
    @Override
    public boolean isShutdown() {
        return exec.isShutdown();
    }

    /**
     * 是否已彻底终止。
     *
     * @return true 表示线程池中所有任务均已结束
     */
    @Override
    public boolean isTerminated() {
        return exec.isTerminated();
    }

    /**
     * 等待线程池终止。
     *
     * @param timeout 最大等待时间
     * @param unit 时间单位
     * @return true 表示在超时前成功终止
     * @throws InterruptedException 如果当前线程在等待期间被中断
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
            throws InterruptedException {
        return exec.awaitTermination(timeout, unit);
    }

    /**
     * 获取“在线程池关闭过程中被取消”的任务列表。
     *
     * <p>这里只允许在线程池完全终止后调用，
     * 因为如果线程池还在运行，记录结果就还可能继续变化，返回快照没有稳定意义。</p>
     *
     * @return 被取消任务的快照列表
     * @throws IllegalStateException 如果在线程池尚未终止时调用
     */
    public List<Runnable> getCancelledTasks() {
        if (!exec.isTerminated()) {
            throw new IllegalStateException("线程池尚未终止，无法安全获取取消任务列表");
        }
        return new ArrayList<>(tasksCancelledAtShutdown);
    }

    /**
     * 提交一个任务执行。
     *
     * <p>这里是 TrackingExecutor 的核心逻辑：
     * 并不是直接把调用者传入的 runnable 原样交给底层线程池，
     * 而是再包一层监控逻辑。</p>
     *
     * <p>包装任务在 finally 中执行检查：</p>
     *
     * <p>1. 如果线程池已经处于关闭状态；</p>
     * <p>2. 并且当前工作线程在任务结束时仍然带着中断状态；</p>
     * <p>那么就认为这个任务是在关闭过程中被取消的，将其记录下来。</p>
     *
     * <p>之所以放在 finally 中，是为了确保无论任务是正常返回、
     * 抛出异常，还是因中断提前结束，都能执行这段收尾检查。</p>
     *
     * @param runnable 待执行任务
     */
    @Override
    public void execute(final Runnable runnable) {
        exec.execute(new SubmittedTask(runnable));
    }

    /**
     * 对用户提交任务的内部包装。
     *
     * <p>它有两个职责：</p>
     *
     * <p>1. 在 finally 中记录“关闭过程中被取消”的任务；</p>
     * <p>2. 保留对原始 runnable 的引用，便于 shutdownNow() 返回“原始任务列表”，
     *    而不是把内部包装对象暴露给上层调用者。</p>
     */
    private class SubmittedTask implements Runnable {
        private final Runnable originalTask;

        private SubmittedTask(Runnable originalTask) {
            this.originalTask = originalTask;
        }

        @Override
        public void run() {
            try {
                originalTask.run();
            } finally {
                if (isShutdown() && Thread.currentThread().isInterrupted()) {
                    tasksCancelledAtShutdown.add(originalTask);
                }
            }
        }

        private Runnable getOriginalTask() {
            return originalTask;
        }
    }
}
