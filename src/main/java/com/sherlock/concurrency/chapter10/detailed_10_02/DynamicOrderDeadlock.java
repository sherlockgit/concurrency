package com.sherlock.concurrency.chapter10.detailed_10_02;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 动态锁顺序死锁。
 *
 * <p>这是《Java 并发编程实战》中的 10.2。</p>
 *
 * <p>10.1 的死锁来自两个方法写死了相反的加锁顺序：
 * 一个方法先锁 left 再锁 right，另一个方法先锁 right 再锁 left。</p>
 *
 * <p>10.2 更隐蔽：代码看起来只有一个方法 {@link #transferMoney(Account, Account, DollarAmount)}，
 * 方法内部也总是“先锁 fromAccount，再锁 toAccount”。
 * 但是 fromAccount 和 toAccount 是调用方传进来的参数，
 * 所以真实加锁顺序会随着参数顺序动态变化。</p>
 *
 * <p>例如：</p>
 *
 * <p>线程 A 调用 {@code transferMoney(accountA, accountB, amount)}，
 * 它的锁顺序是 accountA -> accountB；</p>
 *
 * <p>线程 B 同时调用 {@code transferMoney(accountB, accountA, amount)}，
 * 它的锁顺序是 accountB -> accountA。</p>
 *
 * <p>这样就重新形成了“两个线程以相反顺序获取同一组锁”的死锁条件。</p>
 *
 * <p>这个类故意保留有问题的实现，10.3 会展示如何通过固定锁顺序避免这类死锁。</p>
 */
public class DynamicOrderDeadlock {

    /**
     * 转账操作。
     *
     * <p>警告：这个方法有死锁风险。</p>
     *
     * <p>它总是先锁转出账户，再锁转入账户。
     * 如果所有调用都只从 A 转到 B，那么锁顺序稳定；
     * 但真实系统中转账方向是动态的，同一时刻可能既有 A->B，也有 B->A，
     * 此时两个线程就可能互相等待。</p>
     *
     * @param fromAccount 转出账户
     * @param toAccount 转入账户
     * @param amount 转账金额
     * @throws InsufficientFundsException 余额不足
     */
    public static void transferMoney(Account fromAccount,
                                     Account toAccount,
                                     DollarAmount amount)
            throws InsufficientFundsException {
        synchronized (fromAccount) {
            synchronized (toAccount) {
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException();
                } else {
                    fromAccount.debit(amount);
                    toAccount.credit(amount);
                }
            }
        }
    }

    /**
     * 为演示稳定复现死锁准备的转账方法。
     *
     * <p>它和 {@link #transferMoney(Account, Account, DollarAmount)} 使用同样的动态锁顺序，
     * 只是在线程拿到第一把锁后，通过 CountDownLatch 等待另一个线程也拿到第一把锁，
     * 从而稳定制造“双方都持有一把锁，并等待对方释放另一把锁”的局面。</p>
     */
    private static void transferMoneyForDemo(Account fromAccount,
                                             Account toAccount,
                                             DollarAmount amount,
                                             CountDownLatch firstLocksAcquired)
            throws InsufficientFundsException {
        synchronized (fromAccount) {
            firstLocksAcquired.countDown();
            awaitQuietly(firstLocksAcquired);

            synchronized (toAccount) {
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException();
                } else {
                    fromAccount.debit(amount);
                    toAccount.credit(amount);
                }
            }
        }
    }

    /**
     * 简单金额类型。
     *
     * <p>官方示例中这个类只是占位实现。
     * 为了让本地示例可以编译和运行，这里用 int 保存金额，并把它实现为不可变对象。</p>
     */
    static class DollarAmount implements Comparable<DollarAmount> {

        private final int amount;

        public DollarAmount(int amount) {
            this.amount = amount;
        }

        public DollarAmount add(DollarAmount other) {
            return new DollarAmount(amount + other.amount);
        }

        public DollarAmount subtract(DollarAmount other) {
            return new DollarAmount(amount - other.amount);
        }

        @Override
        public int compareTo(DollarAmount other) {
            return Integer.compare(amount, other.amount);
        }

        @Override
        public String toString() {
            return String.valueOf(amount);
        }
    }

    /**
     * 账户对象。
     *
     * <p>在这个示例中，账户对象本身就是锁对象。
     * 转账时需要同时锁住两个账户，才能保证“从一个账户扣钱”和“给另一个账户加钱”
     * 这个复合操作是原子的。</p>
     */
    static class Account {

        private DollarAmount balance;

        private final int acctNo;

        private static final AtomicInteger sequence = new AtomicInteger();

        public Account(DollarAmount openingBalance) {
            this.balance = openingBalance;
            this.acctNo = sequence.incrementAndGet();
        }

        void debit(DollarAmount amount) {
            balance = balance.subtract(amount);
        }

        void credit(DollarAmount amount) {
            balance = balance.add(amount);
        }

        DollarAmount getBalance() {
            return balance;
        }

        int getAcctNo() {
            return acctNo;
        }

        @Override
        public String toString() {
            return "Account-" + acctNo;
        }
    }

    static class InsufficientFundsException extends Exception {
    }

    /**
     * 演示动态锁顺序死锁。
     *
     * <p>两个线程分别执行 A->B 和 B->A。
     * 它们持有的锁对象相同，但加锁顺序相反，因此会死锁。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        final Account accountA = new Account(new DollarAmount(1000));
        final Account accountB = new Account(new DollarAmount(1000));
        final DollarAmount amount = new DollarAmount(100);
        final CountDownLatch firstLocksAcquired = new CountDownLatch(2);

        Thread transferAToB = new Thread(new Runnable() {
            @Override
            public void run() {
                transferForDemoQuietly(accountA, accountB, amount, firstLocksAcquired);
            }
        }, "transfer-A-to-B");

        Thread transferBToA = new Thread(new Runnable() {
            @Override
            public void run() {
                transferForDemoQuietly(accountB, accountA, amount, firstLocksAcquired);
            }
        }, "transfer-B-to-A");

        transferAToB.setDaemon(true);
        transferBToA.setDaemon(true);

        transferAToB.start();
        transferBToA.start();

        printDetectedDeadlock();
    }

    private static void transferForDemoQuietly(Account fromAccount,
                                               Account toAccount,
                                               DollarAmount amount,
                                               CountDownLatch firstLocksAcquired) {
        try {
            transferMoneyForDemo(fromAccount, toAccount, amount, firstLocksAcquired);
        } catch (InsufficientFundsException e) {
            throw new AssertionError(e);
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printDetectedDeadlock() throws InterruptedException {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();

        for (int i = 0; i < 20; i++) {
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

        System.out.println("deadlock not detected in time");
    }
}
