package com.sherlock.concurrency.chapter15;

/**
 * 伪随机数生成器的公共基础类。
 *
 * <p>第 15 章后面的几个示例会使用不同同步方式保护同一个 seed。
 * 这个类只提供“如何根据旧 seed 计算新 seed”的纯计算逻辑，不保存共享状态。</p>
 */
public abstract class PseudoRandom {

    /**
     * 生成下一个伪随机 seed。
     *
     * <p>这里使用一个简单的 xorshift 变换。
     * 它不是密码学安全随机数，只是为了让示例能稳定地产生一串不完全相同的整数。</p>
     */
    protected final int calculateNext(int seed) {
        int next = seed;
        next ^= next << 6;
        next ^= next >>> 21;
        next ^= next << 7;
        return next;
    }

    /**
     * 返回一个基于当前 seed 计算出的伪随机整数。
     *
     * <p>注意：为了贴近书中的示例，这个方法返回的范围是 1 到 n，
     * 而不是 JDK Random.nextInt(n) 的 0 到 n - 1。</p>
     */
    public abstract int nextInt(int n);
}
