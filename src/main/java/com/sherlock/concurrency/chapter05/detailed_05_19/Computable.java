package com.sherlock.concurrency.chapter05.detailed_05_19;

/**
 * 可计算接口：定义了一个计算操作，输入类型 A，输出类型 V
 * @param <A> 输入参数类型
 * @param <V> 计算结果类型
 */
public interface Computable<A, V> {
    /**
     * 执行计算（可能耗时很长，也可能响应中断）
     * @param arg 输入参数
     * @return 计算结果
     * @throws InterruptedException 若计算过程中被中断则抛出
     */
    V compute(A arg) throws InterruptedException;
}
