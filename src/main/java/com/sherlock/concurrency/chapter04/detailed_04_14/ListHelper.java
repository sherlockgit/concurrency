package com.sherlock.concurrency.chapter04.detailed_04_14;

import com.sherlock.concurrency.annoations.NotThreadSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 非线程安全的“若没有则添加”(不要这么做)
 * * *
 * 为什么这种方式不能实现线程安全性?毕竟，putIfAbsent 已经声明为synchronized类型的变量，对不对?
 * * 问题在于在错误的锁上进行了同步。无论List使用哪一个锁来保护它的状态，可以确定的是，这个锁并不是ListHelper上的锁。
 * * ListHelper 只是带来了同步的假象，尽管所有的链表操作都被声明为synchronized，但却使用了不同的锁，
 * * 这意味着putlfAbsent相对于List 的其他操作来说并不是原子的，因此就无法确保当putlfAbsent执行时另一个线程不会修改链表。
 *
 *
 *  要想使这个方法能正确执行，必须使List在实现客户端加锁或外部加锁时使用同一个锁。
 *  *  客户端加锁是指，对于使用某个对象X的客户端代码，使用X本身用于保护其状态的锁来保护这段客户代码。
 *  *  要使用客户端加锁，你必须知道对象X使用的是哪一个锁。
 */
@NotThreadSafe
public class ListHelper<E>{
    public List<E> list = Collections.synchronizedList(new ArrayList<>());

    public synchronized boolean putIfAbsent(E x){
        boolean absent = !list.contains(x);
        if (absent) {
            list.add(x);
        }
        return absent;
    }
}
