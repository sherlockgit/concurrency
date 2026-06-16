package com.sherlock.concurrency.chapter07.detailed_07_07;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 不可取消任务：在退出前恢复中断状态。
 *
 * <p>《Java 并发编程实战》中的 7.7 用来说明：
 * 有些方法由于职责限制，不能把 {@link InterruptedException} 继续向上抛出，
 * 但同时又不能简单吞掉中断。
 * 这时可以先记录“当前线程曾经被中断过”，继续完成当前这个不可取消的操作，
 * 然后在方法返回前恢复中断状态。</p>
 *
 * <p>注意，这里的“不可取消”并不是说线程永远不能被取消，
 * 而是说“当前这一步操作必须完成，不能因为一次中断就半途而废”。
 * 但即便如此，也不能丢失中断信号，因此需要在最后调用
 * {@link Thread#interrupt()} 恢复中断状态。</p>
 */
public class NoncancelableTask {

    /**
     * 从阻塞队列中获取下一个任务。
     *
     * <p>这是 7.7 的核心方法：
     * 如果在 {@code take()} 阻塞期间被中断，并不会直接结束方法，
     * 而是先记下“发生过中断”，然后继续重试。
     * 一旦真正拿到任务，就在 {@code finally} 中恢复中断状态。</p>
     *
     * <p>这种写法适用于：
     * 当前方法必须返回一个结果，不能简单因为中断就提前放弃；
     * 但又希望调用者在方法返回后仍然能感知到中断事件。</p>
     *
     * @param queue 任务队列
     * @return 下一个任务
     */
    public Task getNextTask(BlockingQueue<Task> queue) {
        boolean interrupted = false;
        try {
            while (true) {
                try {
                    return queue.take();
                } catch (InterruptedException e) {
                    // 当前这一步操作被设计为“不可取消”：
                    // 即使被中断，也要继续等待下一个任务
                    interrupted = true;
                }
            }
        } finally {
            // 如果在等待期间发生过中断，就在退出前恢复中断状态
            // 这样调用者/上层框架仍然可以知道：当前线程其实被中断过
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 一个最小任务抽象。
     */
    public interface Task {
        void execute();
    }

    /**
     * 一个简单的打印任务，用于演示队列取任务后的执行流程。
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
     * 简单演示 7.7 的行为。
     *
     * <p>流程如下：
     * 1. 工作线程先阻塞在 queue.take() 上；
     * 2. 主线程先对它发出中断；
     * 3. 工作线程不会直接退出，而是记录中断并继续等待；
     * 4. 主线程稍后放入真正任务；
     * 5. 工作线程拿到任务返回，同时恢复中断状态。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();
        final NoncancelableTask demo = new NoncancelableTask();

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                Task task = demo.getNextTask(queue);

                System.out.println(Thread.currentThread().getName()
                        + ": getNextTask 返回后，中断状态 = "
                        + Thread.currentThread().isInterrupted());

                task.execute();
            }
        }, "noncancelable-worker");

        worker.start();

        // 先让工作线程进入阻塞状态
        TimeUnit.MILLISECONDS.sleep(300);
        System.out.println("main: 先中断工作线程");
        worker.interrupt();

        // 再稍等一会儿，随后放入真正任务
        TimeUnit.MILLISECONDS.sleep(300);
        System.out.println("main: 放入真正任务");
        queue.put(new PrintTask("学习 7.7：恢复中断状态"));

        worker.join();
    }
}
