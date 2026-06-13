package com.sherlock.concurrency.chapter07.detailed_07_03;

import java.math.BigInteger;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 有缺陷的素数生产者。
 * 问题：当队列满时，queue.put() 会阻塞。此时即使 cancelled 标志被设置为 true，
 * 线程也无法检查到该标志，因为它在阻塞方法中无法执行循环判断。
 * 而且 catch (InterruptedException) 后只是吞掉了异常，没有重新设置中断标志，
 * 导致无法通过中断来唤醒线程。因此该生产者可能永远无法退出。
 */
class BrokenPrimeProducer extends Thread {
    private final BlockingQueue<BigInteger> queue;
    private volatile boolean cancelled = false;

    BrokenPrimeProducer(BlockingQueue<BigInteger> queue) {
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            BigInteger p = BigInteger.ONE;
            // 循环检查 cancelled 标志，但如果 queue.put() 阻塞，则无法进入下一次检查
            while (!cancelled) {
                // 将下一个素数放入阻塞队列，如果队列已满则阻塞（不可响应 cancelled 标志）
                queue.put(p = p.nextProbablePrime());
            }
        } catch (InterruptedException consumed) {
            // 吞掉中断异常，既不恢复中断状态，也不退出循环，导致线程无法正确响应中断
            // 正确做法应该是 Thread.currentThread().interrupt();
        }
    }

    public void cancel() {
        cancelled = true;
    }
}

/**
 * 示例：消费者使用 BrokenPrimeProducer 的示例。
 * 该方法会暴露出上述问题：当消费者不需要更多素数时，调用 producer.cancel()，
 * 但如果生产者线程正阻塞在 queue.put() 上，它永远无法退出。
 */
class PrimeConsumer {

    void consumePrimes() throws InterruptedException {
        // 创建一个容量有限的阻塞队列（例如容量为10）
        BlockingQueue<BigInteger> primes = new LinkedBlockingQueue<>(10);
        BrokenPrimeProducer producer = new BrokenPrimeProducer(primes);
        producer.start();

        try {
            // 假设需要消费一定数量的素数，比如10个
            while (needMorePrimes()) {
                // 从队列中取出一个素数（如果队列为空则阻塞）
                BigInteger prime = primes.take();
                consume(prime);
            }
        } finally {
            // 取消生产者（设置 cancelled 标志）
            producer.cancel();
            // 注意：如果生产者线程阻塞在 put() 上，它永远不会响应 cancelled 标志，
            // 因此线程无法终止，可能造成资源泄漏。
        }
    }

    private boolean needMorePrimes() {
        // 实际逻辑：决定是否还需要更多素数（示例中可返回 true 几次后变为 false）
        return true; // 示例：总是需要，实际应根据条件判断
    }

    private void consume(BigInteger prime) {
        System.out.println("Consumed prime: " + prime);
    }

    public static void main(String[] args) throws InterruptedException {
        new PrimeConsumer().consumePrimes();
    }
}
