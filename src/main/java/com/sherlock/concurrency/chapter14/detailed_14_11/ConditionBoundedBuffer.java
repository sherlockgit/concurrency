package com.sherlock.concurrency.chapter14.detailed_14_11;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 使用显式条件变量实现的有界缓冲区。
 *
 * <p>这是《Java 并发编程实战》中的 14.11。</p>
 *
 * <p>14.6 的 BoundedBuffer 使用的是内置条件队列：</p>
 *
 * <pre>
 * synchronized + wait + notifyAll
 * </pre>
 *
 * <p>14.11 使用的是显式条件变量：</p>
 *
 * <pre>
 * ReentrantLock + Condition.await + Condition.signal
 * </pre>
 *
 * <p>显式条件变量的最大好处是：一把锁可以关联多个条件队列。
 * 本例中有两个独立的条件队列：</p>
 *
 * <p>1. {@code notFull}：生产者等待“缓冲区非满”；</p>
 * <p>2. {@code notEmpty}：消费者等待“缓冲区非空”。</p>
 *
 * <p>这样 put 后只需要通知消费者队列，take 后只需要通知生产者队列，
 * 不需要像内置条件队列那样把所有等待线程都唤醒。</p>
 *
 * @param <T> 缓冲区中保存的元素类型
 */
@ThreadSafe
public class ConditionBoundedBuffer<T> {

    /**
     * 保护缓冲区所有共享状态的显式锁。
     */
    private final Lock lock = new ReentrantLock();

    /**
     * 等待“缓冲区非满”的条件队列。
     *
     * <p>生产者在缓冲区满时等待这个条件。</p>
     */
    private final Condition notFull = lock.newCondition();

    /**
     * 等待“缓冲区非空”的条件队列。
     *
     * <p>消费者在缓冲区空时等待这个条件。</p>
     */
    private final Condition notEmpty = lock.newCondition();

    /**
     * 环形数组。
     *
     * <p>由 lock 保护。</p>
     */
    private final T[] items;

    /**
     * 下一次取出元素的位置。
     *
     * <p>由 lock 保护。</p>
     */
    private int head;

    /**
     * 下一次插入元素的位置。
     *
     * <p>由 lock 保护。</p>
     */
    private int tail;

    /**
     * 当前元素数量。
     *
     * <p>由 lock 保护。</p>
     */
    private int count;

    @SuppressWarnings("unchecked")
    public ConditionBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        items = (T[]) new Object[capacity];
    }

    /**
     * 放入一个元素。
     *
     * <p>入口协议：缓冲区必须非满。</p>
     *
     * <p>如果缓冲区已满，生产者进入 {@code notFull} 条件队列等待。
     * 当消费者 take 走元素后，会通过 {@code notFull.signal()} 唤醒一个生产者。</p>
     *
     * @param item 要放入的元素
     * @throws InterruptedException 等待空位期间被中断
     */
    public void put(T item) throws InterruptedException {
        if (item == null) {
            throw new IllegalArgumentException("item must not be null");
        }

        lock.lock();
        try {
            /*
             * await 和 wait 一样，必须放在 while 中。
             *
             * await 返回只表示“可以重新检查条件了”，不表示条件一定满足。
             */
            while (count == items.length) {
                notFull.await();
            }

            insert(item);

            /*
             * put 增加了一个元素，因此等待“非空”的消费者可能可以继续执行。
             *
             * 这里使用 signal，而不是 signalAll。
             * 因为一个 put 只新增一个元素，通常只够一个消费者取走。
             * 更重要的是：notEmpty 条件队列里只放消费者，不会唤醒错误类型的线程。
             */
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 取出一个元素。
     *
     * <p>入口协议：缓冲区必须非空。</p>
     *
     * <p>如果缓冲区为空，消费者进入 {@code notEmpty} 条件队列等待。
     * 当生产者 put 进元素后，会通过 {@code notEmpty.signal()} 唤醒一个消费者。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 等待元素期间被中断
     */
    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (count == 0) {
                notEmpty.await();
            }

            T item = extract();

            /*
             * take 释放了一个空位，因此等待“非满”的生产者可能可以继续执行。
             */
            notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 当前元素数量。
     */
    public int size() {
        lock.lock();
        try {
            return count;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 插入元素。
     *
     * <p>调用者必须已经持有 lock，并且已经保证缓冲区非满。</p>
     */
    private void insert(T item) {
        items[tail] = item;
        tail = (tail + 1) % items.length;
        count++;
    }

    /**
     * 取出元素。
     *
     * <p>调用者必须已经持有 lock，并且已经保证缓冲区非空。</p>
     */
    private T extract() {
        T item = items[head];
        items[head] = null;
        head = (head + 1) % items.length;
        count--;
        return item;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        final ConditionBoundedBuffer<Integer> buffer =
                new ConditionBoundedBuffer<Integer>(2);
        final AtomicReference<Integer> takenByConsumer = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        final CountDownLatch consumerStarted = new CountDownLatch(1);

        /*
         * 第一段：消费者先 take，缓冲区为空，应该等待 notEmpty。
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
        }, "condition-buffer-consumer");

        consumer.start();
        consumerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(consumer.isAlive(), "consumer should wait while buffer is empty");

        buffer.put(10);
        consumer.join(2000);

        assertNoFailure(failure);
        assertFalse(consumer.isAlive(), "consumer should finish after put signals notEmpty");
        assertEquals(Integer.valueOf(10), takenByConsumer.get(), "consumer value");

        /*
         * 第二段：填满缓冲区，再启动生产者继续 put，生产者应该等待 notFull。
         */
        buffer.put(20);
        buffer.put(30);

        final CountDownLatch producerStarted = new CountDownLatch(1);
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    producerStarted.countDown();
                    buffer.put(40);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "condition-buffer-producer");

        producer.start();
        producerStarted.await();
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(producer.isAlive(), "producer should wait while buffer is full");

        assertEquals(Integer.valueOf(20), buffer.take(), "first value before producer continues");
        producer.join(2000);

        assertNoFailure(failure);
        assertFalse(producer.isAlive(), "producer should finish after take signals notFull");
        assertEquals(Integer.valueOf(30), buffer.take(), "remaining old value");
        assertEquals(Integer.valueOf(40), buffer.take(), "value inserted by producer");
        assertEquals(0, buffer.size(), "buffer should be empty after all takes");

        System.out.println("14.11 condition bounded buffer passed");
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
