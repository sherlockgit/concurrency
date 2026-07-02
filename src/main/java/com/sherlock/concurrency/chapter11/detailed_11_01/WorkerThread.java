package com.sherlock.concurrency.chapter11.detailed_11_01;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 串行访问任务队列的工作线程。
 *
 * <p>这是《Java 并发编程实战》中的 11.1。</p>
 *
 * <p>这个例子非常简单：一个工作线程不断从 {@link BlockingQueue} 中取出任务，
 * 然后调用任务的 {@link Runnable#run()} 方法。</p>
 *
 * <p>它体现了第 11 章“性能与可伸缩性”中的一个重要点：
 * 如果所有任务都必须经过同一个队列，并且只有一个工作线程消费这个队列，
 * 那么无论有多少任务提交者，任务执行部分都会被串行化。</p>
 *
 * <p>也就是说，这种结构很容易成为吞吐量瓶颈：</p>
 *
 * <p>1. 多个生产者可以同时把任务放入队列；</p>
 * <p>2. 但只有一个 {@code WorkerThread} 负责取任务和执行任务；</p>
 * <p>3. 如果任务执行很慢，队列会越积越多；</p>
 * <p>4. 增加提交任务的线程数量，并不能提高任务执行速度。</p>
 *
 * <p>这不是说单工作线程一定错误。
 * 如果任务必须严格按顺序执行，或者任务本身很轻，这种结构可以很简单。
 * 但如果目标是可伸缩性，就需要考虑多个工作线程、任务拆分、减少串行瓶颈等方案。</p>
 */
public class WorkerThread extends Thread {

    /**
     * 工作线程消费的任务队列。
     *
     * <p>队列本身是线程安全的。
     * 生产者线程可以调用 {@link BlockingQueue#put(Object)} 或 offer 提交任务；
     * 工作线程通过 {@link BlockingQueue#take()} 阻塞等待任务。</p>
     */
    private final BlockingQueue<Runnable> queue;

    public WorkerThread(BlockingQueue<Runnable> queue) {
        this.queue = queue;
    }

    /**
     * 工作线程主循环。
     *
     * <p>{@code queue.take()} 在队列为空时会阻塞。
     * 当其他线程向队列放入任务后，工作线程被唤醒并执行该任务。</p>
     *
     * <p>如果工作线程在等待或执行期间被中断，这里会捕获
     * {@link InterruptedException} 并退出循环，让线程结束。
     * 这是一个简单的取消策略。</p>
     */
    @Override
    public void run() {
        while (true) {
            try {
                Runnable task = queue.take();
                task.run();
            } catch (InterruptedException e) {
                break; // 允许线程退出
            }
        }
    }

    /**
     * 简单演示。
     *
     * <p>这里提交 5 个任务，但只有一个 WorkerThread 执行它们。
     * 从输出可以看到，所有任务都由同一个线程按队列顺序串行执行。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        BlockingQueue<Runnable> queue = new LinkedBlockingQueue<Runnable>();
        WorkerThread workerThread = new WorkerThread(queue);
        workerThread.setName("single-worker");
        workerThread.start();

        for (int i = 0; i < 5; i++) {
            final int taskId = i;
            queue.put(new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName()
                            + " execute task " + taskId);
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        TimeUnit.MILLISECONDS.sleep(700);
        workerThread.interrupt();
        workerThread.join();
        System.out.println("worker stopped");
    }
}
