package com.sherlock.concurrency.chapter02.detailed_02_03;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 延迟初始化中的竞态条件(不要这么做)
 *
 * 在 LazyInitRace 中包含了一个竟态条件，它可能会破坏这个类的正确性。
 * 假定线程A 和线程B同时执行getInstance。A看到instance为空，因而创建一个新的ExpensiveObject 实例。
 * B同样需要判断instance 是否为空。此时的instance 是否为空，要取决于不可预测的时序，包括线程的调度方式，
 * 以及A需要花多长时间来初始化ExpensiveObject并设置instance。
 * 如果当B检查时，instance为空，那么在两次调用getInstance时可能会得到不同的结果，即使getInstance 通常被认为是返回相同的实例。
 *
 * 在并发编程中，这种由于不恰当的执行时序而出现不正确的结果是一种非常重要的情况，它有一个正式的名字:竞态条件(RaceCondition)
 */
@NotThreadSafe
public class LazyInitRace {

    private ExpensiveObject instance = null;

    public ExpensiveObject getInstance(){
        if (instance == null) {
            instance = new ExpensiveObject();
        }
        return instance;
    }

}
