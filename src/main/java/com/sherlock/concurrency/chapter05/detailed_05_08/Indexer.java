package com.sherlock.concurrency.chapter05.detailed_05_08;

import java.io.File;
import java.util.concurrent.BlockingQueue;

/**
 * 桌面搜索应用程序中的消费者任务
 * *
 * *
 */
public class Indexer implements Runnable{

    // 共享的阻塞队列，用于接收 FileCrawler 生产出来的文件
    private final BlockingQueue<File> queue;

    public Indexer(BlockingQueue<File> queue) {
        this.queue = queue;
    }

    public void run() {
        try {
            // 持续从队列中取出文件并处理，直到线程被中断
            while (true) {
                // take() 方法会阻塞，直到队列中有可用的文件
                File file = queue.take();
                // 对文件进行索引（假设的索引方法）
                indexFile(file);
            }
        } catch (InterruptedException e) {
            // 当线程在等待 take() 时被中断，恢复中断状态以便上层逻辑知道
            Thread.currentThread().interrupt();
        }
    }

    // 假设存在的索引文件的方法（需要具体实现）
    private void indexFile(File file) {
        // 实际逻辑：读取文件内容，建立索引等
        System.out.println("Indexing: " + file.getAbsolutePath());
    }

}
