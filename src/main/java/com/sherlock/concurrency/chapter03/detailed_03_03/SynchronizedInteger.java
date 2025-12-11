package com.sherlock.concurrency.chapter03.detailed_03_03;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 线程安全的可变整数类
 *
 *  通过对get和set等方法进行同步，可以使MutableInteger 成为一个线程安全的类。
 *  仅对set方法进行同步是不够的，调用 get的线程仍然会看见失效值。
 */
@ThreadSafe
public class SynchronizedInteger {

    private int value;

    public synchronized int getValue() {
        return value;
    }

    public synchronized void setValue(int value) {
        this.value = value;
    }
}
