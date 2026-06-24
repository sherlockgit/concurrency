package com.sherlock.concurrency.chapter07.detailed_07_18;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.BlockingQueue;

/**
 * 7.18：IndexingService 的生产者线程。
 *
 * <p>这一清单是 7.17 {@code IndexingService} 中生产者线程部分的独立展开版本，
 * 用来单独强调“在 finally 中投递毒丸”这一关闭协议。</p>
 *
 * <p>它适用于这样的场景：
 * 生产者负责递归产生任务，消费者负责从阻塞队列中取任务处理，
 * 当生产者结束时，需要通过一个显式的哨兵对象告诉消费者“不会再有新任务了”。</p>
 */
public class CrawlerThread extends Thread {

    private final BlockingQueue<File> queue;
    private final FileFilter fileFilter;
    private final File root;
    private final File poisonPill;

    public CrawlerThread(BlockingQueue<File> queue,
                         File root,
                         FileFilter fileFilter,
                         File poisonPill) {
        this.queue = queue;
        this.root = root;
        this.fileFilter = fileFilter;
        this.poisonPill = poisonPill;
    }

    @Override
    public void run() {
        try {
            crawl(root);
        } catch (InterruptedException e) {
            // 被中断后结束遍历
        } finally {
            while (true) {
                try {
                    queue.put(poisonPill);
                    break;
                } catch (InterruptedException e) {
                    // 无论发生多少次中断，都必须确保毒丸最终成功投递
                }
            }
        }
    }

    /**
     * 递归遍历目录。
     *
     * @param root 当前遍历根目录
     * @throws InterruptedException 如果在放入队列时被中断
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

    /**
     * 判断文件是否已经建立过索引。
     *
     * <p>这里保留为可覆写的占位实现，方便在学习或扩展时替换为真正逻辑。</p>
     *
     * @param file 待检查文件
     * @return 默认返回 false
     */
    protected boolean alreadyIndexed(File file) {
        return false;
    }
}
