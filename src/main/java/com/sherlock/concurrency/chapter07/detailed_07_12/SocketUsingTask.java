package com.sherlock.concurrency.chapter07.detailed_07_12;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * SocketUsingTask。
 *
 * <p>这是《Java 并发编程实战》中的 7.12，用来说明：
 * 如何把“非标准取消策略”从线程级别进一步推广到任务级别。</p>
 *
 * <p>在 7.11 {@code ReaderThread} 中，作者通过重写 {@code Thread.interrupt()}
 * 来封装“关闭 socket 以打断阻塞 I/O”这种非标准取消方式。
 * 但这种做法有一个局限：它要求任务必须自己控制线程，也就是任务与线程强绑定。</p>
 *
 * <p>而在更常见的 Executor / 线程池模型中，
 * 任务通常只是一个 {@link Callable} 或 {@link Runnable}，
 * 真正执行它的是线程池内部拥有的工作线程。
 * 这时任务本身无法像 7.11 那样直接重写线程的 {@code interrupt()}，
 * 因为线程并不是任务自己创建和拥有的。</p>
 *
 * <p>因此，7.12 提供了一种更通用的方案：
 * 1. 定义一个支持“自定义取消逻辑”的任务接口 {@link CancellableTask}；
 * 2. 让任务自己提供一个带有特殊取消行为的 {@link RunnableFuture}；
 * 3. 在线程池中通过覆盖 {@link ThreadPoolExecutor#newTaskFor(Callable)}，
 *    当发现提交的是 CancellableTask 时，就使用任务自定义的 Future 包装器。</p>
 *
 * <p>这样一来，外部调用者仍然只需要使用标准的 {@code Future.cancel(true)}，
 * 但真正的取消动作可以先执行任务自己的非标准清理逻辑（比如关闭 socket），
 * 然后再继续执行标准 Future 的取消流程。</p>
 *
 * @param <T> 任务返回值类型
 */
public abstract class SocketUsingTask<T> implements CancellableTask<T> {

    /**
     * 与当前任务关联的 socket。
     *
     * <p>之所以保留这个引用，是因为一旦任务需要被取消，
     * 可以通过关闭 socket 来打断阻塞中的 I/O 操作。</p>
     *
     * <p>该字段由 synchronized 方法保护，确保并发访问可见且互斥。</p>
     */
    private Socket socket;

    /**
     * 为当前任务关联一个 socket。
     *
     * <p>通常在任务开始建立网络连接后调用，
     * 这样当外部取消该任务时，任务就知道该关闭哪个 socket。</p>
     *
     * @param s 当前任务使用的 socket
     */
    protected synchronized void setSocket(Socket s) {
        socket = s;
    }

    /**
     * 任务自己的取消逻辑。
     *
     * <p>这里的“取消”并不只是设置中断标志，
     * 而是先执行更符合 socket I/O 特性的取消方式：关闭 socket。</p>
     *
     * <p>一旦 socket 被关闭，阻塞中的 I/O 操作通常会因为 {@link IOException}
     * 而返回或失败，从而让任务尽快结束。</p>
     */
    @Override
    public synchronized void cancel() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // 关闭失败时不额外处理；取消流程仍应继续
        }
    }

    /**
     * 为当前任务创建一个“带有自定义取消行为”的 Future。
     *
     * <p>这是本节最核心的部分：
     * 当外部代码调用 {@code future.cancel(true)} 时，
     * 并不是直接执行默认的 FutureTask 取消逻辑，
     * 而是先调用当前任务自己的 {@link #cancel()}，
     * 例如关闭 socket，随后再调用 {@code super.cancel(mayInterruptIfRunning)}，
     * 继续执行标准的 Future 取消逻辑（包括在需要时中断执行线程）。</p>
     *
     * <p>这样就把“非标准取消”与“标准 Future 取消语义”组合到了一起。</p>
     *
     * @return 带有自定义取消行为的 Future 包装器
     */
    @Override
    public RunnableFuture<T> newTask() {
        return new FutureTask<T>(this) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                try {
                    SocketUsingTask.this.cancel();
                } finally {
                    return super.cancel(mayInterruptIfRunning);
                }
            }
        };
    }
}

/**
 * 支持自定义取消逻辑的任务接口。
 *
 * <p>普通 {@link Callable} 只有“执行”语义，没有“如何取消我自己”的扩展点。
 * 这个接口在 Callable 的基础上增加了两个能力：</p>
 *
 * <p>1. {@link #cancel()}：定义任务自己的取消逻辑；</p>
 * <p>2. {@link #newTask()}：定义如何把自己包装成支持该取消逻辑的 Future。</p>
 *
 * @param <T> 任务结果类型
 */
interface CancellableTask<T> extends Callable<T> {
    /**
     * 执行任务自定义的取消动作。
     */
    void cancel();

    /**
     * 创建与该任务绑定的 Future 包装器。
     *
     * @return 支持自定义取消行为的 Future
     */
    RunnableFuture<T> newTask();
}

/**
 * 支持“可取消任务”扩展点的线程池。
 *
 * <p>标准 {@link ThreadPoolExecutor} 在接收到一个 Callable 时，
 * 会通过 {@link ThreadPoolExecutor#newTaskFor(Callable)} 把它包装成 FutureTask。
 * 这里覆写这个工厂方法：
 * 如果传入的是 {@link CancellableTask}，就使用任务自己提供的 {@link RunnableFuture}；
 * 否则仍回退到默认实现。</p>
 *
 * <p>这样外部调用者完全不需要知道底层是否存在“非标准取消策略”，
 * 仍然只是正常 submit 任务、拿到 Future、调用 cancel(true)。
 * 但对于支持自定义取消的任务，线程池会自动接入它们的特殊取消逻辑。</p>
 */
@ThreadSafe
class CancellingExecutor extends ThreadPoolExecutor {

    public CancellingExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public CancellingExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public CancellingExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public CancellingExecutor(int corePoolSize,
                              int maximumPoolSize,
                              long keepAliveTime,
                              TimeUnit unit,
                              BlockingQueue<Runnable> workQueue,
                              ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    /**
     * 根据任务类型决定使用哪种 Future 包装器。
     *
     * <p>如果是普通 Callable，使用线程池默认的 FutureTask；
     * 如果是 CancellableTask，则使用任务自定义的 Future，
     * 从而让 {@code Future.cancel(true)} 具备任务专属的取消逻辑。</p>
     *
     * @param callable 待包装任务
     * @return 任务对应的 RunnableFuture
     * @param <T> 任务结果类型
     */
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        if (callable instanceof CancellableTask) {
            return ((CancellableTask<T>) callable).newTask();
        }
        return super.newTaskFor(callable);
    }
}
