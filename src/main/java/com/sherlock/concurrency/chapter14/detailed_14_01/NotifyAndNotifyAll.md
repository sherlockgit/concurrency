# notifyAll 和 notify 的区别

## 问题

`notifyAll()` 和 `notify()` 有什么区别？什么时候应该用 `notifyAll()`，什么时候可以用 `notify()`？

## 核心结论

`notify()` 和 `notifyAll()` 都是配合 `wait()` 使用的，用来通知正在某个对象监视器上等待的线程。

区别在于：

| 方法 | 含义 |
| --- | --- |
| `notify()` | 从当前对象的等待队列中随机唤醒一个等待线程 |
| `notifyAll()` | 唤醒当前对象等待队列中的所有等待线程 |

注意：被唤醒不等于马上执行。

调用 `notify()` 或 `notifyAll()` 的线程仍然持有锁。只有当它退出 `synchronized`，释放锁以后，被唤醒的线程才有机会重新竞争锁，并从 `wait()` 返回。

## 标准使用结构

```java
synchronized (lock) {
    while (条件不满足) {
        lock.wait();
    }

    // 条件满足，执行真正的操作

    lock.notifyAll();
}
```

这里有两个重点：

1. `wait()` 会释放锁，让其他线程有机会修改共享状态。
2. `notify()` 和 `notifyAll()` 不会立即释放锁，只是发出通知。

## 为什么通常推荐使用 notifyAll

以 14-1 中的一槽缓冲区为例：

```java
public synchronized void put(E value) throws InterruptedException {
    while (full) {
        wait();
    }

    item = value;
    full = true;

    notifyAll();
}

public synchronized E take() throws InterruptedException {
    while (!full) {
        wait();
    }

    E result = item;
    item = null;
    full = false;

    notifyAll();
    return result;
}
```

这个缓冲区中有两类等待条件：

| 线程类型 | 等待条件 |
| --- | --- |
| 生产者线程 | 等待缓冲区“非满” |
| 消费者线程 | 等待缓冲区“非空” |

问题是，`notify()` 只会随机唤醒一个线程。它不知道哪个线程的等待条件现在真的满足了。

例如，`take()` 取走元素以后，缓冲区从“满”变成“空”。这时候真正应该被唤醒的是正在等待“非满”的生产者线程。

但如果使用 `notify()`，它可能随机唤醒一个消费者线程。消费者醒来以后重新检查条件，发现缓冲区还是空，于是继续 `wait()`。如果生产者没有被唤醒，程序就可能停住。

`notifyAll()` 的做法更保守：把所有等待线程都叫醒，让它们重新竞争锁，然后各自重新检查自己的条件。条件满足的线程继续执行，条件不满足的线程重新进入 `wait()`。

## 为什么必须配合 while

即使使用了 `notifyAll()`，也必须这样写：

```java
while (条件不满足) {
    wait();
}
```

不能简单写成：

```java
if (条件不满足) {
    wait();
}
```

原因有三个：

1. Java 允许出现虚假唤醒，也就是没有明确通知，线程也可能从 `wait()` 返回。
2. `notifyAll()` 会唤醒很多线程，但不是每个线程的条件都满足。
3. 被唤醒线程重新获得锁之前，其他线程可能已经把状态改掉了。

所以，线程从 `wait()` 返回以后，不能直接相信条件已经满足，必须重新检查。

## notify 的适用场景

`notify()` 不是不能用，但条件比较苛刻。

一般只有在下面这些条件同时成立时，才适合考虑 `notify()`：

1. 同一个锁上只有一种等待条件。
2. 任意唤醒一个等待线程，都一定能让程序继续推进。
3. 不会出现“唤醒了错误类型线程”的问题。
4. 你非常确定不会因为随机唤醒导致线程长期等待。

如果这些条件无法保证，优先使用 `notifyAll()`。

## notifyAll 的代价

`notifyAll()` 会唤醒所有等待线程，因此可能带来额外开销：

1. 更多线程被唤醒。
2. 更多线程参与锁竞争。
3. 条件不满足的线程检查后又要重新等待。

但是在很多业务代码中，这个开销通常比“唤醒错误线程导致程序卡住”的风险更容易接受。

因此，在使用 `synchronized + wait` 编写条件队列时，默认选择 `notifyAll()` 通常更安全。

## 更精确的替代方案

如果希望精确唤醒某一类等待线程，可以使用 `ReentrantLock + Condition`。

`Condition` 可以把不同等待条件拆成不同的等待队列：

```java
private final Lock lock = new ReentrantLock();
private final Condition notFull = lock.newCondition();
private final Condition notEmpty = lock.newCondition();
```

这样：

1. 生产者只在 `notFull` 上等待。
2. 消费者只在 `notEmpty` 上等待。
3. 放入元素后只通知 `notEmpty`。
4. 取出元素后只通知 `notFull`。

相比 `wait/notifyAll`，这种方式更精确，也更适合复杂的并发组件。

## 一句话总结

`notify()` 更省，但可能叫错线程；`notifyAll()` 更吵，但更安全。

在同一个锁上存在多种等待条件时，优先使用 `notifyAll()`。如果要精确控制不同条件的唤醒，应该使用 `ReentrantLock + Condition`。
