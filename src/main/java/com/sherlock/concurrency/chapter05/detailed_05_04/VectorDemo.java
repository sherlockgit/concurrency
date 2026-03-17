package com.sherlock.concurrency.chapter05.detailed_05_04;

import java.util.Vector;

/**
 * 带有客户端加锁的迭代
 * * *
 * 在调用 size和相应的get之间，Vector 的长度可能会发生变化，这种风险在对Vector 中的元素进行迭代时仍然会出现\
 *
 * * *
 * 我们可以通过在客户端加锁来解决不可靠迭代的问题，但要牺牲一些伸缩性。通过在迭代期间持有Vector 的锁，
 * * 可以防止其他线程在迭代期间修改Vector，如程所示。然而，这同样会导致其他线程在迭代期间无法访问它，因此降低了并发性。
 *
 */
public class VectorDemo {

    public void doSomething(Vector list){
        synchronized (list) {
            for (int i = 0; i < list.size(); i++) {
                list.get(i);
            }
        }
    }

}
