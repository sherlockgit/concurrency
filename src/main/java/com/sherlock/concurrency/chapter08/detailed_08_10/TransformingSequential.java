package com.sherlock.concurrency.chapter08.detailed_08_10;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 将顺序执行转换为并行执行。
 *
 * <p>这是《Java 并发编程实战》中的 8.10。</p>
 *
 * <p>这个例子的核心思想是：
 * 如果循环中的每次处理彼此独立，那么原来在调用线程中一个接一个执行的操作，
 * 可以改成提交给 {@link Executor}，由线程池中的多个工作线程并行执行。</p>
 *
 * <p>需要注意的是，并不是所有顺序循环都能直接改成并行：</p>
 *
 * <p>1. 每个元素的处理不能依赖前一个元素的处理结果；</p>
 * <p>2. {@link #process(Element)} 不能破坏共享状态的线程安全；</p>
 * <p>3. 并行版本只负责“提交任务”，不负责“等待所有任务完成”；</p>
 * <p>4. {@link Executor} 的创建、关闭和等待终止，应该由调用方负责。</p>
 */
public abstract class TransformingSequential {

    /**
     * 顺序处理一批元素。
     *
     * <p>这里没有引入任何并发。调用线程会依次处理列表中的每个元素：
     * 第一个处理完，才会处理第二个；第二个处理完，才会处理第三个。</p>
     *
     * @param elements 待处理元素列表
     */
    public void processSequentially(List<Element> elements) {
        for (Element element : elements) {
            process(element);
        }
    }

    /**
     * 并行处理一批元素。
     *
     * <p>这里的改变非常小：原来循环体中直接调用 {@link #process(Element)}，
     * 现在改为给每个元素创建一个 {@link Runnable}，再提交给 Executor。</p>
     *
     * <p>这个方法返回时，只能说明“所有任务都已经提交出去”，
     * 不能说明“所有任务都已经执行完成”。原因是 {@link Executor#execute(Runnable)}
     * 通常只是把任务放入线程池队列，真正执行发生在工作线程中。</p>
     *
     * @param executor 执行任务的 Executor，由调用方拥有
     * @param elements 待处理元素列表
     */
    public void processInParallel(Executor executor, List<Element> elements) {
        for (final Element element : elements) {
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    process(element);
                }
            });
        }
    }

    /**
     * 处理单个元素的业务逻辑。
     *
     * <p>这个方法由子类实现。它必须适合并发调用：
     * 如果方法内部会访问共享状态，那么共享状态本身要么是线程安全的，
     * 要么必须在这里进行正确同步。</p>
     *
     * @param element 待处理元素
     */
    public abstract void process(Element element);

    /**
     * 示例中的元素抽象。
     *
     * <p>书中的重点是任务拆分方式，而不是元素本身的数据结构，
     * 所以这里保留一个最小接口即可。</p>
     */
    public interface Element {
    }

    /**
     * 为了让 main 方法能直接演示，补一个简单元素实现。
     */
    private static class NamedElement implements Element {

        private final String name;

        private NamedElement(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * 简单演示顺序处理和并行处理的差异。
     *
     * <p>可以观察控制台输出中的线程名：
     * 顺序版本全部由 main 线程执行；
     * 并行版本由线程池中的工作线程执行。</p>
     */
    public static void main(String[] args) throws InterruptedException {
        List<Element> elements = new ArrayList<Element>();
        for (int i = 0; i < 5; i++) {
            elements.add(new NamedElement("element-" + i));
        }

        TransformingSequential processor = new TransformingSequential() {
            @Override
            public void process(Element element) {
                System.out.println(Thread.currentThread().getName() + " process " + element);
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        System.out.println("sequential:");
        processor.processSequentially(elements);

        System.out.println("parallel:");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            processor.processInParallel(executor, elements);
        } finally {
            executor.shutdown();
        }

        executor.awaitTermination(5, TimeUnit.SECONDS);
    }
}
