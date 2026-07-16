package com.sherlock.concurrency.chapter14.detailed_14_07;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 状态依赖方法的标准形式。
 *
 * <p>这是《Java 并发编程实战》中的 14.7。</p>
 *
 * <p>14.7 本身是一个模板型代码片段，核心结构是：</p>
 *
 * <pre>
 * synchronized (lock) {
 *     while (!conditionPredicate()) {
 *         lock.wait();
 *     }
 *
 *     // 条件已经满足，执行真正的操作
 * }
 * </pre>
 *
 * <p>这个示例用“等待配置就绪”的场景来演示这个模板：</p>
 *
 * <p>1. 调用者需要读取配置；</p>
 * <p>2. 但配置可能还没有加载完成；</p>
 * <p>3. 如果配置未就绪，调用者就等待；</p>
 * <p>4. 加载线程设置配置后，通知等待线程继续执行。</p>
 */
@ThreadSafe
public class StateDependentMethodPattern {

    /**
     * 条件谓词依赖的共享状态：配置是否已经准备好。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private boolean ready;

    /**
     * 条件满足后要读取的数据。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private String configuration;

    /**
     * 状态依赖方法。
     *
     * <p>这个方法依赖的条件谓词是 {@link #isReady()}。
     * 只有配置已经准备好时，调用线程才能继续读取配置。</p>
     *
     * @return 已经准备好的配置
     * @throws InterruptedException 等待配置期间被中断
     */
    public synchronized String waitUntilReady() throws InterruptedException {
        /*
         * 标准形式第一步：在持有锁的情况下检查条件谓词。
         *
         * 条件谓词依赖 ready 和 configuration 这些共享状态。
         * 因此，检查条件和读取状态必须由同一把锁保护。
         */
        while (!isReady()) {
            /*
             * 标准形式第二步：条件不满足时调用 wait。
             *
             * wait 会释放 this 锁，让加载线程有机会进入 setReady 修改状态。
             * 如果不释放锁，加载线程进不来，ready 就永远无法变成 true。
             */
            wait();
        }

        /*
         * 能走到这里，只说明在当前持有锁的时刻，条件谓词为 true。
         * 因为仍然持有 this 锁，所以读取 configuration 是安全的。
         */
        return configuration;
    }

    /**
     * 修改状态的方法。
     *
     * <p>当这个方法把 ready 从 false 改成 true 后，
     * 等待 {@link #waitUntilReady()} 的线程就可能满足条件了。</p>
     *
     * @param value 准备好的配置
     */
    public synchronized void setReady(String value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        configuration = value;
        ready = true;

        /*
         * 标准形式第三步：改变可能影响条件谓词的状态后，发出通知。
         *
         * notifyAll 不是保证等待线程立刻执行，
         * 而是提醒它们“条件可能已经变化，请醒来重新检查”。
         */
        notifyAll();
    }

    /**
     * 条件谓词。
     *
     * <p>这个方法读取共享状态 ready，因此只能在持有 this 锁时调用。</p>
     */
    private boolean isReady() {
        return ready;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final StateDependentMethodPattern pattern = new StateDependentMethodPattern();
        final CountDownLatch waiterStarted = new CountDownLatch(1);
        final AtomicReference<String> result = new AtomicReference<String>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    waiterStarted.countDown();
                    result.set(pattern.waitUntilReady());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "state-dependent-waiter");

        waiter.start();
        waiterStarted.await();

        /*
         * 此时还没有调用 setReady，等待线程应该阻塞在 wait 中。
         */
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(waiter.isAlive(), "waiter should wait while condition is false");

        pattern.setReady("database.url=localhost");
        waiter.join(2000);

        assertNoFailure(failure);
        assertFalse(waiter.isAlive(), "waiter should finish after condition becomes true");
        assertEquals("database.url=localhost", result.get(), "configuration");

        System.out.println("14.7 state-dependent method pattern passed");
    }

    private static void assertNoFailure(AtomicReference<Throwable> failure) {
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }
}
