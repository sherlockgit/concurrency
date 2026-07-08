# Thread.yield() 的作用和用法

## 问题

`Thread.yield()` 的作用和用法是什么？

## 基本作用

`Thread.yield()` 的作用是：**当前线程主动提示调度器：我现在愿意让出 CPU，你可以考虑让其他线程先运行。**

但它只是一个提示，不是强制命令。

JVM 或操作系统调度器可以选择接受这个提示，也可以完全忽略它。

```java
Thread.yield();
```

可以简单理解为：

```text
当前线程正在运行
        ↓
调用 Thread.yield()
        ↓
告诉调度器：可以考虑切换到别的线程
        ↓
可能切换，也可能当前线程马上继续运行
```

## 它不会释放锁

`Thread.yield()` 不会释放当前线程已经持有的锁。

例如：

```java
synchronized (lock) {
    Thread.yield();
    // 当前线程仍然持有 lock
}
```

即使调度器真的切换到了其他线程，其他线程也拿不到这个 `lock`。

因为锁只有在当前线程退出 `synchronized` 代码块后才会释放。

## 它不会让线程进入阻塞状态

`yield()` 和 `sleep()`、`wait()` 不一样。

```text
Thread.yield()
当前线程提示调度器可以让别人运行；不保证暂停多久；不释放锁。

Thread.sleep(ms)
当前线程进入限时休眠；至少睡眠指定时间附近；不释放锁。

Object.wait()
当前线程进入等待状态；会释放对应对象锁；需要 notify/notifyAll 或超时唤醒。
```

## 在业务代码中不应该依赖 yield

业务代码里通常不应该依赖 `Thread.yield()` 来保证正确性。

原因是它的行为不稳定，受很多因素影响：

```text
JVM 实现
操作系统调度策略
CPU 数量
线程优先级
系统当前负载
```

如果程序只有加了 `Thread.yield()` 才能正确，或者依赖它来避免死循环、避免饥饿，那通常说明设计有问题。

正确的并发控制应该使用：

```text
synchronized
Lock
volatile
AtomicInteger
BlockingQueue
CountDownLatch
Semaphore
Condition
```

这些工具有明确的并发语义，而 `yield()` 没有。

## 在并发测试中的用途

`Thread.yield()` 更适合用在并发测试或演示中。

它可以放在容易发生竞态的位置，用来增加线程交错执行的概率。

例如非线程安全的递增操作：

```java
int snapshot = value;
Thread.yield();
value = snapshot + 1;
```

这段代码故意在“读取旧值”和“写回新值”之间插入 `yield()`。

这样当前线程读到旧值后，可能让出 CPU，让另一个线程也读到同一个旧值。

最终两个线程都写回相同的新值，就会发生丢失更新。

例如：

```text
value = 0

线程 A 读取 value，得到 0
线程 A 调用 yield()
线程 B 读取 value，得到 0
线程 B 写回 1
线程 A 写回 1

两个线程都执行了 +1，但最终 value 只是 1，而不是 2
```

## 和 12.10 示例的关系

`detailed_12_10/YieldInterleavingTest.java` 中的非线程安全计数器就是这个思路：

```java
int snapshot = value;
Thread.yield();
value = snapshot + 1;
```

它用 `yield()` 放大“读”和“写”之间的竞争窗口，让丢失更新更容易出现。

同步版本也故意调用了 `yield()`：

```java
public synchronized void increment() {
    int snapshot = value;
    Thread.yield();
    value = snapshot + 1;
}
```

但因为整个方法由 `synchronized` 保护，其他线程不能同时进入这个方法，所以最终结果仍然正确。

这说明：

```text
yield 可以增加线程交错；
yield 不能替代同步；
正确性必须依赖真正的同步机制。
```

## 一句话总结

`Thread.yield()` 就是当前线程对调度器说：

```text
我愿意先让别人跑一下。
```

但调度器不一定听。

所以它适合用来辅助测试和演示线程交错，不适合用来控制业务代码的正确性。
