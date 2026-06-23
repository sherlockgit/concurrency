package com.sherlock.concurrency.chapter07.detailed_07_14;

import com.sherlock.concurrency.annoations.NotRecommend;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 不可靠地为日志服务添加关闭支持。
 *
 * <p>这是《Java 并发编程实战》中的 7.14，对应的是一个“错误示例片段”。</p>
 *
 * <p>它试图在 7.13 的 {@code LogWriter} 基础上加入 shutdown 支持：
 * 通过一个 {@code isShutdown} 标志拒绝后续新日志，
 * 并在关闭时中断后台日志线程。</p>
 *
 * <p>这个思路表面上看合理，但实际上并不可靠，主要有两个问题：</p>
 *
 * <p>1. {@link #log(String)} 中“检查关闭状态”和“把消息放入队列”不是原子操作。
 * 某个生产者线程可能刚检查完 {@code isShutdown == false}，
 * 还没来得及真正把消息放入队列，另一个线程就执行了 {@link #stop()}。
 * 这样会造成关闭与入队之间的竞态。</p>
 *
 * <p>2. 如果队列已满，生产者线程会阻塞在 {@code queue.put(msg)} 上。
 * 此时若服务关闭并中断日志线程，日志线程可能直接退出，
 * 导致再也没有消费者去腾出队列空间，生产者线程就可能永久阻塞，
 * 或者消息最终丢失。</p>
 *
 * <p>这也正是 7.15 要引入 {@code reservations} 计数并重新设计关闭协议的原因。</p>
 */
@NotRecommend
public class BrokenLogWriter {

    /**
     * 日志消息队列。
     */
    private final BlockingQueue<String> queue;

    /**
     * 后台日志线程。
     */
    private final LoggerThread logger;

    /**
     * 队列容量上限。
     */
    private static final int CAPACITY = 1000;

    /**
     * 关闭标志。
     *
     * <p>一旦为 true，表示日志服务不再接受新的日志请求。
     * 该字段通过 synchronized 访问，确保可见性与互斥。</p>
     */
    private boolean isShutdown;

    public BrokenLogWriter(Writer writer) {
        this.queue = new LinkedBlockingQueue<>(CAPACITY);
        this.logger = new LoggerThread(writer);
    }

    /**
     * 启动后台日志线程。
     */
    public void start() {
        logger.start();
    }

    /**
     * 停止日志服务。
     *
     * <p>这里采用的“关闭协议”非常简单：</p>
     *
     * <p>1. 设置 {@code isShutdown = true}，拒绝后续新的日志请求；</p>
     * <p>2. 中断日志线程，使其从 {@code queue.take()} 的阻塞中醒来并退出。</p>
     *
     * <p>问题在于：这种协议无法保证“所有已经提交或即将提交成功的日志消息”
     * 都能在退出前被处理完，因此是一个不可靠的关闭方案。</p>
     */
    public void stop() {
        synchronized (this) {
            isShutdown = true;
        }
        logger.interrupt();
    }

    /**
     * 提交一条日志消息。
     *
     * <p>这是本错误示例最关键的竞态点所在：</p>
     *
     * <p>先在同步块中检查服务是否已关闭；</p>
     * <p>如果还未关闭，再在同步块外执行 {@code queue.put(msg)}。</p>
     *
     * <p>这种写法的问题是，“检查”与“入队”之间不是原子操作。
     * 在检查通过后到真正入队前这段时间内，服务可能已经被关闭。
     * 而一旦队列满了，调用线程还可能阻塞在 put 上，
     * 此时若日志线程已经因为中断而退出，就可能再也没有线程来消费队列了。</p>
     *
     * @param msg 日志消息
     * @throws InterruptedException 如果当前线程在等待队列空间时被中断
     */
    public void log(String msg) throws InterruptedException {
        synchronized (this) {
            if (isShutdown) {
                throw new IllegalStateException("日志服务已关闭");
            }
        }

        // 这里是问题根源：
        // 检查通过后，到真正 put 之前，服务可能已经被 stop() 关闭。
        // 如果此时队列已满，调用线程还会阻塞在这里；
        // 而日志线程却可能已经退出，导致调用线程永久等待。
        queue.put(msg);
    }

    /**
     * 后台日志线程。
     *
     * <p>它不断从队列中取出消息并写入底层 Writer。
     * 一旦在 take() 上收到中断，就直接退出并关闭 writer。</p>
     *
     * <p>这也是当前设计的另一个问题：
     * 日志线程退出时，并不会检查队列里是否还有尚未写出的消息，
     * 因此可能造成日志丢失。</p>
     */
    private class LoggerThread extends Thread {
        private final PrintWriter writer;

        public LoggerThread(Writer writer) {
            this.writer = new PrintWriter(writer, true);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    writer.println(queue.take());
                }
            } catch (InterruptedException ignored) {
                // 当前错误实现把“线程被中断”直接解释为“立刻退出”，
                // 没有保证队列中剩余日志被全部刷出。
            } finally {
                writer.close();
            }
        }
    }
}
