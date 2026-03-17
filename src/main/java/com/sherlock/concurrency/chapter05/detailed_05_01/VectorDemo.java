package com.sherlock.concurrency.chapter05.detailed_05_01;

import java.util.Vector;

/**
 * Vector 上可能导致混乱结果的复合操作
 * * *
 * 这些方法看似没有任何问题，从某种程度上来看也确实如此--无论多少个线程同时调用它们，
 * 也不破坏Vector。但从这些方法的调用者角度来看，情况就不同了。如果线程A在包含10个元素的Vector上调用getLast，
 * 同时线程B在同一个Vector上调用deleteLast，getLast 将抛出 ArrayIndexOutOfBoundsException 异常。
 * 在调用 size与调用getLast 这两个操作之间，Vector 变小了，因此在调用size时得到的索引值将不再有效。
 * 这种情况很好地遵循了Vector的规范--如果请求一个不存在的元素，那么将抛出一个异常。
 * 但这并不是getLast的调用者所希望得到的结果(即使在并发修改的情况下也不希望看到)，除非Vector 从一开始就是空的。
 */
public class VectorDemo {

    public static Object getLast(Vector list){
        int lastIndex = list.size() - 1;
        return list.get(lastIndex);
    }

    public static Object deleteLast(Vector list){
        int lastIndex = list.size() - 1;
        return list.remove(lastIndex);
    }

}
