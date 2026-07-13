package com.sherlock.concurrency.chapter14.detailed_14_03;

import com.sherlock.concurrency.annoations.ThreadSafe;
import com.sherlock.concurrency.chapter14.detailed_14_02.BaseBoundedBuffer;

/**
 * “暴躁”的有界缓冲区。
 *
 * <p>这是《Java 并发编程实战》中的 14.3。</p>
 *
 * <p>这里的 “Grumpy” 可以理解成“脾气不好”：
 * 如果状态条件不满足，它不会等待条件变好，而是直接抛异常。</p>
 *
 * <p>put 操作依赖的前提条件是：缓冲区未满。</p>
 * <p>take 操作依赖的前提条件是：缓冲区非空。</p>
 *
 * <p>这个类是线程安全的，但它不是一个好用的阻塞队列。
 * 因为调用者必须自己捕获异常并决定是否重试，这会把状态依赖操作的复杂性暴露给调用者。</p>
 *
 * @param <V> 缓冲区中保存的元素类型
 */
@ThreadSafe
public class GrumpyBoundedBuffer<V> extends BaseBoundedBuffer<V> {

    public GrumpyBoundedBuffer(int size) {
        super(size);
    }

    /**
     * 放入一个元素。
     *
     * <p>如果缓冲区已满，直接抛出 {@link BufferFullException}。
     * 这就是 14.3 想展示的策略：前提条件不满足时，不阻塞、不等待，只是失败。</p>
     *
     * @param value 要放入的元素
     * @throws BufferFullException 缓冲区已满
     */
    public synchronized void put(V value) throws BufferFullException {
        if (isFull()) {
            throw new BufferFullException();
        }
        doInsert(value);
    }

    /**
     * 取出一个元素。
     *
     * <p>如果缓冲区为空，直接抛出 {@link BufferEmptyException}。
     * 调用者如果想等待，就只能在外部写循环、捕获异常、休眠后重试。</p>
     *
     * @return 取出的元素
     * @throws BufferEmptyException 缓冲区为空
     */
    public synchronized V take() throws BufferEmptyException {
        if (isEmpty()) {
            throw new BufferEmptyException();
        }
        return doExtract();
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        GrumpyBoundedBuffer<Integer> buffer = new GrumpyBoundedBuffer<Integer>(2);

        assertThrowsBufferEmpty(buffer);

        buffer.put(1);
        buffer.put(2);
        assertThrowsBufferFull(buffer);

        assertEquals(Integer.valueOf(1), buffer.take(), "first value");
        assertEquals(Integer.valueOf(2), buffer.take(), "second value");
        assertThrowsBufferEmpty(buffer);

        System.out.println("14.3 grumpy bounded buffer passed");
    }

    private static void assertThrowsBufferEmpty(GrumpyBoundedBuffer<Integer> buffer)
            throws BufferFullException {
        try {
            buffer.take();
            throw new AssertionError("take should fail when buffer is empty");
        } catch (BufferEmptyException expected) {
            // 预期路径：空缓冲区不能 take。
        }
    }

    private static void assertThrowsBufferFull(GrumpyBoundedBuffer<Integer> buffer)
            throws BufferEmptyException {
        try {
            buffer.put(3);
            throw new AssertionError("put should fail when buffer is full");
        } catch (BufferFullException expected) {
            // 预期路径：满缓冲区不能 put。
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(message + ": expected " + expected + ", actual " + actual);
        }
    }
}
