package com.sherlock.concurrency.chapter07.detailed_07_17;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 使用“毒丸对象（poison pill）”关闭桌面搜索服务。
 *
 * <p>这是《Java 并发编程实战》中的 7.17，对应章节标题是：
 * Shutdown with poison pill。</p>
 *
 * <p>该示例展示了一种适用于“生产者线程数量已知、消费者线程数量已知”的关闭协议：</p>
 *
 * <p>1. 正常数据通过阻塞队列在线程之间传递；</p>
 * <p>2. 当生产者确认自己不再产生新任务后，向队列中放入一个特殊对象“毒丸”；</p>
 * <p>3. 消费者线程从队列中取到这个特殊对象后，不再把它当作普通任务处理，而是把它解释为“服务结束信号”，从而退出。</p>
 *
 * <p>与单纯使用中断相比，这种方式的特点是：
 * 它把“结束信号”作为队列协议的一部分显式传递，
 * 非常适合这类基于生产者-消费者模式的服务关闭。</p>
 *
 * <p>在官方源码中，7.18 和 7.19 分别是本类中的两个内部线程类：
 * 生产者线程 {@link CrawlerThread} 与消费者线程 {@link IndexerThread}。</p>
 */
public class IndexingService {

    /**
     * 队列容量上限。
     */
    private static final int CAPACITY = 1000;

    /**
     * 毒丸对象。
     *
     * <p>它并不是一个真正要被索引的文件，而是一个特殊哨兵值，
     * 用来告诉消费者线程：“生产者已经结束，不会再有新任务了”。</p>
     *
     * <p>这里使用 {@code new File("")} 只是为了得到一个唯一对象引用。
     * 关键点不在于这个 File 的路径，而在于消费者通过引用比较 {@code file == POISON}
     * 来识别它不是普通业务数据。</p>
     */
    private static final File POISON = new File("");

    /**
     * 消费者线程：负责从队列中取出文件并建立索引。
     */
    private final IndexerThread consumer = new IndexerThread();

    /**
     * 生产者线程：负责遍历目录并把待索引文件放入队列。
     */
    private final CrawlerThread producer = new CrawlerThread();

    /**
     * 生产者与消费者之间共享的任务队列。
     */
    private final BlockingQueue<File> queue;

    /**
     * 文件过滤器。
     *
     * <p>这里额外包装了一层，保证目录本身总是会被接受，
     * 否则递归遍历无法继续深入子目录。</p>
     */
    private final FileFilter fileFilter;

    /**
     * 遍历根目录。
     */
    private final File root;

    public IndexingService(File root, final FileFilter fileFilter) {
        this.root = root;
        this.queue = new LinkedBlockingQueue<>(CAPACITY);
        this.fileFilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || fileFilter.accept(file);
            }
        };
    }

    /**
     * 判断文件是否已被索引。
     *
     * <p>书中的示例没有展开具体索引存储逻辑，因此这里保留为占位实现。</p>
     *
     * @param file 待检查文件
     * @return 默认返回 false，表示都需要继续索引
     */
    private boolean alreadyIndexed(File file) {
        return false;
    }

    /**
     * 7.18：IndexingService 的生产者线程。
     *
     * <p>它负责递归遍历根目录，把尚未建立索引的文件放入共享队列。</p>
     *
     * <p>最重要的逻辑在 {@code finally} 中：
     * 无论遍历是正常结束，还是因为中断而提前结束，
     * 它都会保证最终向队列中放入一个毒丸对象。
     * 这样消费者线程就能可靠地知道“生产已经结束”。</p>
     */
    class CrawlerThread extends Thread {
        @Override
        public void run() {
            try {
                crawl(root);
            } catch (InterruptedException e) {
                // 收到中断后结束遍历，随后仍会在 finally 中投递毒丸
            } finally {
                while (true) {
                    try {
                        queue.put(POISON);
                        break;
                    } catch (InterruptedException e) {
                        // 如果在放毒丸时又被中断，仍然必须重试，
                        // 因为“让消费者收到结束信号”比保留这次中断更重要
                    }
                }
            }
        }

        /**
         * 递归遍历目录树。
         *
         * @param root 当前遍历根目录
         * @throws InterruptedException 如果在向队列放文件时被中断
         */
        private void crawl(File root) throws InterruptedException {
            File[] entries = root.listFiles(fileFilter);
            if (entries != null) {
                for (File entry : entries) {
                    if (entry.isDirectory()) {
                        crawl(entry);
                    } else if (!alreadyIndexed(entry)) {
                        queue.put(entry);
                    }
                }
            }
        }
    }

    /**
     * 7.19：IndexingService 的消费者线程。
     *
     * <p>它不断从队列中取出文件：
     * 如果取到的是普通文件，就执行索引；
     * 如果取到的是毒丸对象，就说明生产阶段结束，于是退出线程。</p>
     */
    class IndexerThread extends Thread {
        @Override
        public void run() {
            try {
                while (true) {
                    File file = queue.take();
                    if (file == POISON) {
                        break;
                    } else {
                        indexFile(file);
                    }
                }
            } catch (InterruptedException consumed) {
                // 当前示例把消费者中断视为直接退出
            }
        }

        /**
         * 为文件建立索引。
         *
         * <p>书中的清单未展开具体实现，这里只保留占位方法。</p>
         *
         * @param file 待索引文件
         */
        public void indexFile(File file) {
            // 占位：这里可以接入真正的全文索引逻辑
        }
    }

    /**
     * 启动服务。
     */
    public void start() {
        producer.start();
        consumer.start();
    }

    /**
     * 请求停止服务。
     *
     * <p>这里停止的是生产者线程：
     * 生产者被中断后会结束目录遍历，并在 finally 中投递毒丸；
     * 消费者随后读取到毒丸后退出。</p>
     */
    public void stop() {
        producer.interrupt();
    }

    /**
     * 等待服务终止。
     *
     * <p>当消费者线程结束时，说明它已经读取到了毒丸，
     * 并处理完了毒丸前所有已经入队的任务。</p>
     *
     * @throws InterruptedException 如果等待期间当前线程被中断
     */
    public void awaitTermination() throws InterruptedException {
        consumer.join();
    }
}
