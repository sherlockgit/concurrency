package com.sherlock.concurrency.chapter05.detailed_05_03;

import java.util.Vector;

/**
 * 可龍抛出 ArrayIndexOutOfBoundsException 的迭代操作
 * * *
 * 在调用 size和相应的get之间，Vector 的长度可能会发生变化，这种风险在对Vector 中的元素进行迭代时仍然会出现\
 *
 * * *
 * 只这种迭代操作的正确性要依赖于运气，即在调用 size 和get之间没有线程会修改Vector。
 * * 在单线程环境中，这种假设完全成立，但在有其他线程并发地修改Vector时，则可能导致麻烦。
 * * 与getLast一样，如果在对Vector进行迭代时，另一个线程删除了一个元素，并且这两个操作交替执行，
 * * 那么这种迭代方法将抛出ArrayIndexOutOfBoundsException异常。
 *
 * 虽然在程序清单5-3的迭代操作中可能抛出异常，但并不意味着Vector就不是线程安全的。
 * Vector的状态仍然是有效的，而抛出的异常也与其规范保持一致。然而，
 * 像在读取最后一个元素或者迭代等这样的简单操作中抛出异常显然不是人们所期望的。
 *
 */
public class VectorDemo {

    public void doSomething(Vector list){
        for (int i = 0;i<list.size();i++){
            list.get(i);
        }
    }

    public static Object deleteLast(Vector list){
        synchronized (list){
            int lastIndex = list.size() - 1;
            return list.remove(lastIndex);
        }
    }

}
