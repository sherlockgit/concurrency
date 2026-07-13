package com.sherlock.concurrency.chapter14.detailed_14_01;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 阻塞的状态依赖操作的基本结构。
 *
 * <p>这是《Java 并发编程实战》中的 14.1。</p>
 *
 * <p>所谓“状态依赖操作”，就是方法能不能继续执行，取决于对象当前状态。
 * 例如：缓冲区为空时不能 take，缓冲区已满时不能 put。</p>
 *
 * <p>所谓“阻塞的状态依赖操作”，就是当前提条件不满足时，线程不立刻失败，
 * 而是等待状态发生变化；当前提条件满足后，再继续执行真正的操作。</p>
 */
public class BlockingStateDependentAction {

    /**
     * 一个容量为 1 的缓冲区。
     *
     * <p>这个类故意写得很小，因为 14.1 想表达的重点不是完整队列，
     * 而是阻塞状态依赖操作的标准结构：</p>
     *
     * <pre>
     * synchronized method() {
     *     while (前提条件不满足) {
     *         wait();
     *     }
     *     执行真正的操作;
     *     notifyAll();
     * }
     * </pre>
     */
    private static class OneSlotBuffer<E> {

        /**
         * 共享状态：槽位中的元素。
         *
         * <p>它必须由 this 这把内置锁保护，不能在未同步的情况下读写。</p>
         */
        private E item;

        /**
         * 共享状态：当前槽位是否已有元素。
         *
         * <p>{@code full == true} 表示 put 的前提条件不满足，take 的前提条件满足。
         * {@code full == false} 表示 take 的前提条件不满足，put 的前提条件满足。</p>
         */
        private boolean full;

        /**
         * 放入一个元素。
         *
         * <p>这个操作依赖的状态条件是“缓冲区未满”。如果缓冲区已经满了，
         * 当前线程就调用 wait 进入等待，直到其他线程 take 走元素并发出通知。</p>
         */
        public synchronized void put(E value) throws InterruptedException {
            if (value == null) {
                throw new IllegalArgumentException("value must not be null");
            }

            /*
             * 必须使用 while，而不是 if。
             *
             * 原因有三个：
             * 1. wait 可能出现虚假唤醒；
             * 2. notifyAll 可能唤醒了多个线程，但只有一部分线程的条件真的满足；
             * 3. 被唤醒线程重新获得锁之前，条件可能又被其他线程改变了。
             */
            while (full) {
                /*
                 * wait 会做两件关键的事：
                 * 1. 释放 this 这把锁，让其他线程有机会进入 take 修改状态；
                 * 2. 被唤醒并重新拿到 this 这把锁后，才从 wait 返回。
                 */
                wait();
            }

            /*
             * 执行真正的状态变更。
             *
             * 这几行必须和上面的条件检查放在同一个 synchronized 方法中，
             * 否则可能出现“检查时条件满足，但真正执行时状态已经被别人改了”的竞态。
             */
            item = value;
            full = true;

            /*
             * 状态已经从“空”变成“满”，等待 take 的线程现在可能可以继续执行。
             *
             * 这里使用 notifyAll，而不是 notify，是因为同一把锁上可能有多种条件：
             * 有的线程在等“非满”，有的线程在等“非空”。notify 只随机唤醒一个线程，
             * 可能唤醒到条件仍然不满足的线程，导致真正能继续的线程没被唤醒。
             */
            notifyAll();
        }

        /**
         * 取出一个元素。
         *
         * <p>这个操作依赖的状态条件是“缓冲区非空”。如果缓冲区为空，
         * 当前线程就调用 wait 进入等待，直到其他线程 put 进元素并发出通知。</p>
         */
        public synchronized E take() throws InterruptedException {
            while (!full) {
                wait();
            }

            E result = item;
            item = null;
            full = false;

            /*
             * 状态已经从“满”变成“空”，等待 put 的线程现在可能可以继续执行。
             */
            notifyAll();

            return result;
        }

        /**
         * 只用于示例中的断言。
         */
        public synchronized boolean isFull() {
            return full;
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        final OneSlotBuffer<Integer> buffer = new OneSlotBuffer<Integer>();
        final CountDownLatch consumerStarted = new CountDownLatch(1);
        final AtomicReference<Integer> taken = new AtomicReference<Integer>();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    consumerStarted.countDown();
                    taken.set(buffer.take());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "state-dependent-consumer");

        consumer.start();
        consumerStarted.await();

        /*
         * 此时还没有生产者放入数据，消费者应该阻塞在 take 的 wait 中。
         * sleep 不是业务逻辑的一部分，只是为了让演示更直观。
         */
        TimeUnit.MILLISECONDS.sleep(100);
        assertTrue(consumer.isAlive(), "consumer should wait while buffer is empty");

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    buffer.put(42);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "state-dependent-producer");

        producer.start();

        producer.join(2000);
        consumer.join(2000);

        Throwable throwable = failure.get();
        if (throwable != null) {
            throw new AssertionError("worker failed", throwable);
        }

        assertFalse(producer.isAlive(), "producer should finish after put");
        assertFalse(consumer.isAlive(), "consumer should finish after put");
        assertEquals(Integer.valueOf(42), taken.get(), "taken value");
        assertFalse(buffer.isFull(), "buffer should be empty after take");

        System.out.println("14.1 blocking state-dependent action passed");
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
