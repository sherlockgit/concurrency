package com.sherlock.concurrency.chapter12.detailed_12_07;

import com.sherlock.concurrency.chapter12.detailed_12_01.SemaphoreBoundedBuffer;

import java.util.concurrent.TimeUnit;

/**
 * 测试有界缓冲区是否存在资源泄漏。
 *
 * <p>这是《Java 并发编程实战》中的 12.7。</p>
 *
 * <p>前面的 12.2、12.3、12.5 主要验证功能正确性：
 * 是否为空、是否为满、是否阻塞、生产者放入的数据最终是否都能取出。</p>
 *
 * <p>12.7 验证的是另一个问题：对象被 {@code take} 出来以后，
 * 缓冲区内部是否还错误地保留着这些对象的引用。</p>
 *
 * <p>如果缓冲区内部数组在取出元素后没有把对应槽位清成 {@code null}，
 * 那么即使调用者已经不再使用这些元素，GC 也会认为它们仍然可达，
 * 最终导致内存无法回收，这就是资源泄漏。</p>
 */
public class ResourceLeakTest {

    /**
     * 缓冲区容量。
     *
     * <p>书中的示例容量更大。本地示例适当调小，避免运行时占用过多内存。</p>
     */
    private static final int CAPACITY = 128;

    /**
     * 每个测试对象持有的字节数。
     *
     * <p>这里让每个对象明显占用一块内存，这样前后堆快照的差异更容易观察。</p>
     */
    private static final int BYTES_PER_OBJECT = 64 * 1024;

    /**
     * 允许的堆占用误差。
     *
     * <p>堆快照不是精确测量：
     * JVM 可能扩展堆、延迟回收、保留 TLAB、加载类元数据，都会带来噪声。
     * 因此这个测试只能用一个阈值判断“是否明显泄漏”，不能要求前后字节数完全相同。</p>
     */
    private static final long LEAK_THRESHOLD_BYTES = 4L * 1024 * 1024;

    /**
     * 用来放入缓冲区的大对象。
     *
     * <p>对象本身不重要，关键是它内部持有一块较大的 byte 数组。
     * 如果缓冲区泄漏引用，这些数组就很难被 GC 回收，堆占用会明显升高。</p>
     */
    private static class Big {
        private final byte[] data = new byte[BYTES_PER_OBJECT];
    }

    /**
     * 测试：put 一批大对象，再全部 take 出来，然后检查堆占用是否大致回到原水平。
     */
    public void testLeak() throws InterruptedException {
        SemaphoreBoundedBuffer<Big> buffer =
                new SemaphoreBoundedBuffer<Big>(CAPACITY);

        long heapSizeBefore = snapshotHeap();

        /*
         * 放入 CAPACITY 个大对象。
         *
         * 如果缓冲区容量是 128，每个对象 64KB，
         * 这里大约会让缓冲区暂时持有 8MB 的对象数据。
         */
        for (int i = 0; i < CAPACITY; i++) {
            buffer.put(new Big());
        }

        /*
         * 全部取出。
         *
         * 正确实现中，take 对应的内部逻辑应该把数组槽位设置为 null。
         * 例如当前项目的 SemaphoreBoundedBuffer#doExtract 中有：
         *
         *     items[i] = null;
         *
         * 这行代码就是避免资源泄漏的关键。
         */
        for (int i = 0; i < CAPACITY; i++) {
            buffer.take();
        }

        long heapSizeAfter = snapshotHeap();
        long leakedBytes = Math.abs(heapSizeBefore - heapSizeAfter);

        assertTrue(
                leakedBytes < LEAK_THRESHOLD_BYTES,
                "heap usage should return close to its original size, leakedBytes=" + leakedBytes);
    }

    /**
     * 获取当前堆中大致正在使用的字节数。
     *
     * <p>书中把这个方法写成占位方法，因为真实项目中可以用更专业的测试工具、
     * profiler、或者 JVM 诊断接口来观察内存。</p>
     *
     * <p>这里为了让示例可以直接运行，用 {@link Runtime#totalMemory()} 减去
     * {@link Runtime#freeMemory()} 得到近似值，并在测量前主动触发几次 GC。</p>
     */
    private long snapshotHeap() throws InterruptedException {
        forceGc();

        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    /**
     * 尽量让 GC 在取快照前完成。
     *
     * <p>{@code System.gc()} 只是请求 JVM 执行 GC，不是强制命令。
     * 所以这里循环调用几次，并短暂 sleep，降低测试的偶然性。</p>
     */
    private void forceGc() throws InterruptedException {
        for (int i = 0; i < 4; i++) {
            System.gc();
            System.runFinalization();
            TimeUnit.MILLISECONDS.sleep(100);
        }
    }

    /**
     * 简单运行入口。
     */
    public static void main(String[] args) throws InterruptedException {
        ResourceLeakTest test = new ResourceLeakTest();
        test.testLeak();
        System.out.println("12.7 resource leak test passed");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
