package com.sherlock.concurrency.chapter14.detailed_14_05;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter14.detailed_14_02.BaseBoundedBuffer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 通过休眠和重试实现的有界缓冲区。
 *
 * <p>这是《Java 并发编程实战》中的 14.5。</p>
 *
 * <p>和 14.3 的 GrumpyBoundedBuffer 相比，这个类不会把“失败后怎么重试”的责任交给调用者。
 * 如果 put 时缓冲区已满，或者 take 时缓冲区为空，它会在内部休眠一小段时间，然后重新检查条件。</p>
 *
 * <p>这种写法比 14.4 的客户端重试稍微封装得好一些，但它仍然不是理想方案：
 * 因为线程不是在条件真正发生变化时被唤醒，而是靠定时醒来轮询。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
@ThreadSafe
public class SleepyBoundedBuffer<V> extends BaseBoundedBuffer<V> {

    /**
     * 每次发现条件不满足后，休眠的时间。
     *
     * <p>这个值很难选：</p>
     *
     * <p>1. 太小：线程频繁醒来检查条件，浪费 CPU；</p>
     * <p>2. 太大：条件明明已经满足，线程可能还在睡，响应性变差。</p>
     */
    private static final long SLEEP_GRANULARITY_MILLIS = 50L;

    /**
     * 示例统计：因为缓冲区为空而休眠过多少次。
     */
    private final AtomicInteger sleepAfterEmptyAttempts = new AtomicInteger();

    /**
     * 示例统计：因为缓冲区已满而休眠过多少次。
     */
    private final AtomicInteger sleepAfterFullAttempts = new AtomicInteger();

    public SleepyBoundedBuffer(int size) {
        super(size);
    }

    /**
     * 放入一个元素。
     *
     * <p>put 的前提条件是“缓冲区未满”。如果缓冲区已满，就释放锁、睡一会儿、再重试。</p>
     *
     * @param value 要放入的元素
     * @throws InterruptedException 休眠等待期间被中断
     */
    public void put(V value) throws InterruptedException {
        while (true) {
            synchronized (this) {
                /*
                 * 检查条件和执行插入必须在同一个 synchronized 块中完成。
                 *
                 * 如果先检查 isFull，然后离开锁，再调用 doInsert，
                 * 另一个线程可能在中间插入元素，导致刚才的检查结果已经过期。
                 */
                if (!isFull()) {
                    doInsert(value);
                    return;
                }
            }

            /*
             * sleep 必须放在 synchronized 块外面。
             *
             * 如果线程睡觉时仍然持有 this 锁，消费者就无法进入 take 改变状态，
             * 生产者也就永远等不到“非满”条件。
             */
            sleepAfterFullAttempts.incrementAndGet();
            TimeUnit.MILLISECONDS.sleep(SLEEP_GRANULARITY_MILLIS);
        }
    }

    /**
     * 取出一个元素。
     *
     * <p>take 的前提条件是“缓冲区非空”。如果缓冲区为空，就释放锁、睡一会儿、再重试。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 休眠等待期间被中断
     */
    public V take() throws InterruptedException {
        while (true) {
            synchronized (this) {
                /*
                 * 检查条件和执行取出必须在同一个 synchronized 块中完成。
                 *
                 * 如果先检查 isEmpty，然后离开锁，再调用 doExtract，
                 * 另一个线程可能已经取走元素，导致刚才的检查结果失效。
                 */
                if (!isEmpty()) {
                    return doExtract();
                }
            }

            /*
             * 同样，sleep 必须在 synchronized 块外。
             * 这样生产者才有机会进入 put，把缓冲区从“空”变成“非空”。
             */
            sleepAfterEmptyAttempts.incrementAndGet();
            TimeUnit.MILLISECONDS.sleep(SLEEP_GRANULARITY_MILLIS);
        }
    }

    public int getSleepAfterEmptyAttempts() {
        return sleepAfterEmptyAttempts.get();
    }

    public int getSleepAfterFullAttempts() {
        return sleepAfterFullAttempts.get();
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final SleepyBoundedBuffer<Integer> buffer = new SleepyBoundedBuffer<Integer>(1);
        final AtomicReference<Integer> takenByConsumer = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        /*
         * 第一段：消费者先 take，缓冲区为空。
         * 它不会抛异常给调用者，而是在 SleepyBoundedBuffer 内部 sleep 后重试。
         */
        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    takenByConsumer.set(buffer.take());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "sleepy-buffer-consumer");

        consumer.start();
        waitUntilEmptySleep(buffer);

        buffer.put(100);
        consumer.join(2000);

        assertNoFailure(failure);
        assertFalse(consumer.isAlive(), "consumer should finish after value is put");
        assertEquals(Integer.valueOf(100), takenByConsumer.get(), "consumer value");

        /*
         * 第二段：先填满容量为 1 的缓冲区，再启动生产者继续 put。
         * 生产者会在缓冲区满时 sleep，直到主线程 take 走元素腾出空间。
         */
        buffer.put(200);

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    buffer.put(300);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "sleepy-buffer-producer");

        producer.start();
        waitUntilFullSleep(buffer);

        assertEquals(Integer.valueOf(200), buffer.take(), "first value after full retry");
        producer.join(2000);

        assertNoFailure(failure);
        assertFalse(producer.isAlive(), "producer should finish after space is available");
        assertEquals(Integer.valueOf(300), buffer.take(), "second value after full retry");

        System.out.println("sleepAfterEmptyAttempts=" + buffer.getSleepAfterEmptyAttempts());
        System.out.println("sleepAfterFullAttempts=" + buffer.getSleepAfterFullAttempts());
        System.out.println("14.5 sleepy bounded buffer passed");
    }

    private static void waitUntilEmptySleep(SleepyBoundedBuffer<?> buffer)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (buffer.getSleepAfterEmptyAttempts() == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(buffer.getSleepAfterEmptyAttempts() > 0,
                "take should sleep at least once before value is available");
    }

    private static void waitUntilFullSleep(SleepyBoundedBuffer<?> buffer)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (buffer.getSleepAfterFullAttempts() == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(buffer.getSleepAfterFullAttempts() > 0,
                "put should sleep at least once while buffer is full");
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
