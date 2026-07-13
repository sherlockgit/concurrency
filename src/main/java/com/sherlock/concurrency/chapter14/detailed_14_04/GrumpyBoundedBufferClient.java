package com.sherlock.concurrency.chapter14.detailed_14_04;

import com.sherlock.concurrency.chapter14.detailed_14_03.BufferEmptyException;
import com.sherlock.concurrency.chapter14.detailed_14_03.BufferFullException;
import com.sherlock.concurrency.chapter14.detailed_14_03.GrumpyBoundedBuffer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 调用 GrumpyBoundedBuffer 的客户端逻辑。
 *
 * <p>这是《Java 并发编程实战》中的 14.4。</p>
 *
 * <p>14.3 的 GrumpyBoundedBuffer 在前提条件不满足时会直接抛异常：
 * 缓冲区满时 put 失败，缓冲区空时 take 失败。</p>
 *
 * <p>如果调用者想要“等到可以 put/take 为止”，就只能在客户端写循环：
 * 捕获异常、休眠一小段时间、然后重试。</p>
 *
 * <p>这种写法能工作，但不是好的阻塞实现。因为等待策略被迫暴露给调用者，
 * 而且休眠时间不好选择：睡太短会浪费 CPU，睡太长会降低响应性。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
public class GrumpyBoundedBufferClient<V> {

    /**
     * 每次失败后休眠的时间。
     *
     * <p>这个值体现了轮询方案的尴尬：</p>
     *
     * <p>1. 值太小：线程频繁醒来、抛异常、再睡眠，浪费 CPU；</p>
     * <p>2. 值太大：即使条件已经满足，线程也可能还在睡眠，响应变慢。</p>
     */
    private static final long SLEEP_GRANULARITY_MILLIS = 50L;

    /**
     * 14.3 中实现的“暴躁”缓冲区。
     */
    private final GrumpyBoundedBuffer<V> buffer;

    /**
     * 示例统计：take 因为空缓冲区失败过多少次。
     */
    private final AtomicInteger failedTakeAttempts = new AtomicInteger();

    /**
     * 示例统计：put 因为满缓冲区失败过多少次。
     */
    private final AtomicInteger failedPutAttempts = new AtomicInteger();

    public GrumpyBoundedBufferClient(int capacity) {
        this.buffer = new GrumpyBoundedBuffer<V>(capacity);
    }

    /**
     * 一直重试，直到成功放入元素。
     *
     * <p>这相当于在客户端手写一个“阻塞 put”。但它不是被真正的条件通知唤醒，
     * 而是靠定时醒来重试，所以响应性和效率都不理想。</p>
     *
     * @param value 要放入的元素
     * @throws InterruptedException 休眠等待期间被中断
     */
    public void put(V value) throws InterruptedException {
        while (true) {
            try {
                buffer.put(value);
                return;
            } catch (BufferFullException e) {
                failedPutAttempts.incrementAndGet();

                /*
                 * 这里无法知道什么时候会有消费者 take 走元素。
                 * 只能睡一会儿再试，这就是轮询。
                 */
                TimeUnit.MILLISECONDS.sleep(SLEEP_GRANULARITY_MILLIS);
            }
        }
    }

    /**
     * 一直重试，直到成功取出元素。
     *
     * <p>这相当于在客户端手写一个“阻塞 take”。但它不是在缓冲区状态变化时被通知，
     * 而是每隔一段时间醒来检查一次。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 休眠等待期间被中断
     */
    public V take() throws InterruptedException {
        while (true) {
            try {
                return buffer.take();
            } catch (BufferEmptyException e) {
                failedTakeAttempts.incrementAndGet();

                /*
                 * 这里无法知道什么时候会有生产者 put 进元素。
                 * 只能睡一会儿再试。
                 */
                TimeUnit.MILLISECONDS.sleep(SLEEP_GRANULARITY_MILLIS);
            }
        }
    }

    public int getFailedTakeAttempts() {
        return failedTakeAttempts.get();
    }

    public int getFailedPutAttempts() {
        return failedPutAttempts.get();
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final GrumpyBoundedBufferClient<Integer> client =
                new GrumpyBoundedBufferClient<Integer>(1);
        final AtomicReference<Integer> takenByConsumer = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        /*
         * 第一段：消费者先 take，缓冲区为空，所以客户端会捕获 BufferEmptyException 并重试。
         */
        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    takenByConsumer.set(client.take());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "grumpy-client-consumer");

        consumer.start();
        waitUntilFailedTake(client);

        client.put(100);
        consumer.join(2000);

        assertNoFailure(failure);
        assertFalse(consumer.isAlive(), "consumer should finish after value is put");
        assertEquals(Integer.valueOf(100), takenByConsumer.get(), "consumer value");

        /*
         * 第二段：先把容量为 1 的缓冲区填满，再启动生产者继续 put。
         * 生产者会因为缓冲区已满而捕获 BufferFullException 并重试。
         */
        client.put(200);

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.put(300);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "grumpy-client-producer");

        producer.start();
        waitUntilFailedPut(client);

        assertEquals(Integer.valueOf(200), client.take(), "first value after full retry");
        producer.join(2000);

        assertNoFailure(failure);
        assertFalse(producer.isAlive(), "producer should finish after space is available");
        assertEquals(Integer.valueOf(300), client.take(), "second value after full retry");

        System.out.println("failedTakeAttempts=" + client.getFailedTakeAttempts());
        System.out.println("failedPutAttempts=" + client.getFailedPutAttempts());
        System.out.println("14.4 grumpy bounded buffer client passed");
    }

    private static void waitUntilFailedTake(GrumpyBoundedBufferClient<?> client)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (client.getFailedTakeAttempts() == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(client.getFailedTakeAttempts() > 0,
                "take should fail at least once before value is available");
    }

    private static void waitUntilFailedPut(GrumpyBoundedBufferClient<?> client)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000L;
        while (client.getFailedPutAttempts() == 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertTrue(client.getFailedPutAttempts() > 0,
                "put should fail at least once while buffer is full");
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
