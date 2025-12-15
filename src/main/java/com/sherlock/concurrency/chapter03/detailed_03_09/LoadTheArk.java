package com.sherlock.concurrency.chapter03.detailed_03_09;

import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 *
 * 基本类型的局部变量与引用变量的线程封闭性
 *
 *
 * 栈封闭是线程封闭的一种特例，在栈封闭中，只能通过局部变量才能访问对象。
 * 正如封装能使得代码更容易维持不变性条件那样，同步变量也能使对象更易于封闭在线程中。
 * 局部变量的固有属性之一就是封闭在执行线程中。它们位于执行线程的栈中，其他线程无法访问这个栈。
 * 栈封闭(也被称为线程内部使用或者线程局部使用，不要与核心类库中的ThreadLocal混淆)比 Ad-hoc 线程封闭更易于维护，也更加健壮。
 *
 * 对于基本类型的局部变量，method方法的numPairs，无论如何都不会破坏栈封闭性。
 * 由于任何方法都无法获得对基本类型的引用，因此Java语言的这种语义就确保了基本类型的局部变量始终封闭在线程内。
 */
public class LoadTheArk {

    public int method(Collectors candidates){
        int numPairs = 0;
        return numPairs;
    }

}
