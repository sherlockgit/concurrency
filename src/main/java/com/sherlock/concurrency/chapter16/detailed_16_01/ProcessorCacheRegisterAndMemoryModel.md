# 处理器、缓存、寄存器和内存模型

## 问题

书中说内存模型要回答这个问题：

```text
在什么条件下，读取 aVariable 的线程将看到这个值为 3？
```

这句话背后的核心是：

```text
线程 A 写入 aVariable = 3 之后，线程 B 不一定马上能看到 3。
```

如果没有正确同步，线程 B 可能看到旧值。

## 硬件层次

可以先把硬件结构简化成下面这样：

```text
CPU 核心
  -> 寄存器
  -> L1/L2 本地缓存
  -> L3 共享缓存
  -> 主内存 RAM
```

越靠近 CPU，速度越快，但容量越小。

越靠近主内存，容量越大，但速度越慢。

## 寄存器的作用

寄存器是 CPU 真正执行计算时直接使用的位置。

例如：

```java
a = a + 1;
```

CPU 通常不会直接在主内存中做加法，而是类似这样：

```text
1. 从内存或缓存读取 a；
2. 把 a 放进寄存器；
3. 在寄存器里执行 +1；
4. 再把结果写回缓存或内存。
```

所以寄存器的作用是：

```text
保存 CPU 当前正在计算的数据。
```

它速度最快，但每个 CPU 核心都有自己的寄存器。

## 处理器本地缓存的作用

主内存很慢，如果 CPU 每次读写变量都直接访问主内存，性能会很差。

所以 CPU 会把常用数据缓存到本地缓存中，例如 L1/L2 缓存。

处理器本地缓存的作用是：

```text
缓存主内存中的数据副本，减少访问主内存的次数。
```

问题是：多个 CPU 核心可能缓存同一个变量的不同副本。

例如：

```text
主内存中 a = 0

CPU1 本地缓存中 a = 0
CPU2 本地缓存中 a = 0

线程 A 在 CPU1 上执行：a = 3
线程 B 在 CPU2 上执行：读取 a
```

线程 A 写入 `a = 3` 后，这个值可能先进入 CPU1 的缓存或写缓冲区。

此时 CPU2 可能仍然从自己的缓存里读到旧值 `0`。

## 主内存的作用

主内存可以理解为所有线程共享数据最终所在的位置。

Java 中的对象字段、数组元素等共享变量，最终都位于主内存中。

但要注意：

```text
最终在主内存中，不等于每次读写都直接访问主内存。
```

CPU 为了性能，会通过寄存器和缓存来读写数据。

## 数据流动过程

一个共享变量在硬件层面大致会经历这样的路径：

```text
主内存中的变量
    -> 被加载到 CPU 缓存
    -> 被加载到寄存器
    -> CPU 在寄存器中计算
    -> 写回 CPU 缓存
    -> 再经过缓存一致性协议影响其他 CPU
    -> 最终写回或同步到主内存
```

所以 Java 代码中看起来简单的一句：

```java
aVariable = 3;
```

在底层可能涉及：

- 编译器优化；
- 指令重排序；
- 寄存器读写；
- 缓存读写；
- 缓存一致性协议；
- 内存屏障。

## 为什么其他线程可能看不到最新值

假设有两个变量：

```java
int a = 0;
boolean ready = false;
```

线程 A：

```java
a = 3;
ready = true;
```

线程 B：

```java
if (ready) {
    System.out.println(a);
}
```

你可能以为：

```text
线程 B 只要看到 ready == true，就一定能看到 a == 3。
```

但如果没有同步，这个结论不成立。

线程 B 可能看到：

```text
ready == true
a == 0
```

原因可能是：

- `a = 3` 对线程 B 还不可见；
- `ready = true` 先对线程 B 可见；
- 编译器或处理器对指令做了重排序；
- 线程 B 从自己的缓存中读到了旧的 `a`。

## Java 内存模型要解决什么

Java 内存模型不是直接描述某一种 CPU 的缓存结构。

它要做的是定义一套规则：

```text
在什么条件下，一个线程的写入必须对另一个线程可见。
```

也就是书中说的：

```text
读取 aVariable 的线程，在什么条件下必须看到值 3。
```

这些规则屏蔽了不同 CPU、编译器、缓存实现之间的差异。

## volatile 如何解决可见性问题

可以把 `ready` 改成 `volatile`：

```java
int a = 0;
volatile boolean ready = false;
```

线程 A：

```java
a = 3;
ready = true;
```

线程 B：

```java
if (ready) {
    System.out.println(a);
}
```

如果线程 B 读到了 `ready == true`，那么它一定能看到 `a == 3`。

原因是：

```text
对 volatile 变量的写
happens-before
后续对同一个 volatile 变量的读
```

并且：

```text
volatile 写之前的普通写入，
对读到这个 volatile 写的线程可见。
```

所以：

```text
a = 3
ready = true    // volatile 写

ready == true   // volatile 读
读取 a          // 一定能看到 3
```

## synchronized 和 Lock 的作用

`synchronized` 和 `Lock` 也能建立可见性关系。

例如：

```text
线程 A 释放锁
happens-before
线程 B 后续获取同一把锁
```

也就是说：

```java
synchronized (lock) {
    a = 3;
}
```

另一个线程：

```java
synchronized (lock) {
    System.out.println(a);
}
```

只要两个线程使用的是同一把锁，后一个线程进入同步块后，就能看到前一个线程在同步块中写入的结果。

## 还有哪些同步能保证可见性

除了 `volatile` 和 `synchronized`，这些也能建立可见性保证：

- `Lock.unlock()` happens-before 后续同一把锁的 `Lock.lock()`；
- `Thread.start()` 之前的操作，对新线程可见；
- 一个线程中的操作，在线程结束后对成功 `join()` 的线程可见；
- `CountDownLatch.countDown()` 之前的操作，对成功从 `await()` 返回的线程可见；
- `Future` 任务中的操作，对成功从 `Future.get()` 返回的线程可见；
- `AtomicInteger`、`AtomicReference` 等原子变量的读写具有 volatile 语义。

## 一句话总结

```text
寄存器负责 CPU 当前计算；
处理器本地缓存负责加速访问；
主内存负责保存共享数据；
但缓存和寄存器会导致一个线程不一定马上看到另一个线程的写入。
```

Java 的 `volatile`、`synchronized`、`Lock`、`Atomic` 等同步机制，
就是用来规定：

```text
什么时候一个线程的写入必须对另一个线程可见。
```

