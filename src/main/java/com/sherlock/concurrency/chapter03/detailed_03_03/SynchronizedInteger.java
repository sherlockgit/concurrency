package com.sherlock.concurrency.chapter03.detailed_03_03;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 线程安全的可变整数类
 *
 *  通过对get和set等方法进行同步，可以使MutableInteger 成为一个线程安全的类。
 *  仅对set方法进行同步是不够的，调用 get的线程仍然会看见失效值。
 *
 * 当线程在没有同步的情况下读取变量时，可能会得到一个失效值，但至少这个值是由之前某个线程设置的值，而不是一个随机值。
 * 这种安全性保证也被称为最低安全性
 *
 * 最低安全性适用于绝大多数变量，但是存在一个例外:非volatile类型的64位数值变量(double和long)。
 * Java内存模型要求，变量的读取操作和写入操作都必须是原子操作，但对于非volatile 类型的long和double变量，
 * JVM 允许将64位的读操作或写操作分解为两个32位的操作。当读取一个非volatile 类型的long变量时，
 * 如果对该变量的读操作和写操作在不同的线程中执行，那么很可能会读取到某个值的高32位和另一个值的低 32 位日因此，
 * 即使不考虑失效数据问题，在多线程程序中使用共享且可变的long和double等类型的变量也是不安全的，除非用关键字volatile 来声明它们，
 * 或者用锁保护起来。
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
