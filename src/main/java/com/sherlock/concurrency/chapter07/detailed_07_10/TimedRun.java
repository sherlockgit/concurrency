package com.sherlock.concurrency.chapter07.detailed_07_10;

import com.sherlock.concurrency.annoations.Recommend;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 使用 Future 取消任务。
 *
 * <p>这是《Java 并发编程实战》中的 7.10，也是对 7.8、7.9 的进一步改进。</p>
 *
 * <p>7.8 的问题是：它把任务直接运行在调用者线程中，并在稍后去中断这个借用来的线程，
 * 因此可能误伤调用者后续逻辑。</p>
 *
 * <p>7.9 的问题虽然缓和了一些：它为任务创建了专用线程，
 * 因而不会把中断错误地传播到调用者线程；
 * 但调用者仍然需要自己管理线程创建、超时中断、join 等细节，
 * 实现上不够简洁，也不够通用。</p>
 *
 * <p>7.10 使用 {@link Future} 来表达“任务的执行结果和生命周期”，
 * 把线程管理、等待完成、超时控制、取消任务这些职责都交给 {@link ExecutorService}。
 * 这是更符合实际工程习惯的做法。</p>
 */
@Recommend
public class TimedRun {

    /**
     * 用于执行限时任务的线程池。
     *
     * <p>这里采用缓存线程池，表示每次 timedRun 提交的任务都由线程池统一调度，
     * 而不再由调用者自己手动 new Thread。</p>
     */
    private static final ExecutorService taskExec = Executors.newCachedThreadPool();

    /**
     * 在限定时间内运行一个任务。
     *
     * <p>实现步骤如下：</p>
     *
     * <p>1. 将任务提交给线程池，得到一个 {@link Future}；</p>
     * <p>2. 调用 {@link Future#get(long, TimeUnit)}，最多等待指定时长；</p>
     * <p>3. 如果超时，则在 finally 中调用 {@link Future#cancel(boolean)} 尝试取消任务；</p>
     * <p>4. 如果任务内部抛出异常，则通过 {@link ExecutionException#getCause()} 取出原始异常并重新抛出。</p>
     *
     * <p>与 7.9 相比，这里不再显式管理“专用线程”对象，
     * 而是通过 Future 这层抽象来管理任务的执行和取消。</p>
     *
     * @param r 待执行任务
     * @param timeout 超时时长
     * @param unit 超时单位
     * @throws InterruptedException 如果调用 timedRun 的线程在等待结果时被中断
     */
    public static void timedRun(Runnable r, long timeout, TimeUnit unit)
            throws InterruptedException {
        // 将任务提交给线程池，返回一个 Future 用来表示“这次任务执行”
        Future<?> task = taskExec.submit(r);

        try {
            // 最多等待 timeout 时长
            // 如果任务在时限内正常完成，这里会顺利返回
            task.get(timeout, unit);
        } catch (TimeoutException e) {
            // 超时本身不需要在这里做额外处理
            // 因为真正的取消动作统一放在 finally 中执行
        } catch (ExecutionException e) {
            // 如果任务线程内部抛出异常，Future 会把它包装成 ExecutionException
            // 这里取出原始 cause，并转换为合适的未检查异常重新抛出
            throw launderThrowable(e.getCause());
        } finally {
            // 无论是正常完成、异常完成还是超时，
            // 都调用 cancel(true) 作为“收尾动作”
            //
            // 1. 如果任务已经完成，那么这次 cancel 是无害的；
            // 2. 如果任务还在运行，那么 true 表示允许通过中断来取消它；
            // 3. 如果任务尚未开始执行，那么线程池会尽量避免再执行它。
            task.cancel(true);
        }
    }

    /**
     * 将 Throwable 转换为 RuntimeException 或 Error。
     *
     * <p>这是书中常见的 launderThrowable 模式：
     * 运行时异常直接抛出；
     * Error 直接抛出；
     * 其他受检异常则包装为 IllegalStateException。</p>
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
