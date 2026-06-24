package com.sherlock.concurrency.chapter07.detailed_07_22;

import com.sherlock.concurrency.chapter07.detailed_07_21.TrackingExecutor;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * 使用 TrackingExecutor 保存未完成任务，以便稍后恢复执行的网络爬虫。
 *
 * <p>这是《Java 并发编程实战》中的 7.22，
 * 也是 7.21 {@link TrackingExecutor} 的直接使用场景。</p>
 *
 * <p>这一节要解决的问题是：</p>
 *
 * <p>1. 爬虫会不断发现新页面并递归提交新的抓取任务；</p>
 * <p>2. 当服务关闭时，部分任务可能尚未开始执行，部分任务可能执行到一半被取消；</p>
 * <p>3. 如果希望下次启动时能“从上次中断处继续抓”，
 *    就必须把这些未完成任务对应的 URL 保存下来。</p>
 *
 * <p>本类采用的策略是：</p>
 *
 * <p>1. 用 {@link TrackingExecutor} 包装线程池；</p>
 * <p>2. 关闭时先通过 {@code shutdownNow()} 拿到“尚未开始执行”的任务；</p>
 * <p>3. 如果在线程池终止前，某些正在执行的任务因为关闭而被中断取消，
 *    再通过 {@link TrackingExecutor#getCancelledTasks()} 取回这些任务；</p>
 * <p>4. 将这些任务对应的页面 URL 保存到 {@code urlsToCrawl} 中，
 *    以便下次 {@link #start()} 时继续提交。</p>
 *
 * <p>这就是“关闭时保存未完成工作，稍后恢复执行”的典型模式。</p>
 */
public abstract class WebCrawler {

    /**
     * 当前运行时使用的可跟踪线程池。
     *
     * <p>之所以声明为 volatile，是因为 start/stop 可能在不同线程中调用，
     * 需要确保对 exec 引用的更新对其他线程可见。</p>
     */
    private volatile TrackingExecutor exec;

    /**
     * 尚未完成抓取、需要在下一次启动时继续处理的 URL 集合。
     *
     * <p>受当前对象锁保护，因此所有访问都需要在 synchronized 方法或同步块中进行。</p>
     */
    private final Set<URL> urlsToCrawl = new HashSet<>();

    /**
     * 已经见过的 URL 记录。
     *
     * <p>用于避免重复抓取同一页面。
     * 这里使用并发 Map，允许多个抓取任务并发检查和记录 URL。</p>
     */
    private final ConcurrentMap<URL, Boolean> seen = new ConcurrentHashMap<>();

    /**
     * 停止时等待线程池终止的最大时间。
     */
    private static final long TIMEOUT = 500;

    /**
     * 等待时间单位。
     */
    private static final TimeUnit UNIT = MILLISECONDS;

    public WebCrawler(URL startUrl) {
        urlsToCrawl.add(startUrl);
    }

    /**
     * 启动爬虫。
     *
     * <p>每次启动都会新建一个 TrackingExecutor，
     * 然后把之前保存下来的待抓取 URL 重新提交为抓取任务。
     * 提交完成后清空 {@code urlsToCrawl}，因为这些任务已经再次进入执行流程。</p>
     */
    public synchronized void start() {
        exec = new TrackingExecutor(Executors.newCachedThreadPool());
        for (URL url : urlsToCrawl) {
            submitCrawlTask(url);
        }
        urlsToCrawl.clear();
    }

    /**
     * 停止爬虫，并保存所有未完成任务，以便下次恢复。
     *
     * <p>关闭步骤如下：</p>
     *
     * <p>1. 先调用 {@code shutdownNow()}，拿到尚未开始执行的任务；</p>
     * <p>2. 把这些任务对应的 URL 保存下来；</p>
     * <p>3. 等待线程池在给定时间内终止；</p>
     * <p>4. 如果成功终止，再取回“关闭过程中被中断取消”的任务；</p>
     * <p>5. 把这些任务对应的 URL 也保存下来；</p>
     * <p>6. 最后把 exec 置空，表示当前爬虫已经停止。</p>
     *
     * @throws InterruptedException 如果当前线程在等待线程池终止时被中断
     */
    public synchronized void stop() throws InterruptedException {
        try {
            saveUncrawled(exec.shutdownNow());
            if (exec.awaitTermination(TIMEOUT, UNIT)) {
                saveUncrawled(exec.getCancelledTasks());
            }
        } finally {
            exec = null;
        }
    }

    /**
     * 处理一个页面并返回从该页面中解析出的链接列表。
     *
     * <p>书中的示例把具体网页抓取/解析逻辑留给子类实现，
     * 因为这里的重点是“取消与恢复未完成任务”，而不是 HTML 解析本身。</p>
     *
     * @param url 待处理页面
     * @return 从该页面中提取出的后续链接
     */
    protected abstract List<URL> processPage(URL url);

    /**
     * 将未完成任务列表还原为待抓取 URL 集合。
     *
     * <p>TrackingExecutor 返回的是尚未执行或执行中被取消的原始任务对象，
     * 这里把它们转换回页面 URL，保存到 {@code urlsToCrawl} 中，
     * 以便下次启动时重新提交。</p>
     *
     * @param uncrawled 未完成任务列表
     */
    private void saveUncrawled(List<Runnable> uncrawled) {
        for (Runnable task : uncrawled) {
            urlsToCrawl.add(((CrawlTask) task).getPage());
        }
    }

    /**
     * 提交一个页面抓取任务。
     *
     * @param url 待抓取页面
     */
    private void submitCrawlTask(URL url) {
        exec.execute(new CrawlTask(url));
    }

    /**
     * 单个页面抓取任务。
     *
     * <p>每个任务负责处理一个页面，并把从该页面中发现的新链接继续提交为新的抓取任务。</p>
     */
    private class CrawlTask implements Runnable {
        private final URL url;

        CrawlTask(URL url) {
            this.url = url;
        }

        /**
         * 判断当前页面是否已经抓取过。
         *
         * <p>如果已存在于 seen 中，说明该 URL 已经处理过或正在处理，就不应重复抓取。</p>
         *
         * @return true 表示已抓取过
         */
        boolean alreadyCrawled() {
            return seen.putIfAbsent(url, true) != null;
        }

        /**
         * 将当前页面标记回“未抓取”状态。
         *
         * <p>书中保留了这个辅助方法，表示如果任务在某些时机决定放弃当前页面，
         * 可以把 seen 中的记录清掉，从而允许后续重试。</p>
         */
        void markUncrawled() {
            seen.remove(url);
            System.out.printf("marking %s uncrawled%n", url);
        }

        @Override
        public void run() {
            if (alreadyCrawled()) {
                return;
            }

            for (URL link : processPage(url)) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                submitCrawlTask(link);
            }
        }

        /**
         * 获取当前任务对应的页面 URL。
         *
         * @return 当前任务页面
         */
        public URL getPage() {
            return url;
        }
    }
}
