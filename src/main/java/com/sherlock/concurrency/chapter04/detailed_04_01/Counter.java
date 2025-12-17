package com.sherlock.concurrency.chapter04.detailed_04_01;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 使用Java监视器模式的线程安全计数器
 *
 * 要分析对象的状态，首先从对象的域开始。如果对象中所有的域都是基本类型的变量，
 * 那么这些域将构成对象的全部状态。程序中的Counter只有一个域value，
 * 因此这个域就是Counter的全部状态。对于含有n个基本类型域的对象，其状态就是这些域构成的n元组。
 * 例如，二维点的状态就是它的坐标值(x，y)。如果在对象的域中引用了其他对象，那么该对象的状态将包含被引用对象的域。
 * 例如，LinkedList的状态就包括该链表中所有节点对象的状态。
 *
 *
 */
@ThreadSafe
public class Counter {

    private long value = 0;

    public synchronized long getValue(){
        return value;
    }

    public synchronized long increment(){
        if (value == Long.MAX_VALUE) {
            throw new IllegalStateException("counter overflow");
        }
        return ++value;
    }

}
