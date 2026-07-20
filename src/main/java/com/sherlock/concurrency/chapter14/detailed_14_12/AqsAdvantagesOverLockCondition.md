# AQS 相比 Lock + Condition 的优势

## 问题

如何理解这段话：

```text
AQS 解决了在实现同步器时涉及的大量细节问题，例如等待线程采用 FIFO 队列操作顺序。
在不同的同步器中还可以定义一些灵活的标准来判断某个线程是应该通过还是需要等待。
基于 AQS 来构建同步器能带来许多好处。
它不仅能极大地减少实现工作，而且也不必处理在多个位置上发生的竞争问题。
在 SemaphoreOnLock 中，获取许可的操作可能在两个时刻阻塞：
当锁保护信号量状态时，以及当许可不可用时。
在基于 AQS 构建的同步器中，只可能在一个时刻发生阻塞，
从而降低上下文切换的开销，并提高吞吐量。
```

## 简单理解

这段话是在对比两种同步器实现方式：

```text
方式一：手写 Lock + Condition。
方式二：基于 AQS 实现。
```

`Lock + Condition` 的方式需要自己管理：

```text
1. 状态变量。
2. 加锁和解锁。
3. 条件队列。
4. 等待和通知。
5. 中断、超时、取消等细节。
```

AQS 的方式则是：

```text
你只定义“什么时候能获取，什么时候能释放”；
AQS 负责排队、阻塞、唤醒、FIFO 顺序和大量并发细节。
```

## 先看 SemaphoreOnLock 的问题

`14-12` 中的 `SemaphoreOnLock.acquire()` 大致是：

```java
public void acquire() throws InterruptedException {
    lock.lock();
    try {
        while (permits <= 0) {
            permitsAvailable.await();
        }
        permits--;
    } finally {
        lock.unlock();
    }
}
```

它的逻辑是：

```text
先拿锁。
拿到锁后检查 permits。
如果 permits <= 0，就进入 Condition 等待。
```

这里有两个可能阻塞的位置。

## 第一个阻塞点：等待 lock

线程进入 `acquire()` 时，首先要执行：

```java
lock.lock();
```

如果此时有其他线程正在执行 `acquire()` 或 `release()`，当前线程可能拿不到锁。

于是它会阻塞在：

```text
ReentrantLock 的同步队列上。
```

这是一层等待。

## 第二个阻塞点：等待 permitsAvailable

如果线程拿到了锁，但发现：

```java
permits <= 0
```

它又会执行：

```java
permitsAvailable.await();
```

这时它会：

```text
释放 lock。
进入 permitsAvailable 条件队列。
等待 release() 增加许可后 signal。
```

这又是一层等待。

所以，`SemaphoreOnLock` 中一次获取许可可能经历：

```text
竞争 lock
拿到 lock
检查 permits
发现没有许可
进入 Condition 队列
被 signal 唤醒
重新竞争 lock
再次检查 permits
消耗许可
释放 lock
```

这就是书中说的：

```text
获取许可的操作可能在两个时刻阻塞。
```

## 两套队列带来的问题

`SemaphoreOnLock` 实际上涉及两套队列：

| 队列 | 作用 |
| --- | --- |
| Lock 的同步队列 | 等待获得 `lock` |
| Condition 的条件队列 | 等待 `permits > 0` |

这会带来额外复杂度：

```text
1. 线程可能先等锁，再等条件。
2. 从条件队列被唤醒后，还要重新竞争锁。
3. 上下文切换更多。
4. 实现者要手动处理等待和通知协议。
```

这不是说 `Lock + Condition` 不能用，而是说它更像“用通用组件拼同步器”。

如果要实现高质量同步器，细节会越来越多。

## AQS 是什么

AQS 是：

```text
AbstractQueuedSynchronizer
```

它是 `java.util.concurrent` 中很多同步器的基础。

常见基于 AQS 的类包括：

```text
ReentrantLock
Semaphore
CountDownLatch
ReentrantReadWriteLock
FutureTask 的部分同步思想
```

AQS 提供了一个同步器框架。

它内部主要做两件事：

```text
1. 维护同步状态 state。
2. 维护等待线程队列。
```

## AQS 的 state

AQS 内部有一个核心状态：

```java
private volatile int state;
```

不同同步器会赋予 `state` 不同含义。

| 同步器 | state 的含义 |
| --- | --- |
| `ReentrantLock` | 锁的重入次数 |
| `Semaphore` | 当前可用许可数量 |
| `CountDownLatch` | 剩余计数 |
| `ReentrantReadWriteLock` | 读锁数量和写锁状态 |

AQS 不关心这些具体含义。

它只要求子类回答两个问题：

```text
当前线程能不能获取同步状态？
释放同步状态后，要不要唤醒等待线程？
```

## AQS 的等待队列

如果线程获取同步状态失败，AQS 会把线程加入等待队列。

可以简化理解为：

```text
获取失败的线程排队。
释放同步状态时，AQS 唤醒队列中的后继线程。
```

书中说的 FIFO 队列，就是指这种等待顺序。

FIFO 的意思是：

```text
先进入队列的线程，通常先获得继续竞争的机会。
```

这使同步器可以支持更清晰的排队规则。

例如公平锁：

```text
如果前面有人排队，新来的线程不能插队。
```

非公平锁：

```text
新来的线程可以先尝试抢一次，失败再排队。
```

这些队列维护、线程挂起、线程唤醒的细节，都由 AQS 处理。

## AQS 如何实现 Semaphore 的思路

如果用 AQS 实现信号量，可以把：

```text
state
```

理解成：

```text
可用许可数。
```

获取许可时：

```text
如果 state > 0，就尝试用 CAS 把 state 减 1。
如果 CAS 成功，获取许可成功。
如果 state <= 0，进入 AQS 队列等待。
```

释放许可时：

```text
用 CAS 把 state 加 1。
然后 AQS 根据释放结果唤醒等待线程。
```

它不需要先获取一把外部 `ReentrantLock`，再进入一个 `Condition` 队列。

## CAS 在这里的作用

CAS 可以理解为：

```text
比较当前值是不是我预期的值。
如果是，就原子地改成新值。
如果不是，说明被别人改过，当前操作失败，重新尝试。
```

对于信号量来说，获取许可大致可以理解成：

```text
读取当前许可数。
如果许可数大于 0，尝试用 CAS 把许可数减 1。
CAS 成功，说明拿到许可。
CAS 失败，说明并发竞争中别人先改了，重新尝试。
```

这种方式在许可可用时通常不需要阻塞线程。

失败后才进入 AQS 队列。

## 为什么说 AQS 只在一个时刻发生阻塞

在 `SemaphoreOnLock` 中，线程可能在两个地方阻塞：

```text
1. 等待 lock。
2. 等待 permitsAvailable 条件。
```

而基于 AQS 的同步器中，阻塞点集中在：

```text
AQS 的同步队列。
```

线程获取失败时，进入 AQS 队列并被挂起。

它不需要经历：

```text
先等一把外部锁，再等一个条件队列。
```

所以书中说：

```text
只可能在一个时刻发生阻塞。
```

这句话不是说线程一生只会阻塞一次。

它的意思是：

```text
阻塞机制被集中到了 AQS 的队列中，而不是分散在 Lock 队列和 Condition 队列两个位置。
```

## 为什么能降低上下文切换

上下文切换可以简单理解成：

```text
CPU 暂停运行当前线程，保存它的状态，再切换去运行另一个线程。
```

阻塞、唤醒、重新竞争锁，通常都会增加上下文切换机会。

`SemaphoreOnLock` 的路径可能是：

```text
阻塞等 lock
进入 Condition 队列
signal 后醒来
重新竞争 lock
```

这里涉及更多调度和切换。

AQS 把失败线程统一放入同步队列，由框架控制挂起和唤醒。

路径更集中，额外切换更少。

## 为什么吞吐量更高

吞吐量指的是：

```text
单位时间内完成了多少操作。
```

AQS 提高吞吐量的原因主要有几个。

## 原因一：快速路径更短

例如信号量还有许可时，AQS 可以直接通过 CAS 减少 `state`。

如果成功：

```text
线程直接通过。
```

不需要进入条件队列，也不一定需要挂起。

## 原因二：阻塞路径由框架统一管理

获取失败时，AQS 统一处理：

```text
入队
park 阻塞
处理中断
处理取消
被唤醒后重新尝试获取
```

这些逻辑如果手写，很容易出错。

## 原因三：减少无效唤醒和重复竞争

`Condition.signal` 后，等待线程还要重新竞争锁。

AQS 的同步队列结构更贴近同步器本身的获取/释放语义，能更好地控制唤醒顺序。

## “灵活标准”是什么意思

书中说：

```text
在不同的同步器中还可以定义一些灵活的标准来判断某个线程是应该通过还是需要等待。
```

意思是：

```text
AQS 不规定“通过条件”是什么。
```

不同同步器可以自己定义通过标准。

例如：

| 同步器 | 通过标准 |
| --- | --- |
| `ReentrantLock` | 锁未被占用，或者当前线程已经持有锁 |
| `Semaphore` | 许可数大于 0 |
| `CountDownLatch` | 计数已经变成 0 |
| `ReadWriteLock` | 读写锁兼容规则允许 |

AQS 只提供框架：

```text
如果能获取，就通过。
如果不能获取，就排队等待。
释放时如果可能让别人通过，就唤醒后继线程。
```

## 手写同步器的问题

如果不用 AQS，而是自己用 `Lock + Condition` 写同步器，要处理很多细节：

```text
1. 条件谓词怎么定义。
2. 等待时是否释放锁。
3. 醒来后是否重新检查条件。
4. 状态变化后通知谁。
5. 用 signal 还是 signalAll。
6. 中断怎么处理。
7. 超时怎么处理。
8. 等待线程取消后怎么清理。
9. 排队顺序如何保证。
```

这些问题并不是不能解决，但很容易遗漏。

AQS 的价值就是：

```text
把这些通用问题抽象成框架。
同步器作者只写最核心的状态获取和释放规则。
```

## 一句话总结

```text
SemaphoreOnLock 是用 Lock 和 Condition 拼出来的同步器；
AQS 是专门为构建同步器设计的框架。
```

AQS 的优势是：

```text
1. 减少实现工作。
2. 减少忘记通知、错误唤醒、竞态条件等问题。
3. 用 FIFO 队列统一管理等待线程。
4. 用 state 和 CAS 支持高效获取。
5. 把阻塞集中到一个同步队列中，减少额外上下文切换。
6. 支持不同同步器定义自己的通过标准。
```
