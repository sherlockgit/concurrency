package com.sherlock.concurrency.chapter12.detailed_12_03;

import com.sherlock.concurrency.chapter12.detailed_12_01.SemaphoreBoundedBuffer;

/**
 * 测试阻塞和对中断的响应。
 *
 * <p>这是《Java 并发编程实战》中的 12.3。</p>
 *
 * <p>12.2 测试的是有界缓存的基本后验条件：
 * 构造后为空，放满后为满。</p>
 *
 * <p>12.3 进一步测试阻塞行为：</p>
 *
 * <p>1. 当缓冲区为空时，调用 {@link SemaphoreBoundedBuffer#take()} 应该阻塞；</p>
 * <p>2. 如果阻塞中的线程被中断，take 应该抛出 {@link InterruptedException}；</p>
 * <p>3. 被中断的测试线程应该能正常退出，而不是永久卡住。</p>
 *
 * <p>官方示例使用 JUnit 的 {@code fail/assertFalse}。
 * 这里使用简单断言方法，使示例可以直接编译运行。</p>
 */
public class TestBlockingAndInterruption {

    /**
     * 用来判断线程是否卡死的等待时间。
     *
     * <p>这类测试有一定时序敏感性：
     * 等太短可能还没来得及进入阻塞状态；
     * 等太长会拖慢测试。</p>
     */
    private static final long LOCKUP_DETECT_TIMEOUT = 1000;

    /**
     * 测试：空缓冲区上的 take 会阻塞，并且能响应中断。
     */
    public void testTakeBlocksWhenEmpty() {
        final SemaphoreBoundedBuffer<Integer> buffer =
                new SemaphoreBoundedBuffer<Integer>(10);

        Thread taker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    buffer.take();
                    fail("take should block when buffer is empty");
                } catch (InterruptedException expected) {
                    /*
                     * 预期路径：
                     * 主线程确认 taker 被阻塞后，会中断 taker。
                     * buffer.take() 应该响应中断并抛出 InterruptedException。
                     */
                }
            }
        }, "empty-buffer-taker");

        try {
            taker.start();

            /*
             * 等待一段时间。如果 take 没有阻塞，taker 会执行到 fail。
             * 如果 take 正确阻塞，taker 此时仍然存活。
             */
            Thread.sleep(LOCKUP_DETECT_TIMEOUT);

            assertTrue(taker.isAlive(), "taker should still be blocked on empty buffer");

            taker.interrupt();
            taker.join(LOCKUP_DETECT_TIMEOUT);

            assertFalse(taker.isAlive(), "taker should terminate after interruption");
        } catch (InterruptedException unexpected) {
            Thread.currentThread().interrupt();
            fail("test thread was interrupted unexpectedly");
        }
    }

    /**
     * 简单测试入口。
     */
    public static void main(String[] args) {
        TestBlockingAndInterruption test = new TestBlockingAndInterruption();
        test.testTakeBlocksWhenEmpty();
        System.out.println("12.3 blocking/interruption test passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        assertTrue(!condition, message);
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
