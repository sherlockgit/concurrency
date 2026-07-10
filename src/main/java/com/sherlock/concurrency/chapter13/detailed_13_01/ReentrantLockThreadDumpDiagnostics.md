# ReentrantLock 和线程转储中的锁诊断信息

## 问题

如何理解书中这段话：

> 在 Java 5.0 中，内置锁与 ReentrantLock 相比还有另一个优点：在线程转储中能给出在哪些调用帧中获得了哪些锁，并能够检测和识别发生死锁的线程。JVM 并不知道哪些线程持有 ReentrantLock，因此在调试使用 ReentrantLock 的线程的问题时，将起不到帮助作用。Java 6 解决了这个问题，它提供了一个管理和调试接口，锁可以通过该接口进行注册，从而与 ReentrantLock 相关的加锁信息就能出现在锁转储中，并通过其他的管理接口和调试接口来访问。与 synchronized 相比，这些调试消息是一种重要的优势，即便它们大部分都是临时性消息，线程转储中的加锁信息能给很多程序员带来帮助。ReentrantLock 的非块结构特性仍然意味着，获取锁的操作不能与特定的栈帧关联起来，而内置锁却可以。

## 核心意思

这段话在比较：

```text
synchronized 内置锁
ReentrantLock 显式锁
```

在调试、线程转储和死锁诊断方面的差异。

简单说：

**`synchronized` 是 JVM 语言级机制，JVM 天然知道它的锁信息；`ReentrantLock` 是库级机制，早期 JVM 不一定知道它在语义上是一把锁。**

## 什么是线程转储

线程转储通常指 thread dump。

它会展示 JVM 中各个线程当前在做什么，例如：

```text
线程名
线程状态
当前调用栈
正在等待什么锁
已经持有什么锁
是否发生死锁
```

当程序卡住、响应慢、疑似死锁时，线程转储非常有用。

例如你可以看到：

```text
Thread-A 正在等待某把锁
Thread-B 持有这把锁
Thread-B 又在等待 Thread-A 持有的另一把锁
```

这能帮助定位死锁。

## synchronized 为什么容易被 JVM 诊断

`synchronized` 是 Java 内置锁机制。

例如：

```java
synchronized (lock) {
    // 临界区
}
```

它不是普通方法调用，而是 JVM 明确支持的 monitor 机制。

所以 JVM 很清楚：

```text
哪个线程持有哪个 monitor
哪个线程正在等待哪个 monitor
哪个 synchronized 代码块对应哪个调用栈位置
哪些线程形成了 monitor 死锁
```

在线程转储中，通常能看到类似信息：

```text
Thread-1 locked <0x12345>
Thread-2 waiting to lock <0x12345>
```

这就是书中说的：

```text
线程转储中能给出在哪些调用帧中获得了哪些锁
并能够检测和识别发生死锁的线程
```

## Java 5 中 ReentrantLock 的问题

`ReentrantLock` 是普通 Java 类：

```java
Lock lock = new ReentrantLock();

lock.lock();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

在 Java 5 中，JVM 对 `ReentrantLock` 的诊断支持不如 `synchronized`。

JVM 看到的是：

```text
普通对象
普通字段
普通方法调用
```

它不一定能在线程转储中清楚表达：

```text
哪个线程持有这个 ReentrantLock
哪个线程正在等待这个 ReentrantLock
这些等待关系是否构成死锁
```

所以书中说：

```text
JVM 并不知道哪些线程持有 ReentrantLock
```

这里不是说 JVM 完全无法执行 `ReentrantLock`。

而是说：

```text
早期诊断工具不能像识别 synchronized 那样，
清晰识别 ReentrantLock 的拥有者和等待者。
```

这会让排查问题变困难。

## Java 6 做了什么改进

Java 6 改善了这个问题。

它提供了更多管理和调试接口，让基于 `java.util.concurrent` 的同步器能暴露部分锁信息。

`ReentrantLock` 基于 AQS，也就是 `AbstractQueuedSynchronizer`。

Java 6 以后，JVM 和管理接口可以获取更多类似信息：

```text
哪些线程正在等待某些显式锁
某些 ReentrantLock 的拥有者是谁
线程转储中显示 ownable synchronizer 信息
管理接口能访问锁等待信息
```

可以简单理解为：

```text
Java 5：
JVM 很懂 synchronized
JVM 不太懂 ReentrantLock 的诊断信息

Java 6+：
JVM/管理接口也能看到一部分 ReentrantLock/AQS 锁信息
```

这就是书中说的：

```text
锁可以通过该接口进行注册，
从而与 ReentrantLock 相关的加锁信息就能出现在锁转储中。
```

## 为什么这些诊断信息很重要

并发问题通常很难复现。

例如：

```text
程序偶尔卡住
某些请求一直不返回
线程池线程耗尽
CPU 不高但系统无响应
怀疑发生死锁
```

这时线程转储里的锁信息能帮助你判断：

```text
线程到底在等什么
锁被谁持有
是否存在循环等待
是否某个线程长时间占着锁
```

所以即使这些信息只是某一瞬间的快照，也很有价值。

书中说“临时性消息”，意思是：

```text
线程转储只反映 dump 那一刻的状态
下一秒线程状态可能已经改变
```

但这个快照仍然能为排查问题提供关键线索。

## 什么是 ReentrantLock 的非块结构特性

最后一句最关键：

```text
ReentrantLock 的非块结构特性仍然意味着，
获取锁的操作不能与特定的栈帧关联起来，
而内置锁却可以。
```

`synchronized` 是块结构的：

```java
synchronized (lock) {
    // 持有锁
}
```

它的范围非常明确：

```text
进入代码块时获得锁
离开代码块时释放锁
```

所以线程转储容易把锁和具体栈帧关联起来。

也就是说，它能更容易说明：

```text
这个线程是在当前调用栈的这个 synchronized 块中持有锁的。
```

## ReentrantLock 为什么难以关联到具体栈帧

`ReentrantLock` 是显式加锁、显式释放：

```java
lock.lock();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

这看起来像块结构，但语言并不强制它必须这样写。

你可以写成更复杂的形式：

```java
void begin() {
    lock.lock();
}

void end() {
    lock.unlock();
}
```

甚至可能跨方法、跨类、跨复杂控制流。

因此线程转储也许能知道：

```text
线程 A 持有某个 ReentrantLock
```

但不一定能准确知道：

```text
线程 A 是在调用栈的哪一层、哪一个代码块里获得这把锁的
```

这就是“非块结构特性”的代价。

## 对比总结

```text
synchronized：
JVM 原生支持。
线程转储容易显示持有者、等待者、锁所在栈帧。
更容易做死锁诊断。
锁获取和释放与代码块绑定。

ReentrantLock：
库级显式锁。
Java 6 后诊断信息更丰富。
可以在线程转储/管理接口中看到部分锁信息。
但因为不是语言级块结构，仍然不容易把锁获取点和特定栈帧精确绑定。
```

## 一句话总结

`synchronized` 的锁信息天然被 JVM 理解，所以线程转储更容易定位锁和死锁。

`ReentrantLock` 在 Java 6 后也能暴露更多诊断信息，但由于它是非块结构的显式锁，仍然不如 `synchronized` 那样容易和具体调用栈位置绑定。
