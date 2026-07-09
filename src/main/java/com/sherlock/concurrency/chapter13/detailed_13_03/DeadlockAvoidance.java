package com.sherlock.concurrency.chapter13.detailed_13_03;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用 tryLock 避免锁顺序死锁。
 *
 * <p>这是《Java 并发编程实战》中的 13.3。</p>
 *
 * <p>第 10 章讲过动态锁顺序死锁：
 * 线程 A 先锁账户 1，再锁账户 2；
 * 线程 B 先锁账户 2，再锁账户 1；
 * 如果两个线程各自持有一把锁并等待另一把锁，就会死锁。</p>
 *
 * <p>本例使用 {@link Lock#tryLock()} 改写转账逻辑。
 * 如果拿不到第二把锁，就释放已经拿到的第一把锁，稍微等待后重试。
 * 这样线程不会永远持有一把锁等待另一把锁，因此可以避免锁顺序死锁。</p>
 */
public class DeadlockAvoidance {

    private static final Random random = new Random();

    /**
     * 固定退避时间，单位纳秒。
     */
    private static final int DELAY_FIXED = 1;

    /**
     * 随机退避上限，单位纳秒。
     */
    private static final int DELAY_RANDOM = 2;

    /**
     * 转账。
     *
     * <p>这个方法需要同时锁住两个账户。
     * 如果用普通 {@code lock()} 按调用参数顺序加锁，就可能出现锁顺序死锁。
     * 这里改用 {@code tryLock()}：</p>
     *
     * <p>1. 尝试获得转出账户锁；</p>
     * <p>2. 成功后尝试获得转入账户锁；</p>
     * <p>3. 如果两把锁都拿到，就检查余额并完成转账；</p>
     * <p>4. 如果第二把锁拿不到，就释放第一把锁；</p>
     * <p>5. 短暂随机等待后重试；</p>
     * <p>6. 如果超过 timeout 仍然无法完成，返回 false。</p>
     *
     * @return true 表示转账成功，false 表示超时后放弃
     */
    public boolean transferMoney(Account fromAccount,
                                 Account toAccount,
                                 DollarAmount amount,
                                 long timeout,
                                 TimeUnit unit)
            throws InsufficientFundsException, InterruptedException {
        long fixedDelay = getFixedDelayComponentNanos(timeout, unit);
        long randomMod = getRandomDelayModulusNanos(timeout, unit);
        long timeoutNanos = unit.toNanos(timeout);
        long startTime = System.nanoTime();

        while (true) {
            if (fromAccount.lock.tryLock()) {
                try {
                    if (toAccount.lock.tryLock()) {
                        try {
                            if (fromAccount.getBalance().compareTo(amount) < 0) {
                                throw new InsufficientFundsException();
                            }

                            fromAccount.debit(amount);
                            toAccount.credit(amount);
                            return true;
                        } finally {
                            toAccount.lock.unlock();
                        }
                    }
                } finally {
                    /*
                     * 无论是否拿到第二把锁，都要释放第一把锁。
                     *
                     * 这是避免死锁的关键：
                     * 拿不到全部所需锁时，不要一直占着已经拿到的锁。
                     */
                    fromAccount.lock.unlock();
                }
            }

            if (System.nanoTime() - startTime >= timeoutNanos) {
                return false;
            }

            /*
             * 随机退避。
             *
             * 如果两个线程每次失败后都立刻重试，可能反复撞在一起：
             * A 拿到账户 1，B 拿到账户 2，然后都失败，再同时重试。
             * 加一点随机等待，可以降低这种“活锁式反复冲突”的概率。
             */
            TimeUnit.NANOSECONDS.sleep(fixedDelay + Math.abs(random.nextLong() % randomMod));
        }
    }

    static long getFixedDelayComponentNanos(long timeout, TimeUnit unit) {
        return DELAY_FIXED;
    }

    static long getRandomDelayModulusNanos(long timeout, TimeUnit unit) {
        return DELAY_RANDOM;
    }

    /**
     * 金额值对象。
     *
     * <p>为了让示例独立运行，这里用 int 表示金额。
     * 真实金融系统不能这样简单处理金额和精度。</p>
     */
    static class DollarAmount implements Comparable<DollarAmount> {
        private final int dollars;

        DollarAmount(int dollars) {
            if (dollars < 0) {
                throw new IllegalArgumentException("dollars must not be negative");
            }
            this.dollars = dollars;
        }

        @Override
        public int compareTo(DollarAmount other) {
            return dollars - other.dollars;
        }

        DollarAmount add(DollarAmount other) {
            return new DollarAmount(dollars + other.dollars);
        }

        DollarAmount subtract(DollarAmount other) {
            if (dollars < other.dollars) {
                throw new IllegalArgumentException("amount would become negative");
            }
            return new DollarAmount(dollars - other.dollars);
        }

        int intValue() {
            return dollars;
        }
    }

    /**
     * 账户。
     *
     * <p>每个账户都有自己的锁。
     * 转账需要同时持有两个账户的锁，才能保证“扣款”和“入账”作为一个整体完成。</p>
     */
    static class Account {
        private final Lock lock = new ReentrantLock();
        private DollarAmount balance;

        Account(int initialBalance) {
            this.balance = new DollarAmount(initialBalance);
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

        int getBalanceValue() {
            lock.lock();
            try {
                return balance.intValue();
            } finally {
                lock.unlock();
            }
        }
    }

    static class InsufficientFundsException extends Exception {
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        final DeadlockAvoidance service = new DeadlockAvoidance();
        final Account accountA = new Account(1000);
        final Account accountB = new Account(1000);
        final int iterations = 1000;
        final CountDownLatch startGate = new CountDownLatch(1);
        final CountDownLatch endGate = new CountDownLatch(2);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread aToB = new Thread(new Runnable() {
            @Override
            public void run() {
                runTransfers(service, accountA, accountB, iterations, startGate, failure);
                endGate.countDown();
            }
        }, "a-to-b");

        Thread bToA = new Thread(new Runnable() {
            @Override
            public void run() {
                runTransfers(service, accountB, accountA, iterations, startGate, failure);
                endGate.countDown();
            }
        }, "b-to-a");

        aToB.start();
        bToA.start();

        startGate.countDown();
        endGate.await();

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("transfer worker failed", throwable);
        }

        int total = accountA.getBalanceValue() + accountB.getBalanceValue();
        assertEquals(2000, total, "total balance");

        System.out.println("accountA=" + accountA.getBalanceValue());
        System.out.println("accountB=" + accountB.getBalanceValue());
        System.out.println("13.3 deadlock avoidance passed");
    }

    private static void runTransfers(DeadlockAvoidance service,
                                     Account from,
                                     Account to,
                                     int iterations,
                                     CountDownLatch startGate,
                                     AtomicReference<Throwable> failure) {
        try {
            startGate.await();

            for (int i = 0; i < iterations; i++) {
                service.transferMoney(
                        from,
                        to,
                        new DollarAmount(1),
                        1,
                        TimeUnit.SECONDS);
            }
        } catch (Throwable t) {
            failure.compareAndSet(null, t);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
        }
    }
}
