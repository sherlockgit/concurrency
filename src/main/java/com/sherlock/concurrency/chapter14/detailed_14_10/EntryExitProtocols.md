# 入口协议和出口协议

## 问题

如何理解这段话：

```text
Wellings 通过“入口协议和出口协议”来描述 wait 和 notify 方法的正确使用。
对于每个依赖状态的操作，以及每个修改其他操作依赖状态的操作，都应该定义一个入口协议和出口协议。
入口协议就是该操作的条件谓词，出口协议则包括检查该操作修改的所有状态变量，
并确认它们是否使某个其他的条件谓词变为真，如果是，则通知相关的条件队列。
```

以及：

```text
在 AbstractQueuedSynchronizer 中使用出口协议。
这个类并不是由同步器类执行自己的通知，
而是要求同步器方法返回一个值来表示该类的操作是否已经解除一个或多个等待线程的阻塞。
这种明确的 API 调用需求使得更难忘记在某些状态转换发生时进行通知。
```

## 简单理解

这段话是在说：

```text
写 wait/notify 时，不要凭感觉通知。
应该把每个状态依赖操作拆成两套协议：入口协议和出口协议。
```

也就是：

```text
入口协议：这个操作什么时候可以执行？
出口协议：这个操作执行完以后，可能让哪些等待线程继续执行？
```

## 什么是入口协议

入口协议就是：

```text
一个操作开始执行前必须满足的条件。
```

在并发代码中，这个条件通常叫：

```text
条件谓词
```

例如有界缓冲区 `BoundedBuffer`：

```java
public synchronized void put(V v) throws InterruptedException {
    while (isFull()) {
        wait();
    }

    doInsert(v);
    notifyAll();
}
```

`put` 的入口协议是：

```text
缓冲区不能满。
```

写成条件谓词就是：

```text
not-full = !isFull()
```

所以 `put` 入口处要写：

```java
while (isFull()) {
    wait();
}
```

意思是：

```text
只要入口条件还不满足，就不能继续执行 put。
```

## 什么是出口协议

出口协议就是：

```text
一个操作修改状态之后，需要检查这次状态变化是否可能让其他线程等待的条件成立。
如果可能成立，就要通知相关条件队列。
```

还是看 `put`。

`put` 会执行：

```java
doInsert(v);
```

这会改变缓冲区状态：

```text
元素数量 +1
```

这个状态变化可能导致：

```text
缓冲区从“空”变成“非空”。
```

而 `take` 等待的条件正是：

```text
缓冲区非空。
```

所以 `put` 的出口协议是：

```text
put 后，如果缓冲区可能变成非空，那么等待 take 的线程可能可以继续执行，需要通知它们。
```

因此代码中有：

```java
notifyAll();
```

## take 的入口协议和出口协议

`take` 的代码：

```java
public synchronized V take() throws InterruptedException {
    while (isEmpty()) {
        wait();
    }

    V v = doExtract();
    notifyAll();
    return v;
}
```

`take` 的入口协议是：

```text
缓冲区不能空。
```

写成条件谓词：

```text
not-empty = !isEmpty()
```

所以入口处检查：

```java
while (isEmpty()) {
    wait();
}
```

`take` 的出口协议是：

```text
take 取走元素后，缓冲区可能从“满”变成“非满”。
等待 put 的生产者可能可以继续执行。
```

所以状态修改后要通知：

```java
notifyAll();
```

## 为什么不是无脑 notifyAll

出口协议的重点是：

```text
不是所有状态修改后都无脑通知。
而是要分析：这次状态变化有没有可能让某个等待条件从 false 变成 true。
```

例如 `14-8` 的条件通知：

```java
boolean wasEmpty = isEmpty();
doInsert(value);

if (wasEmpty) {
    notifyAll();
}
```

这就是更精细的出口协议。

意思是：

```text
只有 put 前缓冲区为空时，这次 put 才会让“非空”条件从 false 变成 true。
这时才需要通知等待 take 的线程。
```

如果 put 前缓冲区已经非空，那么这次 put 虽然修改了状态，但并没有让“非空”这个条件刚刚变成真，所以可以不通知消费者。

## AQS 中的出口协议

书中提到 `AbstractQueuedSynchronizer`，简称 AQS。

AQS 是 `java.util.concurrent` 中很多同步器的基础，例如：

```text
ReentrantLock
Semaphore
CountDownLatch
ReentrantReadWriteLock
```

AQS 的思想是：

```text
同步器子类不直接负责阻塞和唤醒线程。
子类只负责说明：获取是否成功，释放是否可能唤醒等待线程。
```

## 以锁为例

获取锁时，入口协议是：

```text
锁当前是否可获取？
```

如果锁不可获取，AQS 会把线程放入等待队列并阻塞。

释放锁时，出口协议是：

```text
释放锁之后，是否可能让等待队列中的线程继续执行？
```

AQS 不要求同步器子类自己写：

```java
notifyAll();
```

而是要求类似 `tryRelease` 的方法返回一个结果。

这个返回值告诉 AQS：

```text
这次释放是否可能解除一个或多个等待线程的阻塞。
```

如果返回 `true`，AQS 就知道：

```text
状态变化可能让等待线程继续执行，需要唤醒后继线程。
```

## 为什么这样更不容易忘记通知

手写 `wait/notify` 时，常见错误是：

```text
状态改了，但忘记 notify。
```

AQS 的设计把这个问题变成了 API 协议：

```text
你释放资源时，必须返回一个值。
这个值明确表示是否需要唤醒等待线程。
```

这样，同步器实现者更难“忘记通知”。

因为通知不再是散落在代码里的某个 `notifyAll()` 调用，而是释放方法语义的一部分。

## 一句话总结

```text
入口协议决定线程什么时候可以执行；
出口协议决定状态改变后要不要通知等待线程。
```

`wait/notify` 的正确使用，本质上就是把这两个协议设计清楚。

AQS 则把这套协议框架化了：

```text
同步器子类负责判断获取/释放是否成功；
AQS 负责排队、阻塞和唤醒。
```
