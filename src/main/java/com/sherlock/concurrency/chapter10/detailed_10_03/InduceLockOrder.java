package com.sherlock.concurrency.chapter10.detailed_10_03;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通过诱导锁顺序来避免死锁。
 *
 * <p>这是《Java 并发编程实战》中的 10.3。</p>
 *
 * <p>10.2 中的问题是：转账方法根据调用参数决定加锁顺序。
 * A->B 时先锁 A 再锁 B，B->A 时先锁 B 再锁 A，
 * 两个线程并发执行时就可能形成动态锁顺序死锁。</p>
 *
 * <p>本例的解决办法是：无论调用方传入参数的顺序是什么，
 * 方法内部都先根据两个账户对象的 {@link System#identityHashCode(Object)}
 * 计算出一个固定顺序，然后始终按这个固定顺序加锁。</p>
 *
 * <p>这样即使一个线程执行 A->B，另一个线程执行 B->A，
 * 它们在内部看到的锁顺序也是一样的。例如：</p>
 *
 * <p>如果 identityHashCode(A) 小于 identityHashCode(B)，
 * 那么两个线程都会先锁 A，再锁 B；</p>
 *
 * <p>如果 identityHashCode(B) 小于 identityHashCode(A)，
 * 那么两个线程都会先锁 B，再锁 A。</p>
 *
 * <p>只要所有线程获取多把锁时都遵守同一个全局顺序，
 * 就不会出现“你等我的锁，我等你的锁”的环路等待。</p>
 */
public class InduceLockOrder {

    /**
     * 平局锁。
     *
     * <p>{@link System#identityHashCode(Object)} 理论上可能冲突。
     * 如果两个不同账户对象的 identityHashCode 正好相同，
     * 就无法通过哈希值判断谁先锁、谁后锁。</p>
     *
     * <p>此时先获取一个全局 tieLock，让所有哈希冲突的转账操作串行进入，
     * 再锁两个账户对象。这样可以避免哈希冲突时重新出现不一致的加锁顺序。</p>
     */
    private static final Object tieLock = new Object();

    /**
     * 按固定锁顺序执行转账。
     *
     * <p>这个方法的业务语义仍然是从 fromAccount 转钱到 toAccount。
     * 但加锁顺序不再由 from/to 参数顺序决定，而是由两个账户对象的身份哈希决定。</p>
     *
     * @param fromAccount 转出账户
     * @param toAccount 转入账户
     * @param amount 转账金额
     * @throws InsufficientFundsException 余额不足
     */
    public void transferMoney(final Account fromAccount,
                              final Account toAccount,
                              final DollarAmount amount)
            throws InsufficientFundsException {

        class Helper {
            void transfer() throws InsufficientFundsException {
                if (fromAccount.getBalance().compareTo(amount) < 0) {
                    throw new InsufficientFundsException();
                } else {
                    fromAccount.debit(amount);
                    toAccount.credit(amount);
                }
            }
        }

        int fromHash = System.identityHashCode(fromAccount);
        int toHash = System.identityHashCode(toAccount);

        if (fromHash < toHash) {
            synchronized (fromAccount) {
                synchronized (toAccount) {
                    new Helper().transfer();
                }
            }
        } else if (fromHash > toHash) {
            synchronized (toAccount) {
                synchronized (fromAccount) {
                    new Helper().transfer();
                }
            }
        } else {
            synchronized (tieLock) {
                synchronized (fromAccount) {
                    synchronized (toAccount) {
                        new Helper().transfer();
                    }
                }
            }
        }
    }

    /**
     * 简单金额类型。
     *
     * <p>为了让示例可运行，这里使用不可变 int 金额。</p>
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
     * <p>账户对象本身仍然作为锁对象使用。
     * 和 10.2 不同的是，转账方法不再直接按 from/to 顺序锁账户，
     * 而是先诱导出一个一致的锁顺序。</p>
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
            return "Account-" + acctNo + "(" + balance + ")";
        }
    }

    static class InsufficientFundsException extends Exception {
    }

    /**
     * 简单演示。
     *
     * <p>两个线程同时做相反方向的转账。
     * 在 10.2 的动态锁顺序版本中，这种调用组合有死锁风险；
     * 在本例中，由于加锁顺序被固定下来，两个线程会竞争同一个第一把锁，
     * 而不是分别持有一把锁再互相等待。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        final InduceLockOrder demo = new InduceLockOrder();
        final Account accountA = new Account(new DollarAmount(1000));
        final Account accountB = new Account(new DollarAmount(1000));
        final DollarAmount amount = new DollarAmount(1);
        final CountDownLatch startGate = new CountDownLatch(1);

        Thread transferAToB = new Thread(new Runnable() {
            @Override
            public void run() {
                awaitQuietly(startGate);
                transferRepeatedly(demo, accountA, accountB, amount, 1000);
            }
        }, "transfer-A-to-B");

        Thread transferBToA = new Thread(new Runnable() {
            @Override
            public void run() {
                awaitQuietly(startGate);
                transferRepeatedly(demo, accountB, accountA, amount, 1000);
            }
        }, "transfer-B-to-A");

        transferAToB.start();
        transferBToA.start();
        startGate.countDown();

        transferAToB.join(TimeUnit.SECONDS.toMillis(5));
        transferBToA.join(TimeUnit.SECONDS.toMillis(5));

        long[] deadlockedThreads = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
        if (deadlockedThreads == null && !transferAToB.isAlive() && !transferBToA.isAlive()) {
            System.out.println("finished without deadlock");
            System.out.println(accountA);
            System.out.println(accountB);
        } else {
            printDeadlockStatus(deadlockedThreads);
        }
    }

    private static void transferRepeatedly(InduceLockOrder demo,
                                           Account fromAccount,
                                           Account toAccount,
                                           DollarAmount amount,
                                           int times) {
        for (int i = 0; i < times; i++) {
            try {
                demo.transferMoney(fromAccount, toAccount, amount);
            } catch (InsufficientFundsException e) {
                throw new AssertionError(e);
            }
        }
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printDeadlockStatus(long[] deadlockedThreads) {
        if (deadlockedThreads == null) {
            System.out.println("threads did not finish in time, but no deadlock was detected");
        } else {
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            System.out.println("deadlock detected:");
            for (long threadId : deadlockedThreads) {
                System.out.println(threadMXBean.getThreadInfo(threadId).getThreadName());
            }
        }
    }
}
