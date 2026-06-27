package com.sherlock.concurrency.chapter08.detailed_08_11;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 将顺序递归转换为并行递归。
 *
 * <p>这是《Java 并发编程实战》中的 8.11。</p>
 *
 * <p>8.10 处理的是普通循环：每个元素都可以作为一个独立任务提交给 Executor。
 * 8.11 处理的是递归结构：每个节点的计算可以作为一个独立任务提交，
 * 同时继续递归遍历它的子节点。</p>
 *
 * <p>这个例子适合这样的场景：</p>
 *
 * <p>1. 数据结构是树或图中的一棵树；</p>
 * <p>2. 每个节点的计算 {@link Node#compute()} 彼此独立；</p>
 * <p>3. 节点计算结果的收集顺序不重要；</p>
 * <p>4. 用来保存结果的集合能承受并发写入。</p>
 */
public class TransformingSequentialRecursive {

    /**
     * 顺序递归处理。
     *
     * <p>调用线程会深度遍历节点列表：
     * 先计算当前节点，再递归处理当前节点的所有子节点。
     * 所有工作都发生在调用线程中。</p>
     *
     * @param nodes 当前层级的节点
     * @param results 保存计算结果的集合
     * @param <T> 结果类型
     */
    public <T> void sequentialRecursive(List<Node<T>> nodes, Collection<T> results) {
        for (Node<T> node : nodes) {
            results.add(node.compute());
            sequentialRecursive(node.getChildren(), results);
        }
    }

    /**
     * 并行递归处理。
     *
     * <p>这个方法和顺序版本的结构非常接近，但有一个关键变化：
     * 当前节点的 {@link Node#compute()} 不再由调用线程直接执行，
     * 而是包装成任务提交给 Executor。</p>
     *
     * <p>递归遍历本身仍然由调用线程继续完成。
     * 也就是说，这个方法会快速走完整棵树，把每个节点的计算任务都提交出去；
     * 真正的计算则在线程池工作线程中并发执行。</p>
     *
     * <p>因为多个任务会同时调用 {@code results.add(...)}，
     * 所以并行版本传入的 results 必须是线程安全集合，
     * 例如 {@link ConcurrentLinkedQueue} 或同步包装后的集合。</p>
     *
     * <p>这个方法返回时，只表示所有递归节点的计算任务都已经提交，
     * 不表示这些任务已经全部完成。等待完成、关闭线程池是调用方的职责。</p>
     *
     * @param executor 执行节点计算任务的 Executor
     * @param nodes 当前层级的节点
     * @param results 保存计算结果的线程安全集合
     * @param <T> 结果类型
     */
    public <T> void parallelRecursive(final Executor executor,
                                      List<Node<T>> nodes,
                                      final Collection<T> results) {
        for (final Node<T> node : nodes) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    results.add(node.compute());
                }
            });

            parallelRecursive(executor, node.getChildren(), results);
        }
    }

    /**
     * 递归结构中的节点抽象。
     *
     * @param <T> 节点计算结果类型
     */
    public interface Node<T> {

        /**
         * 计算当前节点的结果。
         *
         * <p>为了能被安全并行化，这个方法最好不要依赖其他节点的计算结果；
         * 如果访问共享状态，则共享状态必须是线程安全的。</p>
         */
        T compute();

        /**
         * 返回当前节点的子节点。
         */
        List<Node<T>> getChildren();
    }

    /**
     * 为演示准备的简单树节点。
     */
    private static class SimpleNode implements Node<String> {

        private final String name;
        private final List<Node<String>> children = new ArrayList<Node<String>>();

        private SimpleNode(String name) {
            this.name = name;
        }

        private SimpleNode add(SimpleNode child) {
            children.add(child);
            return this;
        }

        @Override
        public String compute() {
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            String result = Thread.currentThread().getName() + " computed " + name;
            System.out.println(result);
            return name;
        }

        @Override
        public List<Node<String>> getChildren() {
            return children;
        }
    }

    /**
     * 简单演示。
     *
     * <p>顺序版本的输出都来自 main 线程；
     * 并行版本的输出来自线程池工作线程，并且结果顺序不保证和树遍历顺序一致。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        List<Node<String>> roots = createDemoTree();
        TransformingSequentialRecursive transformer = new TransformingSequentialRecursive();

        System.out.println("sequential:");
        List<String> sequentialResults = new ArrayList<String>();
        transformer.sequentialRecursive(roots, sequentialResults);
        System.out.println("sequential results: " + sequentialResults);

        System.out.println("parallel:");
        Queue<String> parallelResults = new ConcurrentLinkedQueue<String>();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            transformer.parallelRecursive(executor, roots, parallelResults);
        } finally {
            executor.shutdown();
        }

        executor.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("parallel results: " + parallelResults);
    }

    private static List<Node<String>> createDemoTree() {
        SimpleNode rootA = new SimpleNode("A");
        rootA.add(new SimpleNode("A-1"))
                .add(new SimpleNode("A-2").add(new SimpleNode("A-2-1")));

        SimpleNode rootB = new SimpleNode("B");
        rootB.add(new SimpleNode("B-1"))
                .add(new SimpleNode("B-2"));

        List<Node<String>> roots = new ArrayList<Node<String>>();
        roots.add(rootA);
        roots.add(rootB);
        return roots;
    }
}
