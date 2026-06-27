package com.sherlock.concurrency.chapter08.detailed_08_09;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 带有日志和计时功能的线程池。
 *
 * <p>这是《Java 并发编程实战》中的 8.9。</p>
 *
 * <p>这个例子展示的是 {@link ThreadPoolExecutor} 提供的几个“钩子方法”：</p>
 *
 * <p>1. {@link #beforeExecute(Thread, Runnable)}：每个任务执行前调用；</p>
 * <p>2. {@link #afterExecute(Runnable, Throwable)}：每个任务执行后调用；</p>
 * <p>3. {@link #terminated()}：线程池完全终止后调用。</p>
 *
 * <p>通过继承线程池并重写这些钩子方法，就可以在不修改任务本身的情况下，
 * 给所有任务统一增加监控、日志、计时、统计等横切行为。</p>
 */
public class TimingThreadPool extends ThreadPoolExecutor {

    /**
     * 每个工作线程独立保存当前任务的开始时间。
     *
     * <p>这里使用 {@link ThreadLocal} 的原因是：
     * 一个线程池里有多个工作线程，它们可能同时执行不同任务。
     * 如果只用一个普通字段保存 startTime，那么多个线程会互相覆盖；
     * 使用 ThreadLocal 后，每个工作线程看到的是自己的那份开始时间。</p>
     */
    private final ThreadLocal<Long> startTime = new ThreadLocal<Long>();

    /**
     * 记录线程池级别的日志。
     */
    private final Logger log = Logger.getLogger(TimingThreadPool.class.getName());

    /**
     * 已经完成并被统计的任务数量。
     */
    private final AtomicLong numTasks = new AtomicLong();

    /**
     * 所有已完成任务的总耗时，单位是纳秒。
     */
    private final AtomicLong totalTime = new AtomicLong();

    /**
     * 创建一个可直接运行的单线程计时线程池。
     *
     * <p>官方示例中的构造器重点不在队列配置，而在演示钩子方法。
     * 为了让这个本地示例可以直接运行，这里使用 {@link LinkedBlockingQueue}
     * 作为默认工作队列。</p>
     */
    public TimingThreadPool() {
        this(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
    }

    /**
     * 暴露完整构造参数，方便把这个计时能力应用到不同线程池配置上。
     *
     * @param corePoolSize 核心线程数
     * @param maximumPoolSize 最大线程数
     * @param keepAliveTime 空闲非核心线程存活时间
     * @param unit keepAliveTime 的时间单位
     * @param workQueue 任务队列
     */
    public TimingThreadPool(int corePoolSize,
                            int maximumPoolSize,
                            long keepAliveTime,
                            TimeUnit unit,
                            BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    /**
     * 暴露带 ThreadFactory 的构造器。
     *
     * <p>如果你想统一设置线程名称、daemon 状态或 UncaughtExceptionHandler，
     * 可以通过这个构造器把自定义 {@link ThreadFactory} 传进来。</p>
     */
    public TimingThreadPool(int corePoolSize,
                            int maximumPoolSize,
                            long keepAliveTime,
                            TimeUnit unit,
                            BlockingQueue<Runnable> workQueue,
                            ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    /**
     * 每个任务运行前，记录开始时间。
     *
     * <p>注意调用顺序：先调用父类方法，再执行自己的扩展逻辑。
     * 虽然 ThreadPoolExecutor 的默认 beforeExecute 什么也不做，
     * 但如果未来父类实现发生变化，或者中间又加了一层子类，
     * 保持这个调用习惯会更稳妥。</p>
     *
     * @param thread 即将执行任务的工作线程
     * @param task 即将执行的任务
     */
    @Override
    protected void beforeExecute(Thread thread, Runnable task) {
        super.beforeExecute(thread, task);
        log.fine(String.format("Thread %s: start %s", thread.getName(), task));
        startTime.set(System.nanoTime());
    }

    /**
     * 每个任务运行后，计算本次任务耗时，并累加到线程池统计信息中。
     *
     * <p>{@code throwable} 参数表示任务通过 {@link #execute(Runnable)} 提交时抛出的未捕获异常。
     * 如果任务是通过 {@code submit} 提交的，真实异常通常会被 FutureTask 捕获并封装进 Future，
     * 这里看到的 throwable 往往是 null。这一点和第 7 章讨论的 execute/submit 异常处理是一致的。</p>
     *
     * @param task 已经执行完成的任务
     * @param throwable 任务执行期间抛出的异常；没有异常时为 null
     */
    @Override
    protected void afterExecute(Runnable task, Throwable throwable) {
        try {
            Long startedAt = startTime.get();
            if (startedAt != null) {
                long taskTime = System.nanoTime() - startedAt;
                long taskNumber = numTasks.incrementAndGet();
                totalTime.addAndGet(taskTime);
                log.fine(String.format("Thread %s: end %s, time=%dns, taskNumber=%d",
                        Thread.currentThread().getName(), task, taskTime, taskNumber));
            }

            if (throwable != null) {
                log.log(Level.WARNING, "Task terminated with exception: " + task, throwable);
            }
        } finally {
            startTime.remove();
            super.afterExecute(task, throwable);
        }
    }

    /**
     * 线程池完全终止后，输出整体统计结果。
     *
     * <p>这个方法在所有任务都结束、所有工作线程都退出之后调用。
     * 因此它适合做线程池级别的收尾统计，而不适合做单个任务的处理。</p>
     */
    @Override
    protected void terminated() {
        try {
            long tasks = numTasks.get();
            if (tasks == 0) {
                log.info("Terminated: no completed tasks");
            } else {
                log.info(String.format("Terminated: avg time=%dns, tasks=%d",
                        totalTime.get() / tasks, tasks));
            }
        } finally {
            super.terminated();
        }
    }

    public long getMeasuredTaskCount() {
        return numTasks.get();
    }

    public long getTotalTimeNanos() {
        return totalTime.get();
    }

    public long getAverageTaskTimeNanos() {
        long tasks = numTasks.get();
        return tasks == 0 ? 0 : totalTime.get() / tasks;
    }

    /**
     * 简单演示。
     *
     * <p>为了能在控制台直接看到 FINE 级别日志，这里临时调整根日志处理器级别。
     * 真实项目中通常应该把日志级别交给日志配置文件管理。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.FINE);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(Level.FINE);
        }

        TimingThreadPool executor = new TimingThreadPool();

        for (int i = 0; i < 3; i++) {
            final int taskId = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100L + taskId * 50L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                @Override
                public String toString() {
                    return "demo-task-" + taskId;
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("completed tasks: " + executor.getMeasuredTaskCount());
        System.out.println("average time(ns): " + executor.getAverageTaskTimeNanos());
    }
}
