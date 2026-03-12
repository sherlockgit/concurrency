package com.sherlock.concurrency.chapter04.detailed_04_16;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * 通过组合实现“若没有则添加”
 * * *
 * “ImprovedList 通过自身的内置锁增加了一层额外的加锁。”
 * ImprovedList 的所有公有方法（如 putIfAbsent、clear 等）都使用 synchronized 修饰，即以当前 ImprovedList 实例自身作为锁。
 * * 这样，无论内部持有的 List 对象原本是否线程安全，对它的每次访问都在同一把锁的保护下进行，相当于在原有 List 之上叠加了一层同步控制。
 *
 * “它并不关心底层的 List 是否是线程安全的，即使 List 不是线程安全的或者修改了它的加锁实现，ImprovedList 也会提供一致的加锁机制来实现线程安全性。”
 * 因为 ImprovedList 自己负责同步，所以它完全不依赖底层 List 的线程安全性。即使传入的 List 是非线程安全的（如 ArrayList），或者底层 List
 * * 原本是线程安全的但后来改变了加锁策略，ImprovedList 的同步机制依然能保证通过它进行的操作是线程安全的。这避免了与底层 List 的锁策略耦合。
 *
 * “虽然额外的同步层可能导致轻微的性能损失，但与模拟另一个对象的加锁策略相比，ImprovedList 更为健壮。”
 * 如果试图“模仿”底层 List 的加锁策略（例如，当底层 List 是 Vector 或 Collections.synchronizedList 包装的，
 * * 我们想复用它的锁来实现复合操作的原子性），就需要深入了解底层锁的细节，并且要保证复合操作时正确获取那些锁，
 * * 这很容易出错。而 ImprovedList 简单地在所有方法上加自己的锁，虽然可能带来一点性能开销（因为多了一层锁），
 * * 但实现简单、不易出错，因此更加健壮可靠。
 *
 * “事实上，我们使用了 Java 监视器模式来封装现有的 List，并且只要在类中拥有指向底层 List 的唯一外部引用，就能确保线程安全性。”
 * Java 监视器模式是指将一个对象（这里是 ImprovedList 实例）作为监视器，所有对共享状态的访问都通过该监视器同步。
 * * 这里的关键前提是：包装后的底层 List 对象只能通过 ImprovedList 实例来访问，即外部代码不能再持有原始 List 的引用并直接操作它。
 * * 如果满足这一条件，那么所有对 List 的访问都经过 ImprovedList 的同步方法，线程安全性就能得到保证。
 */
@ThreadSafe
public class ImprovedList<T> implements List<T> {
    private final List<T> list;

    public ImprovedList(List<T> list) { this.list = list; }

    public synchronized boolean putIfAbsent(T x) {
        boolean contains = list.contains(x);
        if (contains)
            list.add(x);
        return !contains;
    }

    @Override
    public synchronized int size() {
        return 0;
    }

    @Override
    public synchronized boolean isEmpty() {
        return false;
    }

    @Override
    public synchronized boolean contains(Object o) {
        return false;
    }

    @Override
    public synchronized Iterator<T> iterator() {
        return null;
    }

    @Override
    public synchronized Object[] toArray() {
        return new Object[0];
    }

    @Override
    public synchronized <T1> T1[] toArray(T1[] a) {
        return null;
    }

    @Override
    public synchronized boolean add(T t) {
        return false;
    }

    @Override
    public synchronized boolean remove(Object o) {
        return false;
    }

    @Override
    public synchronized boolean containsAll(Collection<?> c) {
        return false;
    }

    @Override
    public synchronized boolean addAll(Collection<? extends T> c) {
        return false;
    }

    @Override
    public synchronized boolean addAll(int index, Collection<? extends T> c) {
        return false;
    }

    @Override
    public synchronized boolean removeAll(Collection<?> c) {
        return false;
    }

    @Override
    public synchronized boolean retainAll(Collection<?> c) {
        return false;
    }

    public synchronized void clear() { list.clear(); }

    @Override
    public synchronized T get(int index) {
        return null;
    }

    @Override
    public synchronized T set(int index, T element) {
        return null;
    }

    @Override
    public synchronized void add(int index, T element) {

    }

    @Override
    public synchronized T remove(int index) {
        return null;
    }

    @Override
    public synchronized int indexOf(Object o) {
        return 0;
    }

    @Override
    public synchronized int lastIndexOf(Object o) {
        return 0;
    }

    @Override
    public synchronized ListIterator<T> listIterator() {
        return null;
    }

    @Override
    public synchronized ListIterator<T> listIterator(int index) {
        return null;
    }

    @Override
    public synchronized List<T> subList(int fromIndex, int toIndex) {
        return null;
    }
    // ... 按照类似的方式委托 List 的其他方法
}
