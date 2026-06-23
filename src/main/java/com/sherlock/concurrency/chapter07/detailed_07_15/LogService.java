package com.sherlock.concurrency.chapter07.detailed_07_15;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 为 LogWriter 添加可靠的取消与关闭支持。
 *
 * <p>这是《Java 并发编程实战》中的 7.15，也是对 7.13、7.14 的正式修正方案。</p>
 *
 * <p>7.13 的 {@code LogWriter} 只有生产者-消费者结构，没有关闭协议；</p>
 * <p>7.14 的 {@code BrokenLogWriter} 虽然增加了 {@code isShutdown} 和中断退出逻辑，
 * 但“检查关闭状态”和“把消息放入队列”之间不是原子操作，
 * 仍然可能导致日志丢失或生产者永久阻塞。</p>
 *
 * <p>7.15 的关键改进是引入 {@code reservations} 计数：</p>
 *
 * <p>1. 生产者在确认服务尚未关闭后，先把 {@code reservations} 加一，
 *    表示“我预定了一条将来必须被写出的日志”；</p>
 * <p>2. 然后再把消息真正放入队列；</p>
 * <p>3. 消费者线程每成功取出并写出一条日志后，再把 {@code reservations} 减一；</p>
 * <p>4. 关闭时只有在 {@code isShutdown == true && reservations == 0} 时，
 *    日志线程才真正退出。</p>
 *
 * <p>这样即使关闭请求与日志提交并发发生，也能保证：
 * 所有在关闭前“成功预定”的日志消息，最终都会被写出后再退出。</p>
 */
@ThreadSafe
public class LogService {

    /**
     * 日志消息队列。
     */
    private final BlockingQueue<String> queue;

    /**
     * 后台日志线程。
     */
    private final LoggerThread loggerThread;

    /**
     * 最终执行日志输出的 writer。
     */
    private final PrintWriter writer;

    /**
     * 是否已进入关闭流程。
     *
     * <p>受当前对象锁保护；所有访问都需要在 synchronized(this) 中进行。</p>
     */
    private boolean isShutdown;

    /**
     * 已经“被接受但尚未写出”的日志条目数。
     *
     * <p>这个字段是 7.15 解决竞态的核心：
     * 它表示还有多少条日志是系统承诺要处理完的。</p>
     */
    private int reservations;

    public LogService(Writer writer) {
        this.queue = new LinkedBlockingQueue<>();
        this.loggerThread = new LoggerThread();
        this.writer = new PrintWriter(writer);
    }

    /**
     * 启动后台日志线程。
     */
    public void start() {
        loggerThread.start();
    }

    /**
     * 请求关闭日志服务。
     *
     * <p>这里并不会立即粗暴终止日志线程，而是分两步：</p>
     *
     * <p>1. 先设置 {@code isShutdown = true}，拒绝后续新的日志请求；</p>
     * <p>2. 再中断日志线程，使其从可能的 {@code queue.take()} 阻塞中醒来，
     *    重新检查是否已经满足退出条件。</p>
     *
     * <p>真正的退出条件不是“收到中断就退出”，
     * 而是“已经关闭，并且没有任何待完成日志”。</p>
     */
    public void stop() {
        synchronized (this) {
            isShutdown = true;
        }
        loggerThread.interrupt();
    }

    /**
     * 提交一条日志消息。
     *
     * <p>这是 7.15 与 7.14 最大的区别所在：</p>
     *
     * <p>生产者线程先在同步块中做两件事：</p>
     * <p>1. 检查服务是否已经关闭；</p>
     * <p>2. 如果未关闭，则先增加 {@code reservations}。</p>
     *
     * <p>这样一来，就把“我接受这条日志”这个动作和“将来必须写出它”这个承诺绑定在了一起。</p>
     *
     * <p>即使线程随后阻塞在 {@code queue.put(msg)} 上，
     * 或者此时正好另一个线程调用了 {@link #stop()}，
     * 日志线程也不会过早退出，因为它会看到还有未完成的 reservations。</p>
     *
     * @param msg 日志消息
     * @throws InterruptedException 如果当前线程在等待队列空间时被中断
     */
    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            if (isShutdown) {
                throw new IllegalStateException("日志服务已关闭");
            }
            ++reservations;
        }
        queue.put(msg);
    }

    /**
     * 后台日志线程。
     *
     * <p>它的退出条件不是简单的“被中断”，而是：</p>
     *
     * <p>{@code isShutdown == true && reservations == 0}</p>
     *
     * <p>只要还有预定未完成的日志，就必须继续工作。
     * 如果在 {@code queue.take()} 上被中断，也只是醒来重新检查退出条件，
     * 而不是立即结束线程。</p>
     */
    private class LoggerThread extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    try {
                        synchronized (LogService.this) {
                            if (isShutdown && reservations == 0) {
                                break;
                            }
                        }

                        String msg = queue.take();

                        synchronized (LogService.this) {
                            --reservations;
                        }

                        writer.println(msg);
                    } catch (InterruptedException e) {
                        // 收到中断时不立刻退出，而是回到循环开头重新检查：
                        // 如果已关闭且无待写日志，则退出；
                        // 否则继续处理剩余日志。
                    }
                }
            } finally {
                writer.close();
            }
        }
    }
}
