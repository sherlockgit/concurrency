package com.sherlock.concurrency.chapter07.detailed_07_04;

import java.util.concurrent.TimeUnit;

/**
 * Thread 中断相关方法演示。
 *
 * <p>需要说明的是，书中的 7.4 官方代码清单并不是一个独立的 Java 类，
 * 而是指向 {@link Thread} 中断相关方法的 Javadoc。
 * 为了便于本地学习和调试，这里额外补充了一个演示类，
 * 用来观察 interrupt()、isInterrupted()、interrupted() 的实际行为。</p>
 */
public class ThreadInterruptDemo {

    public static void main(String[] args) throws InterruptedException {
        demonstrateInterruptOnBlockedThread();
        demonstrateInterruptedVsIsInterrupted();
    }

    /**
     * 演示 interrupt() 如何取消一个阻塞中的线程。
     *
     * <p>工作线程会周期性休眠，主线程稍后调用 interrupt()。
     * 因为 sleep 是可中断阻塞方法，所以工作线程会收到
     * InterruptedException，从而提前结束。</p>
     */
    private static void demonstrateInterruptOnBlockedThread() throws InterruptedException {
        Thread worker = new Thread(new SleepyWorker(), "sleepy-worker");
        worker.start();

        // 给工作线程一点时间，让它先进入休眠状态
        TimeUnit.MILLISECONDS.sleep(300);

        System.out.println("main: 准备中断工作线程");
        worker.interrupt();

        // 等待工作线程结束，便于观察完整输出
        worker.join();
        System.out.println("main: 工作线程已结束");
    }

    /**
     * 演示 isInterrupted() 与 interrupted() 的区别。
     *
     * <p>这里直接操作当前线程（main 线程）的中断标志：
     * 先设置中断状态，再分别调用 isInterrupted() 和 interrupted()，
     * 观察“是否清除标志位”的区别。</p>
     */
    private static void demonstrateInterruptedVsIsInterrupted() {
        Thread current = Thread.currentThread();

        // 先人为设置当前线程的中断状态
        current.interrupt();

        System.out.println("main: isInterrupted() = " + current.isInterrupted());

        // interrupted() 检查的是当前线程，并且会清除中断状态
        System.out.println("main: 第一次 Thread.interrupted() = " + Thread.interrupted());
        System.out.println("main: 第二次 Thread.interrupted() = " + Thread.interrupted());

        // 上面的第一次 interrupted() 已经把中断状态清除了，所以这里会输出 false
        System.out.println("main: 清除后 isInterrupted() = " + current.isInterrupted());
    }

    /**
     * 一个会周期性进入休眠状态的工作任务。
     *
     * <p>该任务的重点不在业务逻辑，而在于演示：
     * 当线程阻塞在 sleep 上时，如果外部线程调用 interrupt()，
     * sleep 会立即抛出 InterruptedException。</p>
     */
    private static class SleepyWorker implements Runnable {

        @Override
        public void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    System.out.println(Thread.currentThread().getName() + ": 开始休眠");

                    // sleep 是典型的可中断阻塞方法
                    TimeUnit.SECONDS.sleep(5);

                    // 如果没有被中断，休眠结束后会继续执行到这里
                    System.out.println(Thread.currentThread().getName() + ": 正常醒来");
                }
            } catch (InterruptedException e) {
                System.out.println(Thread.currentThread().getName() + ": 在阻塞时收到中断");

                // 当 sleep 抛出 InterruptedException 时，中断标志已经被清除了
                System.out.println(Thread.currentThread().getName()
                        + ": 捕获异常后 isInterrupted() = "
                        + Thread.currentThread().isInterrupted());

                // 如果当前任务的策略是“不能吞掉中断”，就应该恢复中断状态
                Thread.currentThread().interrupt();

                System.out.println(Thread.currentThread().getName()
                        + ": 恢复中断后 isInterrupted() = "
                        + Thread.currentThread().isInterrupted());
            }
        }
    }
}
