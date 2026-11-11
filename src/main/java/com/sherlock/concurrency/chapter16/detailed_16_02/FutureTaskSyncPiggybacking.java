package com.sherlock.concurrency.chapter16.detailed_16_02;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

/**
 * FutureTask 内部 Sync 类中的同步借用。
 *
 * <p>这是《Java 并发编程实战》中的 16.2。</p>
 *
 * <p>早期 JDK 的 FutureTask 曾经使用 AQS 实现等待任务完成。
 * 这个例子不是完整重写 FutureTask，而是保留其中和内存可见性有关的核心结构。</p>
 *
 * <p>重点在于：result 和 exception 字段都不是 volatile，也没有被 synchronized 保护。
 * 它们的可见性是“借用”了 AQS 的同步路径获得的。
 * 这种技术称为 synchronization piggybacking，也就是“同步借用”。</p>
 */
@ThreadSafe
public class FutureTaskSyncPiggybacking<V> implements Runnable {

    /**
     * 真正要执行的任务。
     */
    private final Callable<V> callable;

    /**
     * 同步控制逻辑封装在 AQS 子类中。
     */
    private final Sync sync = new Sync();

    public FutureTaskSyncPiggybacking(Callable<V> callable) {
        if (callable == null) {
            throw new NullPointerException("callable must not be null");
        }
        this.callable = callable;
    }

    /**
     * 执行任务。
     *
     * <p>任务只能从 READY 状态切换到 RUNNING 状态一次。
     * 后续重复调用 run() 会直接返回，不会重复执行 callable。</p>
     */
    @Override
    public void run() {
        sync.innerRun();
    }

    /**
     * 等待任务完成并获取结果。
     */
    public V get() throws InterruptedException, ExecutionException {
        return sync.innerGet();
    }

    /**
     * 任务是否已经完成。
     */
    public boolean isDone() {
        return sync.innerIsDone();
    }

    /**
     * 简化版取消操作。
     *
     * <p>这里不处理中断正在运行的线程，只用于说明完成状态也可以释放等待者。</p>
     */
    public boolean cancel() {
        return sync.innerCancel();
    }

    /**
     * AQS 同步器。
     *
     * <p>AQS 的 state 表示任务状态：</p>
     *
     * <p>1. READY：还没有运行；</p>
     * <p>2. RUNNING：正在运行；</p>
     * <p>3. RAN：已经正常或异常完成；</p>
     * <p>4. CANCELLED：已经取消。</p>
     */
    private final class Sync extends AbstractQueuedSynchronizer {

        private static final int READY = 0;
        private static final int RUNNING = 1;
        private static final int RAN = 2;
        private static final int CANCELLED = 4;

        /**
         * 任务结果。
         *
         * <p>这个字段故意不是 volatile。
         * 它的可见性依赖任务完成时的 releaseShared，以及 get() 中的 acquireSharedInterruptibly。</p>
         */
        private V result;

        /**
         * 任务抛出的异常。
         *
         * <p>同样故意不是 volatile。</p>
         */
        private Throwable exception;

        /**
         * 正在运行任务的线程。
         *
         * <p>runner 是 volatile。
         * 任务线程在 releaseShared 中把 runner 置为 null，
         * 等待线程在 tryAcquireShared 中读取 runner。
         * 这条 volatile 写-读路径用于发布 result 和 exception。</p>
         */
        private volatile Thread runner;

        void innerRun() {
            /*
             * 只有一个线程能把任务从 READY 推进到 RUNNING。
             */
            if (!compareAndSetState(READY, RUNNING)) {
                return;
            }

            runner = Thread.currentThread();

            try {
                V value = callable.call();
                innerSet(value);
            } catch (Throwable t) {
                innerSetException(t);
            }
        }

        V innerGet() throws InterruptedException, ExecutionException {
            /*
             * 如果任务还没完成，当前线程会进入 AQS 共享等待队列。
             * 如果任务已经完成，tryAcquireShared 会返回成功，当前线程直接继续执行。
             */
            acquireSharedInterruptibly(0);

            int state = getState();
            if (state == CANCELLED) {
                throw new CancellationException();
            }
            if (exception != null) {
                throw new ExecutionException(exception);
            }
            return result;
        }

        boolean innerCancel() {
            for (;;) {
                int state = getState();
                if (ranOrCancelled(state)) {
                    return false;
                }
                if (compareAndSetState(state, CANCELLED)) {
                    releaseShared(0);
                    return true;
                }
            }
        }

        boolean innerIsDone() {
            int state = getState();
            return ranOrCancelled(state) && runner == null;
        }

        /**
         * 正常完成任务。
         *
         * <p>这里的关键顺序是：</p>
         *
         * <p>1. 把状态推进到 RAN；</p>
         * <p>2. 写入非 volatile 的 result；</p>
         * <p>3. 调用 releaseShared；</p>
         * <p>4. releaseShared 会调用 tryReleaseShared，把 volatile runner 写成 null；</p>
         * <p>5. get() 线程在 tryAcquireShared 中读到 runner == null 后，能看到 result。</p>
         */
        private void innerSet(V value) {
            for (;;) {
                int state = getState();
                if (ranOrCancelled(state)) {
                    return;
                }
                if (compareAndSetState(state, RAN)) {
                    result = value;
                    releaseShared(0);
                    return;
                }
            }
        }

        /**
         * 异常完成任务。
         */
        private void innerSetException(Throwable throwable) {
            for (;;) {
                int state = getState();
                if (ranOrCancelled(state)) {
                    return;
                }
                if (compareAndSetState(state, RAN)) {
                    exception = throwable;
                    releaseShared(0);
                    return;
                }
            }
        }

        /**
         * AQS 共享获取。
         *
         * <p>返回正数表示获取成功，get() 可以继续返回结果。
         * 返回负数表示任务尚未完成，get() 线程需要入队等待。</p>
         */
        @Override
        protected int tryAcquireShared(int ignored) {
            return innerIsDone() ? 1 : -1;
        }

        /**
         * AQS 共享释放。
         *
         * <p>这里不是为了释放某把业务锁，而是为了建立可见性并唤醒等待 get() 的线程。</p>
         */
        @Override
        protected boolean tryReleaseShared(int ignored) {
            /*
             * volatile 写。
             * 它发布了 releaseShared 之前写入的 result 或 exception。
             */
            runner = null;
            return true;
        }

        private boolean ranOrCancelled(int state) {
            return state == RAN || state == CANCELLED;
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws Exception {
        testGetWaitsForResult();
        testNonVolatilePayloadIsSafelyPublished();
        testExceptionIsWrappedByGet();
        testRunOnlyOnce();

        System.out.println("16.2 FutureTask sync piggybacking passed");
    }

    /**
     * 验证：get 会等待任务完成并返回结果。
     */
    private static void testGetWaitsForResult() throws Exception {
        FutureTaskSyncPiggybacking<String> task =
                new FutureTaskSyncPiggybacking<String>(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        TimeUnit.MILLISECONDS.sleep(100);
                        return "done";
                    }
                });

        Thread worker = new Thread(task, "future-task-worker");
        worker.start();

        assertEquals("done", task.get(), "get should return task result");
        assertTrue(task.isDone(), "task should be done after get returns");
    }

    /**
     * 验证：result 不是 volatile，仍然能通过 get 安全看到其中的字段值。
     */
    private static void testNonVolatilePayloadIsSafelyPublished() throws Exception {
        FutureTaskSyncPiggybacking<Payload> task =
                new FutureTaskSyncPiggybacking<Payload>(new Callable<Payload>() {
                    @Override
                    public Payload call() {
                        Payload payload = new Payload();
                        payload.value = 42;
                        return payload;
                    }
                });

        Thread worker = new Thread(task, "future-task-payload-worker");
        worker.start();

        Payload payload = task.get();
        assertEquals(42, payload.value, "payload field should be visible after get");
    }

    /**
     * 验证：任务抛出的异常会通过 get 包装成 ExecutionException。
     */
    private static void testExceptionIsWrappedByGet() throws Exception {
        FutureTaskSyncPiggybacking<String> task =
                new FutureTaskSyncPiggybacking<String>(new Callable<String>() {
                    @Override
                    public String call() {
                        throw new IllegalStateException("boom");
                    }
                });

        task.run();

        boolean failed = false;
        try {
            task.get();
        } catch (ExecutionException expected) {
            failed = true;
            assertTrue(expected.getCause() instanceof IllegalStateException,
                    "cause should be original exception");
        }

        assertTrue(failed, "get should throw ExecutionException");
        assertTrue(task.isDone(), "exceptional task should be done");
    }

    /**
     * 验证：重复调用 run 不会重复执行任务。
     */
    private static void testRunOnlyOnce() throws Exception {
        final AtomicInteger calls = new AtomicInteger();
        FutureTaskSyncPiggybacking<Integer> task =
                new FutureTaskSyncPiggybacking<Integer>(new Callable<Integer>() {
                    @Override
                    public Integer call() {
                        return calls.incrementAndGet();
                    }
                });

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread first = newRunningThread(task, ready, start, failure, "future-task-run-first");
        Thread second = newRunningThread(task, ready, start, failure, "future-task-run-second");

        first.start();
        second.start();
        ready.await();
        start.countDown();
        first.join();
        second.join();

        assertNoFailure(failure);
        assertEquals(1, calls.get(), "callable should run exactly once");
        assertEquals(1, task.get().intValue(), "result should come from the only successful run");
    }

    private static Thread newRunningThread(final FutureTaskSyncPiggybacking<?> task,
                                           final CountDownLatch ready,
                                           final CountDownLatch start,
                                           final AtomicReference<Throwable> failure,
                                           String name) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ready.countDown();
                    start.await();
                    task.run();
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, name);
    }

    private static class Payload {
        int value;
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
}
