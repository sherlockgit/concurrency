package com.sherlock.concurrency.chapter04.detailed_04_13;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Vector;

/**
 * 扩展Vector并增加一个“若没有则添加”方法
 *
 * Java类库包含许多有用的“基础模块”类。通常，我们应该优先选择重用这些现有的类而不是创建新的类:重用能降低开发工作量、
 * * 开发风险(因为现有的类都已经通过测试)以及维护成本。有时候，某个现有的线程安全类能支持我们需要的所有操作，但更多时候，
 * * 现有的类只能支持大部分的操作，此时就需要在不破坏线程安全性的情况下添加一个新的操作。
 *
 *  要添加一个新的原子操作，最安全的方法是修改原始的类，但这通常无法做到，因为你可能无法访问或修改类的源代码。要想修改原始的类，
 *  *  就需要理解代码中的同步策略，这样增加的功能才能与原有的设计保持一致。如果直接将新方法添加到类中，
 *  *  那么意味着实现同步策略的所有代码仍然处于一个源代码文件中，从而更容易理解与维护
 *
 * 另一种方法是扩展这个类，假定在设计这个类时考虑了可扩展性。程序清单中的BetterVector 对 Vector 进行了扩展，
 * * 并添加了一个新方法putIfAbsent。扩展Vector 很简单，但并非所有的类都像Vector那样将状态向子类公开，因此也就不适合采用这种方法。
 *
 *
 * “扩展”方法比直接将代码添加到类中更加脆弱，因为现在的同步策略实现被分布到多个单独维护的源代码文件中。
 * * 如果底层的类改变了同步策略并选择了不同的锁来保护它的状态变量，那么子类会被破坏，
 * * 因为在同步策略改变后它无法再使用正确的锁来控制对基类状态的并发访问。(在Vector 的规范中定义了它的同步策略，因此BetterVector 不存在这个问题。)
 */
@ThreadSafe
public class BetterVector<E> extends Vector<E> {
    public synchronized boolean putIfAbsent(E x){
        boolean absent = !contains(x);
        if (absent) {
            add(x);
        }
        return absent;
    }
}
