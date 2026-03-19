package com.sherlock.concurrency.chapter05.detailed_05_05;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * 通过 Iterator 来迭代 List
 *
 * 为了将问题阐述清楚，我们使用了Vector，虽然这是一个“古老”的容器类。然而，许多“现代”的容器类也并没有消除复合操作中的问题。
 * * 无论在直接迭代还是在Java5.0引人的for-each循环语法中，对容器类进行迭代的标准方式都是使用Iterator。
 * * 然而，如果有其他线程并发地修改容器，那么即使是使用迭代器也无法避免在迭代期间对容器加锁。
 * * 在设计同步容器类的迭代器时并没有考虑到并发修改的问题，并且它们表现出的行为是“及时失败”(fail-fast)的。
 * * 这意味着，当它们发现容器在迭代过程中被修改时，就会抛出一个ConcurrentModificationException 异常
 *
 * 这种“及时失败”的迭代器并不是一种完备的处理机制，而只是“善意地”捕获并发错误，因此只能作为并发问题的预警指示器。
 * * 它们采用的实现方式是，将计数器的变化与容器关联起来:如果在迭代期间计数器被修改，
 * * 那么hasNext或next将抛出ConcurrentModificationException。然而，这种检查是在没有同步的情况下进行的，
 * * 因此可能会看到失效的计数值，而迭代器可能并没有意识到已经发生了修改。这是一种设计上的权衡，从而降低并发修改操作的检测代码对程序性能带来的影响。
 *
 * 程序说明了如何使用for-each循环语法对List容器进行迭代。从内部来看，javac将生成使用Iterator 的代码，
 * * 反复调用hasNext和next来迭代List对象。与迭代 Vector一样，要想避免出现ConcurrentModificationException，就必须在迭代过程持有容器的锁。
 */
public class ForEachDemo {

    List<Integer> list = Collections.synchronizedList(new ArrayList<>());

    public void doSomething(){
        //可能抛出 ConcurrentModificationException
        for (Integer i : list){
            System.out.println(i);
        }
    }

}
