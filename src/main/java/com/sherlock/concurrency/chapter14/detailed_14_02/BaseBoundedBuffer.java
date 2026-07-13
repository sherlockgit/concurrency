package com.sherlock.concurrency.chapter14.detailed_14_02;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 有界缓冲区的基础类。
 *
 * <p>这是《Java 并发编程实战》中的 14.2。</p>
 *
 * <p>这个类只负责维护“环形数组”中的共享状态，
 * 不负责决定缓冲区满了或者空了时应该怎么办。</p>
 *
 * <p>后面的几个示例会在这个基础类上展示不同策略：</p>
 *
 * <p>1. 缓冲区状态不满足时，直接失败；</p>
 * <p>2. 缓冲区状态不满足时，休眠一段时间后重试；</p>
 * <p>3. 缓冲区状态不满足时，使用 wait/notifyAll 正确阻塞等待。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
@ThreadSafe
public abstract class BaseBoundedBuffer<V> {

    /**
     * 保存元素的环形数组。
     *
     * <p>由 this 对象锁保护。</p>
     *
     * <p>数组长度固定，因此这是一个“有界”缓冲区。
     * put 操作不能无限放入元素，take 操作也不能在没有元素时取出元素。</p>
     */
    private final V[] buffer;

    /**
     * 下一次取出元素的位置。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private int head;

    /**
     * 下一次插入元素的位置。
     *
     * <p>由 this 对象锁保护。</p>
     */
    private int tail;

    /**
     * 当前缓冲区中的元素数量。
     *
     * <p>由 this 对象锁保护。</p>
     *
     * <p>{@code count == 0} 表示缓冲区为空；
     * {@code count == buffer.length} 表示缓冲区已满。</p>
     */
    private int count;

    @SuppressWarnings("unchecked")
    protected BaseBoundedBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.buffer = (V[]) new Object[capacity];
    }

    /**
     * 插入一个元素。
     *
     * <p>这个方法只做真正的插入动作，不检查缓冲区是否已满。
     * 因此子类在调用它之前，必须先保证“缓冲区未满”这个前提条件成立。</p>
     *
     * <p>方法被声明为 {@code final}，是为了防止子类改写核心状态维护逻辑。
     * 后续不同示例只应该改变“状态不满足时怎么等待”，不应该改变数组结构本身。</p>
     *
     * @param value 要插入的元素
     */
    protected synchronized final void doInsert(V value) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        buffer[tail] = value;

        /*
         * tail 指向下一次插入位置。
         *
         * 如果 tail 已经走到数组末尾，就回到 0。
         * 这就是“环形数组”的含义：逻辑上像一个环，实际存储仍然是普通数组。
         */
        tail = (tail + 1) % buffer.length;
        count++;
    }

    /**
     * 取出一个元素。
     *
     * <p>这个方法只做真正的取出动作，不检查缓冲区是否为空。
     * 因此子类在调用它之前，必须先保证“缓冲区非空”这个前提条件成立。</p>
     *
     * @return 取出的元素
     */
    protected synchronized final V doExtract() {
        V value = buffer[head];

        /*
         * 清空槽位，避免数组继续持有已经取出对象的引用。
         * 如果不清空，可能导致对象本来可以被 GC 回收，却因为数组还引用它而无法回收。
         */
        buffer[head] = null;

        /*
         * head 指向下一次取出位置。
         *
         * 和 tail 一样，走到数组末尾后回到 0。
         */
        head = (head + 1) % buffer.length;
        count--;

        return value;
    }

    /**
     * 判断缓冲区是否已满。
     *
     * <p>这个方法也是 synchronized，因为它读取了共享状态 count。</p>
     */
    protected synchronized final boolean isFull() {
        return count == buffer.length;
    }

    /**
     * 判断缓冲区是否为空。
     *
     * <p>这个方法也是 synchronized，因为它读取了共享状态 count。</p>
     */
    protected synchronized final boolean isEmpty() {
        return count == 0;
    }
}
