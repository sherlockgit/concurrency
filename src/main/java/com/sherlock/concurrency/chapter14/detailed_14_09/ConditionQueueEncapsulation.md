# 为什么要封装条件队列

## 问题

如何理解这句话：

```text
通常，我们应该把条件队列封装起来，因而除了使用条件队列的类，就不能在其他地方访问它。
否则，调用者会自以为理解了在等待和通知上使用的协议，并且采用一种违背设计的方式来使用条件队列。
```

## 简单理解

这句话的核心意思是：

```text
条件队列对象是类内部同步协议的一部分，不应该暴露给外部代码。
```

谁能拿到条件队列对象，谁就能对它做这些事情：

```java
synchronized (obj) {
    obj.wait();
}
```

或者：

```java
synchronized (obj) {
    obj.notifyAll();
}
```

如果外部代码也能对同一个条件队列对象调用 `wait`、`notify`、`notifyAll`，就可能破坏类内部设计好的等待和通知协议。

## 结合 BoundedBuffer 理解

在 `14-6` 的 `BoundedBuffer` 中，代码类似这样：

```java
public synchronized void put(V value) throws InterruptedException {
    while (isFull()) {
        wait();
    }

    doInsert(value);
    notifyAll();
}

public synchronized V take() throws InterruptedException {
    while (isEmpty()) {
        wait();
    }

    V value = doExtract();
    notifyAll();
    return value;
}
```

这里的：

```java
wait();
notifyAll();
```

其实等价于：

```java
this.wait();
this.notifyAll();
```

也就是说，`BoundedBuffer` 使用 `this` 同时充当两个角色：

```text
1. 锁对象
2. 条件队列对象
```

这种写法很常见，但有一个问题：

```text
this 会被外部代码拿到。
```

例如：

```java
BoundedBuffer<Integer> buffer = new BoundedBuffer<Integer>(10);
```

外部代码已经拿到了 `buffer`，也就是拿到了 `this`。

## 外部代码可能错误使用条件队列

如果外部代码这样写：

```java
synchronized (buffer) {
    buffer.wait();
}
```

问题是：

```text
BoundedBuffer 根本不知道这个外部线程在等什么条件。
```

`BoundedBuffer` 自己设计的等待条件只有两个：

| 操作 | 等待条件 |
| --- | --- |
| `put` | 缓冲区未满 |
| `take` | 缓冲区非空 |

但是外部线程可能在等一个完全无关的条件。

然后 `put` 或 `take` 中的：

```java
notifyAll();
```

可能会把这个外部线程唤醒。

这个外部线程可能误以为自己等待的条件已经满足，但实际上 `BoundedBuffer` 并没有也不可能维护它的条件。

## 外部代码也可能乱发通知

外部代码还可能这样写：

```java
synchronized (buffer) {
    buffer.notifyAll();
}
```

这会导致等待在 `buffer` 条件队列上的线程被提前唤醒。

如果 `BoundedBuffer` 内部写得正确，使用的是：

```java
while (条件不满足) {
    wait();
}
```

那么提前唤醒通常不会破坏正确性，因为线程醒来后会重新检查条件。

但是它仍然会带来问题：

```text
1. 造成不必要的唤醒和锁竞争。
2. 干扰类内部的等待/通知协议。
3. 如果某些代码错误地使用 if 而不是 while，就可能直接出 bug。
```

所以，外部代码不应该参与条件队列的通知。

## 为什么说调用者会“自以为理解协议”

条件队列不是简单的“睡眠和叫醒工具”。

它背后有完整的协议：

```text
1. 哪些共享状态由哪把锁保护。
2. 哪些条件谓词对应哪些等待线程。
3. 状态发生什么变化时需要通知。
4. 被唤醒后必须重新检查哪些条件。
```

如果外部调用者直接对条件队列对象调用 `wait` 或 `notifyAll`，它其实是在绕过类的封装，私自参与同步协议。

但外部调用者通常并不知道类内部完整的协议，因此很容易错误使用。

## 更好的设计：使用私有锁对象

为了封装条件队列，可以不要使用 `this` 作为锁和条件队列，而是使用一个私有对象：

```java
public class PrivateLockBoundedBuffer<V> {
    private final Object lock = new Object();

    public void put(V value) throws InterruptedException {
        synchronized (lock) {
            while (isFull()) {
                lock.wait();
            }

            doInsert(value);
            lock.notifyAll();
        }
    }

    public V take() throws InterruptedException {
        synchronized (lock) {
            while (isEmpty()) {
                lock.wait();
            }

            V value = doExtract();
            lock.notifyAll();
            return value;
        }
    }
}
```

这样外部代码只能拿到：

```java
PrivateLockBoundedBuffer<Integer> buffer
```

但拿不到：

```java
lock
```

因此外部代码不能这样做：

```java
synchronized (lock) {
    lock.wait();
}
```

也不能这样做：

```java
synchronized (lock) {
    lock.notifyAll();
}
```

这就把条件队列封装在类内部了。

## 代价：不再支持客户端加锁

书中也提到，使用私有锁对象之后，新的 `BoundedBuffer` 不再支持任何形式的客户端加锁。

也就是说，外部代码不能再这样做：

```java
synchronized (buffer) {
    // 试图把多个 buffer 操作组合成一个原子操作
}
```

但这通常不是坏事。

因为如果一个类的线程安全依赖复杂的条件队列协议，那么外部代码本来就不应该随意参与它的加锁和通知。

更好的做法是：

```text
把需要的复合操作设计成类自己的方法。
```

而不是让外部代码拿着锁来拼装同步逻辑。

## 和 Java 监视器模式的关系

常见的 Java 监视器模式通常写成：

```java
public synchronized void method() {
    ...
}
```

这种写法使用的是对象自己的内置锁，也就是 `this`。

它的优点是简单。

但缺点是：

```text
this 是公开可访问的。
```

所以在条件队列比较复杂、等待/通知协议比较严格的类中，使用私有锁对象通常更稳妥。

## 一句话总结

```text
条件队列是类内部等待/通知协议的一部分。
外部代码不应该能直接 wait 或 notify 它。
如果外部代码能访问条件队列对象，就可能破坏同步协议。
所以最好用 private lock 把条件队列封装起来。
```
