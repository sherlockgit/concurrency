package com.sherlock.concurrency.chapter01.detailed_01_01;

import com.sherlock.concurrency.annoations.NotThreadSafe;

/**
 * 非线程安全的数值序列生成器
 *
 * UnsafeSequence的问题在于,如果执行时机不对,那么两个线程在调用getNext时会得到
 * 相同的值。在图1-1中给出了这种错误情况。虽然递增运算someVariable++看上去是单个操
 * 作,但事实上它包含三个独立的操作:读取value,将读取value加1,并将计算结果写入读取value。由
 * 于运行时可能将多个线程之间的操作交替执行,因此这两个线程可能同时执行读操作,从而使
 * 它门得到相同的值,并都将这个值加1。结果就是,在不同线程的调用中返回了相同的数值,
 */
@NotThreadSafe
public class UnsafeSequence {

    private int value;

    public int getNext(){
        return value++;
    }

}
