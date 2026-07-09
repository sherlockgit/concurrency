# synchronized 和 Lock 的区别

## 问题

如何理解书中这句话：

> 为什么要创建一种与内置锁如此相似的新加锁机制？在大多数情况下，内置锁都能很好地工作，但在功能上存在一些局限性，例如，无法中断一个正在等待获取锁的线程，或者无法在请求获取一个锁时无限地等待下去。内置锁必须在获取该锁的代码块中释放，这就简化了编码工作，并且与异常处理操作实现了很好的交互。但却无法实现非阻塞结构的加锁规则。这些都是建议使用 synchronized 的原因，但在某些情况下，一种更灵活的加锁机制通常能提供更好的活跃性或性能。

## 核心意思

这段话在讲：

**`synchronized` 简单、安全，适合大多数场景；但它的控制能力有限，所以 Java 又提供了 `Lock` 这种更灵活的显式锁。**

`synchronized` 是 Java 的内置锁机制。

`Lock` 是 `java.util.concurrent.locks` 包提供的显式锁接口，最常用的实现是 `ReentrantLock`。

两者都能实现互斥访问：

```text
同一时刻只允许一个线程进入临界区
```

但 `Lock` 提供了更多控制能力。

## synchronized 的基本用法

```java
synchronized (lock) {
    // 临界区
}
```

它的特点是：

```text
进入 synchronized 代码块时自动获取锁
离开 synchronized 代码块时自动释放锁
如果临界区抛出异常，也会自动释放锁
```

所以 `synchronized` 的优点是：

```text
语法简单
不容易忘记释放锁
异常安全
代码结构清晰
适合大多数普通互斥场景
```

## Lock 的基本用法

```java
Lock lock = new ReentrantLock();

lock.lock();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

`Lock` 是显式加锁、显式释放锁。

因此必须注意：

```text
unlock 必须放在 finally 中
```

否则如果临界区抛出异常，锁可能永远无法释放。

错误写法：

```java
lock.lock();
doSomething();
lock.unlock();
```

如果 `doSomething()` 抛异常，`unlock()` 就不会执行。

正确写法：

```java
lock.lock();
try {
    doSomething();
} finally {
    lock.unlock();
}
```

## synchronized 的限制 1：等待锁时不能响应中断

如果一个线程正在等待进入 `synchronized` 代码块：

```java
synchronized (lock) {
    // 临界区
}
```

此时其他线程调用：

```java
thread.interrupt();
```

等待锁的线程不会因为中断就停止等待。

它仍然会继续等锁。

也就是说，`synchronized` 等锁过程不能被取消。

`Lock` 可以使用：

```java
lock.lockInterruptibly();
```

如果线程在等待锁时被中断，会抛出：

```java
InterruptedException
```

这让线程有机会退出等待。

适合这些场景：

```text
任务取消
服务关闭
避免线程长时间卡住
需要响应中断的并发任务
```

## synchronized 的限制 2：不能限时等待锁

`synchronized` 获取锁的方式是：

```text
拿到锁就进入
拿不到锁就一直等
```

它不能表达：

```text
最多等 100ms，拿不到就放弃
```

`Lock` 可以：

```java
if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {
    try {
        // 拿到锁
    } finally {
        lock.unlock();
    }
} else {
    // 超时，走降级逻辑
}
```

这种方式适合：

```text
避免无限等待
提高响应性
实现超时控制
避免某些死锁场景
```

## synchronized 的限制 3：不能立即尝试后返回

`synchronized` 没有“试试看”的能力。

它不能表达：

```text
如果锁空闲就拿
如果锁忙就马上返回
```

`Lock` 可以：

```java
if (lock.tryLock()) {
    try {
        // 拿到锁
    } finally {
        lock.unlock();
    }
} else {
    // 没拿到锁，不阻塞，做别的事
}
```

这叫非阻塞式获取锁。

适合：

```text
尝试执行
失败后降级
避免死锁
减少等待
提高系统活跃性
```

## synchronized 的限制 4：加锁和释放锁绑定在同一个代码块

`synchronized` 的锁释放和代码块绑定：

```java
synchronized (lock) {
    // 出了这个代码块就释放锁
}
```

这很安全，也很简单。

但它不够灵活。

有些高级同步结构需要：

```text
在一个方法中加锁，在另一个方法中释放
尝试获取多把锁
获取不到某把锁时释放已获得的锁
实现更复杂的非阻塞控制流程
```

这些场景中，`Lock` 更容易表达。

当然，越灵活也越容易写错。

## 为什么 synchronized 仍然值得优先考虑

书中说：

```text
这些都是建议使用 synchronized 的原因
```

这里的“这些”主要指：

```text
自动释放锁
异常安全
语法简单
结构清楚
不容易发生锁泄漏
```

如果只是普通互斥访问，`synchronized` 通常更直接。

例如：

```java
public synchronized void increment() {
    count++;
}
```

或者：

```java
synchronized (lock) {
    count++;
}
```

没有必要为了“看起来高级”而换成 `Lock`。

## 为什么某些情况下 Lock 更好

书中说：

```text
一种更灵活的加锁机制通常能提供更好的活跃性或性能
```

这里的活跃性可以理解为：

```text
线程不要无限期卡住
任务能响应取消
系统能从锁竞争中恢复
不要因为等待锁导致整体停滞
```

`Lock` 能提供更好活跃性的原因是：

```text
lockInterruptibly 可以响应中断
tryLock 可以拿不到锁就放弃
tryLock(timeout) 可以限制等待时间
可以用 tryLock 避免锁顺序死锁
```

这些能力是 `synchronized` 没有的。

## 对比总结

```text
synchronized：
简单、安全、自动释放锁、异常安全。
缺点是等待锁时不能中断，不能超时，不能尝试失败后立即返回。

Lock：
更灵活，支持可中断、可超时、可尝试的加锁方式。
缺点是必须手动 unlock，写错容易造成锁泄漏。
```

## 和 13.1 示例的关系

`LockInterfaceDemo.java` 展示了这些能力：

```java
lock.lock();
try {
    value++;
} finally {
    lock.unlock();
}
```

普通加锁。

```java
lock.tryLock();
```

尝试加锁，拿不到立即返回。

```java
lock.tryLock(timeout, unit);
```

限时等待锁。

```java
lock.lockInterruptibly();
```

等待锁时可以响应中断。

这些就是 `Lock` 相比 `synchronized` 更灵活的地方。

## 一句话总结

`synchronized` 是简单、安全的默认选择。

`Lock` 是更灵活但也更容易写错的显式锁。

当你需要可中断、可超时、可尝试获取锁，或者需要更复杂的加锁规则时，才更适合使用 `Lock`。
