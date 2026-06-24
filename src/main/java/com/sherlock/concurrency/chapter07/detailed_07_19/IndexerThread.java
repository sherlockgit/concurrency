package com.sherlock.concurrency.chapter07.detailed_07_19;

import java.io.File;
import java.util.concurrent.BlockingQueue;

/**
 * 7.19：IndexingService 的消费者线程。
 *
 * <p>这一清单是 7.17 {@code IndexingService} 中消费者线程部分的独立展开版本，
 * 用来单独强调“识别毒丸并退出”的消费协议。</p>
 *
 * <p>它不断从阻塞队列中取出文件：
 * 普通文件进入索引逻辑；
 * 一旦取到毒丸对象，就说明生产者已经结束，于是消费者线程退出。</p>
 */
public class IndexerThread extends Thread {

    private final BlockingQueue<File> queue;
    private final File poisonPill;

    public IndexerThread(BlockingQueue<File> queue, File poisonPill) {
        this.queue = queue;
        this.poisonPill = poisonPill;
    }

    @Override
    public void run() {
        try {
            while (true) {
                File file = queue.take();
                if (file == poisonPill) {
                    break;
                } else {
                    indexFile(file);
                }
            }
        } catch (InterruptedException consumed) {
            // 当前示例把消费者中断解释为退出线程
        }
    }

    /**
     * 为文件建立索引。
     *
     * <p>这里保留为可覆写的占位实现，方便以后接入真正的索引逻辑。</p>
     *
     * @param file 待索引文件
     */
    protected void indexFile(File file) {
        // 占位：这里可以扩展为真正的索引逻辑
    }
}
