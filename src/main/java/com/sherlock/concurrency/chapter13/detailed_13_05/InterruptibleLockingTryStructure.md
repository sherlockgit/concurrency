# 可中断锁获取为什么需要两个 try

## 问题

如何理解书中这句话：

> 可中断的锁获取操作的标准结构比普通的锁获取操作略微复杂一些，因为需要两个 try 块。如果在可中断的锁获取操作中抛出了 InterruptedException，那么可以使用标准的 try-finally 加锁模式。

## 核心意思

这句话的重点是：

**`lockInterruptibly()` 可能在还没有拿到锁时就抛出 `InterruptedException`，所以不能直接把它和普通 `lock()` 写成完全一样的结构。**

普通 `lock()` 的标准写法是：

```java
lock.lock();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

这个结构成立的前提是：

```text
lock.lock() 正常返回后，当前线程一定已经拿到锁。
```

所以 `finally` 中执行 `unlock()` 是安全的。

## lockInterruptibly 的特殊点

`lockInterruptibly()` 的语义是：

```java
lock.lockInterruptibly();
```

如果锁空闲，它会获取锁并返回。

如果锁被其他线程持有，当前线程会等待。

如果等待期间当前线程被中断，它会抛出：

```java
InterruptedException
```

关键点是：

```text
抛出 InterruptedException 时，当前线程可能根本还没有拿到锁。
```

因此不能无条件执行 `unlock()`。

## 错误写法

下面这种写法是危险的：

```java
try {
    lock.lockInterruptibly();
    // 临界区
} finally {
    lock.unlock();
}
```

问题在于：

```text
如果 lockInterruptibly() 在等待锁时被中断，
它会抛出 InterruptedException。
此时当前线程还没有获得锁。
但 finally 仍然会执行 unlock()。
```

于是会出现：

```text
当前线程没有持有锁，却调用 unlock()
```

这通常会导致：

```java
IllegalMonitorStateException
```

所以这不是可中断锁获取的安全结构。

## 标准结构：两个 try

安全写法通常是两个 `try`：

```java
try {
    lock.lockInterruptibly();

    try {
        // 临界区
    } finally {
        lock.unlock();
    }
} catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    // 或者继续向上抛出
}
```

为什么需要两个 `try`？

外层 `try` 负责处理：

```text
等待锁期间可能抛出的 InterruptedException
```

内层 `try-finally` 负责保证：

```text
只有在 lockInterruptibly() 成功返回，也就是确实拿到锁之后，才会执行 unlock()
```

## 两种执行路径

路径 1：成功拿到锁。

```text
调用 lockInterruptibly()
        ↓
成功获得锁
        ↓
进入内层 try
        ↓
执行临界区
        ↓
finally 中 unlock
```

路径 2：等待锁时被中断。

```text
调用 lockInterruptibly()
        ↓
等待锁
        ↓
线程被 interrupt
        ↓
抛出 InterruptedException
        ↓
不会进入内层 try-finally
        ↓
不会调用 unlock
        ↓
外层 catch 处理中断
```

这个结构保证：

```text
拿到锁才释放锁；
没拿到锁就不释放锁。
```

## 书中括号里的话如何理解

书中说：

> 如果在可中断的锁获取操作中抛出了 InterruptedException，那么可以使用标准的 try-finally 加锁模式。

更准确地理解是：

**一旦 `lockInterruptibly()` 成功返回，后面的临界区就可以使用普通的 `try-finally` 模式。**

也就是：

```java
lock.lockInterruptibly(); // 成功返回说明已经拿到锁
try {
    // 临界区
} finally {
    lock.unlock();
}
```

但由于 `lockInterruptibly()` 本身会抛出 `InterruptedException`，所以整体外层通常还需要一个 `try/catch`，或者让方法继续 `throws InterruptedException`。

例如书中 13.5 的方式：

```java
public boolean sendOnSharedLine(String message)
        throws InterruptedException {
    lock.lockInterruptibly();
    try {
        return cancellableSendOnSharedLine(message);
    } finally {
        lock.unlock();
    }
}
```

这里没有显式写外层 `catch`，因为方法直接把 `InterruptedException` 抛给调用者。

它仍然是安全的，因为：

```text
如果 lockInterruptibly() 抛出 InterruptedException，
后面的 try-finally 根本不会执行。
```

## 和普通 lock 的区别

普通 `lock()`：

```java
lock.lock();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

可中断 `lockInterruptibly()`：

```java
lock.lockInterruptibly();
try {
    // 临界区
} finally {
    lock.unlock();
}
```

表面上很像。

但区别在于：

```text
lock() 等待锁时不会因为中断而抛出 InterruptedException。
lockInterruptibly() 等待锁时可能因为中断而抛出 InterruptedException。
```

所以如果你要在当前方法内捕获中断，就需要外层 `try/catch`。

## 一句话总结

普通 `lock()` 成功返回后一定拿到了锁，所以可以直接用 `try-finally` 释放。

`lockInterruptibly()` 可能在没拿到锁时就被中断并抛异常，所以必须确保：

```text
只有成功拿到锁之后，才执行 unlock。
```

这就是可中断锁获取通常需要两个 `try` 的原因。
