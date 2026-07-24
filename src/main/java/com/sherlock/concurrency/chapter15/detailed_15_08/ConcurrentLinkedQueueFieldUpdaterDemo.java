package com.sherlock.concurrency.chapter15.detailed_15_08;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

/**
 * 使用 AtomicReferenceFieldUpdater 的无阻塞链表队列。
 *
 * <p>这是《Java 并发编程实战》中的 15.8。</p>
 *
 * <p>15.7 中的队列可以把每个需要 CAS 的位置都写成 AtomicReference。
 * 但在真实的 JDK 并发容器中，很多时候会直接声明普通的 volatile 字段，
 * 然后通过 AtomicReferenceFieldUpdater 对这些字段执行 CAS。</p>
 *
 * <p>这样做的核心好处是：</p>
 *
 * <p>1. 字段本身仍然是普通对象字段，不需要额外创建 AtomicReference 包装对象；</p>
 * <p>2. 仍然可以对这些 volatile 字段执行 CAS；</p>
 * <p>3. 更适合节点数量很多的数据结构，例如 ConcurrentLinkedQueue。</p>
 */
@ThreadSafe
public class ConcurrentLinkedQueueFieldUpdaterDemo<E> {

    /**
     * 队列节点。
     *
     * <p>注意：next 是普通 volatile 字段，不是 AtomicReference。
     * 对 next 的 CAS 由 NEXT_UPDATER 完成。</p>
     */
    private static class Node<E> {
        final E item;
        volatile Node<E> next;

        private static final AtomicReferenceFieldUpdater<Node, Node> NEXT_UPDATER =
                AtomicReferenceFieldUpdater.newUpdater(Node.class, Node.class, "next");

        Node(E item) {
            this.item = item;
        }

        boolean casNext(Node<E> expected, Node<E> update) {
            return NEXT_UPDATER.compareAndSet(this, expected, update);
        }
    }

    /**
     * head 和 tail 也是普通 volatile 字段。
     *
     * <p>对 head/tail 的 CAS 由下面两个 FieldUpdater 完成。
     * 这和直接使用 AtomicReference<Node<E>> 的效果类似，但少了一层包装对象。</p>
     */
    private volatile Node<E> head;
    private volatile Node<E> tail;

    private static final AtomicReferenceFieldUpdater<ConcurrentLinkedQueueFieldUpdaterDemo, Node>
            HEAD_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
            ConcurrentLinkedQueueFieldUpdaterDemo.class, Node.class, "head");

    private static final AtomicReferenceFieldUpdater<ConcurrentLinkedQueueFieldUpdaterDemo, Node>
            TAIL_UPDATER = AtomicReferenceFieldUpdater.newUpdater(
            ConcurrentLinkedQueueFieldUpdaterDemo.class, Node.class, "tail");

    public ConcurrentLinkedQueueFieldUpdaterDemo() {
        Node<E> dummy = new Node<E>(null);
        this.head = dummy;
        this.tail = dummy;
    }

    /**
     * 入队。
     *
     * <p>算法和 15.7 的 Michael-Scott 队列相同：</p>
     *
     * <p>1. 先读取当前 tail；</p>
     * <p>2. 如果 tail.next 不为空，说明 tail 落后了，先帮忙推进 tail；</p>
     * <p>3. 如果 tail.next 为空，说明它是真正的尾节点，尝试 CAS 链接新节点；</p>
     * <p>4. 链接成功后，再尝试把 tail 推到新节点。</p>
     */
    public void offer(E item) {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }

        Node<E> newNode = new Node<E>(item);

        for (;;) {
            Node<E> currentTail = tail;
            Node<E> tailNext = currentTail.next;

            if (currentTail == tail) {
                if (tailNext != null) {
                    casTail(currentTail, tailNext);
                } else if (currentTail.casNext(null, newNode)) {
                    casTail(currentTail, newNode);
                    return;
                }
            }
        }
    }

    /**
     * 出队。
     *
     * <p>这里同样保留“帮忙推进”的逻辑。
     * 如果 head 和 tail 指向同一个节点，但 head.next 已经不为空，
     * 说明有线程完成了链接新节点，但还没来得及推进 tail，当前线程会帮它推进。</p>
     */
    public E poll() {
        for (;;) {
            Node<E> currentHead = head;
            Node<E> currentTail = tail;
            Node<E> first = currentHead.next;

            if (currentHead == head) {
                if (first == null) {
                    return null;
                }

                if (currentHead == currentTail) {
                    casTail(currentTail, first);
                } else {
                    E item = first.item;
                    if (casHead(currentHead, first)) {
                        return item;
                    }
                }
            }
        }
    }

    /**
     * 查看队头元素，但不移除。
     */
    public E peek() {
        Node<E> first = head.next;
        return first == null ? null : first.item;
    }

    public boolean isEmpty() {
        return peek() == null;
    }

    private boolean casHead(Node<E> expected, Node<E> update) {
        return HEAD_UPDATER.compareAndSet(this, expected, update);
    }

    private boolean casTail(Node<E> expected, Node<E> update) {
        return TAIL_UPDATER.compareAndSet(this, expected, update);
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSingleThreadFifoOrder();
        testConcurrentOffersDoNotLoseItems();
        testPollOnEmptyQueueReturnsNull();

        System.out.println("15.8 AtomicReferenceFieldUpdater queue passed");
    }

    /**
     * 验证：单线程下队列保持 FIFO 顺序。
     */
    private static void testSingleThreadFifoOrder() {
        ConcurrentLinkedQueueFieldUpdaterDemo<String> queue =
                new ConcurrentLinkedQueueFieldUpdaterDemo<String>();

        assertTrue(queue.isEmpty(), "queue should start empty");
        assertNull(queue.poll(), "polling empty queue should return null");

        queue.offer("A");
        queue.offer("B");
        queue.offer("C");

        assertEquals("A", queue.poll(), "first poll should return first offered item");
        assertEquals("B", queue.poll(), "second poll should return second offered item");
        assertEquals("C", queue.poll(), "third poll should return third offered item");
        assertNull(queue.poll(), "queue should be empty again");
    }

    /**
     * 验证：多个线程并发 offer 时，不会丢失节点。
     */
    private static void testConcurrentOffersDoNotLoseItems() throws Exception {
        final ConcurrentLinkedQueueFieldUpdaterDemo<Integer> queue =
                new ConcurrentLinkedQueueFieldUpdaterDemo<Integer>();
        final int threadCount = 4;
        final int offersPerThread = 1000;
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threadCount);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        for (int i = 0; i < threadCount; i++) {
            final int base = i * offersPerThread;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        ready.countDown();
                        start.await();

                        for (int j = 0; j < offersPerThread; j++) {
                            queue.offer(base + j);
                        }
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                }
            }, "field-updater-queue-offer-" + i);

            thread.start();
        }

        ready.await();
        start.countDown();
        done.await();

        assertNoFailure(failure);

        List<Integer> polled = new ArrayList<Integer>();
        Integer value;
        while ((value = queue.poll()) != null) {
            polled.add(value);
        }

        assertEquals(threadCount * offersPerThread, polled.size(),
                "all offered items should be present");

        Set<Integer> unique = new HashSet<Integer>(polled);
        assertEquals(polled.size(), unique.size(), "items should not be duplicated");
        for (int i = 0; i < threadCount * offersPerThread; i++) {
            assertTrue(unique.contains(i), "missing item: " + i);
        }
    }

    /**
     * 验证：空队列 poll 返回 null。
     */
    private static void testPollOnEmptyQueueReturnsNull() {
        ConcurrentLinkedQueueFieldUpdaterDemo<Object> queue =
                new ConcurrentLinkedQueueFieldUpdaterDemo<Object>();
        assertNull(queue.poll(), "empty queue should return null");
        assertTrue(queue.isEmpty(), "queue should remain empty");
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
