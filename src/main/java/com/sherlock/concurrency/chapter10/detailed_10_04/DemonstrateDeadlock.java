package com.sherlock.concurrency.chapter10.detailed_10_04;

import com.sherlock.concurrency.chapter10.detailed_10_02.DynamicOrderDeadlock;
import com.sherlock.concurrency.chapter10.detailed_10_02.DynamicOrderDeadlock.Account;
import com.sherlock.concurrency.chapter10.detailed_10_02.DynamicOrderDeadlock.DollarAmount;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 在典型条件下诱发死锁的驱动程序。
 *
 * <p>这是《Java 并发编程实战》中的 10.4。</p>
 *
 * <p>10.2 展示了一个有死锁风险的转账方法：
 * 它总是先锁 fromAccount，再锁 toAccount。
 * 当两个线程分别执行 A->B 和 B->A 时，它们可能以相反顺序获取同一组账户锁，
 * 从而发生动态锁顺序死锁。</p>
 *
 * <p>10.4 的作用是把这个风险放大：
 * 创建多个账户、多个转账线程，让这些线程反复随机选择两个账户进行转账。
 * 当运行次数足够多时，某一刻很可能出现两个线程刚好选择相反方向的转账，
 * 并在错误时机各自持有一把锁，最终形成死锁。</p>
 *
 * <p>这个类故意调用 10.2 中有问题的
 * {@link DynamicOrderDeadlock#transferMoney(Account, Account, DollarAmount)}。
 * 10.3 的 {@code InduceLockOrder} 展示的是修复方式。</p>
 */
public class DemonstrateDeadlock {

    /**
     * 转账线程数量。
     */
    private static final int NUM_THREADS = 20;

    /**
     * 账户数量。
     */
    private static final int NUM_ACCOUNTS = 5;

    /**
     * 每个线程尝试转账的次数。
     */
    private static final int NUM_ITERATIONS = 1000000;

    /**
     * 监控死锁的最长时间。
     *
     * <p>官方示例直接启动普通线程，会在死锁后一直挂住。
     * 本地示例为了便于运行和观察，把工作线程设为 daemon，
     * 并由 main 线程在限定时间内检测死锁。</p>
     */
    private static final long DEADLOCK_DETECTION_TIMEOUT_SECONDS = 10;

    public static void main(String[] args) throws InterruptedException {
        final Random random = new Random();
        final Account[] accounts = new Account[NUM_ACCOUNTS];

        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(new DollarAmount(1000000));
        }

        class TransferThread extends Thread {

            TransferThread(int index) {
                super("transfer-thread-" + index);
                setDaemon(true);
            }

            @Override
            public void run() {
                for (int i = 0; i < NUM_ITERATIONS; i++) {
                    int fromAccount = random.nextInt(NUM_ACCOUNTS);
                    int toAccount = random.nextInt(NUM_ACCOUNTS);
                    DollarAmount amount = new DollarAmount(random.nextInt(1000));

                    try {
                        DynamicOrderDeadlock.transferMoney(
                                accounts[fromAccount],
                                accounts[toAccount],
                                amount);
                    } catch (DynamicOrderDeadlock.InsufficientFundsException ignored) {
                        /*
                         * 这个驱动程序关注的是锁顺序死锁，不关注余额不足。
                         * 余额不足时忽略异常，继续下一次随机转账。
                         */
                    }
                }
            }
        }

        for (int i = 0; i < NUM_THREADS; i++) {
            new TransferThread(i).start();
        }

        detectDeadlock();
    }

    private static void detectDeadlock() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        long deadline = System.nanoTime()
                + TimeUnit.SECONDS.toNanos(DEADLOCK_DETECTION_TIMEOUT_SECONDS);

        while (System.nanoTime() < deadline) {
            long[] deadlockedThreadIds = threadMXBean.findDeadlockedThreads();
            if (deadlockedThreadIds != null) {
                ThreadInfo[] threadInfos =
                        threadMXBean.getThreadInfo(deadlockedThreadIds, true, true);
                System.out.println("deadlock detected:");
                for (ThreadInfo threadInfo : threadInfos) {
                    System.out.println(threadInfo.getThreadName()
                            + " waiting for " + threadInfo.getLockName());
                }
                return;
            }

            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("deadlock not detected within "
                + DEADLOCK_DETECTION_TIMEOUT_SECONDS + " seconds");
    }
}
