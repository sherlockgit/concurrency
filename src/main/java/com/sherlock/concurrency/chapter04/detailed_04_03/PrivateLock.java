package com.sherlock.concurrency.chapter04.detailed_04_03;

/**
 *  通过一个私有锁来保护状态
 *
 * 使用私有的锁对象而不是对象的内置锁(或任何其他可通过公有方式访问的锁)，有许多优点。
 * 私有的锁对象可以将锁封装起来，使客户代码无法得到锁，但客户代码可以通过公有方法来访问锁，
 * 以便(正确或者不正确地)参与到它的同步策略中。如果客户代码错误地获得了另一个对象的锁，
 * 那么可能会产生活跃性问题。此外，要想验证某个公有访问的锁在程序中是否被正确地使用，则需要检查整个程序，而不是单个的类。
 */
public class PrivateLock {

    private final Object myLock = new Object();
    Widget widget;


    void someMethod(){
        synchronized (myLock){
            //修改或者访问Widget状态
        }
    }

}
