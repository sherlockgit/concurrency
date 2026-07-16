# wait 返回不等于条件已经成立

## 问题

如何理解这句话：

```text
虽然在锁、条件谓词和条件队列之间的三元关系并不复杂，
但 wait 方法的返回并不一定意味着线程正在等待的条件谓词已经变成真了。
```

## 简单理解

这句话的核心意思是：

```text
wait() 返回，只能说明线程不再处于等待状态；
但不能说明它等待的条件现在一定满足。
```

换句话说，`notifyAll()` 只是告诉等待线程：

```text
共享状态可能变了，你醒来重新检查一下。
```

它不是在保证：

```text
你等待的条件现在一定成立。
```

## 结合 BoundedBuffer 理解

以 `14-6` 的 `BoundedBuffer.take()` 为例：

```java
public synchronized V take() throws InterruptedException {
    while (isEmpty()) {
        wait();
    }

    V value = doExtract();
    notifyAll();
    return value;
}
```

消费者调用 `take()` 时，它等待的条件谓词是：

```text
缓冲区非空
```

如果缓冲区为空，消费者就会执行：

```java
wait();
```

这表示：

```text
现在缓冲区为空，我不能继续 take。
我先释放锁并进入条件队列等待。
```

但是，消费者从 `wait()` 返回，并不等于缓冲区现在一定非空。

## wait 返回的几种可能原因

`wait()` 返回可能有多种原因：

| 返回原因 | 条件谓词一定为真吗 |
| --- | --- |
| 其他线程调用了 `notifyAll()` | 不一定 |
| 其他线程调用了 `notify()` | 不一定 |
| 发生虚假唤醒 | 不一定 |
| 等待线程被中断 | 不会正常返回，而是抛出 `InterruptedException` |
| 被唤醒后重新竞争锁，但状态又被其他线程改了 | 不一定 |

所以，线程从 `wait()` 返回后，必须重新检查条件。

## 一个典型场景

假设缓冲区为空：

```text
buffer = []
```

然后两个消费者都来取数据：

```text
消费者 A 调用 take()，发现空，进入 wait。
消费者 B 调用 take()，发现空，进入 wait。
```

此时生产者放入一个元素：

```text
buffer = [100]
```

生产者调用：

```java
notifyAll();
```

于是消费者 A 和消费者 B 都被唤醒。

但注意，被唤醒不等于同时执行。它们还要重新竞争锁。

可能发生下面的顺序：

```text
1. 消费者 A 先抢到锁。
2. 消费者 A 从 wait 返回。
3. 消费者 A 重新检查 isEmpty()，发现缓冲区非空。
4. 消费者 A 取走 100。
5. 缓冲区重新变空。
6. 消费者 A 释放锁。
7. 消费者 B 后抢到锁。
8. 消费者 B 从 wait 返回。
9. 但此时缓冲区已经又空了。
```

对消费者 B 来说：

```text
wait 返回了，但它等待的“缓冲区非空”条件并不成立。
```

这正是书中那句话要强调的问题。

## 为什么不能用 if

错误写法：

```java
public synchronized V take() throws InterruptedException {
    if (isEmpty()) {
        wait();
    }

    return doExtract();
}
```

这个写法的问题是：

```text
线程从 wait 返回后，会直接执行 doExtract()。
```

但如果它醒来后缓冲区仍然为空，就会错误地从空缓冲区中取数据。

所以，`if` 只检查一次条件是不够的。

## 为什么必须用 while

正确写法：

```java
public synchronized V take() throws InterruptedException {
    while (isEmpty()) {
        wait();
    }

    return doExtract();
}
```

`while` 的作用是：

```text
每次从 wait 返回后，都重新检查条件。
如果条件仍然不满足，就继续 wait。
如果条件真的满足，才执行后续操作。
```

所以 `while` 不是为了“多循环几次”，而是为了保证状态依赖操作的正确性。

## notifyAll 的真实含义

`notifyAll()` 的真实含义不是：

```text
你们等待的条件都已经成立了。
```

而是：

```text
共享状态可能已经变化了，你们可以醒来重新检查自己的条件。
```

因此，`notifyAll()` 和 `while` 是配套使用的：

| 操作 | 作用 |
| --- | --- |
| `notifyAll()` | 通知等待线程：状态可能变了 |
| `while` | 等待线程重新确认：我的条件现在是否真的满足 |

## 一句话总结

```text
wait 返回只表示“可以重新检查条件了”，不表示“条件已经满足了”。
```

所以状态依赖方法必须使用：

```java
while (!conditionPredicate()) {
    wait();
}
```

而不是：

```java
if (!conditionPredicate()) {
    wait();
}
```
