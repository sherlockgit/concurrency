package com.sherlock.concurrency.chapter08.detailed_08_12;

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
 * 等待并行递归任务的结果。
 *
 * <p>这是《Java 并发编程实战》中的 8.12。</p>
 *
 * <p>8.11 中的 {@code parallelRecursive} 只负责把节点计算任务提交给 Executor，
 * 方法返回时并不能保证任务已经执行完成。8.12 在它外面再包一层
 * {@link #getParallelResults(List)}，负责创建线程池、提交任务、关闭线程池、
 * 等待所有任务结束，并最终返回结果集合。</p>
 *
 * <p>这个例子的重点是所有权：</p>
 *
 * <p>1. 如果 Executor 是调用方传进来的，当前方法通常不应该关闭它；</p>
 * <p>2. 如果 Executor 是当前方法自己创建的，当前方法就应该负责关闭它；</p>
 * <p>3. 只有关闭线程池后，{@link ExecutorService#awaitTermination(long, TimeUnit)}
 * 才能等待“所有已提交任务都执行完成并且工作线程退出”。</p>
 */
public class TransformingSequentialResults {

    /**
     * 并行递归提交节点计算任务。
     *
     * <p>这个方法只提交任务，不等待任务完成。
     * 等待逻辑由 {@link #getParallelResults(List)} 统一处理。</p>
     *
     * @param executor 执行节点计算任务的 Executor
     * @param nodes 当前层级的节点
     * @param results 保存结果的线程安全集合
     * @param <T> 节点计算结果类型
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
     * 获取并行递归计算得到的全部结果。
     *
     * <p>执行步骤如下：</p>
     *
     * <p>1. 创建一个临时线程池；</p>
     * <p>2. 创建一个线程安全结果队列；</p>
     * <p>3. 递归遍历整棵树，并把每个节点的计算任务提交给线程池；</p>
     * <p>4. 调用 {@link ExecutorService#shutdown()}，表示不再接受新任务；</p>
     * <p>5. 调用 {@link ExecutorService#awaitTermination(long, TimeUnit)}，
     * 等待已经提交的任务全部完成；</p>
     * <p>6. 返回结果队列。</p>
     *
     * <p>这里使用 {@link ConcurrentLinkedQueue} 是因为多个工作线程会同时写入结果。
     * 如果改成普通 {@link ArrayList}，并发 add 会破坏集合内部状态。</p>
     *
     * <p>书中示例使用 {@code Long.MAX_VALUE} 秒作为等待时间，
     * 意思是“几乎无限期等待”。真实业务中通常应该根据场景设置一个明确超时时间。</p>
     *
     * @param nodes 根节点列表
     * @param <T> 节点计算结果类型
     * @return 并行计算得到的结果集合
     * @throws InterruptedException 当前线程等待任务完成时被中断
     */
    public <T> Collection<T> getParallelResults(List<Node<T>> nodes)
            throws InterruptedException {
        ExecutorService executor = Executors.newCachedThreadPool();
        Queue<T> resultQueue = new ConcurrentLinkedQueue<T>();

        try {
            parallelRecursive(executor, nodes, resultQueue);
        } finally {
            executor.shutdown();
        }

        executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
        return resultQueue;
    }

    /**
     * 递归结构中的节点抽象。
     *
     * @param <T> 节点计算结果类型
     */
    public interface Node<T> {

        T compute();

        List<Node<T>> getChildren();
    }

    /**
     * 为演示准备的简单节点。
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
     * <p>调用 {@link #getParallelResults(List)} 后，main 线程会等待所有节点计算完成，
     * 因此最后打印结果时，集合里已经包含所有节点的计算结果。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        TransformingSequentialResults transformer = new TransformingSequentialResults();
        Collection<String> results = transformer.getParallelResults(createDemoTree());
        System.out.println("results: " + results);
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
