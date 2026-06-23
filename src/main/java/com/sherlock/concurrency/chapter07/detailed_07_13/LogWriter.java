package com.sherlock.concurrency.chapter07.detailed_07_13;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * LogWriter。
 *
 * <p>这是《Java 并发编程实战》中的 7.13，
 * 用来展示一个基于生产者-消费者模式的日志服务。</p>
 *
 * <p>多个业务线程负责调用 {@link #log(String)} 生产日志消息，
 * 专门的日志线程 {@link LoggerThread} 负责从阻塞队列中取出日志并写入目标 Writer。
 * 这种设计把“业务线程产生日志”和“真正执行 I/O 写日志”解耦开了，
 * 既简化了调用方逻辑，也让日志写入天然具备串行化顺序。</p>
 *
 * <p>不过，这一版实现有一个重要缺陷：
 * 它没有提供可靠的关闭机制（shutdown support）。
 * 一旦日志线程启动，它会一直阻塞在 {@code queue.take()} 上等待消息；
 * 如果程序想优雅停止日志服务，就会遇到一系列取消与关闭问题。
 * 这正是 7.14、7.15 接下来要解决的主题。</p>
 */
public class LogWriter {

    /**
     * 日志消息队列。
     *
     * <p>业务线程通过 {@link #log(String)} 把日志消息放入队列，
     * 日志线程则不断从该队列中取出并写出。</p>
     */
    private final BlockingQueue<String> queue;

    /**
     * 真正执行日志写出的后台线程。
     */
    private final LoggerThread logger;

    /**
     * 队列容量上限。
     *
     * <p>使用有界队列可以避免日志消息无限堆积导致内存无限增长。
     * 但也意味着：当队列已满时，调用 {@link #log(String)} 的线程会阻塞在 {@code put()} 上。</p>
     */
    private static final int CAPACITY = 1000;

    /**
     * 构造日志服务。
     *
     * @param writer 日志最终写入的目标 Writer
     */
    public LogWriter(Writer writer) {
        this.queue = new LinkedBlockingQueue<>(CAPACITY);
        this.logger = new LoggerThread(writer);
    }

    /**
     * 启动后台日志线程。
     *
     * <p>调用此方法后，日志线程会开始不断从队列中取消息并写出。</p>
     */
    public void start() {
        logger.start();
    }

    /**
     * 提交一条日志消息。
     *
     * <p>如果队列未满，消息会被放入队列等待后台线程处理；
     * 如果队列已满，当前调用线程会阻塞在 {@code put()} 上，
     * 直到队列腾出空间。</p>
     *
     * <p>这里声明 {@link InterruptedException}，
     * 表示调用者如果在等待队列空间时被中断，需要自行决定如何处理中断。</p>
     *
     * @param msg 待写出的日志消息
     * @throws InterruptedException 如果当前线程在等待队列空间时被中断
     */
    public void log(String msg) throws InterruptedException {
        queue.put(msg);
    }

    /**
     * 后台日志线程。
     *
     * <p>它不断从队列中获取日志消息，并顺序写入底层 Writer。
     * 由于只有这一个线程真正接触 Writer，
     * 所以不需要多个业务线程直接争用 I/O 资源。</p>
     *
     * <p>但这里也埋下了 7.13 的缺陷：
     * 一旦 run 方法进入 {@code while (true)}，就缺少一个明确、可靠的退出条件。
     * 当前实现只能依赖线程被中断来跳出阻塞，
     * 可是单纯中断并不足以保证“所有待写日志都被处理完再关闭”，
     * 也没有明确的“拒绝新日志”策略。</p>
     */
    private class LoggerThread extends Thread {
        private final PrintWriter writer;

        /**
         * 创建日志线程，并将底层 Writer 包装成 PrintWriter。
         *
         * <p>这里开启 autoFlush，表示在适用的写操作后自动刷新缓冲区。</p>
         *
         * @param writer 最终日志写出目标
         */
        public LoggerThread(Writer writer) {
            this.writer = new PrintWriter(writer, true);
        }

        /**
         * 持续从队列中取出日志并写出。
         *
         * <p>当线程阻塞在 {@code queue.take()} 上时，如果收到中断，
         * 会抛出 {@link InterruptedException}，随后跳到 finally 关闭 writer。</p>
         *
         * <p>问题在于：这种“被中断就直接退出”的实现没有可靠关闭协议。
         * 如果此时队列里还有尚未处理的日志，或者此时仍有线程正在调用 {@link #log(String)}，
         * 那么服务就可能处于不一致状态。这正是后续章节要改进的地方。</p>
         */
        @Override
        public void run() {
            try {
                while (true) {
                    writer.println(queue.take());
                }
            } catch (InterruptedException ignored) {
                // 当前版本把中断简单地当作退出信号，
                // 但并没有定义完整的关闭协议。
            } finally {
                writer.close();
            }
        }
    }
}
