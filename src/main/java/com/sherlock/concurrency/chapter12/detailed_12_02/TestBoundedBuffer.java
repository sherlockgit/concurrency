package com.sherlock.concurrency.chapter12.detailed_12_02;

import com.sherlock.concurrency.chapter12.detailed_12_01.SemaphoreBoundedBuffer;

/**
 * BoundedBuffer 的基础单元测试。
 *
 * <p>这是《Java 并发编程实战》中的 12.2。</p>
 *
 * <p>官方示例使用 JUnit 的 {@code TestCase}。
 * 由于这个项目的示例代码主要放在 {@code src/main/java} 下，
 * 为了让本文件可以像其他清单一样直接用 javac 编译运行，
 * 这里用简单的 {@link #assertTrue(boolean, String)} 和
 * {@link #assertFalse(boolean, String)} 模拟基础断言。</p>
 *
 * <p>12.2 的重点不是复杂并发测试，而是先验证有界缓存最基本的状态：</p>
 *
 * <p>1. 刚构造出来时应该是空的，并且不是满的；</p>
 * <p>2. 放入 capacity 个元素后应该是满的，并且不是空的。</p>
 *
 * <p>这些测试看起来简单，但它们很重要：
 * 先把基本边界条件测清楚，后续 12.3 才继续测试阻塞和中断响应。</p>
 */
public class TestBoundedBuffer {

    /**
     * 测试：缓冲区刚构造完成时应该为空。
     */
    public void testIsEmptyWhenConstructed() {
        SemaphoreBoundedBuffer<Integer> buffer =
                new SemaphoreBoundedBuffer<Integer>(10);

        assertTrue(buffer.isEmpty(), "new buffer should be empty");
        assertFalse(buffer.isFull(), "new buffer should not be full");
    }

    /**
     * 测试：放入容量个元素后，缓冲区应该为满。
     */
    public void testIsFullAfterPuts() throws InterruptedException {
        SemaphoreBoundedBuffer<Integer> buffer =
                new SemaphoreBoundedBuffer<Integer>(10);

        for (int i = 0; i < 10; i++) {
            buffer.put(i);
        }

        assertTrue(buffer.isFull(), "buffer should be full after capacity puts");
        assertFalse(buffer.isEmpty(), "full buffer should not be empty");
    }

    /**
     * 简单测试入口。
     *
     * <p>运行成功时打印 passed。
     * 如果断言失败，会抛出 {@link AssertionError}。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        TestBoundedBuffer test = new TestBoundedBuffer();

        test.testIsEmptyWhenConstructed();
        test.testIsFullAfterPuts();

        System.out.println("12.2 basic bounded-buffer tests passed");
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
