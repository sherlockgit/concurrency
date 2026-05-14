package com.sherlock.concurrency.chapter05.detailed_05_09;

import com.sherlock.concurrency.chapter05.detailed_05_08.FileCrawler;
import com.sherlock.concurrency.chapter05.detailed_05_08.Indexer;

import java.io.File;
import java.io.FileFilter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 启动桌面搜索
 * *
 * 在程序中启动了多个爬虫程序和索引建立程序，每个程序都在各自的线程中运行。
 * 消费者线程永远不会退出，因而程序无法终止，虽然这个示例使用了显式管理的线程，
 * 但许多生产者一消费者设计也可以通过Executor任务执行框架来实现，
 * 本身也使用了生产者一消费者模式。
 */
public class StartIndexing {

    /**
     * 启动文件索引过程。
     * 该方法创建生产者-消费者模式的线程：生产者（FileCrawler）遍历文件系统并将文件放入队列，
     * 消费者（Indexer）从队列中取出文件进行索引。
     *
     * @param roots 要索引的根目录数组
     */
    public static void startIndexing(File[] roots) {
        // 创建一个有界的阻塞队列，用于在生产者和消费者之间传递文件对象
        BlockingQueue<File> queue = new LinkedBlockingQueue<>();

        // 定义一个文件过滤器，此处接受所有文件（无条件通过）
        FileFilter filter = new FileFilter() {
            public boolean accept(File file) {
                return true; // 接受任何文件
            }
        };

        // 为每个根目录启动一个生产者线程（FileCrawler）
        for (File root : roots) { // 为 for 循环添加花括号
            new Thread(new FileCrawler(queue, filter, root)).start();
        }

        // 启动固定数量的消费者线程（Indexer），N_CONSUMERS 为消费者线程数
        for (int i = 0; i < 5; i++) { // 为 for 循环添加花括号
            new Thread(new Indexer(queue)).start();
        }
    }

}
