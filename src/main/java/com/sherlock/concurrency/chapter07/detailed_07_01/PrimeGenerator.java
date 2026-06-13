package com.sherlock.concurrency.chapter07.detailed_07_01;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * 可取消的素数生成器。
 * 该任务在一个单独的线程中持续生成素数，直到被取消。
 * 使用 volatile 类型的 cancelled 标志来安全地取消任务。
 * 注意：这里的同步仅保证 primes 列表的线程安全，取消操作本身不需要同步。
 */
@ThreadSafe
public class PrimeGenerator implements Runnable {

    // 保存已生成的素数列表，受当前对象锁保护
    private final List<BigInteger> primes = new ArrayList<>();

    // volatile 保证取消标志的可见性，无需同步即可安全地读取和修改
    private volatile boolean cancelled;

    /**
     * 核心生成逻辑：不断生成下一个素数，直到被取消。
     * 由于 nextProbablePrime 是 CPU 密集型操作，取消标志会在每次迭代开始时检查。
     */
    @Override
    public void run() {
        BigInteger p = BigInteger.ONE;
        // 每次循环前检查取消标志，若已取消则退出
        while (!cancelled) {
            // 生成下一个可能的素数（该方法不会阻塞，计算量较大）
            p = p.nextProbablePrime();
            // 将新素数加入列表（需要同步，因为列表非线程安全且可能被其他线程读取）
            synchronized (this) {
                primes.add(p);
            }
        }
    }

    /**
     * 取消素数生成任务。
     * 将 cancelled 标志设为 true，正在运行的线程会在下一次循环开始处检测到并退出。
     */
    public void cancel() {
        cancelled = true;
    }

    /**
     * 获取当前已生成的所有素数的快照。
     * 返回一个新的 ArrayList 副本，避免调用方直接修改内部列表。
     * @return 当前已生成的素数列表（副本）
     */
    public synchronized List<BigInteger> get() {
        return new ArrayList<>(primes);
    }
}
