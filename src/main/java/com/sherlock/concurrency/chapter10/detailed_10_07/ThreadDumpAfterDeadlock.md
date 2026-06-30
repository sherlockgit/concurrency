# 10.7 死锁后的线程转储片段

这是《Java 并发编程实战》中的 10.7。

官方清单里的 10.7 不是一段 Java 源码，而是一段死锁发生后的线程转储片段。它的作用是让你学会从线程 dump 中判断：

1. 哪些线程死锁了；
2. 每个线程当前持有什么锁；
3. 每个线程正在等待什么锁；
4. 这些等待关系如何组成一个环。

## 怎么看线程转储

线程转储里和死锁最相关的信息通常有这几类：

```text
"left-then-right" daemon prio=...
   java.lang.Thread.State: BLOCKED (on object monitor)
   - waiting to lock <0x...> (a java.lang.Object)
   - locked <0x...> (a java.lang.Object)
```

含义如下：

```text
Thread.State: BLOCKED
```

表示这个线程正在等待进入某个 `synchronized` 临界区。

```text
waiting to lock <0x...>
```

表示它想获取哪把锁，但还没有拿到。

```text
locked <0x...>
```

表示它当前已经持有哪些锁。

如果你看到：

```text
线程 A：已经持有 lock1，正在等待 lock2
线程 B：已经持有 lock2，正在等待 lock1
```

这就是一个典型的两线程死锁。

## 和 10.1 的关系

10.1 的 `LeftRightDeadlock` 中：

```text
leftRight:  先拿 left，再拿 right
rightLeft:  先拿 right，再拿 left
```

如果两个线程分别卡在第二把锁上，线程转储会显示类似关系：

```text
left-then-right  持有 left，等待 right
right-then-left  持有 right，等待 left
```

这就是线程转储要揭示的核心：不是只告诉你“有死锁”，而是告诉你死锁环里的每个线程和锁对象。

## 本地演示

同目录下的 `ThreadDumpAfterDeadlockDemo.java` 会稳定制造一个锁顺序死锁，并打印 `ThreadInfo` 信息。

运行后重点看：

1. `Thread state = BLOCKED`
2. `Lock owner`
3. `Locked monitors`
4. 两个线程互相等待对方持有的锁

这和真实线上用 `jstack`、`jcmd Thread.print` 或 IDE 线程 dump 看到的信息是同一类信息。
