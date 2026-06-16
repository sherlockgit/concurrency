package com.sherlock.concurrency.chapter07.detailed_07_06;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 向调用者传递 InterruptedException。
 *
 * <p>《Java 并发编程实战》中的 7.6 是一个代码片段，
 * 用来说明处理中断的第一种基本策略：如果当前方法本身允许抛出
 * {@link InterruptedException}，那么最简单、最合理的做法通常就是
 * 不要吞掉这个异常，而是直接向调用者传递。</p>
 *
 * <p>本地这里将书中的片段补成一个完整类，方便结合 7.5、7.7 一起理解：
 * 7.6 讲的是“能传播就传播”，7.7 讲的是“如果不能传播，就恢复中断状态”。</p>
 */
public class TaskQueue {

    /**
     * 任务队列。
     *
     * <p>消费者线程可以通过 {@link #getNextTask()} 从队列中获取下一个任务；
     * 如果此时队列为空，{@code take()} 会进入阻塞等待，并且该阻塞可以响应中断。</p>
     */
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

    /**
     * 向队列中放入一个任务。
     *
     * @param task 待加入队列的任务
     * @throws InterruptedException 如果当前线程在等待队列空间时被中断
     */
    public void put(Task task) throws InterruptedException {
        queue.put(task);
    }

    /**
     * 获取下一个待执行任务。
     *
     * <p>这里是 7.6 的核心：
     * 调用了会抛出 {@link InterruptedException} 的阻塞方法 {@code take()}，
     * 但并不在当前方法中捕获该异常，而是直接将它声明在方法签名上，
     * 让调用者自行决定如何处理中断。</p>
     *
     * <p>这种写法适用于“当前方法本身就是一个阻塞方法”的场景。
     * 它不会擅自吞掉中断，也不会替上层调用者做决策。</p>
     *
     * @return 下一个任务
     * @throws InterruptedException 如果当前线程在等待任务时被中断
     */
    public Task getNextTask() throws InterruptedException {
        return queue.take();
    }

    /**
     * 获取并执行一个任务。
     *
     * <p>这个方法同样选择继续传播 {@link InterruptedException}。
     * 这说明只要调用链上的方法都允许抛出该异常，就可以一直向上层传播，
     * 直到某个真正“拥有中断策略”的边界处再统一处理。</p>
     *
     * @throws InterruptedException 如果等待任务时被中断
     */
    public void runOneTask() throws InterruptedException {
        Task task = getNextTask();
        task.execute();
    }

    /**
     * 一个最小任务抽象。
     *
     * <p>书里的片段只关心“如何传播中断”，并不关心任务本身的复杂业务逻辑，
     * 所以这里保留最简单的任务接口即可。</p>
     */
    public interface Task {
        void execute();
    }

    /**
     * 一个简单的打印任务，用来演示任务执行流程。
     */
    public static class PrintTask implements Task {
        private final String message;

        public PrintTask(String message) {
            this.message = message;
        }

        @Override
        public void execute() {
            System.out.println("执行任务: " + message);
        }
    }

    /**
     * 简单演示：
     * 一个生产者线程稍后放入任务，主线程阻塞等待并执行该任务。
     *
     * <p>这里的 main 方法直接声明 {@code throws InterruptedException}，
     * 目的是让示例更清楚地表现“向调用者传播中断”的思想，而不是在每一层都捕获它。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        final TaskQueue taskQueue = new TaskQueue();

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 稍微延迟一下，模拟主线程先进入阻塞等待
                    TimeUnit.MILLISECONDS.sleep(300);
                    taskQueue.put(new PrintTask("学习 7.6：传播 InterruptedException"));
                } catch (InterruptedException e) {
                    // 生产者线程是 Runnable，不能继续向外抛 checked exception，
                    // 因此这里按照 7.7/5.10 的思路恢复中断状态。
                    Thread.currentThread().interrupt();
                }
            }
        }, "task-producer");

        producer.start();

        // 主线程在这里等待任务；如果等待期间被中断，就直接将中断异常传播给 JVM/上层调用者
        taskQueue.runOneTask();
        producer.join();
    }
}
