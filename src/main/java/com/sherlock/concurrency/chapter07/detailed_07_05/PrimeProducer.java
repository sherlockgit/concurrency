package com.sherlock.concurrency.chapter07.detailed_07_05;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;

/**
 * 使用中断来取消素数生产者。
 *
 * <p>这个示例是 {@code detailed_07_03.BrokenPrimeProducer} 的修正版，用来说明：
 * 当任务可能阻塞在诸如 {@link BlockingQueue#put(Object)} 这样的阻塞方法上时，
 * 仅靠一个 {@code volatile} 取消标志并不可靠，因为线程一旦阻塞，就没有机会再次轮询该标志。</p>
 *
 * <p>正确做法是使用“中断”作为取消机制。因为 {@code put} 是可响应中断的阻塞方法，
 * 所以当外部线程调用 {@link #cancel()} 时，即使当前生产者线程正阻塞在 {@code put} 上，
 * 也会立刻抛出 {@link InterruptedException}，从而及时结束线程。</p>
 */
public class PrimeProducer extends Thread {
    /**
     * 用于保存已生成素数的阻塞队列。
     *
     * <p>生产者线程不断生成新的素数并放入该队列，
     * 消费者线程则可以从队列中按需取出。</p>
     */
    private final BlockingQueue<BigInteger> queue;

    public PrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    /**
     * 不断生成素数，直到当前线程收到中断信号。
     *
     * <p>循环条件使用 {@code isInterrupted()} 检查当前线程的中断状态。
     * 与错误示例不同，这里不再维护单独的取消标志，而是直接复用线程的中断机制，
     * 这样既能在普通执行路径中检查取消状态，也能在阻塞操作中收到取消通知。</p>
     */
    @Override
    public void run() {
        try {
            // 从 1 开始，借助 nextProbablePrime 依次计算下一个素数
            BigInteger p = BigInteger.ONE;

            // 只要线程未被中断，就持续生产素数
            while (!Thread.currentThread().isInterrupted()) {
                // 将下一个素数放入阻塞队列
                // 如果队列已满，put 会阻塞等待；但该阻塞可以被 interrupt 打断
                queue.put(p = p.nextProbablePrime());
            }
        } catch (InterruptedException consumed) {
            // 这里直接结束 run 方法即可
            // 因为在本示例中，“收到中断并退出”就是任务被取消时的预期行为
        }
    }

    /**
     * 取消生产者线程。
     *
     * <p>通过调用 Thread.interrupt() 发出中断请求：
     * 如果线程正在正常运行，下一轮循环会检测到中断状态并退出；
     * 如果线程阻塞在 put 上，则会立刻抛出 InterruptedException 并结束。</p>
     */
    public void cancel() {
        interrupt();
    }
}
