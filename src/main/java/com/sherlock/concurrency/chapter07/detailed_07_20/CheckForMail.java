package com.sherlock.concurrency.chapter07.detailed_07_20;

import com.sherlock.concurrency.annoations.Recommend;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 使用“生命周期被单次方法调用约束”的私有 Executor。
 *
 * <p>这是《Java 并发编程实战》中的 7.20。
 * 这一节想说明的重点不是某种复杂取消协议，而是另一种更简单的资源管理方式：</p>
 *
 * <p>如果某个线程池只为了完成一次方法调用中的并行工作而存在，
 * 那就不要把它做成全局共享资源，而应该在方法内部临时创建、
 * 在方法结束前关闭，并把它的生命周期严格限制在这次方法调用之内。</p>
 *
 * <p>这种做法的好处是：</p>
 *
 * <p>1. 所有权非常清晰：谁创建线程池，谁负责关闭它；</p>
 * <p>2. 不需要让外部调用者感知这个 Executor 的存在；</p>
 * <p>3. 不会留下难以管理的长期存活后台线程。</p>
 *
 * <p>本示例中，{@link #checkMail(Set, long, TimeUnit)} 会并发检查多个邮件主机，
 * 只要其中任意一个主机存在新邮件，就把结果记录到 {@link AtomicBoolean} 中。
 * 无论最终是否超时、是否发现新邮件，方法结束前都会关闭这个私有线程池。</p>
 */
@Recommend
public class CheckForMail {

    /**
     * 并发检查多个主机上是否有新邮件。
     *
     * <p>实现思路如下：</p>
     *
     * <p>1. 为这一次方法调用临时创建一个私有线程池；</p>
     * <p>2. 为每个主机提交一个检查任务；</p>
     * <p>3. 所有任务共享一个 {@link AtomicBoolean}，只要任意任务发现新邮件，就把它设置为 true；</p>
     * <p>4. 无论任务提交过程是否发生异常，都在 finally 中关闭线程池并等待指定时长；</p>
     * <p>5. 最后返回是否发现新邮件。</p>
     *
     * <p>这里的线程池不是类成员，而是方法内局部变量，
     * 正是为了体现“生命周期由一次方法调用边界限定”的设计思想。</p>
     *
     * @param hosts 需要检查的主机集合
     * @param timeout 等待所有检查任务结束的最长时间
     * @param unit 超时单位
     * @return 只要任意一个主机发现新邮件，就返回 true
     * @throws InterruptedException 如果当前线程在等待线程池终止时被中断
     */
    public boolean checkMail(Set<String> hosts, long timeout, TimeUnit unit)
            throws InterruptedException {
        ExecutorService exec = Executors.newCachedThreadPool();
        final AtomicBoolean hasNewMail = new AtomicBoolean(false);

        try {
            for (final String host : hosts) {
                exec.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (checkMail(host)) {
                            hasNewMail.set(true);
                        }
                    }
                });
            }
        } finally {
            // 不再接收新任务，并等待已提交任务在给定时间内执行完成
            exec.shutdown();
            exec.awaitTermination(timeout, unit);
        }

        return hasNewMail.get();
    }

    /**
     * 检查某个具体主机是否存在新邮件。
     *
     * <p>书中的清单没有展开具体网络交互细节，这里保留为占位实现。</p>
     *
     * @param host 邮件主机
     * @return 默认返回 false，表示未发现新邮件
     */
    private boolean checkMail(String host) {
        // 占位：这里可以替换成真实的邮件轮询逻辑
        return false;
    }
}
