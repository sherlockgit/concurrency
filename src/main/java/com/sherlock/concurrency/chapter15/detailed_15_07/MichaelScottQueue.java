package com.sherlock.concurrency.chapter15.detailed_15_07;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Michael-Scott 无阻塞队列。
 *
 * <p>这是《Java 并发编程实战》中的 15.7。</p>
 *
 * <p>这个版本演示的是无阻塞队列的核心插入算法。它使用两个原子引用维护队列：</p>
 *
 * <p>1. head 指向哨兵节点；</p>
 * <p>2. tail 指向队尾节点；</p>
 * <p>3. offer 时先把新节点链接到当前尾节点后面，再尝试把 tail 前移；</p>
 * <p>4. 如果发现 tail 落后，线程会顺手帮忙把 tail 推进到最新节点。</p>
 *
 * <p>这就是 Michael-Scott 队列的典型特征：线程不仅会推进自己的操作，
 * 还会帮助推进别的线程留下的“半完成状态”。</p>
 */
@ThreadSafe
public class MichaelScottQueue<E> {

    /**
     * 队列节点。
     *
     * <p>item 是节点携带的数据，next 指向后继节点。
     * next 使用 AtomicReference，是为了能对链表尾部链接做 CAS。</p>
     */
    private static class Node<E> {
        final E item;
        final AtomicReference<Node<E>> next;

        Node(E item) {
            this.item = item;
            this.next = new AtomicReference<Node<E>>(null);
        }
    }

    /**
     * head 和 tail 都指向队列中的节点。
     *
     * <p>初始化时构造一个哨兵节点，head 和 tail 都指向它。
     * 这样队列为空时，head.next == null。</p>
     */
    private final AtomicReference<Node<E>> head;
    private final AtomicReference<Node<E>> tail;

    public MichaelScottQueue() {
        Node<E> dummy = new Node<E>(null);
        this.head = new AtomicReference<Node<E>>(dummy);
        this.tail = new AtomicReference<Node<E>>(dummy);
    }

    /**
     * 入队。
     *
     * <p>核心流程：</p>
     *
     * <p>1. 读取当前尾节点 tail；</p>
     * <p>2. 读取尾节点的 next；</p>
     * <p>3. 如果 tail 还没有落后，就尝试把新节点链接到 tail.next；</p>
     * <p>4. 如果 tail 已经落后，则先帮忙把 tail 向前推进；</p>
     * <p>5. CAS 失败则重新读取并重试。</p>
     */
    public void offer(E item) {
        if (item == null) {
            throw new NullPointerException("item must not be null");
        }

        Node<E> newNode = new Node<E>(item);

        for (;;) {
            Node<E> currentTail = tail.get();
            Node<E> tailNext = currentTail.next.get();

            /*
             * 只有在当前读到的 tail 仍然是最新值时，下面的判断才有效。
             * 这样可以避免在并发更新下基于过期快照做错误判断。
             */
            if (currentTail == tail.get()) {
                if (tailNext != null) {
                    /*
                     * tail 已经落后了。
                     * 先帮忙把 tail 推到 next，之后下一轮循环再继续插入。
                     */
                    tail.compareAndSet(currentTail, tailNext);
                } else {
                    /*
                     * currentTail 仍是真正的尾节点。
                     * 先把新节点链接到尾部，再尝试把 tail 前移到新节点。
                     */
                    if (currentTail.next.compareAndSet(null, newNode)) {
                        tail.compareAndSet(currentTail, newNode);
                        return;
                    }
                }
            }
        }
    }

    /**
     * 出队。
     *
     * <p>这里保留了 Michael-Scott 算法中另一个关键动作：如果 tail 落后，也会帮忙推进 tail。</p>
     */
    public E poll() {
        for (;;) {
            Node<E> currentHead = head.get();
            Node<E> currentTail = tail.get();
            Node<E> first = currentHead.next.get();

            if (currentHead == head.get()) {
                if (first == null) {
                    return null;
                }

                if (currentHead == currentTail) {
                    /*
                     * 队列里已经有元素，但 tail 还停留在旧位置。
                     * 这里帮助把 tail 推进到 first。
                     */
                    tail.compareAndSet(currentTail, first);
                } else {
                    E item = first.item;
                    if (head.compareAndSet(currentHead, first)) {
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
        Node<E> first = head.get().next.get();
        return first == null ? null : first.item;
    }

    /**
     * 判断队列是否为空。
     */
    public boolean isEmpty() {
        return peek() == null;
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testSingleThreadFifoOrder();
        testConcurrentOffersDoNotLoseItems();
        testPollOnEmptyQueueReturnsNull();

        System.out.println("15.7 Michael-Scott queue passed");
    }

    /**
     * 验证：单线程下队列保持 FIFO 顺序。
     */
    private static void testSingleThreadFifoOrder() {
        MichaelScottQueue<String> queue = new MichaelScottQueue<String>();

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
        final MichaelScottQueue<Integer> queue = new MichaelScottQueue<Integer>();
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
            }, "ms-queue-offer-" + i);

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
        MichaelScottQueue<Object> queue = new MichaelScottQueue<Object>();
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
