package com.sherlock.concurrency.chapter01.detailed_01_02;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 线程安全的数值序列生成器
 *
 */
@ThreadSafe
public class Sequence {

    private int value;

    public synchronized int getNext(){
        return value++;
    }

}
