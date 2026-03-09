package com.sherlock.concurrency.chapter04.detailed_04_09;

import com.sherlock.concurrency.annoations.ThreadSafe;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 通过将线程安全性委托给多个独立的状态变量来实现线程安全
 *
 * 类中定义了两个 CopyOnWriteArrayList 类型的成员变量，分别用于存储键盘监听器（KeyListener）和鼠标监听器（MouseListener）。
 * CopyOnWriteArrayList 是 Java 并发包（java.util.concurrent）中的线程安全集合，它通过“写时复制”机制保证线程安全：
 * 所有修改操作（如 add、remove）都会创建底层数组的新副本，因此读操作不需要加锁，适合读多写少的场景。
 *
 * 每个公开方法（如 addKeyListener、removeMouseListener 等）都只是简单地将调用委托给对应的 CopyOnWriteArrayList 实例。
 * 由于这两个 list 本身是线程安全的，且它们之间没有共享的复合操作（例如一个操作需要同时修改两个 list 或依赖两个 list 的状态），
 * 因此整个 VisualComponent 类的线程安全性完全由这两个线程安全的成员变量来保证。
 *
 * 这种设计模式称为“将线程安全性委托给多个状态变量”。只要这些变量彼此独立（没有不变性条件需要同时满足），委托就能有效工作。
 * 如果有需要同时访问两个变量的复合操作，则需要额外的同步机制（如加锁）来维护线程安全性。
 */
@ThreadSafe
public class VisualComponent {
    private final List<KeyListener> keyListeners
            = new CopyOnWriteArrayList<>();
    private final List<MouseListener> mouseListeners
            = new CopyOnWriteArrayList<MouseListener>();

    public void addKeyListener(KeyListener listener) {
        keyListeners.add(listener);
    }

    public void addMouseListener(MouseListener listener) {
        mouseListeners.add(listener);
    }

    public void removeKeyListener(KeyListener listener) {
        keyListeners.remove(listener);
    }

    public void removeMouseListener(MouseListener listener) {
        mouseListeners.remove(listener);
    }
}
