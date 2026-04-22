package com.sherlock.concurrency.chapter05.detailed_05_08;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.BlockingQueue;

/**
 * 桌面搜索应用程序中的生产者任务
 *
 * 生产者一消费者模式提供了一种适合线程的方法将桌面搜索问题分解为更简单的组件。
 * * 将文件遍历与建立索引等功能分解为独立的操作，比将所有功能都放到一个操作中
 * * 实现有着更高的代码可读性和可重用性:每个操作只需完成一个任务，并且阻塞队
 * * 列将负责所有的控制流，因此每个功能的代码都更加简单和清晰。
 * *
 * 生产者一消费者模式同样能带来许多性能优势。生产者和消费者可以并发地执行。
 * * 如果一个是IO密集型，另一个是CPU密集型，那么并发执行的吞吐率要高于串行执行的吞吐率。
 * * 如果生产者和消费者的并行度不同，那么将它们紧密耦合在一起会把整体并行度降低为二者中更小的并行度
 */
public class FileCrawler implements Runnable{
    // 阻塞队列，用于存放待处理的文件（生产者-消费者模式中的队列）
    private final BlockingQueue<File> fileQueue;
    // 文件过滤器，用于筛选需要处理的文件（例如只接受 .txt 文件）
    private final FileFilter fileFilter;
    // 文件遍历的根目录
    private final File root;

    public FileCrawler(BlockingQueue<File> fileQueue,FileFilter fileFilter,File root) {
        this.fileQueue = fileQueue;
        this.fileFilter = fileFilter;
        this.root = root;
    }

    // 线程执行入口
    public void run() {
        try {
            // 从根目录开始递归遍历
            crawl(root);
        } catch (InterruptedException e) {
            // 若当前线程被中断，恢复中断状态（让上层代码能够感知）
            Thread.currentThread().interrupt();
        }
    }

    // 递归遍历目录，将符合条件的文件放入阻塞队列
    private void crawl(File root) throws InterruptedException {
        // 获取当前目录下所有符合 fileFilter 的文件/子目录
        File[] entries = root.listFiles(fileFilter);
        if (entries != null) {
            for (File entry : entries) {
                if (entry.isDirectory()) {
                    // 如果是子目录，递归调用 crawl 继续遍历
                    crawl(entry);
                } else if (!alreadyIndexed(entry)) {
                    // 如果是文件，且尚未被索引过，则将其放入阻塞队列
                    // 阻塞队列的 put 方法会在队列满时阻塞，直到有空间可用
                    fileQueue.put(entry);
                }
            }
        }
    }

    // 假设存在的方法：检查文件是否已经被处理过（避免重复放入队列）
    // 实际代码中需要具体实现，例如维护一个 Set<File>
    private boolean alreadyIndexed(File f) {
        // 此处仅为示意，真实逻辑可能是判断文件是否已存在于某个索引集合中
        return false;
    }
}
