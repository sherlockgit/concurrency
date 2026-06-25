package com.sherlock.concurrency.chapter07.detailed_07_23;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 典型的线程池工作线程结构。
 *
 * <p>这是《Java 并发编程实战》中的 7.23。
 * 官方清单本身是一个“片段（fragment）”，来源于 JDK {@code ThreadPoolExecutor}
 * 中工作线程的主运行循环，并不是一个可以直接独立运行的完整类。</p>
 *
 * <p>因此，这里补成一个“简化且可编译”的教学版本，
 * 目的是帮助理解线程池工作线程通常是如何组织执行循环的：</p>
 *
 * <p>1. 工作线程可能带着一个首个任务启动；</p>
 * <p>2. 之后不断从任务队列中获取下一个任务；</p>
 * <p>3. 每次执行前调用 beforeExecute 钩子；</p>
 * <p>4. 执行任务并记录异常；</p>
 * <p>5. 每次执行后调用 afterExecute 钩子；</p>
 * <p>6. 当没有更多任务可取或线程池进入关闭状态时，退出循环；</p>
 * <p>7. 最终统一进入 processWorkerExit 做收尾。</p>
 *
 * <p>这个类并不试图完整复刻 JDK 的所有状态机细节，
 * 例如 workerCount、runState、线程中断细节、线程补偿创建等；
 * 它只保留 7.23 想强调的“典型 worker 主循环结构”。</p>
 */
public class WorkerThreadStructure {

    /**
     * 共享任务队列。
     *
     * <p>真正的线程池通常会把待执行任务放到一个阻塞队列中，
     * 然后由多个工作线程从中不断获取任务。</p>
     */
    private final BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();

    /**
     * 是否进入关闭状态。
     *
     * <p>这里做了简化：当 shutdown=true 且队列已空时，worker 退出。</p>
     */
    private volatile boolean shutdown;

    /**
     * 向队列中提交一个任务。
     *
     * @param task 待执行任务
     * @throws InterruptedException 如果当前线程在等待队列空间时被中断
     */
    public void execute(Runnable task) throws InterruptedException {
        workQueue.put(task);
    }

    /**
     * 请求关闭线程池。
     *
     * <p>简化语义：不再期望继续获取新任务，
     * 已经在队列中的任务仍然会被工作线程取走并执行完。</p>
     */
    public void shutdown() {
        shutdown = true;
    }

    /**
     * 创建一个工作线程，并让它进入典型的 worker 运行循环。
     *
     * <p>这里允许传入一个首个任务 {@code firstTask}，
     * 这和 JDK 中 worker 线程可能带着“初始化任务”启动的模式一致。</p>
     *
     * @param firstTask 首个任务，可为 null
     * @param threadName 工作线程名称
     * @return 创建出的线程对象
     */
    public Thread newWorker(Runnable firstTask, String threadName) {
        final Worker worker = new Worker(firstTask);
        return new Thread(new Runnable() {
            @Override
            public void run() {
                runWorker(worker);
            }
        }, threadName);
    }

    /**
     * 从队列中获取下一个任务。
     *
     * <p>这个方法对应 JDK 里的 {@code getTask()}，
     * 负责决定 worker 线程何时继续等待任务、何时退出。</p>
     *
     * <p>这里的简化退出规则是：</p>
     *
     * <p>1. 如果线程池已关闭并且队列为空，则返回 null，通知 worker 退出；</p>
     * <p>2. 否则，尝试限时轮询队列；</p>
     * <p>3. 如果轮询超时但线程池尚未关闭，则继续等待下一轮；</p>
     * <p>4. 如果等待期间被中断，而线程池已经关闭，则允许退出；否则继续重试。</p>
     *
     * @return 下一个待执行任务；若返回 null，则表示 worker 应退出
     */
    private Runnable getTask() {
        while (true) {
            if (shutdown && workQueue.isEmpty()) {
                return null;
            }

            try {
                Runnable task = workQueue.poll(200, TimeUnit.MILLISECONDS);
                if (task != null) {
                    return task;
                }
            } catch (InterruptedException e) {
                if (shutdown) {
                    return null;
                }
            }
        }
    }

    /**
     * 工作线程的主运行循环。
     *
     * <p>这是 7.23 的核心：</p>
     *
     * <p>1. 如果 worker 带着首个任务启动，就先执行首个任务；</p>
     * <p>2. 否则不断调用 {@link #getTask()} 从队列中获取任务；</p>
     * <p>3. 每轮执行前后都调用钩子方法；</p>
     * <p>4. 无论正常结束还是异常结束，最终都会调用 {@link #processWorkerExit(Worker, boolean)}。</p>
     *
     * @param worker 当前工作线程上下文
     */
    final void runWorker(Worker worker) {
        Thread currentThread = Thread.currentThread();
        Runnable task = worker.firstTask;
        worker.firstTask = null;

        boolean completedAbruptly = true;
        try {
            while (task != null || (task = getTask()) != null) {
                Throwable thrown = null;
                try {
                    beforeExecute(currentThread, task);
                    task.run();
                } catch (RuntimeException x) {
                    thrown = x;
                    throw x;
                } catch (Error x) {
                    thrown = x;
                    throw x;
                } catch (Throwable x) {
                    thrown = x;
                    throw new Error(x);
                } finally {
                    afterExecute(task, thrown);
                }

                task = null;
                worker.completedTasks++;
            }

            completedAbruptly = false;
        } finally {
            processWorkerExit(worker, completedAbruptly);
        }
    }

    /**
     * 任务执行前钩子。
     *
     * <p>对应 JDK 中的 {@code beforeExecute}。
     * 子类可以覆写它来加入日志、监控、上下文初始化等逻辑。</p>
     *
     * @param thread 当前工作线程
     * @param task 即将执行的任务
     */
    protected void beforeExecute(Thread thread, Runnable task) {
    }

    /**
     * 任务执行后钩子。
     *
     * <p>对应 JDK 中的 {@code afterExecute}。
     * 即使任务抛出异常，这个方法也会被调用。</p>
     *
     * @param task 已执行任务
     * @param throwable 任务抛出的异常；若任务正常完成则为 null
     */
    protected void afterExecute(Runnable task, Throwable throwable) {
    }

    /**
     * 工作线程退出后的收尾逻辑。
     *
     * <p>对应 JDK 中的 {@code processWorkerExit}。
     * 子类可以覆写它来统计退出信息、决定是否补建线程等。</p>
     *
     * @param worker 当前 worker 上下文
     * @param completedAbruptly true 表示由于异常导致的突然退出
     */
    protected void processWorkerExit(Worker worker, boolean completedAbruptly) {
        System.out.println("worker exit, completedTasks=" + worker.completedTasks
                + ", completedAbruptly=" + completedAbruptly);
    }

    /**
     * worker 上下文。
     *
     * <p>真实的 ThreadPoolExecutor.Worker 还包含锁、线程引用等更多状态；
     * 这里仅保留 7.23 理解所需的最小字段。</p>
     */
    static final class Worker {
        private Runnable firstTask;
        private long completedTasks;

        private Worker(Runnable firstTask) {
            this.firstTask = firstTask;
        }
    }

    /**
     * 简单演示。
     *
     * <p>这个 main 不是书中清单的一部分，
     * 只是为了让本地补全版本可直观运行：</p>
     *
     * <p>1. 创建一个 worker 并给它一个首个任务；</p>
     * <p>2. 再向队列提交两个后续任务；</p>
     * <p>3. 请求 shutdown；</p>
     * <p>4. 等待 worker 把队列中的任务处理完并退出。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        WorkerThreadStructure structure = new WorkerThreadStructure();

        structure.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("queue task 1");
            }
        });

        structure.execute(new Runnable() {
            @Override
            public void run() {
                System.out.println("queue task 2");
            }
        });

        Thread worker = structure.newWorker(new Runnable() {
            @Override
            public void run() {
                System.out.println("first task");
            }
        }, "worker-demo");

        worker.start();
        structure.shutdown();
        worker.join();
    }
}
