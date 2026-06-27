package com.sherlock.concurrency.chapter08.detailed_08_17;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.CountDownLatch;

/**
 * 带返回值的一次性门闩。
 *
 * <p>这是《Java 并发编程实战》中的 8.17。</p>
 *
 * <p>{@link CountDownLatch} 本身只能表达“某件事已经发生”，
 * 不能直接携带“这件事产生了什么结果”。
 * {@code ValueLatch} 在 CountDownLatch 外面包了一层，
 * 让等待线程不仅能等到事件发生，还能拿到事件产生的值。</p>
 *
 * <p>在 8.16 的并发拼图求解器中，它的用途是保存“第一个找到的解”：</p>
 *
 * <p>1. 调用 {@link #getValue()} 的线程会阻塞，直到某个搜索任务找到解；</p>
 * <p>2. 第一个调用 {@link #setValue(Object)} 的搜索任务会保存结果并唤醒等待线程；</p>
 * <p>3. 后续搜索任务再调用 {@code setValue} 时会被忽略，保证结果只设置一次。</p>
 *
 * <p>线程安全性来自两个部分：</p>
 *
 * <p>1. {@code value} 字段通过 synchronized 保护，读写都在同一把锁下进行；</p>
 * <p>2. {@code done} 是 CountDownLatch，用来完成线程间的等待和唤醒。</p>
 *
 * @param <T> 结果值类型
 */
@ThreadSafe
public class ValueLatch<T> {

    /**
     * 被保存的结果值。
     *
     * <p>这个字段由 this 内置锁保护。
     * 只有 {@link #setValue(Object)} 和 {@link #getValue()} 在同步块中访问它。</p>
     */
    private T value = null;

    /**
     * 一次性完成信号。
     *
     * <p>初始计数为 1，表示结果尚未设置。
     * 第一次成功设置结果时调用 countDown，计数变成 0，
     * 所有等待在 {@link #getValue()} 上的线程都会被唤醒。</p>
     */
    private final CountDownLatch done = new CountDownLatch(1);

    /**
     * 判断结果是否已经被设置。
     *
     * @return 如果门闩已经打开，返回 true
     */
    public boolean isSet() {
        return done.getCount() == 0;
    }

    /**
     * 设置结果值。
     *
     * <p>只有第一次调用会真正设置 value 并打开门闩。
     * 后续调用即使传入不同的值，也不会覆盖已经保存的结果。</p>
     *
     * @param newValue 要保存的结果值
     */
    public synchronized void setValue(T newValue) {
        if (!isSet()) {
            value = newValue;
            done.countDown();
        }
    }

    /**
     * 等待结果被设置，并返回结果值。
     *
     * <p>如果结果还没设置，调用线程会阻塞在 {@link CountDownLatch#await()}。
     * 一旦某个线程调用 {@link #setValue(Object)} 打开门闩，当前线程被唤醒，
     * 再进入 synchronized 块读取 value。</p>
     *
     * @return 被设置的结果值
     * @throws InterruptedException 当前线程等待结果时被中断
     */
    public T getValue() throws InterruptedException {
        done.await();
        synchronized (this) {
            return value;
        }
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) throws InterruptedException {
        final ValueLatch<String> latch = new ValueLatch<String>();

        Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                latch.setValue("result-from-worker");
                latch.setValue("ignored-result");
            }
        }, "value-latch-worker");

        worker.start();

        System.out.println("waiting...");
        System.out.println(latch.getValue());
    }
}
