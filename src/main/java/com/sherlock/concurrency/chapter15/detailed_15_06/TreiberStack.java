package com.sherlock.concurrency.chapter15.detailed_15_06;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 使用 Treiber 算法实现的非阻塞栈。
 *
 * <p>这是《Java 并发编程实战》中的 15.6。</p>
 *
 * <p>Treiber 栈是经典的 lock-free 栈实现。它的核心思路很简单：</p>
 *
 * <p>1. 栈顶用一个 AtomicReference 保存；</p>
 * <p>2. push 时读取旧栈顶，构造一个新节点，把新节点的 next 指向旧栈顶，然后 CAS 替换；</p>
 * <p>3. pop 时读取旧栈顶，再读取旧栈顶的 next，然后 CAS 把 top 从旧节点换成 next；</p>
 * <p>4. 如果 CAS 失败，说明别的线程已经改过栈顶，当前线程重新读取再试。</p>
 */
@ThreadSafe
public class TreiberStack<E> {

    /**
     * 栈节点。
     *
     * <p>节点是不可变的：item 和 next 都在构造时确定，之后不再修改。
     * 这样能降低并发编程的复杂度。</p>
     */
    private static class Node<E> {
        final E item;
        final Node<E> next;

        Node(E item, Node<E> next) {
            this.item = item;
            this.next = next;
        }
    }

    /**
     * 栈顶引用。
     */
    private final AtomicReference<Node<E>> top = new AtomicReference<Node<E>>();

    /**
     * 入栈。
     *
     * <p>push 的重试循环可以读成：</p>
     *
     * <p>1. 读取当前栈顶 oldTop；</p>
     * <p>2. 用 item 和 oldTop 构造新节点 newTop；</p>
     * <p>3. 尝试把 top 从 oldTop CAS 改成 newTop；</p>
     * <p>4. 如果失败，说明别的线程先一步改了栈顶，重新读取并重试。</p>
     */
    public void push(E item) {
        Node<E> newTop;
        Node<E> oldTop;

        do {
            oldTop = top.get();
            newTop = new Node<E>(item, oldTop);
        } while (!top.compareAndSet(oldTop, newTop));
    }

    /**
     * 出栈。
     *
     * <p>pop 的流程和 push 类似，但要先判断栈是否为空。</p>
     *
     * <p>如果当前 top 为 null，说明栈为空，直接返回 null。</p>
     */
    public E pop() {
        Node<E> oldTop;
        Node<E> newTop;

        do {
            oldTop = top.get();
            if (oldTop == null) {
                return null;
            }
            newTop = oldTop.next;
        } while (!top.compareAndSet(oldTop, newTop));

        return oldTop.item;
    }

    /**
     * 查看栈顶元素，但不移除。
     */
    public E peek() {
        Node<E> node = top.get();
        return node == null ? null : node.item;
    }

    /**
     * 判断栈是否为空。
     */
    public boolean isEmpty() {
        return top.get() == null;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testLifoOrderInSingleThread();
        testConcurrentPushDoesNotLoseItems();
        testPopOnEmptyStackReturnsNull();

        System.out.println("15.6 Treiber stack passed");
    }

    /**
     * 验证：单线程下栈具有 LIFO 语义。
     */
    private static void testLifoOrderInSingleThread() {
        TreiberStack<String> stack = new TreiberStack<String>();

        assertTrue(stack.isEmpty(), "stack should start empty");
        assertNull(stack.pop(), "popping empty stack should return null");

        stack.push("A");
        stack.push("B");
        stack.push("C");

        assertEquals("C", stack.pop(), "first pop should return last pushed item");
        assertEquals("B", stack.pop(), "second pop should return second pushed item");
        assertEquals("A", stack.pop(), "third pop should return first pushed item");
        assertNull(stack.pop(), "stack should be empty again");
    }

    /**
     * 验证：多个线程并发 push 时，不会丢失节点。
     *
     * <p>测试方法是：多个线程同时向栈中压入互不重复的整数，
     * 然后主线程把它们全部 pop 出来，检查数量和集合内容是否一致。</p>
     */
    private static void testConcurrentPushDoesNotLoseItems() throws Exception {
        final TreiberStack<Integer> stack = new TreiberStack<Integer>();
        final int threadCount = 4;
        final int pushesPerThread = 1000;
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < threadCount; i++) {
            final int base = i * pushesPerThread;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();

                        for (int j = 0; j < pushesPerThread; j++) {
                            stack.push(base + j);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }
            }, "treiber-pusher-" + i);

            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertNoFailure(failure);

        List<Integer> popped = new ArrayList<Integer>();
        Integer value;
        while ((value = stack.pop()) != null) {
            popped.add(value);
        }

        assertEquals(threadCount * pushesPerThread, popped.size(),
                "all pushed items should be present");

        Set<Integer> unique = new HashSet<Integer>(popped);
        assertEquals(popped.size(), unique.size(), "items should not be duplicated");
        for (int i = 0; i < threadCount * pushesPerThread; i++) {
            assertTrue(unique.contains(i), "missing item: " + i);
        }
    }

    /**
     * 验证：空栈 pop 返回 null。
     */
    private static void testPopOnEmptyStackReturnsNull() {
        TreiberStack<Object> stack = new TreiberStack<Object>();
        assertNull(stack.pop(), "empty stack should return null");
        assertTrue(stack.isEmpty(), "stack should remain empty");
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

    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + ": expected null, actual " + value);
        }
    }
}
