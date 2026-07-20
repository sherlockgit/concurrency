package com.sherlock.concurrency.chapter14.detailed_14_09;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 可重新关闭的门。
 *
 * <p>这是《Java 并发编程实战》中的 14.9。</p>
 *
 * <p>ThreadGate 可以理解成一个线程闸门：</p>
 *
 * <p>1. 门打开时，调用 {@link #await()} 的线程可以直接通过；</p>
 * <p>2. 门关闭时，调用 {@link #await()} 的线程会等待；</p>
 * <p>3. 调用 {@link #open()} 会打开门，并放行当前等待的一批线程；</p>
 * <p>4. 调用 {@link #close()} 可以再次关闭门，让后续到达的线程继续等待。</p>
 *
 * <p>这个例子的关键是 {@code generation}。
 * 它表示“开门轮次”，用于区分线程是在第几轮关门期间开始等待的。</p>
 */
@ThreadSafe
public class ThreadGate {

    /**
     * 门当前是否打开。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private boolean open;

    /**
     * 开门轮次。
     *
     * <p>由 this 对象锁保护。</p>
     *
     * <p>每调用一次 {@link #open()}，generation 都会加一。
     * 对等待线程来说，只要 generation 变了，就说明“自己等待期间发生过一次开门”。</p>
     */
    private int generation;

    /**
     * 关闭门。
     *
     * <p>关闭门不会改变 generation。
     * 因为 close 只是让后续新来的线程等待，并不代表发生了一次“放行”。</p>
     */
    public synchronized void close() {
        open = false;
    }

    /**
     * 打开门。
     *
     * <p>打开门时需要做两件事：</p>
     *
     * <p>1. 增加 generation，表示发生了一次新的开门轮次；</p>
     * <p>2. 调用 notifyAll，唤醒正在 await 中等待的线程，让它们重新检查条件。</p>
     */
    public synchronized void open() {
        generation++;
        open = true;
        notifyAll();
    }

    /**
     * 等待门打开。
     *
     * <p>这个方法的条件谓词不是简单的 {@code open == true}，
     * 而是：</p>
     *
     * <pre>
     * open || arrivalGeneration != generation
     * </pre>
     *
     * <p>含义是：</p>
     *
     * <p>1. 如果门现在是开的，线程可以通过；</p>
     * <p>2. 如果线程等待期间发生过一次 open，即使之后门又被 close，它也可以通过。</p>
     *
     * @throws InterruptedException 等待期间被中断
     */
    public synchronized void await() throws InterruptedException {
        /*
         * 记录当前线程到达时的开门轮次。
         *
         * 如果后面 generation 变了，就说明当前线程等待期间发生过一次 open。
         */
        int arrivalGeneration = generation;

        /*
         * 必须使用 while。
         *
         * wait 返回不代表条件一定满足，可能是虚假唤醒，也可能是被 notifyAll 唤醒后状态又变了。
         */
        while (!open && arrivalGeneration == generation) {
            wait();
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final ThreadGate gate = new ThreadGate();
        final AtomicInteger passed = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        /*
         * 第一段：门默认关闭，线程应该阻塞在 await 中。
         */
        final CountDownLatch firstWaiterStarted = new CountDownLatch(1);
        Thread firstWaiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    firstWaiterStarted.countDown();
                    gate.await();
                    passed.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "thread-gate-first-waiter");

        firstWaiter.start();
        firstWaiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(firstWaiter.isAlive(), "first waiter should wait while gate is closed");

        /*
         * 第二段：打开门再立刻关闭门。
         *
         * 即使等待线程醒来前门又被关闭，只要 generation 变了，
         * 它也知道“自己等待期间发生过一次开门”，因此可以通过。
         */
        gate.open();
        gate.close();
        firstWaiter.join(2000);

        assertNoFailure(failure);
        assertFalse(firstWaiter.isAlive(), "first waiter should pass after open");
        assertEquals(1, passed.get(), "passed count after first open");

        /*
         * 第三段：门已经重新关闭，新来的线程应该等待下一次 open。
         */
        final CountDownLatch secondWaiterStarted = new CountDownLatch(1);
        Thread secondWaiter = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    secondWaiterStarted.countDown();
                    gate.await();
                    passed.incrementAndGet();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "thread-gate-second-waiter");

        secondWaiter.start();
        secondWaiterStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(secondWaiter.isAlive(),
                "second waiter should wait after gate has been reclosed");

        gate.open();
        secondWaiter.join(2000);

        assertNoFailure(failure);
        assertFalse(secondWaiter.isAlive(), "second waiter should pass after second open");
        assertEquals(2, passed.get(), "passed count after second open");

        /*
         * 第四段：门保持打开时，await 应该立即返回。
         */
        gate.await();
        assertEquals(2, passed.get(), "main thread pass is not counted");

        System.out.println("14.9 thread gate passed");
    }

    private static void assertNoFailure(AtomicReference<Throwable> failure) {
        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
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
