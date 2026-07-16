package com.sherlock.concurrency.chapter14.detailed_14_08;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter14.detailed_14_02.BaseBoundedBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 在 put 中使用条件通知的有界缓冲区。
 *
 * <p>这是《Java 并发编程实战》中的 14.8。</p>
 *
 * <p>14.6 的 BoundedBuffer 每次 put 或 take 后都会调用 notifyAll。
 * 这很安全，但有时会产生不必要的通知。</p>
 *
 * <p>14.8 展示了一个优化：put 只有在缓冲区从“空”变成“非空”时才通知。
 * 因为只有这种状态转换，才可能让正在等待“非空”条件的消费者继续执行。</p>
 *
 * <p>条件通知的关键不是“少通知”，而是“只有当某个条件谓词可能变成 true 时才通知”。
 * 如果判断错了，可能导致线程永远等不到通知，所以这种优化要谨慎使用。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
@ThreadSafe
public class BoundedBufferWithConditionalNotification<V> extends BaseBoundedBuffer<V> {

    /**
     * 示例统计：put 中因为“空 -> 非空”而触发通知的次数。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private int conditionalPutNotifications;

    public BoundedBufferWithConditionalNotification(int size) {
        super(size);
    }

    /**
     * 使用条件通知的 put。
     *
     * <p>put 的条件谓词是：缓冲区未满。</p>
     *
     * <p>执行插入之前，先记录缓冲区是否为空。
     * 如果插入前是空的，插入后就会变成非空，此时等待 take 的消费者可能可以继续执行，
     * 因此需要调用 notifyAll。</p>
     *
     * <p>如果插入前已经非空，插入后仍然非空。
     * 这次 put 并没有让“非空”这个条件从 false 变成 true，
     * 所以通常不需要为消费者发通知。</p>
     *
     * @param value 要放入的元素
     * @throws InterruptedException 等待空位期间被中断
     */
    public synchronized void put(V value) throws InterruptedException {
        while (isFull()) {
            wait();
        }

        /*
         * 必须在持有同一把锁时读取 wasEmpty，并且插入操作也必须在这把锁中完成。
         * 否则“插入前是否为空”的判断可能在并发修改下失效。
         */
        boolean wasEmpty = isEmpty();
        doInsert(value);

        /*
         * 只有从“空”变成“非空”时，才可能唤醒等待 take 的消费者。
         * 这就是条件通知。
         */
        if (wasEmpty) {
            conditionalPutNotifications++;
            notifyAll();
        }
    }

    /**
     * 取出一个元素。
     *
     * <p>这里仍然使用 14.6 的普通通知方式。
     * 14.8 只想强调 put 中的条件通知，不把 take 的条件通知优化混在一起。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 等待元素期间被中断
     */
    public synchronized V take() throws InterruptedException {
        while (isEmpty()) {
            wait();
        }

        V value = doExtract();

        /*
         * take 之后可能从“满”变成“非满”，因此等待 put 的生产者可能可以继续执行。
         */
        notifyAll();
        return value;
    }

    public synchronized int getConditionalPutNotifications() {
        return conditionalPutNotifications;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final BoundedBufferWithConditionalNotification<Integer> buffer =
                new BoundedBufferWithConditionalNotification<Integer>(2);
        final AtomicReference<Integer> takenByConsumer = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final CountDownLatch consumerStarted = new CountDownLatch(1);

        /*
         * 第一段：消费者先 take，缓冲区为空，因此消费者会 wait。
         * put 第一个元素时，缓冲区从空变成非空，必须通知消费者。
         */
        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    consumerStarted.countDown();
                    takenByConsumer.set(buffer.take());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "conditional-notification-consumer");

        consumer.start();
        consumerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(consumer.isAlive(), "consumer should wait while buffer is empty");

        buffer.put(10);
        consumer.join(2000);

        assertNoFailure(failure);
        assertFalse(consumer.isAlive(), "consumer should finish after first put");
        assertEquals(Integer.valueOf(10), takenByConsumer.get(), "consumer value");
        assertEquals(1, buffer.getConditionalPutNotifications(),
                "first put should notify because buffer was empty");

        /*
         * 第二段：先放入一个元素，让缓冲区变成非空。
         * 再放入第二个元素时，缓冲区插入前已经非空，所以 put 不需要通知消费者。
         */
        buffer.put(20);
        int notificationsAfterEmptyToNonEmpty = buffer.getConditionalPutNotifications();
        buffer.put(30);

        assertEquals(notificationsAfterEmptyToNonEmpty, buffer.getConditionalPutNotifications(),
                "second put should not notify because buffer was already non-empty");
        assertEquals(Integer.valueOf(20), buffer.take(), "first stored value");
        assertEquals(Integer.valueOf(30), buffer.take(), "second stored value");

        /*
         * 第三段：验证 take 的普通通知仍然能唤醒等待 put 的生产者。
         */
        buffer.put(40);
        buffer.put(50);

        final CountDownLatch producerStarted = new CountDownLatch(1);
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    producerStarted.countDown();
                    buffer.put(60);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "conditional-notification-producer");

        producer.start();
        producerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(producer.isAlive(), "producer should wait while buffer is full");

        assertEquals(Integer.valueOf(40), buffer.take(), "value before producer can continue");
        producer.join(2000);

        assertNoFailure(failure);
        assertFalse(producer.isAlive(), "producer should finish after take creates space");
        assertEquals(Integer.valueOf(50), buffer.take(), "remaining old value");
        assertEquals(Integer.valueOf(60), buffer.take(), "value inserted by producer");

        System.out.println("conditionalPutNotifications="
                + buffer.getConditionalPutNotifications());
        System.out.println("14.8 conditional notification bounded buffer passed");
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
