package com.sherlock.concurrency.chapter14.detailed_14_06;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter14.detailed_14_02.BaseBoundedBuffer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用条件队列实现的有界缓冲区。
 *
 * <p>这是《Java 并发编程实战》中的 14.6。</p>
 *
 * <p>14.3 的 GrumpyBoundedBuffer 在条件不满足时直接抛异常。</p>
 * <p>14.5 的 SleepyBoundedBuffer 在条件不满足时释放锁、sleep、再重试。</p>
 * <p>14.6 的 BoundedBuffer 才是更合理的阻塞实现：
 * 条件不满足时调用 wait，进入对象的条件队列；条件可能满足时调用 notifyAll 唤醒等待线程。</p>
 *
 * <p>这里使用的是内置条件队列，也就是每个 Java 对象都自带的 wait/notifyAll 机制。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
@ThreadSafe
public class BoundedBuffer<V> extends BaseBoundedBuffer<V> {

    public BoundedBuffer(int size) {
        super(size);
    }

    /**
     * 放入一个元素。
     *
     * <p>put 的条件谓词是：缓冲区未满。</p>
     *
     * <p>如果缓冲区已满，调用线程会执行 wait。
     * wait 会释放 this 锁，这样消费者线程才有机会进入 take，取走元素并改变状态。</p>
     *
     * @param value 要放入的元素
     * @throws InterruptedException 等待期间被中断
     */
    public synchronized void put(V value) throws InterruptedException {
        /*
         * 必须使用 while，而不是 if。
         *
         * 线程从 wait 返回后，只能说明“有人通知过”或者“发生了虚假唤醒”，
         * 不能说明缓冲区现在一定未满。
         *
         * 因此每次醒来后都要重新检查条件。
         */
        while (isFull()) {
            wait();
        }

        /*
         * 检查条件和修改状态必须在同一把锁中完成。
         * 这里的 doInsert 只负责插入，不再检查是否满，所以调用前必须保证条件成立。
         */
        doInsert(value);

        /*
         * 插入元素后，缓冲区从“空”变成“非空”是可能的。
         * 因此等待 take 的消费者线程现在可能可以继续执行。
         */
        notifyAll();
    }

    /**
     * 取出一个元素。
     *
     * <p>take 的条件谓词是：缓冲区非空。</p>
     *
     * <p>如果缓冲区为空，调用线程会执行 wait。
     * wait 会释放 this 锁，这样生产者线程才有机会进入 put，放入元素并改变状态。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 等待期间被中断
     */
    public synchronized V take() throws InterruptedException {
        /*
         * 同样必须使用 while。
         *
         * 即使被 notifyAll 唤醒，也可能有多个消费者一起竞争锁。
         * 第一个消费者拿走元素后，后面的消费者重新获得锁时，缓冲区可能又空了。
         */
        while (isEmpty()) {
            wait();
        }

        V value = doExtract();

        /*
         * 取出元素后，缓冲区从“满”变成“非满”是可能的。
         * 因此等待 put 的生产者线程现在可能可以继续执行。
         */
        notifyAll();

        return value;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final BoundedBuffer<Integer> buffer = new BoundedBuffer<Integer>(1);
        final AtomicReference<Integer> takenByConsumer = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final CountDownLatch consumerStarted = new CountDownLatch(1);

        /*
         * 第一段：消费者先 take，缓冲区为空。
         * 它应该阻塞在 wait 中，直到主线程 put 进元素并 notifyAll。
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
        }, "bounded-buffer-consumer");

        consumer.start();
        consumerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(consumer.isAlive(), "consumer should wait while buffer is empty");

        buffer.put(100);
        consumer.join(2000);

        assertNoFailure(failure);
        assertFalse(consumer.isAlive(), "consumer should finish after value is put");
        assertEquals(Integer.valueOf(100), takenByConsumer.get(), "consumer value");

        /*
         * 第二段：先填满容量为 1 的缓冲区，再启动生产者继续 put。
         * 生产者应该阻塞在 wait 中，直到主线程 take 走元素并 notifyAll。
         */
        buffer.put(200);

        final CountDownLatch producerStarted = new CountDownLatch(1);
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    producerStarted.countDown();
                    buffer.put(300);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "bounded-buffer-producer");

        producer.start();
        producerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(producer.isAlive(), "producer should wait while buffer is full");

        assertEquals(Integer.valueOf(200), buffer.take(), "first value after full wait");
        producer.join(2000);

        assertNoFailure(failure);
        assertFalse(producer.isAlive(), "producer should finish after space is available");
        assertEquals(Integer.valueOf(300), buffer.take(), "second value after full wait");
        assertTrue(buffer.isEmpty(), "buffer should be empty after final take");

        System.out.println("14.6 bounded buffer passed");
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
