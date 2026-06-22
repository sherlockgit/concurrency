package com.sherlock.concurrency.chapter07.detailed_07_09;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 在专用线程中中断任务。
 *
 * <p>这是《Java 并发编程实战》中的 7.9，用来改进 7.8 的错误做法。</p>
 *
 * <p>7.8 的问题在于：它把任务直接运行在“调用者当前线程”中，
 * 然后再安排一个延迟中断去打断这个借用来的线程。
 * 这样一来，延迟中断可能误伤任务结束后调用线程继续执行的其他逻辑。</p>
 *
 * <p>7.9 的改进点在于：
 * 不再让任务运行在调用者线程中，而是为任务单独创建一个专用线程。
 * 超时后被中断的也是这个专用线程，而不是调用者线程本身。
 * 因此，即使任务超时或不响应中断，也不会把中断错误地传播给无关代码。</p>
 *
 * <p>不过，它仍然不是最终最优方案：
 * 如果任务完全不响应中断，那么这个专用线程仍然可能在后台继续运行；
 * 只不过此时调用者线程已经通过 {@code join(timeout)} 拿回了控制权。
 * 真正更完整、更工程化的方案是 7.10 中基于 {@code Future} 的取消。</p>
 */
public class TimedRun2 {

    /**
     * 用于安排“超时后中断任务线程”的定时调度器。
     *
     * <p>所有 timedRun 调用共享同一个取消调度线程池。</p>
     */
    private static final ScheduledExecutorService cancelExec = Executors.newScheduledThreadPool(1);

    /**
     * 在限定时间内运行一个任务。
     *
     * <p>整体流程如下：</p>
     *
     * <p>1. 先把待执行任务包装成一个可记录异常的任务对象；</p>
     * <p>2. 用一个专用线程来执行它；</p>
     * <p>3. 安排一个定时任务，在超时后中断这个专用线程；</p>
     * <p>4. 当前调用线程通过 {@code join(timeout)} 最多等待指定时长；</p>
     * <p>5. 如果任务线程内部抛出了异常，就在调用线程中重新抛出。</p>
     *
     * @param r 待执行任务
     * @param timeout 超时时长
     * @param unit 超时单位
     * @throws InterruptedException 如果调用 timedRun 的线程在等待 join 时被中断
     */
    public static void timedRun(final Runnable r, long timeout, TimeUnit unit)
            throws InterruptedException {

        /**
         * 一个可“延迟重新抛出异常”的包装任务。
         *
         * <p>由于任务真正运行在另一个线程中，若它内部抛出异常，
         * 异常不会自动传播回 timedRun 的调用线程。
         * 因此这里先把异常保存下来，等调用线程等待结束后再统一重新抛出。</p>
         */
        class RethrowableTask implements Runnable {
            private volatile Throwable t;

            @Override
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    this.t = throwable;
                }
            }

            /**
             * 如果任务线程执行期间捕获到了异常，
             * 则在调用线程中将其转换并重新抛出。
             */
            void rethrow() {
                if (t != null) {
                    throw launderThrowable(t);
                }
            }
        }

        RethrowableTask task = new RethrowableTask();

        // 为任务创建专用线程。这样超时后的中断只会作用在该任务线程上。
        final Thread taskThread = new Thread(task, "timed-run-2-task");
        taskThread.start();

        // 安排一个延迟取消任务：到达超时点后，中断任务线程。
        cancelExec.schedule(new Runnable() {
            @Override
            public void run() {
                taskThread.interrupt();
            }
        }, timeout, unit);

        // 当前调用线程最多等待 timeout 时长。
        // 无论任务线程是否真正结束，等待时间一到，调用线程都会继续向下执行。
        taskThread.join(unit.toMillis(timeout));

        // 如果任务内部抛出了异常，这里在调用线程中重新抛出。
        task.rethrow();
    }

    /**
     * 将 Throwable 转换为 RuntimeException 或 Error。
     *
     * <p>这是书中常见的 launderThrowable 模式：
     * 如果是运行时异常，直接原样抛出；
     * 如果是 Error，也直接抛出；
     * 否则包装成 IllegalStateException。</p>
     *
     * @param t 原始异常
     * @return 实际上不会正常返回，总是抛出异常
     */
    private static RuntimeException launderThrowable(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else {
            throw new IllegalStateException("未检查异常", t);
        }
    }
}
