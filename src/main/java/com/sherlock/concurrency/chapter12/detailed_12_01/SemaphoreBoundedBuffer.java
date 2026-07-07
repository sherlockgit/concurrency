package com.sherlock.concurrency.chapter12.detailed_12_01;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 使用 Semaphore 实现的有界缓存。
 *
 * <p>这是《Java 并发编程实战》中的 12.1。</p>
 *
 * <p>这个类是后续第 12 章测试示例的基础。
 * 它实现了一个固定容量的生产者-消费者缓冲区：</p>
 *
 * <p>1. 生产者调用 {@link #put(Object)} 放入元素；</p>
 * <p>2. 消费者调用 {@link #take()} 取出元素；</p>
 * <p>3. 缓冲区满时，put 会阻塞；</p>
 * <p>4. 缓冲区空时，take 会阻塞。</p>
 *
 * <p>这里使用两个信号量控制边界条件：</p>
 *
 * <p>{@code availableSpaces} 表示当前还有多少个空位可以放元素；</p>
 * <p>{@code availableItems} 表示当前有多少个元素可以取。</p>
 *
 * <p>真正的数组插入和取出仍然用 synchronized 保护，
 * 因为 {@code putPosition}、{@code takePosition} 和 {@code items} 是共享可变状态。</p>
 *
 * @param <E> 缓冲区元素类型
 */
@ThreadSafe
public class SemaphoreBoundedBuffer<E> {

    /**
     * 当前可取元素数量。
     *
     * <p>初始为 0，因为刚创建的缓冲区是空的。</p>
     */
    private final Semaphore availableItems;

    /**
     * 当前可用空位数量。
     *
     * <p>初始为 capacity，因为刚创建时所有槽位都是空的。</p>
     */
    private final Semaphore availableSpaces;

    /**
     * 环形数组，用来保存元素。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private final E[] items;

    /**
     * 下一次插入元素的位置。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private int putPosition = 0;

    /**
     * 下一次取出元素的位置。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private int takePosition = 0;

    @SuppressWarnings("unchecked")
    public SemaphoreBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        availableItems = new Semaphore(0);
        availableSpaces = new Semaphore(capacity);
        items = (E[]) new Object[capacity];
    }

    /**
     * 判断缓冲区是否为空。
     *
     * <p>这里直接读取 availableItems 的许可数。
     * 如果没有可取元素，说明缓冲区为空。</p>
     */
    public boolean isEmpty() {
        return availableItems.availablePermits() == 0;
    }

    /**
     * 判断缓冲区是否已满。
     *
     * <p>如果没有可用空位，说明缓冲区已满。</p>
     */
    public boolean isFull() {
        return availableSpaces.availablePermits() == 0;
    }

    /**
     * 放入一个元素。
     *
     * <p>步骤如下：</p>
     *
     * <p>1. 先获取一个空位许可；如果缓冲区已满，就阻塞等待；</p>
     * <p>2. 获取空位后，把元素插入环形数组；</p>
     * <p>3. 插入完成后，释放一个可取元素许可，通知消费者可以取元素。</p>
     *
     * @param item 要放入的元素
     * @throws InterruptedException 等待空位时被中断
     */
    public void put(E item) throws InterruptedException {
        availableSpaces.acquire();
        doInsert(item);
        availableItems.release();
    }

    /**
     * 取出一个元素。
     *
     * <p>步骤如下：</p>
     *
     * <p>1. 先获取一个可取元素许可；如果缓冲区为空，就阻塞等待；</p>
     * <p>2. 获取许可后，从环形数组中取出元素；</p>
     * <p>3. 取出完成后，释放一个空位许可，通知生产者可以继续放元素。</p>
     *
     * @return 取出的元素
     * @throws InterruptedException 等待元素时被中断
     */
    public E take() throws InterruptedException {
        availableItems.acquire();
        E item = doExtract();
        availableSpaces.release();
        return item;
    }

    /**
     * 把元素插入环形数组。
     *
     * <p>这个方法只在成功获得空位许可后调用。
     * synchronized 负责保护数组和 putPosition 的一致性。</p>
     */
    private synchronized void doInsert(E item) {
        int i = putPosition;
        items[i] = item;
        putPosition = (++i == items.length) ? 0 : i;
    }

    /**
     * 从环形数组取出元素。
     *
     * <p>这个方法只在成功获得可取元素许可后调用。
     * 取出后把槽位设置为 null，避免对象引用滞留。</p>
     */
    private synchronized E doExtract() {
        int i = takePosition;
        E item = items[i];
        items[i] = null;
        takePosition = (++i == items.length) ? 0 : i;
        return item;
    }

    /**
     * 简单演示。
     */
    public static void main(String[] args) throws InterruptedException {
        final SemaphoreBoundedBuffer<Integer> buffer =
                new SemaphoreBoundedBuffer<Integer>(2);

        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    try {
                        buffer.put(i);
                        System.out.println("put " + i);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "producer");

        Thread consumer = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 5; i++) {
                    try {
                        TimeUnit.MILLISECONDS.sleep(100);
                        System.out.println("take " + buffer.take());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "consumer");

        producer.start();
        consumer.start();

        producer.join();
        consumer.join();

        System.out.println("empty=" + buffer.isEmpty());
        System.out.println("full=" + buffer.isFull());
    }
}
