package com.sherlock.concurrency.chapter04.detailed_04_11;

import com.sherlock.concurrency.annoations.ThreadSafe;

/**
 * 线程安全且可变的Point类
 * *
 * SafePoint提供的get方法同时获得x和y的值，并将二者放在一个数组中返回。
 * * 如果为x和y分别提供get方法，那么在获得这两个不同坐标的操作之间，x和y的值发生变化，
 * * 从而导致调用者看到不一致的值:车辆从来没有到达过位置(x，y)。
 * * 通过使用SafePoint，可以构造一个发布其底层可变状态的车辆追踪器，还能确保其线程安全性不被破坏
 */
@ThreadSafe
public class SafePoint {

    /*
    在 SafePoint 类中，拷贝构造函数 public SafePoint(SafePoint p) 需要根据另一个 SafePoint 对象创建一个新的实例，
    并且必须保证新对象看到的是源对象的一个一致性快照，即源对象的 x 和 y 值必须来自同一时刻。如果分别调用 p.getX() 和 p.getY()
    这样的方法（假设存在），则可能出现线程安全问题：在两次调用之间，另一个线程可能修改了源对象的坐标，导致新对象获得的是不同时刻的值，破坏了原子性。

     为了避免这个问题，SafePoint 提供了一个同步的 get() 方法，它一次性返回包含 (x, y) 的数组，从而保证了读取操作的原子性。
     拷贝构造函数需要利用这个快照来初始化新对象。但是，在 Java 的构造函数中，对 this(...) 的调用（即调用另一个构造器）
     必须是构造函数体的第一条语句。这意味着我们不能先调用 p.get() 获取数组，再根据数组元素调用主构造器，因为这样 this(...) 就不在第一行了。*/
    private int x, y;

    private SafePoint(int[] a) {
        this(a[0], a[1]);
    }

    public SafePoint(SafePoint p) { this(p.get()); }

    public SafePoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public synchronized int[] get() {
        return new int[] { x, y };
    }

    public synchronized void set(int x, int y) {
        this.x = x;
        this.y = y;
    }
}
