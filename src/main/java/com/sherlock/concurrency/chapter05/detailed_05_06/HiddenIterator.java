package com.sherlock.concurrency.chapter05.detailed_05_06;

import java.util.*;

/**
 * 隐藏在字符串连接中的迭代操作(不要这么做)
 * * *
 * 虽然加锁可以防止迭代器抛出 ConcurrentModificationException，但你必须要记住在所有对共享容器进行迭代的地方都需要加锁。
 * * 实际情况要更加复杂，因为在某些情况下，迭代器会隐藏起来，如程序中的HiddenIterator 所示。在HiddenIterator 中没有显式的迭代操作，
 * * 但在粗体标出的代码中将执行迭代操作。编译器将字符串的连接操作转换为调用 StringBuilder.append(Object)，
 * * 而这个方法又会调用容器的toString方法，标准容器的toString方法将迭代容器，并在每个元素上调用toString来生成容器内容的格式化表示。
 *
 *
 * 容器的hashCode和equals等方法也会间接地执行迭代操作，当容器作为另一个容器的元
 * 素或键值时，就会出现这种情况。同样，containsAll、removeAll和retainAll等方法，以及把
 * 容器作为参数的构造函数，都会对容器进行迭代。所有这些间接的迭代操作都可能抛出ConcurrentModificationException.
 */
public class HiddenIterator {
    private final Set<Integer> set = new HashSet<Integer>();

    public synchronized void add(Integer i) {
        set.add(i);
    }

    public synchronized void remove(Integer i) {
        set.remove(i);
    }

    /*
    * addTenThings 方法中的 System.out.println 语句将 set 与字符串连接，
    * * 这会调用 StringBuilder.append(Object)，进而调用 set.toString()。
    * * HashSet 的 toString() 方法会迭代集合中的所有元素。
    *
    * 如果在迭代过程中有其他线程修改了 set（例如调用 remove），就可能抛出 ConcurrentModificationException。
    *
    * 即使没有其他线程，如果在单线程中直接通过 set.remove 而不是迭代器的 remove 删除元素，也可能在后续迭代中抛出该异常。
    * * * * * */
    public void addTenThings() {
        Random r = new Random();
        for (int i = 0; i < 10; i++) {
            add(r.nextInt());
        }
        // 这里的字符串连接操作会隐式调用 set.toString()，从而迭代 set
        System.out.println("DEBUG: added ten elements to " + set);
    }
}
