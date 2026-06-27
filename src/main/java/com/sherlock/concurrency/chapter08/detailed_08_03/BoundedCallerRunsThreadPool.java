package com.sherlock.concurrency.chapter08.detailed_08_03;

import com.sherlock.concurrency.annoations.Recommend;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 固定大小线程池 + 有界队列 + CallerRuns 饱和策略。
 *
 * <p>这是《Java 并发编程实战》中的 8.3。
 * 官方清单本身是一个片段，意图是展示一种常见且实用的线程池配置：</p>
 *
 * <p>1. 工作线程数固定；</p>
 * <p>2. 任务队列有界，避免任务无限堆积导致内存膨胀；</p>
 * <p>3. 当线程池和队列都满时，使用 {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy}，
 *    让提交任务的线程自己执行任务，从而自然地对上游施加背压。</p>
 *
 * <p>这里把书中的片段补成一个完整工具类，方便直接复用和运行。</p>
 */
@Recommend
public final class BoundedCallerRunsThreadPool {

    private BoundedCallerRunsThreadPool() {
    }

    /**
     * 创建一个固定大小、带有有界队列和 CallerRuns 饱和策略的线程池。
     *
     * <p>参数语义如下：</p>
     *
     * <p>1. corePoolSize == maximumPoolSize，表示线程数固定；</p>
     * <p>2. keepAliveTime 为 0，表示没有“超出核心线程数的额外线程”需要回收；</p>
     * <p>3. 使用 {@link ArrayBlockingQueue} 作为有界任务队列；</p>
     * <p>4. 使用 {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy} 作为拒绝策略，
     *    当线程池达到饱和时，由调用者线程自己运行任务。</p>
     *
     * @param nThreads 固定工作线程数
     * @param queueCapacity 队列容量
     * @return 配置完成的线程池
     */
    public static ExecutorService newBoundedFixedThreadPool(int nThreads, int queueCapacity) {
        return new ThreadPoolExecutor(
                nThreads,
                nThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 简单演示。
     *
     * <p>这里只是说明：当线程池和队列满时，调用线程会自己执行任务。
     * 这不是书中的正式清单内容，只是为了方便本地试验。</p>
     */
    public static void main(String[] args) {
        ExecutorService executor = newBoundedFixedThreadPool(2, 2);

        for (int i = 0; i < 8; i++) {
            final int taskId = i;
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println(Thread.currentThread().getName() + " -> task " + taskId);
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        executor.shutdown();
    }
}
