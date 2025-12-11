package com.sherlock.concurrency.chapter03.detailed_03_02;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 非线程安全的可变整数类
 *
 *  MutableInteger，因为get和set都是在没有同步的情况下访问value的。
 *  与其他问题相比，失效值问题更容易出现:如果某个线程调用了set，那么另一个正在调用 get 的线程可能会看到更新后的 value 值，也可能看不到。
 */
@NotThreadSafe
public class MutableInteger {

    private int value;

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }
}
