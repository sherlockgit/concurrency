package com.sherlock.concurrency.chapter05.detailed_05_02;

import java.util.Vector;

/**
 * 在使用客户端加锁的Vector上的复合操作
 * * *
 * 由于同步容器类要遵守同步策略，即支持客户端加锁，因此可能会创建一些新的操作，
 * 只要我们知道应该使用哪一个锁，那么这些新操作就与容器的其他操作一样都是原子操作。
 * 同步容器类通过其自身的锁来保护它的每个方法。通过获得容器类的锁，我们可以使getLast和deleteLast成为原子操作，
 * 并确保Vector的大小在调用size和get之间不会发生变化，
 */
public class VectorDemo {

    public static Object getLast(Vector list){
        synchronized (list){
            int lastIndex = list.size() - 1;
            return list.get(lastIndex);
        }
    }

    public static Object deleteLast(Vector list){
        synchronized (list){
            int lastIndex = list.size() - 1;
            return list.remove(lastIndex);
        }
    }

}
