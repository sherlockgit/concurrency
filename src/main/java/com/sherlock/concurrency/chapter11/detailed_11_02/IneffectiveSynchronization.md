# 11.2 没有效果的同步

这是《Java 并发编程实战》中的 11.2。

官方清单是一个片段，主题是：

```text
Synchronization that has no effect.
```

也就是“看起来用了 synchronized，但实际上没有起到同步保护作用”。

## 核心问题

同步要有效，前提是多个线程必须竞争同一把锁。

如果每个线程、每次方法调用都创建一把新锁，然后在这把新锁上同步：

```java
public void increment() {
    Object lock = new Object();
    synchronized (lock) {
        count++;
    }
}
```

这段代码没有真正保护 `count`。

原因是：

```text
每次调用 increment 都会 new 一个新的 lock；
不同线程拿到的是不同锁；
不同锁之间互不排斥；
所以多个线程仍然可以同时执行 count++。
```

## 正确理解

`synchronized` 的关键不是“代码块写了 synchronized”，而是：

```text
所有访问同一份共享状态的线程，是否都使用同一把锁。
```

如果共享状态是 `count`，那么保护它的锁必须也是共享的，例如：

```java
private final Object lock = new Object();

public void increment() {
    synchronized (lock) {
        count++;
    }
}
```

或者直接使用对象锁：

```java
public synchronized void increment() {
    count++;
}
```

## 和性能/可伸缩性的关系

11.2 放在“性能与可伸缩性”这一章，是因为它说明：

```text
同步不是越少越好，也不是写了 synchronized 就一定有效。
```

错误的同步可能同时带来两个问题：

1. 没有提供线程安全性；
2. 让读代码的人误以为这里已经受保护，从而隐藏 bug。

真正应该关注的是：

```text
共享状态是什么；
保护它的锁是哪一把；
所有访问路径是否都遵守同一套同步策略。
```
